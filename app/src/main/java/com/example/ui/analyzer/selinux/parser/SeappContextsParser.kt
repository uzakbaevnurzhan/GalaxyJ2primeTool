package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.SeappContextEntry

object SeappContextsParser {

    /**
     * Parses a single line from seapp_contexts into SeappContextEntry.
     * Extracts parameters: isSystemServer, isPrivApp, isEphemeralApp, user, seinfo, name, domain, type, levelFrom, level, minTargetSdkVersion.
     * Returns null for comments and blank lines.
     */
    fun parseLine(line: String): SeappContextEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        val pairs = trimmed.split(Regex("\\s+"))
        if (pairs.isEmpty()) return null

        var isSystemServer: Boolean? = null
        var isPrivApp: Boolean? = null
        var isEphemeralApp: Boolean? = null
        var user: String? = null
        var seinfo: String? = null
        var name: String? = null
        var domain: String? = null
        var type: String? = null
        var levelFrom: String? = null
        var level: String? = null
        var minTargetSdkVersion: String? = null

        var isWarning = false
        var warningMsg: String? = null
        var validKeyFound = false

        for (pair in pairs) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx == -1) {
                isWarning = true
                warningMsg = "Malformed token without '=': '$pair'"
                continue
            }
            val key = pair.substring(0, eqIdx).trim()
            val value = pair.substring(eqIdx + 1).trim()

            when (key) {
                "isSystemServer" -> { isSystemServer = value.toBooleanStrictOrNull(); validKeyFound = true }
                "isPrivApp" -> { isPrivApp = value.toBooleanStrictOrNull(); validKeyFound = true }
                "isEphemeralApp" -> { isEphemeralApp = value.toBooleanStrictOrNull(); validKeyFound = true }
                "user" -> { user = value; validKeyFound = true }
                "seinfo" -> { seinfo = value; validKeyFound = true }
                "name" -> { name = value; validKeyFound = true }
                "domain" -> { domain = value; validKeyFound = true }
                "type" -> { type = value; validKeyFound = true }
                "levelFrom" -> { levelFrom = value; validKeyFound = true }
                "level" -> { level = value; validKeyFound = true }
                "minTargetSdkVersion" -> { minTargetSdkVersion = value; validKeyFound = true }
                else -> {
                    // Unknown parameter in seapp_contexts
                    isWarning = true
                    warningMsg = "Unrecognized parameter '$key'"
                }
            }
        }

        if (!validKeyFound) {
            return SeappContextEntry(
                rawLine = trimmed,
                isWarning = true,
                warningMessage = "No recognized seapp_contexts keys found"
            )
        }

        return SeappContextEntry(
            rawLine = trimmed,
            isSystemServer = isSystemServer,
            isPrivApp = isPrivApp,
            isEphemeralApp = isEphemeralApp,
            user = user,
            seinfo = seinfo,
            name = name,
            domain = domain,
            type = type,
            levelFrom = levelFrom,
            level = level,
            minTargetSdkVersion = minTargetSdkVersion,
            isWarning = isWarning,
            warningMessage = warningMsg
        )
    }
}
