package com.example.ui.studio.formats

import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object CpioHandler {
    fun unpack(cpioFile: File, outputDir: File): Boolean {
        if (!cpioFile.exists()) return false
        outputDir.mkdirs()

        try {
            RandomAccessFile(cpioFile, "r").use { raf ->
                val buffer = ByteArray(64 * 1024)
                while (raf.filePointer < raf.length()) {
                    val magicBytes = ByteArray(6)
                    val readLen = raf.read(magicBytes)
                    if (readLen < 6) break
                    val magic = String(magicBytes)
                    if (magic != "070701" && magic != "070702") {
                        break
                    }

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

                    val nameBytes = ByteArray(namesize.toInt())
                    raf.read(nameBytes)
                    val name = String(nameBytes, 0, namesize.toInt() - 1)

                    val headerSize = 110 + namesize
                    val pad = (4 - (headerSize % 4)) % 4
                    raf.skipBytes(pad.toInt())

                    if (name == "TRAILER!!!") {
                        break
                    }

                    val outPath = File(outputDir, name)
                    val type = (mode shr 12) and 0xF

                    when (type) {
                        4L -> { // directory
                            outPath.mkdirs()
                        }
                        8L -> { // regular file
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
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun readHex(raf: RandomAccessFile, len: Int): Long {
        val bytes = ByteArray(len)
        raf.read(bytes)
        return String(bytes).toLongOrNull(16) ?: 0L
    }
}
