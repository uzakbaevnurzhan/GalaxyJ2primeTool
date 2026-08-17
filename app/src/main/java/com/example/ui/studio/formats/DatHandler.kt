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
                    while (lineIdx < lines.size) {
                        val line = lines[lineIdx].trim()
                        val parts = line.split(" ")
                        if (parts.isEmpty()) {
                            lineIdx++
                            continue
                        }

                        val command = parts[0]
                        when (command) {
                            "new", "zero" -> {
                                if (parts.size >= 2) {
                                    val ranges = parts[1].split(",")
                                    if (ranges.size >= 3) {
                                        val count = ranges[0].toIntOrNull() ?: 0
                                        var blockIdx = 1
                                        while (blockIdx < count * 2) {
                                            val start = ranges[blockIdx].toLongOrNull() ?: 0L
                                            val end = ranges[blockIdx + 1].toLongOrNull() ?: 0L
                                            val blocks = end - start
                                            
                                            rawOut.seek(start * 4096)
                                            if (command == "new") {
                                                val data = ByteArray((blocks * 4096).toInt())
                                                datIn.read(data)
                                                rawOut.write(data)
                                            } else {
                                                val zeros = ByteArray((blocks * 4096).toInt())
                                                rawOut.write(zeros)
                                            }
                                            blockIdx += 2
                                        }
                                    }
                                }
                            }
                            else -> {
                                // Ignore 'erase', 'free', etc.
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
