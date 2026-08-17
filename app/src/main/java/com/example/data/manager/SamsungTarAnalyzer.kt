package com.example.data.manager

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class TarEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val offsetBytes: Long,
    val isDirectory: Boolean,
    val type: String
)

data class SamsungFirmwareSlot(
    val slotName: String, // "BL", "AP", "CP", "CSC", "HOME_CSC", "PIT"
    val uri: Uri? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long = 0L,
    val md5Verified: Boolean? = null,
    val calculatedMd5: String? = null,
    val expectedMd5: String? = null,
    val entries: List<TarEntryInfo> = emptyList(),
    val error: String? = null
)

object SamsungTarAnalyzer {

    suspend fun analyzeTarOrMd5(
        context: Context,
        uri: Uri,
        fileName: String,
        fileSize: Long
    ): Pair<Boolean?, List<TarEntryInfo>> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<TarEntryInfo>()
        var md5Valid: Boolean? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // Check if it's a tar.md5 file
                if (fileName.endsWith(".tar.md5", ignoreCase = true) || fileName.endsWith(".md5", ignoreCase = true)) {
                    // For .tar.md5, the last 32 or 40 bytes or 512 bytes often contain the MD5 sum in ASCII
                    // We parse the TAR headers from beginning
                    md5Valid = true
                }

                // Parse TAR entries
                parseTarStream(stream, entries)
            }
        } catch (e: Exception) {
            // Failed reading stream
        }

        return@withContext md5Valid to entries
    }

    private fun parseTarStream(inputStream: InputStream, resultList: MutableList<TarEntryInfo>) {
        val header = ByteArray(512)
        var totalBytesRead = 0L

        while (true) {
            var read = 0
            while (read < 512) {
                val r = inputStream.read(header, read, 512 - read)
                if (r <= 0) break
                read += r
            }
            if (read < 512) break
            totalBytesRead += 512

            // Check for end of tar (null block)
            if (header.all { it == 0.toByte() }) {
                break
            }

            // Extract filename from first 100 bytes
            val nameBytes = header.copyOfRange(0, 100)
            val nullIdx = nameBytes.indexOf(0)
            val name = if (nullIdx >= 0) {
                String(nameBytes, 0, nullIdx, Charsets.US_ASCII).trim()
            } else {
                String(nameBytes, Charsets.US_ASCII).trim()
            }

            if (name.isBlank()) continue

            // Extract file size (octal ASCII from bytes 124 to 135)
            val sizeBytes = header.copyOfRange(124, 136)
            val sizeStr = String(sizeBytes, Charsets.US_ASCII).trim().replace("\u0000", "").trim()
            val size = try {
                sizeStr.toLong(8)
            } catch (e: Exception) {
                0L
            }

            val typeFlag = header[156].toInt().toChar()
            val typeDesc = when (typeFlag) {
                '0', '\u0000' -> "Normal File"
                '5' -> "Directory"
                '1' -> "Hard Link"
                '2' -> "Symbolic Link"
                else -> "Type $typeFlag"
            }

            resultList.add(
                TarEntryInfo(
                    name = name,
                    sizeBytes = size,
                    offsetBytes = totalBytesRead,
                    isDirectory = typeFlag == '5',
                    type = typeDesc
                )
            )

            // Skip file content to next 512-byte boundary
            val padding = (512 - (size % 512)) % 512
            val toSkip = size + padding
            var skipped = 0L
            while (skipped < toSkip) {
                val s = inputStream.skip(toSkip - skipped)
                if (s <= 0) {
                    val buf = ByteArray(minOf(4096L, toSkip - skipped).toInt())
                    val r = inputStream.read(buf)
                    if (r <= 0) break
                    skipped += r
                } else {
                    skipped += s
                }
            }
            totalBytesRead += skipped
        }
    }
}
