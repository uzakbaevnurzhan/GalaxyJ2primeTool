package com.example.ui.analyzer.partition

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.channels.FileChannel

object PartitionTableParser {

    fun parseFile(file: File): PartitionTableAnalyzer.AnalysisResultInternal {
        val fileName = file.name.lowercase()
        if (!file.exists() || file.length() == 0L) {
            return PartitionTableAnalyzer.AnalysisResultInternal(
                PartitionTable(),
                listOf(
                    PartitionIssue(
                        id = "FILE_EMPTY_OR_NOT_FOUND",
                        severity = PartitionIssueSeverity.CRITICAL,
                        title = "File Not Found or Empty",
                        description = "Specified partition table file does not exist or has 0 bytes length: ${file.absolutePath}"
                    )
                )
            )
        }

        // Check if text based (scatter or /proc/partitions)
        if (fileName.endsWith(".txt") || fileName.endsWith(".scatter") || fileName.endsWith(".cfg") || fileName.endsWith(".xml")) {
            return parseTextFile(file)
        }

        // Try Binary GPT / MBR
        FileInputStream(file).use { fis ->
            val channel = fis.channel
            // 1. Try GPT
            val gptResult = GptParser.parse(channel)
            if (gptResult != null) {
                return PartitionTableAnalyzer.AnalysisResultInternal(
                    gptResult.table.copy(sourceName = file.name),
                    gptResult.issues
                )
            }

            // 2. Try MBR
            val mbrResult = MbrParser.parse(channel)
            if (mbrResult != null) {
                return PartitionTableAnalyzer.AnalysisResultInternal(
                    mbrResult.table.copy(sourceName = file.name),
                    mbrResult.issues
                )
            }
        }

        // If binary didn't match, fallback to trying as text
        return parseTextFile(file)
    }

    private fun parseTextFile(file: File): PartitionTableAnalyzer.AnalysisResultInternal {
        return try {
            file.inputStream().use { stream ->
                val result = ScatterParser.parse(stream, file.name)
                PartitionTableAnalyzer.AnalysisResultInternal(result.table, result.issues)
            }
        } catch (e: Exception) {
            PartitionTableAnalyzer.AnalysisResultInternal(
                PartitionTable(sourceName = file.name),
                listOf(
                    PartitionIssue(
                        id = "PARSE_ERROR",
                        severity = PartitionIssueSeverity.CRITICAL,
                        title = "Failed to Parse File",
                        description = e.message ?: "Unknown parse error"
                    )
                )
            )
        }
    }

    fun parseStream(stream: InputStream, name: String): PartitionTableAnalyzer.AnalysisResultInternal {
        val result = ScatterParser.parse(stream, name)
        return PartitionTableAnalyzer.AnalysisResultInternal(result.table, result.issues)
    }
}
