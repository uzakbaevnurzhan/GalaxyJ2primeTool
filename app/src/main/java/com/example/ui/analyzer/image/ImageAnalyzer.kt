package com.example.ui.analyzer.image

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.ui.analyzer.core.AnalyzerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

object ImageAnalyzer {

    suspend fun analyzeUri(
        context: Context,
        uri: Uri,
        onProgress: (progress: Float, phase: String, speedMbPerSec: Double) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): ImageAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            var fileName = "unknown.img"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }

            onProgress(0.1f, "Inspecting partition headers...", 0.0)

            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val channel = fis.channel

                    if (fileSize == 0L) fileSize = channel.size()

                    // Step 1: Detect format via magic numbers
                    val detectedFormat = ImageFormatDetector.detectFromChannel(channel)
                    val finalFormat = if (detectedFormat == ImageFormat.RAW || detectedFormat == ImageFormat.UNKNOWN) {
                        val byExt = ImageFormat.fromExtension(fileName)
                        if (byExt != ImageFormat.UNKNOWN) byExt else detectedFormat
                    } else {
                        detectedFormat
                    }

                    onProgress(0.3f, "Parsing filesystem structure (${finalFormat.displayName})...", 0.0)

                    // Step 2: Parse header / superblock / metadata
                    var metadata = ImageHeaderParser.parseHeader(channel, finalFormat, fileName)
                    if (metadata.fileSize == 0L) {
                        metadata = metadata.copy(fileSize = fileSize)
                    }

                    var partitionsList = mutableListOf<ImagePartition>()

                    // Step 3: Handle Super / Dynamic Partitions
                    if (finalFormat == ImageFormat.SUPER) {
                        val superRes = SuperImageAnalyzer.analyze(channel)
                        if (superRes != null) {
                            partitionsList.addAll(superRes.partitions)
                        }
                    } else {
                        // Single partition representation
                        val partName = inferPartitionName(fileName, metadata.volumeName, metadata.mountPointHint)
                        partitionsList.add(
                            ImagePartition(
                                name = partName,
                                sizeBytes = if (metadata.uncompressedSize > 0) metadata.uncompressedSize else fileSize,
                                startOffset = 0L,
                                blockCount = metadata.totalBlocks,
                                filesystem = finalFormat,
                                isReadOnly = metadata.isReadOnly,
                                groupName = "default"
                            )
                        )
                    }

                    onProgress(0.6f, "Computing checksums & analyzing integrity...", 0.0)

                    // Step 4: Compute Quick Header & Stream Hashes (Stream first 16MB for large files or full for < 64MB)
                    val (md5, sha256) = computeFastHashes(channel, isCancelled) { speed ->
                        onProgress(0.8f, "Hashing partition stream...", speed)
                    }

                    metadata = metadata.copy(
                        md5Hash = md5,
                        sha256Hash = sha256
                    )

                    onProgress(0.9f, "Running porting diagnostics & ABI checks...", 0.0)

                    // Step 5: Issues & Diagnostics
                    val issues = AndroidImageAnalyzer.analyzeIssues(
                        metadata = metadata,
                        partitions = partitionsList,
                        properties = emptyMap(),
                        elfArchs = emptySet()
                    )

                    val summary = buildSummaryString(metadata, partitionsList, issues)
                    val details = buildDetailsString(metadata, partitionsList, issues)

                    onProgress(1.0f, "Analysis complete", 0.0)

                    return@withContext ImageAnalysisResult(
                        status = AnalyzerStatus.SUCCESS,
                        summary = summary,
                        details = details,
                        metadata = metadata,
                        partitions = partitionsList,
                        issues = issues,
                        processingTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            } ?: run {
                return@withContext ImageAnalysisResult(
                    status = AnalyzerStatus.ERROR,
                    summary = "Failed to open file",
                    details = "ContentResolver could not open file descriptor for: $uri",
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            return@withContext ImageAnalysisResult(
                status = AnalyzerStatus.ERROR,
                summary = "Analysis failed with exception",
                details = e.stackTraceToString(),
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Analyzes an extracted ROM directory (SAF DocumentFile directory)
     */
    suspend fun analyzeDirectory(
        context: Context,
        dirUri: Uri,
        onProgress: (progress: Float, phase: String, speedMbPerSec: Double) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): ImageAnalysisResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, dirUri)
                ?: return@withContext ImageAnalysisResult(
                    status = AnalyzerStatus.ERROR,
                    summary = "Invalid Directory",
                    details = "Cannot access DocumentFile from URI: $dirUri"
                )

            val files = rootDoc.listFiles()
            val totalFiles = files.size
            var processed = 0

            val partitions = mutableListOf<ImagePartition>()
            val detectedIssues = mutableListOf<ImageIssue>()
            val detectedProps = mutableMapOf<String, String>()
            val elfArchs = mutableSetOf<String>()

            onProgress(0.1f, "Scanning workspace files (${files.size} found)...", 0.0)

            for (fileDoc in files) {
                if (isCancelled()) break
                processed++
                val fName = fileDoc.name ?: continue
                onProgress(0.1f + (processed.toFloat() / totalFiles) * 0.7f, "Analyzing $fName...", 0.0)

                // Check for build.prop
                if (fName == "build.prop" || fName.endsWith(".prop")) {
                    context.contentResolver.openInputStream(fileDoc.uri)?.use { stream ->
                        val props = parsePropertiesStream(stream)
                        detectedProps.putAll(props)
                    }
                }

                // Check for partition images
                if (fName.endsWith(".img") || fName.endsWith(".simg") || fName.endsWith(".dat.br") || fName.endsWith(".dat")) {
                    val subRes = analyzeUri(context, fileDoc.uri, { _, _, _ -> }, isCancelled)
                    partitions.addAll(subRes.partitions)
                    detectedIssues.addAll(subRes.issues)
                }
            }

            // Cross-module issue check
            val combinedMeta = ImageMetadata(
                fileName = rootDoc.name ?: "ROM Workspace",
                fileSize = partitions.sumOf { it.sizeBytes },
                format = if (partitions.any { it.filesystem == ImageFormat.SUPER }) ImageFormat.SUPER else ImageFormat.RAW,
                filesystemType = if (partitions.isNotEmpty()) partitions.map { it.filesystem.displayName }.distinct().joinToString(", ") else "Mixed",
                uncompressedSize = partitions.sumOf { it.sizeBytes },
                totalBlocks = partitions.sumOf { it.blockCount }
            )

            val crossIssues = AndroidImageAnalyzer.analyzeIssues(
                metadata = combinedMeta,
                partitions = partitions,
                properties = detectedProps,
                elfArchs = elfArchs
            )
            detectedIssues.addAll(crossIssues)

            val summary = "ROM Workspace: ${partitions.size} partitions detected, ${detectedIssues.size} issues."
            val details = buildDetailsString(combinedMeta, partitions, detectedIssues)

            onProgress(1.0f, "Workspace analysis complete", 0.0)

            return@withContext ImageAnalysisResult(
                status = AnalyzerStatus.SUCCESS,
                summary = summary,
                details = details,
                metadata = combinedMeta,
                partitions = partitions,
                issues = detectedIssues.distinctBy { it.id },
                detectedProperties = detectedProps,
                elfArchitecturesFound = elfArchs,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return@withContext ImageAnalysisResult(
                status = AnalyzerStatus.ERROR,
                summary = "Directory analysis failed",
                details = e.stackTraceToString(),
                processingTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun parsePropertiesStream(stream: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
                val idx = trimmed.indexOf('=')
                if (idx > 0) {
                    val k = trimmed.substring(0, idx).trim()
                    val v = trimmed.substring(idx + 1).trim()
                    map[k] = v
                }
            }
        }
        return map
    }

    private fun inferPartitionName(fileName: String, volumeName: String, mountHint: String): String {
        val lower = fileName.lowercase()
        return when {
            volumeName.isNotEmpty() && volumeName != "(none)" -> volumeName
            mountHint.isNotEmpty() && mountHint.startsWith("/") -> mountHint.removePrefix("/")
            lower.contains("system") -> "system"
            lower.contains("vendor") -> "vendor"
            lower.contains("product") -> "product"
            lower.contains("odm") -> "odm"
            lower.contains("system_ext") -> "system_ext"
            lower.contains("boot") -> "boot"
            lower.contains("recovery") -> "recovery"
            lower.contains("userdata") || lower.contains("data") -> "userdata"
            lower.contains("cache") -> "cache"
            lower.contains("super") -> "super"
            else -> fileName.removeSuffix(".img").removeSuffix(".raw").removeSuffix(".bin")
        }
    }

    private fun computeFastHashes(
        channel: FileChannel,
        isCancelled: () -> Boolean,
        onSpeed: (Double) -> Unit
    ): Pair<String, String> {
        val originalPos = channel.position()
        try {
            channel.position(0)
            val md5 = MessageDigest.getInstance("MD5")
            val sha256 = MessageDigest.getInstance("SHA-256")

            // Hash up to first 32MB for quick verification without lagging UI
            val maxHashBytes = minOf(channel.size(), 32L * 1024L * 1024L)
            val buffer = ByteBuffer.allocate(64 * 1024)
            var bytesHashed = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLast = 0L

            while (bytesHashed < maxHashBytes && !isCancelled()) {
                val toRead = minOf(buffer.capacity().toLong(), maxHashBytes - bytesHashed).toInt()
                buffer.clear()
                buffer.limit(toRead)
                val read = channel.read(buffer)
                if (read <= 0) break

                buffer.flip()
                val array = buffer.array()
                md5.update(array, 0, read)
                sha256.update(array, 0, read)

                bytesHashed += read
                bytesSinceLast += read

                val now = System.currentTimeMillis()
                if (now - lastTime >= 300) {
                    val speed = (bytesSinceLast / (1024.0 * 1024.0)) / ((now - lastTime) / 1000.0)
                    onSpeed(speed)
                    lastTime = now
                    bytesSinceLast = 0L
                }
            }

            val md5Hex = md5.digest().joinToString("") { "%02x".format(it) }
            val sha256Hex = sha256.digest().joinToString("") { "%02x".format(it) }
            return Pair(md5Hex, sha256Hex)
        } catch (e: Exception) {
            return Pair("", "")
        } finally {
            channel.position(originalPos)
        }
    }

    private fun buildSummaryString(meta: ImageMetadata, parts: List<ImagePartition>, issues: List<ImageIssue>): String {
        val sb = StringBuilder()
        sb.appendLine("Format: ${meta.format.displayName}")
        sb.appendLine("Size: ${formatBytes(meta.fileSize)} | Raw: ${formatBytes(meta.uncompressedSize)}")
        sb.appendLine("Filesystem: ${meta.filesystemType} | Block Size: ${meta.blockSize}B")
        if (parts.isNotEmpty()) sb.appendLine("Partitions: ${parts.size} (${parts.joinToString { it.name }})")
        sb.appendLine("Issues: ${issues.size} (Critical: ${issues.count { it.severity == IssueSeverity.CRITICAL }})")
        return sb.toString().trim()
    }

    private fun buildDetailsString(meta: ImageMetadata, parts: List<ImagePartition>, issues: List<ImageIssue>): String {
        val sb = StringBuilder()
        sb.appendLine("--- METADATA ---")
        for ((k, v) in meta.rawHeaderFields) {
            sb.appendLine("$k = $v")
        }
        sb.appendLine("\n--- PARTITIONS ---")
        for (p in parts) {
            sb.appendLine("[${p.name}] ${formatBytes(p.sizeBytes)} | FS: ${p.filesystem.displayName} | Offset: 0x${java.lang.Long.toHexString(p.startOffset)}")
        }
        sb.appendLine("\n--- ISSUES ---")
        for (i in issues) {
            sb.appendLine("[${i.severity.name}] ${i.title}: ${i.description}")
        }
        return sb.toString().trim()
    }

    private fun formatBytes(bytes: Long): String {
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        return when {
            bytes >= gib -> String.format(java.util.Locale.US, "%.2f GB", bytes / gib)
            bytes >= mib -> String.format(java.util.Locale.US, "%.2f MB", bytes / mib)
            bytes >= kib -> String.format(java.util.Locale.US, "%.2f KB", bytes / kib)
            else -> "$bytes B"
        }
    }
}
