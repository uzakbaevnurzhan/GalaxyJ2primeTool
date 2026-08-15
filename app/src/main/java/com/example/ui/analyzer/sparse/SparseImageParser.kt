package com.example.ui.analyzer.sparse

import android.content.Context
import android.net.Uri
import com.example.ui.analyzer.core.AnalyzerResult
import com.example.ui.analyzer.core.AnalyzerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseImageParser {
    private const val SPARSE_MAGIC = 0xED26FF3A.toInt()

    suspend fun parse(context: Context, uri: Uri): AnalyzerResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val headerBytes = ByteArray(28)
                var read = inputStream.read(headerBytes)
                if (read < 28) {
                    return@withContext AnalyzerResult(
                        status = AnalyzerStatus.ERROR,
                        summary = "File too small",
                        details = "File is smaller than the 28-byte sparse header."
                    )
                }

                val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buffer.int
                
                if (magic != SPARSE_MAGIC) {
                    return@withContext AnalyzerResult(
                        status = AnalyzerStatus.UNSUPPORTED,
                        summary = "Not a sparse image",
                        details = "Magic number mismatch. Expected 0xED26FF3A, got 0x${Integer.toHexString(magic).uppercase()}"
                    )
                }

                val majorVersion = buffer.short
                val minorVersion = buffer.short
                val fileHdrSz = buffer.short
                val chunkHdrSz = buffer.short
                val blkSz = buffer.int
                val totalBlks = buffer.int
                val totalChunks = buffer.int
                val imageChecksum = buffer.int

                val summary = StringBuilder()
                summary.appendLine("Android Sparse Image Detected")
                summary.appendLine("Version: $majorVersion.$minorVersion")
                summary.appendLine("Block Size: $blkSz bytes")
                summary.appendLine("Total Blocks: $totalBlks")
                summary.appendLine("Total Chunks: $totalChunks")
                
                val outputSize = totalBlks.toLong() * blkSz.toLong()
                summary.appendLine("Estimated Raw Size: ${formatBytes(outputSize)}")

                val details = StringBuilder()
                details.appendLine("--- Chunk Information ---")
                
                // Read chunks
                var chunkCount = 0
                var blockCount = 0
                var rawChunks = 0
                var fillChunks = 0
                var dontCareChunks = 0
                var crcChunks = 0
                
                // Skip any extra header bytes
                if (fileHdrSz > 28) {
                    inputStream.skip((fileHdrSz - 28).toLong())
                }

                while (chunkCount < totalChunks) {
                    val chunkHeaderBytes = ByteArray(chunkHdrSz.toInt())
                    read = inputStream.read(chunkHeaderBytes)
                    if (read < chunkHdrSz.toInt()) break // EOF

                    val chunkBuf = ByteBuffer.wrap(chunkHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val chunkType = chunkBuf.short.toInt() and 0xFFFF
                    val reserved1 = chunkBuf.short
                    val chunkBlks = chunkBuf.int
                    val totalSz = chunkBuf.int // chunk header + data

                    when (chunkType) {
                        0xCAC1 -> rawChunks++
                        0xCAC2 -> fillChunks++
                        0xCAC3 -> dontCareChunks++
                        0xCAC4 -> crcChunks++
                    }

                    // Skip the chunk data
                    val dataSize = totalSz - chunkHdrSz
                    if (dataSize > 0) {
                        var skipped = 0L
                        while (skipped < dataSize) {
                            val s = inputStream.skip(dataSize - skipped)
                            if (s <= 0) break
                            skipped += s
                        }
                    }

                    blockCount += chunkBlks
                    chunkCount++
                }

                details.appendLine("Parsed Chunks: $chunkCount / $totalChunks")
                details.appendLine("RAW Chunks: $rawChunks")
                details.appendLine("FILL Chunks: $fillChunks")
                details.appendLine("DONT CARE Chunks: $dontCareChunks")
                details.appendLine("CRC32 Chunks: $crcChunks")

                val status = if (chunkCount == totalChunks) AnalyzerStatus.SUCCESS else AnalyzerStatus.WARNING
                if (status == AnalyzerStatus.WARNING) {
                    details.appendLine("\nWARNING: Unexpected EOF reached before parsing all chunks.")
                }

                AnalyzerResult(
                    status = status,
                    summary = summary.toString().trim(),
                    details = details.toString().trim(),
                    metadata = mapOf(
                        "magic" to "0xED26FF3A",
                        "block_size" to blkSz.toString(),
                        "output_size" to outputSize.toString()
                    )
                )
            } ?: AnalyzerResult(AnalyzerStatus.ERROR, "Failed to open file", "")
        } catch (e: Exception) {
            AnalyzerResult(AnalyzerStatus.ERROR, "Exception occurred", e.message ?: "Unknown error")
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.2f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }
}
