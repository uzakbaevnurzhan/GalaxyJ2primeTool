package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.FileContextEntry

object FileContextsParser {

    private val FILE_TYPE_QUALIFIERS = setOf("--", "-d", "-c", "-b", "-s", "-l", "-p")

    /**
     * Parses a single line from file_contexts into FileContextEntry.
     * Returns null for comments and empty lines.
     */
    fun parseLine(line: String): FileContextEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        val pattern = tokens[0]
        if (tokens.size == 1) {
            return FileContextEntry(pattern, null, null, trimmed)
        }

        var qualifier: String? = null
        var contextStr: String? = null

        if (tokens.size >= 3 && tokens[1] in FILE_TYPE_QUALIFIERS) {
            qualifier = tokens[1]
            contextStr = tokens[2]
        } else if (tokens.size >= 2) {
            if (tokens[1] in FILE_TYPE_QUALIFIERS) {
                qualifier = tokens[1]
                if (tokens.size >= 3) contextStr = tokens[2]
            } else {
                contextStr = tokens[1]
            }
        }

        val isNone = contextStr == "<<none>>"
        val parsedContext = if (isNone || contextStr == null) null else SelinuxContextParser.parse(contextStr)

        return FileContextEntry(
            pathRegex = pattern,
            fileTypeQualifier = qualifier,
            context = parsedContext,
            rawLine = trimmed,
            isNone = isNone
        )
    }
}
