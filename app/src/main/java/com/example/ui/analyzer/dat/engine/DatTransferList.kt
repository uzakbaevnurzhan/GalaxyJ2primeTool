package com.example.ui.analyzer.dat.engine

import java.io.BufferedReader

data class DatTransferList(
    val version: Int,
    val totalBlocks: Long,
    val stashEntries: Int,
    val stashMaxBlocks: Long,
    val commands: List<DatCommand>
) {
    val newBlocks = commands.filterIsInstance<DatCommand.New>().sumOf { it.blockSet.totalBlocks }
    val zeroBlocks = commands.filterIsInstance<DatCommand.Zero>().sumOf { it.blockSet.totalBlocks }
    val eraseBlocks = commands.filterIsInstance<DatCommand.Erase>().sumOf { it.blockSet.totalBlocks }
    val isIncremental = commands.any { it is DatCommand.Move || it is DatCommand.Stash }

    companion object {
        fun parse(reader: BufferedReader): DatTransferList {
            val versionStr = reader.readLine() ?: throw IllegalArgumentException("Missing version")
            val version = versionStr.trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid version: ${'$'}versionStr")
            val totalBlocks = reader.readLine()?.trim()?.toLongOrNull() ?: 0L
            val stashEntries = reader.readLine()?.trim()?.toIntOrNull() ?: 0
            val stashMaxBlocks = reader.readLine()?.trim()?.toLongOrNull() ?: 0L

            val commands = mutableListOf<DatCommand>()
            var line = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val parts = trimmed.split(" ", limit = 3)
                    if (parts.isNotEmpty()) {
                        val cmd = when (parts[0]) {
                            "new" -> DatCommand.New(DatBlockSet.parse(parts[1]), trimmed)
                            "zero" -> DatCommand.Zero(DatBlockSet.parse(parts[1]), trimmed)
                            "erase" -> DatCommand.Erase(DatBlockSet.parse(parts[1]), trimmed)
                            "move" -> DatCommand.Move(trimmed.substringAfter("move "), trimmed)
                            "stash" -> DatCommand.Stash(trimmed.substringAfter("stash "), trimmed)
                            "free" -> DatCommand.Free(trimmed.substringAfter("free "), trimmed)
                            else -> DatCommand.Unknown(trimmed, trimmed)
                        }
                        commands.add(cmd)
                    }
                }
                line = reader.readLine()
            }
            return DatTransferList(version, totalBlocks, stashEntries, stashMaxBlocks, commands)
        }
    }
}
