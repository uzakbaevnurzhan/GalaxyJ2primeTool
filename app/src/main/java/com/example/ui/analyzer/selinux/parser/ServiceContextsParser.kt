package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.ServiceContextEntry

object ServiceContextsParser {

    /**
     * Parses a single line from service_contexts into ServiceContextEntry.
     * Returns null for comments and empty lines.
     */
    fun parseLine(line: String): ServiceContextEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        val serviceName = tokens[0]
        val contextStr = if (tokens.size >= 2) tokens[1] else null
        val parsedContext = SelinuxContextParser.parse(contextStr)

        return ServiceContextEntry(
            serviceName = serviceName,
            context = parsedContext,
            rawLine = trimmed
        )
    }
}
