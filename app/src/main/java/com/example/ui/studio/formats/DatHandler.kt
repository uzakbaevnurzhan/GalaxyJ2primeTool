package com.example.ui.studio.formats

import java.io.File
import java.io.RandomAccessFile

object DatHandler {
    fun datToRaw(transferList: File, datFile: File, rawFile: File): Boolean {
        if (!transferList.exists() || !datFile.exists()) return false
        try {
            val lines = transferList.readLines()
            if (lines.size < 4) return false
            val version = lines[0].toIntOrNull() ?: return false
            val newBlocks = lines[1].toIntOrNull() ?: return false

            RandomAccessFile(datFile, "r").use { datIn ->
                RandomAccessFile(rawFile, "rw").use { rawOut ->
                    var lineIdx = if (version >= 2) 4 else 2
                    val buffer = ByteArray(4096 * 256) // 1MB buffer

                    while (lineIdx < lines.size) {
                        val line = lines[lineIdx].trim()
                        val parts = line.split(" ")
                        if (parts.isEmpty()) {
                            lineIdx++
                            continue
                        }

                        val command = parts[0]
                        when (command) {
                            "new", "zero", "erase", "free" -> {
                                if (parts.size >= 2) {
                                    val ranges = parts[1].split(",")
                                    if (ranges.size >= 3) {
                                        val count = ranges[0].toIntOrNull() ?: 0
                                        var blockIdx = 1
                                        while (blockIdx < count * 2) {
                                            val start = ranges[blockIdx].toLongOrNull() ?: 0L
                                            val end = ranges[blockIdx + 1].toLongOrNull() ?: 0L
                                            val blocks = end - start
                                            
                                            if (blocks > 0) {
                                                rawOut.seek(start * 4096)
                                                var remainingBytes = blocks * 4096
                                                
                                                if (command == "new") {
                                                    while (remainingBytes > 0) {
                                                        val toRead = remainingBytes.coerceAtMost(buffer.size.toLong()).toInt()
                                                        val read = datIn.read(buffer, 0, toRead)
                                                        if (read == -1) break
                                                        rawOut.write(buffer, 0, read)
                                                        remainingBytes -= read
                                                    }
                                                } else { // zero, erase, free
                                                    buffer.fill(0)
                                                    while (remainingBytes > 0) {
                                                        val toWrite = remainingBytes.coerceAtMost(buffer.size.toLong()).toInt()
                                                        rawOut.write(buffer, 0, toWrite)
                                                        remainingBytes -= toWrite
                                                    }
                                                }
                                            }
                                            blockIdx += 2
                                        }
                                    }
                                }
                            }
                            else -> {
                                // move, stash
                            }
                        }
                        lineIdx++
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
