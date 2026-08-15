package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.GenfsContextEntry

object GenfsContextsParser {

    /**
     * Parses a single line from genfs_contexts into GenfsContextEntry.
     * Pattern: genfscon <fs_name> <path> <context>
     * Returns null for comments and blank lines.
     */
    fun parseLine(line: String): GenfsContextEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.isEmpty()) return null

        if (tokens[0] == "genfscon") {
            if (tokens.size >= 4) {
                val fs = tokens[1]
                val path = tokens[2]
                val ctxStr = tokens[3]
                val ctx = SelinuxContextParser.parse(ctxStr)
                return GenfsContextEntry(fs, path, ctx, trimmed)
            } else if (tokens.size == 3) {
                val fs = tokens[1]
                val path = tokens[2]
                return GenfsContextEntry(fs, path, null, trimmed)
            }
        } else if (tokens.size >= 3) {
            // In case "genfscon" keyword was omitted
            val fs = tokens[0]
            val path = tokens[1]
            val ctx = SelinuxContextParser.parse(tokens[2])
            return GenfsContextEntry(fs, path, ctx, trimmed)
        }

        return null
    }
}
