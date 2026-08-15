package com.example.ui.analyzer.kernel.parser

import com.example.ui.analyzer.kernel.model.KernelArchitecture
import java.util.regex.Pattern

data class ParsedKernelVersion(
    val kernelRelease: String?,
    val fullVersionString: String,
    val compilerInfo: String?,
    val buildDate: String?,
    val architecture: KernelArchitecture
)

object KernelVersionParser {

    private val LINUX_VERSION_REGEX = Pattern.compile("Linux\\s+version\\s+([0-9]+\\.[0-9]+[0-9a-zA-Z._+-]*)", Pattern.CASE_INSENSITIVE)
    private val COMPILER_REGEX = Pattern.compile("\\((?:gcc\\s+version|clang\\s+version|Android\\s+\\(|Android\\s+clang)[^)]*\\)", Pattern.CASE_INSENSITIVE)
    private val DATE_REGEX = Pattern.compile("#\\d+\\s+SMP\\s+PREEMPT\\s+([A-Za-z0-9: ]+)", Pattern.CASE_INSENSITIVE)

    fun parse(line: String): ParsedKernelVersion? {
        if (!line.contains("Linux version", ignoreCase = true)) return null

        val verMatcher = LINUX_VERSION_REGEX.matcher(line)
        val kernelRelease = if (verMatcher.find()) verMatcher.group(1) else null

        val compMatcher = COMPILER_REGEX.matcher(line)
        val compilerInfo = if (compMatcher.find()) compMatcher.group(0)?.removeSurrounding("(", ")") else null

        val dateMatcher = DATE_REGEX.matcher(line)
        val buildDate = if (dateMatcher.find()) dateMatcher.group(1)?.trim() else null

        val arch = detectArchitecture(line)

        return ParsedKernelVersion(
            kernelRelease = kernelRelease,
            fullVersionString = line.trim(),
            compilerInfo = compilerInfo,
            buildDate = buildDate,
            architecture = arch
        )
    }

    fun detectArchitecture(text: String): KernelArchitecture {
        val lower = text.lowercase()
        return when {
            lower.contains("aarch64") || lower.contains("arm64") || lower.contains("armv8") || lower.contains("arm64-v8a") -> KernelArchitecture.ARM64
            lower.contains("armv7") || lower.contains("armv7l") || lower.contains("armv6") || lower.contains("armeabi") || (lower.contains("arm") && !lower.contains("arm64")) -> KernelArchitecture.ARM32
            lower.contains("x86_64") || lower.contains("amd64") -> KernelArchitecture.X86_64
            lower.contains("x86") || lower.contains("i386") || lower.contains("i686") -> KernelArchitecture.X86
            lower.contains("mips") -> KernelArchitecture.MIPS
            lower.contains("riscv") -> KernelArchitecture.RISCV
            else -> KernelArchitecture.UNKNOWN
        }
    }
}
