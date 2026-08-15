package com.example.ui.analyzer.text

import android.content.Context
import android.net.Uri
import com.example.ui.analyzer.core.AnalyzerResult
import com.example.ui.analyzer.core.AnalyzerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object TextHexViewer {

    suspend fun readTextChunk(context: Context, uri: Uri, offset: Long = 0, length: Int = 1024 * 64): AnalyzerResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                if (offset > 0) {
                    var skipped = 0L
                    while (skipped < offset) {
                        val s = inputStream.skip(offset - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                }
                
                val buffer = ByteArray(length)
                val read = inputStream.read(buffer)
                
                if (read <= 0) {
                     return@withContext AnalyzerResult(AnalyzerStatus.WARNING, "End of file", "No more data to read.")
                }
                
                val text = String(buffer, 0, read)
                val isTruncated = read == length
                
                AnalyzerResult(
                    status = AnalyzerStatus.SUCCESS,
                    summary = "Text Preview (Offset: $offset, Bytes: $read)",
                    details = text + if (isTruncated) "\n\n... [TRUNCATED, USE PAGINATION TO SEE MORE] ..." else ""
                )
            } ?: AnalyzerResult(AnalyzerStatus.ERROR, "Failed to open file", "")
        } catch (e: Exception) {
            AnalyzerResult(AnalyzerStatus.ERROR, "Exception occurred", e.message ?: "Unknown error")
        }
    }

    suspend fun readHexChunk(context: Context, uri: Uri, offset: Long = 0, length: Int = 1024 * 4): AnalyzerResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                if (offset > 0) {
                    var skipped = 0L
                    while (skipped < offset) {
                        val s = inputStream.skip(offset - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                }
                
                val buffer = ByteArray(length)
                val read = inputStream.read(buffer)
                
                if (read <= 0) {
                     return@withContext AnalyzerResult(AnalyzerStatus.WARNING, "End of file", "No more data to read.")
                }
                
                val sb = StringBuilder()
                for (i in 0 until read step 16) {
                    val lineOffset = offset + i
                    sb.append(String.format("%08X  ", lineOffset))
                    
                    // Hex part
                    for (j in 0 until 16) {
                        if (i + j < read) {
                            sb.append(String.format("%02X ", buffer[i + j]))
                        } else {
                            sb.append("   ")
                        }
                        if (j == 7) sb.append(" ")
                    }
                    
                    sb.append(" |")
                    
                    // ASCII part
                    for (j in 0 until 16) {
                        if (i + j < read) {
                            val b = buffer[i + j]
                            if (b in 32..126) {
                                sb.append(b.toInt().toChar())
                            } else {
                                sb.append('.')
                            }
                        }
                    }
                    sb.append("|\n")
                }
                
                val isTruncated = read == length
                
                AnalyzerResult(
                    status = AnalyzerStatus.SUCCESS,
                    summary = "Hex Preview (Offset: $offset, Bytes: $read)",
                    details = sb.toString() + if (isTruncated) "\n... [TRUNCATED, USE PAGINATION TO SEE MORE] ..." else ""
                )
            } ?: AnalyzerResult(AnalyzerStatus.ERROR, "Failed to open file", "")
        } catch (e: Exception) {
            AnalyzerResult(AnalyzerStatus.ERROR, "Exception occurred", e.message ?: "Unknown error")
        }
    }
}
