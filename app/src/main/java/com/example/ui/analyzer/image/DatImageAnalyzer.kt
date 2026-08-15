package com.example.ui.analyzer.image

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object DatImageAnalyzer {
    data class DatTransferInfo(
        val version: Int,
        val totalBlocks: Long,
        val maxStashBlocks: Long,
        val commandCount: Int,
        val eraseCommands: Int,
        val newCommands: Int,
        val zeroCommands: Int,
        val stashCommands: Int,
        val freeCommands: Int,
        val totalNewBlocks: Long,
        val isTransferListValid: Boolean
    )

    fun parseTransferList(inputStream: InputStream): DatTransferInfo? {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val vLine = reader.readLine()?.trim() ?: return null
            val version = vLine.toIntOrNull() ?: return null

            val tBlocksLine = reader.readLine()?.trim() ?: return null
            val totalBlocks = tBlocksLine.toLongOrNull() ?: return null

            var maxStash = 0L
            if (version >= 2) {
                val stashEntries = reader.readLine()?.trim()?.toLongOrNull() ?: 0L
                val maxStashLine = reader.readLine()?.trim()?.toLongOrNull() ?: 0L
                maxStash = maxStashLine
            }

            var cmdCount = 0
            var eraseCount = 0
            var newCount = 0
            var zeroCount = 0
            var stashCount = 0
            var freeCount = 0
            var totalNewBlocks = 0L

            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    cmdCount++
                    val parts = trimmed.split(" ")
                    val cmd = parts[0]
                    when (cmd) {
                        "erase" -> eraseCount++
                        "new" -> {
                            newCount++
                            if (parts.size >= 2) {
                                val ranges = parseRangeSet(parts[1])
                                totalNewBlocks += ranges.sumOf { (it.second - it.first) }
                            }
                        }
                        "zero" -> zeroCount++
                        "stash" -> stashCount++
                        "free" -> freeCount++
                    }
                }
                line = reader.readLine()
            }

            return DatTransferInfo(
                version = version,
                totalBlocks = totalBlocks,
                maxStashBlocks = maxStash,
                commandCount = cmdCount,
                eraseCommands = eraseCount,
                newCommands = newCount,
                zeroCommands = zeroCount,
                stashCommands = stashCount,
                freeCommands = freeCount,
                totalNewBlocks = totalNewBlocks,
                isTransferListValid = totalBlocks > 0 && cmdCount > 0
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseRangeSet(rangeStr: String): List<Pair<Long, Long>> {
        val tokens = rangeStr.split(",")
        if (tokens.isEmpty()) return emptyList()
        val numPairs = tokens[0].toIntOrNull() ?: return emptyList()
        val list = mutableListOf<Pair<Long, Long>>()
        var idx = 1
        while (idx + 1 < tokens.size) {
            val start = tokens[idx].toLongOrNull() ?: 0L
            val end = tokens[idx + 1].toLongOrNull() ?: 0L
            list.add(Pair(start, end))
            idx += 2
        }
        return list
    }
}
