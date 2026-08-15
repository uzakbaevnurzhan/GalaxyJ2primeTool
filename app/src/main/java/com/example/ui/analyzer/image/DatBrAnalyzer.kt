package com.example.ui.analyzer.image

import org.brotli.dec.BrotliInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object DatBrAnalyzer {
    fun decompressStream(
        compressedInput: InputStream,
        rawOutput: OutputStream,
        onProgress: (bytesWritten: Long, speedMbPerSec: Double) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Pair<Long, String> {
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val brotliStream = BrotliInputStream(compressedInput)
        val buffer = ByteArray(64 * 1024)
        var totalWritten = 0L
        var lastTime = System.currentTimeMillis()
        var bytesSinceLastTime = 0L

        try {
            while (!isCancelled()) {
                val read = brotliStream.read(buffer)
                if (read <= 0) break

                rawOutput.write(buffer, 0, read)
                md5Digest.update(buffer, 0, read)
                sha256Digest.update(buffer, 0, read)

                totalWritten += read
                bytesSinceLastTime += read

                val now = System.currentTimeMillis()
                if (now - lastTime >= 300) {
                    val speed = (bytesSinceLastTime / (1024.0 * 1024.0)) / ((now - lastTime) / 1000.0)
                    onProgress(totalWritten, speed)
                    lastTime = now
                    bytesSinceLastTime = 0L
                }
            }
            rawOutput.flush()
            val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            return Pair(totalWritten, sha256Hex)
        } finally {
            try { brotliStream.close() } catch (e: Exception) {}
        }
    }
}
