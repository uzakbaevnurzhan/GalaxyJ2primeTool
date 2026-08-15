package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.AvcDenial
import com.example.ui.analyzer.selinux.model.SelinuxContext
import java.util.regex.Pattern

object AvcParser {

    private val PERM_BLOCK_REGEX = Pattern.compile("avc:\\s+(denied|granted)\\s+\\{([^}]+)\\}", Pattern.CASE_INSENSITIVE)
    private val SCONTEXT_REGEX = Pattern.compile("scontext=([^\\s]+)")
    private val TCONTEXT_REGEX = Pattern.compile("tcontext=([^\\s]+)")
    private val TCLASS_REGEX = Pattern.compile("tclass=([^\\s]+)")
    private val PID_REGEX = Pattern.compile("pid=(\\d+)")
    private val UID_REGEX = Pattern.compile("uid=(\\d+)")
    private val COMM_REGEX = Pattern.compile("comm=(?:\"([^\"]+)\"|([^\\s]+))")
    private val PATH_REGEX = Pattern.compile("path=(?:\"([^\"]+)\"|([^\\s]+))")
    private val NAME_REGEX = Pattern.compile("name=(?:\"([^\"]+)\"|([^\\s]+))")
    private val SERVICE_REGEX = Pattern.compile("service=(?:\"([^\"]+)\"|([^\\s]+))")
    private val PERMISSIVE_REGEX = Pattern.compile("permissive=(\\d+)")
    private val INO_REGEX = Pattern.compile("ino=(\\d+)")
    private val DEV_REGEX = Pattern.compile("dev=(?:\"([^\"]+)\"|([^\\s]+))")
    private val IOCTL_REGEX = Pattern.compile("ioctlcmd=([^\\s]+)")

    private val AUDIT_TIME_REGEX = Pattern.compile("audit\\(([0-9.]+):\\d+\\)")
    private val DMESG_TIME_REGEX = Pattern.compile("^\\[\\s*([0-9.]+)\\]")
    private val LOGCAT_TIME_REGEX = Pattern.compile("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})")

    /**
     * Checks if a line is likely an SELinux AVC audit log entry.
     */
    fun isAvcLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("avc: denied") || lower.contains("avc:  denied") ||
               lower.contains("avc: granted") || lower.contains("avc:  granted")
    }

    /**
     * Parses a single log line into an AvcDenial object.
     * Returns null if the line does not contain an AVC event or cannot be parsed.
     */
    fun parseLine(line: String): AvcDenial? {
        if (!isAvcLine(line)) return null

        val permMatcher = PERM_BLOCK_REGEX.matcher(line)
        if (!permMatcher.find()) return null

        val operation = permMatcher.group(1)?.lowercase() ?: "denied"
        val rawPermissions = permMatcher.group(2)?.trim() ?: ""
        val permissions = rawPermissions.split(Regex("\\s+")).filter { it.isNotBlank() }

        val scontextStr = extractRegex(SCONTEXT_REGEX, line)
        val tcontextStr = extractRegex(TCONTEXT_REGEX, line)
        val tclass = extractRegex(TCLASS_REGEX, line)

        val scontext = SelinuxContextParser.parse(scontextStr)
        val tcontext = SelinuxContextParser.parse(tcontextStr)

        val pid = extractRegex(PID_REGEX, line)?.toIntOrNull()
        val uid = extractRegex(UID_REGEX, line)?.toIntOrNull()
        val comm = extractQuotedOrPlain(COMM_REGEX, line)
        val path = extractQuotedOrPlain(PATH_REGEX, line) ?: extractQuotedOrPlain(NAME_REGEX, line)
        val serviceName = extractQuotedOrPlain(SERVICE_REGEX, line)

        val permissiveInt = extractRegex(PERMISSIVE_REGEX, line)?.toIntOrNull()
        val isPermissive = permissiveInt == 1

        val ino = extractRegex(INO_REGEX, line)?.toLongOrNull()
        val dev = extractQuotedOrPlain(DEV_REGEX, line)
        val ioctlCmd = extractRegex(IOCTL_REGEX, line)

        // Extract timestamp from various formats
        val timestamp = extractTimestamp(line)

        return AvcDenial(
            rawLine = line,
            timestamp = timestamp,
            comm = comm,
            pid = pid,
            uid = uid,
            scontext = scontext,
            tcontext = tcontext,
            tclass = tclass,
            permissions = permissions,
            path = path,
            isPermissive = isPermissive,
            operation = operation,
            ino = ino,
            dev = dev,
            ioctlCmd = ioctlCmd,
            serviceName = serviceName
        )
    }

    private fun extractRegex(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractQuotedOrPlain(pattern: Pattern, text: String): String? {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1) ?: matcher.group(2)
        }
        return null
    }

    private fun extractTimestamp(line: String): String? {
        val logcatMatcher = LOGCAT_TIME_REGEX.matcher(line)
        if (logcatMatcher.find()) return logcatMatcher.group(1)

        val dmesgMatcher = DMESG_TIME_REGEX.matcher(line)
        if (dmesgMatcher.find()) return "[${dmesgMatcher.group(1)}s]"

        val auditMatcher = AUDIT_TIME_REGEX.matcher(line)
        if (auditMatcher.find()) return auditMatcher.group(1)

        return null
    }
}
