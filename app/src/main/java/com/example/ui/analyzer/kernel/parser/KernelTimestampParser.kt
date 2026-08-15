package com.example.ui.analyzer.kernel.parser

import java.util.regex.Pattern

data class ParsedTimestamp(
    val rawTimestamp: String,
    val uptimeSeconds: Double? = null,
    val cleanedLine: String
)

object KernelTimestampParser {

    private val DMESG_REGEX = Pattern.compile("^\\[\\s*([0-9]+\\.[0-9]+)\\]\\s*(.*)$")
    private val DMESG_LEVEL_REGEX = Pattern.compile("^\\[\\s*([0-9]+\\.[0-9]+)\\]\\s*<[0-9]+>\\s*(.*)$")
    private val LOGCAT_REGEX = Pattern.compile("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+[^:]*:\\s*(.*)$")
    private val LOGCAT_SHORT_REGEX = Pattern.compile("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(.*)$")
    private val SYSLOG_REGEX = Pattern.compile("^([A-Z][a-z]{2}\\s+\\d+\\s+\\d{2}:\\d{2}:\\d{2})\\s+[^\\s]+\\s+kernel:\\s*(.*)$")

    fun parseLine(line: String): ParsedTimestamp {
        val trimmed = line.trim()

        // 1. Try dmesg with log level e.g. [ 123.456789] <0> [1:swapper/0] ...
        val dmesgLevelMatcher = DMESG_LEVEL_REGEX.matcher(trimmed)
        if (dmesgLevelMatcher.find()) {
            val tsStr = dmesgLevelMatcher.group(1) ?: ""
            val rest = dmesgLevelMatcher.group(2) ?: ""
            val uptime = tsStr.toDoubleOrNull()
            return ParsedTimestamp("[$tsStr]", uptime, rest.trim())
        }

        // 2. Try standard dmesg e.g. [ 123.456789] Kernel panic ...
        val dmesgMatcher = DMESG_REGEX.matcher(trimmed)
        if (dmesgMatcher.find()) {
            val tsStr = dmesgMatcher.group(1) ?: ""
            val rest = dmesgMatcher.group(2) ?: ""
            val uptime = tsStr.toDoubleOrNull()
            return ParsedTimestamp("[$tsStr]", uptime, rest.trim())
        }

        // 3. Try standard logcat e.g. 08-15 12:30:20.123 1234 1234 E Kernel: ...
        val logcatMatcher = LOGCAT_REGEX.matcher(trimmed)
        if (logcatMatcher.find()) {
            val tsStr = logcatMatcher.group(1) ?: ""
            val rest = logcatMatcher.group(2) ?: ""
            return ParsedTimestamp(tsStr, null, rest.trim())
        }

        // 4. Try short logcat
        val shortLogcatMatcher = LOGCAT_SHORT_REGEX.matcher(trimmed)
        if (shortLogcatMatcher.find()) {
            val tsStr = shortLogcatMatcher.group(1) ?: ""
            val rest = shortLogcatMatcher.group(2) ?: ""
            return ParsedTimestamp(tsStr, null, rest.trim())
        }

        // 5. Try syslog
        val syslogMatcher = SYSLOG_REGEX.matcher(trimmed)
        if (syslogMatcher.find()) {
            val tsStr = syslogMatcher.group(1) ?: ""
            val rest = syslogMatcher.group(2) ?: ""
            return ParsedTimestamp(tsStr, null, rest.trim())
        }

        return ParsedTimestamp("", null, trimmed)
    }
}
