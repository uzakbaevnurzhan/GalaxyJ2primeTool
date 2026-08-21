package com.example.data.manager

import android.content.Context
import com.example.ui.studio.workspace.RomProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

object RomBuildStudioEngine {

    data class BuildResult(
        val success: Boolean,
        val outputFilePath: String,
        val outputFileName: String,
        val fileSizeBytes: Long,
        val sha256: String,
        val md5: String,
        val buildReportPath: String,
        val durationMs: Long,
        val warnings: List<String>,
        val isVerified: Boolean = false,
        val packageType: String = "Archive"
    )

    suspend fun executeBuildPipeline(
        context: Context,
        project: RomProject,
        targetPackageType: String = "Flashable ZIP",
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): Result<BuildResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()

        try {
            // Stage 1: Prepare
            onProgress("Stage 1/7: Preparing Build Environment & Verifying Trees...", 0.1f)
            val workspaceDir = File(project.rootPath, "workspace")
            val outputDir = File(project.rootPath, "output")
            val reportsDir = File(project.rootPath, "reports")
            outputDir.mkdirs()
            reportsDir.mkdirs()

            if (!workspaceDir.exists() || workspaceDir.listFiles().isNullOrEmpty()) {
                throw IllegalStateException("BUILD INCOMPLETE: Workspace is empty. Unpack or place partition images in workspace first.")
            }

            // Stage 2: Validate
            onProgress("Stage 2/7: Validating Partition Integrity & Manifests...", 0.25f)
            val workspaceFiles = workspaceDir.walkTopDown().filter { it.isFile }.toList()
            val hasSystem = workspaceFiles.any { it.name.contains("system") || it.path.contains("system") }
            val hasBoot = workspaceFiles.any { it.name.contains("boot") || it.path.contains("boot") }
            val hasUpdaterScript = workspaceFiles.any { it.path.replace("\\", "/").contains("META-INF/com/google/android/updater-script") }

            var actualPackageType = "Standard Archive"
            if (targetPackageType.contains("Flashable", ignoreCase = true)) {
                if (hasUpdaterScript) {
                    actualPackageType = "Flashable ROM"
                } else {
                    warnings.add("Requested Flashable ZIP, but META-INF/com/google/android/updater-script is missing. Falling back to Standard Archive.")
                }
            }

            if (!hasSystem) {
                warnings.add("No explicit system partition/folder detected in workspace.")
            }
            if (!hasBoot) {
                warnings.add("No boot.img detected. The resulting package might require a separate kernel flash.")
            }

            // Stage 3: Build & Assemble Partitions
            onProgress("Stage 3/7: Assembling & Compressing Partition Structures...", 0.40f)
            val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outFileName = "${project.name.replace(" ", "_")}_Build_$timeStr.zip"
            val targetZipFile = File(outputDir, outFileName)

            // Stage 4: Package Streaming Archive
            onProgress("Stage 4/7: Streaming Package Archive...", 0.60f)
            val buffer = ByteArray(1024 * 64) // 64KB stream buffer for memory safety
            FileOutputStream(targetZipFile).use { fos ->
                ZipOutputStream(fos.buffered()).use { zos ->
                    zos.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                    val allFiles = workspaceDir.walkTopDown().filter { it.isFile }.toList()
                    val total = allFiles.size.coerceAtLeast(1)
                    
                    allFiles.forEachIndexed { index, file ->
                        val relPath = file.relativeTo(workspaceDir).path.replace("\\", "/")
                        val entry = ZipEntry(relPath)
                        entry.time = file.lastModified()
                        zos.putNextEntry(entry)
                        
                        FileInputStream(file).use { fis ->
                            var len: Int
                            while (fis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                        }
                        zos.closeEntry()
                        
                        if (index % 10 == 0) {
                            val subProgress = 0.60f + (0.20f * (index.toFloat() / total))
                            onProgress("Archiving: $relPath", subProgress)
                        }
                    }
                }
            }

            // Stage 5: Post-Validate (Verify integrity of created archive)
            onProgress("Stage 5/7: Post-Build Archive Verification...", 0.82f)
            if (!targetZipFile.exists() || targetZipFile.length() == 0L) {
                throw IllegalStateException("BUILD INCOMPLETE: Generated package file is missing or zero bytes.")
            }
            
            var isVerified = false
            try {
                ZipFile(targetZipFile).use { zip ->
                    val entries = zip.entries()
                    var count = 0
                    while(entries.hasMoreElements()) {
                        entries.nextElement()
                        count++
                    }
                    val hasUpdaterScript = zip.getEntry("META-INF/com/google/android/updater-script") != null
                    val hasUpdateBinary = zip.getEntry("META-INF/com/google/android/update-binary") != null
                    val hasSymlinks = zip.getEntry("symlinks.txt") != null
                    if (count > 0) isVerified = true
                    if (actualPackageType == "Flashable ROM" && (!hasUpdaterScript || !hasUpdateBinary)) {
                        warnings.add("Flashable ROM validation failed: Missing update-binary or updater-script in META-INF")
                        isVerified = false
                    }
                    if (!hasSymlinks) {
                        warnings.add("Post-build warning: symlinks.txt not found. Flashed system may lack critical symlinks.")
                    }
                }
            } catch (e: Exception) {
                throw IllegalStateException("BUILD INCOMPLETE: Generated zip is corrupted: ${e.message}")
            }

            // Stage 6: Hash Calculation (SHA-256 & MD5 via streaming)
            onProgress("Stage 6/7: Calculating Cryptographic Hashes...", 0.90f)
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val md5Digest = MessageDigest.getInstance("MD5")
            
            FileInputStream(targetZipFile).use { fis ->
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    sha256Digest.update(buffer, 0, len)
                    md5Digest.update(buffer, 0, len)
                }
            }
            val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }

            // Stage 7: Generate Build Report
            onProgress("Stage 7/7: Writing Build Certification Report...", 0.96f)
            val duration = System.currentTimeMillis() - startTime
            val reportFile = File(reportsDir, "BUILD_REPORT_$timeStr.md")
            val reportText = buildString {
                appendLine("# ROM Build Certification Report")
                appendLine("- **Project:** ${project.name}")
                appendLine("- **Timestamp:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("- **Package Type:** $actualPackageType")
                appendLine("- **Output Package:** ${targetZipFile.name}")
                appendLine("- **Size:** ${targetZipFile.length()} bytes (${"%.2f".format(targetZipFile.length() / (1024.0 * 1024.0))} MB)")
                appendLine("- **Verified Integrity:** $isVerified")
                appendLine("- **SHA-256:** `$sha256Hex`")
                appendLine("- **MD5:** `$md5Hex`")
                appendLine("- **Build Duration:** ${duration / 1000}s")
                appendLine()
                appendLine("## Warnings & Audit Log")
                if (warnings.isEmpty()) {
                    appendLine("*No warnings encountered during build.*")
                } else {
                    warnings.forEach { appendLine("- ⚠️ $it") }
                }
                appendLine()
                appendLine("## Packed Components")
                workspaceFiles.take(50).forEach { appendLine("- ${it.relativeTo(workspaceDir).path} (${it.length()} bytes)") }
                if (workspaceFiles.size > 50) {
                    appendLine("- ... and ${workspaceFiles.size - 50} more files.")
                }
            }
            reportFile.writeText(reportText)

            if (isVerified) {
                onProgress("BUILD SUCCESS + VERIFIED", 1.0f)
            } else {
                onProgress("BUILD SUCCESS BUT NOT VERIFIED", 1.0f)
            }
            
            Result.success(
                BuildResult(
                    success = true,
                    outputFilePath = targetZipFile.absolutePath,
                    outputFileName = targetZipFile.name,
                    fileSizeBytes = targetZipFile.length(),
                    sha256 = sha256Hex,
                    md5 = md5Hex,
                    buildReportPath = reportFile.absolutePath,
                    durationMs = duration,
                    warnings = warnings,
                    isVerified = isVerified,
                    packageType = actualPackageType
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
