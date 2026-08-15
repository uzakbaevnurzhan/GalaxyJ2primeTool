package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.PropertyContextEntry

object PropertyContextsParser {

    /**
     * Parses a single line from property_contexts into PropertyContextEntry.
     * Returns null for comments and empty lines.
     */
    fun parseLine(line: String): PropertyContextEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        val pattern = tokens[0]
        val contextStr = if (tokens.size >= 2) tokens[1] else null
        val typeClass = if (tokens.size >= 3) tokens[2] else null

        val parsedContext = SelinuxContextParser.parse(contextStr)

        return PropertyContextEntry(
            propertyPattern = pattern,
            context = parsedContext,
            typeClass = typeClass,
            rawLine = trimmed
        )
    }
}
