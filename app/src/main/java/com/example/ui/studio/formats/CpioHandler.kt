package com.example.ui.studio.formats

import android.system.Os
import com.example.utils.SecurityUtil
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object CpioHandler {
    private const val MAX_ENTRIES = 50_000
    private const val MAX_TOTAL_SIZE = 2L * 1024 * 1024 * 1024 // 2 GB

    fun unpack(cpioFile: File, outputDir: File): Boolean {
        if (!cpioFile.exists() || cpioFile.length() == 0L) return false
        outputDir.mkdirs()

        var entriesCount = 0
        var totalBytesWritten = 0L
        var validHeaderEncountered = false

        try {
            RandomAccessFile(cpioFile, "r").use { raf ->
                val buffer = ByteArray(64 * 1024)
                while (raf.filePointer < raf.length()) {
                    val magicBytes = ByteArray(6)
                    val readLen = raf.read(magicBytes)
                    if (readLen < 6) break
                    val magic = String(magicBytes)
                    if (magic != "070701" && magic != "070702") {
                        if (!validHeaderEncountered) return false
                        break
                    }
                    validHeaderEncountered = true

                    val ino = readHex(raf, 8)
                    val mode = readHex(raf, 8)
                    val uid = readHex(raf, 8)
                    val gid = readHex(raf, 8)
                    val nlink = readHex(raf, 8)
                    val mtime = readHex(raf, 8)
                    val filesize = readHex(raf, 8)
                    val devmajor = readHex(raf, 8)
                    val devminor = readHex(raf, 8)
                    val rdevmajor = readHex(raf, 8)
                    val rdevminor = readHex(raf, 8)
                    val namesize = readHex(raf, 8)
                    val check = readHex(raf, 8)

                    if (namesize <= 0 || namesize > 4096) {
                        return false
                    }

                    val nameBytes = ByteArray(namesize.toInt())
                    raf.read(nameBytes)
                    val rawName = String(nameBytes, 0, (namesize.toInt() - 1).coerceAtLeast(0))

                    val headerSize = 110 + namesize
                    val pad = (4 - (headerSize % 4)) % 4
                    raf.skipBytes(pad.toInt())

                    if (rawName == "TRAILER!!!") {
                        break
                    }

                    // Security: Safe resolution inside outputDir
                    val outPath = try {
                        SecurityUtil.safeResolve(outputDir, rawName)
                    } catch (e: SecurityException) {
                        // Skip or reject malicious entry
                        return false
                    }

                    entriesCount++
                    if (entriesCount > MAX_ENTRIES) {
                        return false
                    }

                    val type = (mode shr 12) and 0xF

                    when (type) {
                        4L -> { // directory
                            outPath.mkdirs()
                        }
                        8L -> { // regular file
                            totalBytesWritten += filesize
                            if (totalBytesWritten > MAX_TOTAL_SIZE) {
                                return false
                            }
                            outPath.parentFile?.mkdirs()
                            FileOutputStream(outPath).use { fos ->
                                var remaining = filesize
                                while (remaining > 0) {
                                    val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                                    val r = raf.read(buffer, 0, toRead)
                                    if (r == -1) break
                                    fos.write(buffer, 0, r)
                                    remaining -= r
                                }
                            }
                        }
                        10L -> { // symlink
                            outPath.parentFile?.mkdirs()
                            val targetBytes = ByteArray(filesize.toInt())
                            raf.read(targetBytes)
                            val target = String(targetBytes, 0, filesize.toInt())
                            try {
                                outPath.delete()
                                Os.symlink(target, outPath.absolutePath)
                            } catch (e: Exception) {
                                outPath.writeText("SYMLINK:$target")
                            }
                        }
                        else -> { // block, char, fifo
                            raf.skipBytes(filesize.toInt())
                        }
                    }

                    val dataPad = (4 - (filesize % 4)) % 4
                    raf.skipBytes(dataPad.toInt())
                }
            }
            return validHeaderEncountered
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun readHex(raf: RandomAccessFile, len: Int): Long {
        val bytes = ByteArray(len)
        val read = raf.read(bytes)
        if (read < len) return 0L
        return String(bytes).toLongOrNull(16) ?: 0L
    }
}

