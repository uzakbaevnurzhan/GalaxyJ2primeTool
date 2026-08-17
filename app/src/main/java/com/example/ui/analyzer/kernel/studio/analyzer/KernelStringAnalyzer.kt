package com.example.ui.analyzer.kernel.studio.analyzer

import com.example.ui.analyzer.kernel.studio.models.KernelVersionInfo

object KernelStringAnalyzer {

    data class StringAnalysisResult(
        val versionInfo: KernelVersionInfo,
        val buildDate: String,
        val compiler: String,
        val compilerVersion: String,
        val isSmp: Boolean,
        val isPreempt: Boolean,
        val hasModuleSupport: Boolean,
        val rawStrings: List<String>,
        val configLines: List<String>
    )

    fun analyze(bytes: ByteArray, maxScanBytes: Int = 8 * 1024 * 1024): StringAnalysisResult {
        val strings = extractAsciiStrings(bytes, maxScanBytes)

        var versionInfo = KernelVersionInfo()
        var buildDate = "UNKNOWN"
        var compiler = "UNKNOWN"
        var compilerVersion = "UNKNOWN"
        var isSmp = false
        var isPreempt = false
        var hasModuleSupport = false
        val configLines = mutableListOf<String>()

        val linuxVersionRegex = Regex("""Linux version (\d+\.\d+[\w.-]*)""", RegexOption.IGNORE_CASE)
        val gccRegex = Regex("""gcc version ([\d.]+([^\s)]+)?)""", RegexOption.IGNORE_CASE)
        val clangRegex = Regex("""clang version ([\d.]+([^\s)]+)?)""", RegexOption.IGNORE_CASE)
        val llvmRegex = Regex("""LLVM version ([\d.]+([^\s)]+)?)""", RegexOption.IGNORE_CASE)

        for (s in strings) {
            // Check Linux Version
            if (versionInfo.fullString == "UNKNOWN") {
                val match = linuxVersionRegex.find(s)
                if (match != null) {
                    val parsed = KernelVersionParser.parse(s)
                    versionInfo = parsed
                }
            }

            // Check Compiler
            if (compiler == "UNKNOWN") {
                val gccMatch = gccRegex.find(s)
                if (gccMatch != null) {
                    compiler = "GCC"
                    compilerVersion = gccMatch.groupValues[1]
                } else {
                    val clangMatch = clangRegex.find(s)
                    if (clangMatch != null) {
                        compiler = "Clang"
                        compilerVersion = clangMatch.groupValues[1]
                    } else {
                        val llvmMatch = llvmRegex.find(s)
                        if (llvmMatch != null) {
                            compiler = "LLVM"
                            compilerVersion = llvmMatch.groupValues[1]
                        }
                    }
                }
            }

            // Check SMP / PREEMPT
            if (s.contains("SMP", ignoreCase = false)) {
                isSmp = true
            }
            if (s.contains("PREEMPT", ignoreCase = false)) {
                isPreempt = true
            }

            // Check module support flag strings
            if (s.contains("sys_init_module") || s.contains("sys_finit_module") || s.contains("module_layout")) {
                hasModuleSupport = true
            }

            // Check build dates (e.g. "SMP PREEMPT Thu Jan 1 00:00:00 UTC 2021" or "(root@buildhost) (gcc ... ) #1 SMP ...")
            if (buildDate == "UNKNOWN" && (s.contains("UTC ") || s.contains("KST ") || s.contains("CST ") || s.contains("EST "))) {
                if (s.contains("201") || s.contains("202")) {
                    buildDate = s.trim()
                }
            }

            // Check Config lines
            if (s.startsWith("CONFIG_") && s.contains("=")) {
                configLines.add(s)
            }
        }

        val finalVersionInfo = versionInfo.copy(
            buildDate = buildDate,
            compiler = compiler,
            compilerVersion = compilerVersion,
            isSmp = isSmp,
            isPreempt = isPreempt,
            hasModuleSupport = hasModuleSupport
        )

        return StringAnalysisResult(
            versionInfo = finalVersionInfo,
            buildDate = buildDate,
            compiler = compiler,
            compilerVersion = compilerVersion,
            isSmp = isSmp,
            isPreempt = isPreempt,
            hasModuleSupport = hasModuleSupport,
            rawStrings = strings,
            configLines = configLines
        )
    }

    private fun extractAsciiStrings(bytes: ByteArray, maxScan: Int): List<String> {
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        val limit = bytes.size.coerceAtMost(maxScan)

        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                if (sb.length >= 4) {
                    strings.add(sb.toString())
                }
                sb.setLength(0)
            }
        }
        if (sb.length >= 4) strings.add(sb.toString())
        return strings
    }
}
