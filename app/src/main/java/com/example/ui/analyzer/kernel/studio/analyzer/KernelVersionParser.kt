package com.example.ui.analyzer.kernel.studio.analyzer

import com.example.ui.analyzer.kernel.studio.models.KernelVersionInfo

object KernelVersionParser {

    private val LINUX_VERSION_REGEX = Regex(
        """Linux version (\d+)\.(\d+)(?:\.(\d+))?([^\s]*)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(versionString: String): KernelVersionInfo {
        if (versionString.isBlank() || versionString == "UNKNOWN") {
            return KernelVersionInfo()
        }

        val match = LINUX_VERSION_REGEX.find(versionString)
        if (match != null) {
            val major = match.groupValues[1].toIntOrNull() ?: 0
            val minor = match.groupValues[2].toIntOrNull() ?: 0
            val patch = match.groupValues[3].toIntOrNull() ?: 0
            val extra = match.groupValues[4]

            return KernelVersionInfo(
                fullString = match.value,
                major = major,
                minor = minor,
                patch = patch,
                extraVersion = extra
            )
        }

        // Try direct numbers
        val simpleMatch = Regex("""(\d+)\.(\d+)\.(\d+)""").find(versionString)
        if (simpleMatch != null) {
            return KernelVersionInfo(
                fullString = versionString,
                major = simpleMatch.groupValues[1].toIntOrNull() ?: 0,
                minor = simpleMatch.groupValues[2].toIntOrNull() ?: 0,
                patch = simpleMatch.groupValues[3].toIntOrNull() ?: 0,
                extraVersion = ""
            )
        }

        return KernelVersionInfo(fullString = versionString)
    }
}
