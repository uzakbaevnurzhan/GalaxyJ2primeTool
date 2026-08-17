package com.example.ui.analyzer.kernel.studio.analyzer

import com.example.ui.analyzer.kernel.studio.models.CmdlineCategory
import com.example.ui.analyzer.kernel.studio.models.CmdlineComparisonItem
import com.example.ui.analyzer.kernel.studio.models.KernelCmdlineEntry

object KernelCmdlineAnalyzer {

    fun parse(cmdline: String, source: String = "boot.img"): List<KernelCmdlineEntry> {
        if (cmdline.isBlank()) return emptyList()

        val tokens = splitCmdline(cmdline)
        val entries = mutableListOf<KernelCmdlineEntry>()

        for (token in tokens) {
            val (key, value) = if (token.contains("=")) {
                val p = token.split("=", limit = 2)
                Pair(p[0].trim(), p[1].trim())
            } else {
                Pair(token.trim(), null)
            }

            val category = categorizeCmdline(key)
            val description = describeCmdline(key, value)

            entries.add(
                KernelCmdlineEntry(
                    key = key,
                    value = value,
                    raw = token,
                    category = category,
                    source = source,
                    description = description
                )
            )
        }

        return entries
    }

    fun compare(bootEntries: List<KernelCmdlineEntry>, liveEntries: List<KernelCmdlineEntry>): List<CmdlineComparisonItem> {
        val bootMap = bootEntries.associateBy { it.key }
        val liveMap = liveEntries.associateBy { it.key }
        val allKeys = (bootMap.keys + liveMap.keys).sorted()

        val results = mutableListOf<CmdlineComparisonItem>()
        for (k in allKeys) {
            val bootItem = bootMap[k]
            val liveItem = liveMap[k]

            val status = when {
                bootItem != null && liveItem != null -> {
                    if (bootItem.value == liveItem.value) "MATCH" else "DIFFERENCE"
                }
                bootItem != null -> "BOOT_ONLY"
                else -> "LIVE_ONLY"
            }

            results.add(
                CmdlineComparisonItem(
                    key = k,
                    bootValue = bootItem?.value,
                    liveValue = liveItem?.value,
                    status = status
                )
            )
        }

        return results
    }

    private fun splitCmdline(cmdline: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (char in cmdline.trim()) {
            if (char == '\"') {
                inQuotes = !inQuotes
                sb.append(char)
            } else if (char == ' ' && !inQuotes) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                }
            } else {
                sb.append(char)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }

        return tokens
    }

    private fun categorizeCmdline(key: String): CmdlineCategory {
        val lower = key.lowercase()
        return when {
            lower.startsWith("console") || lower.startsWith("earlycon") || lower.startsWith("earlyprintk") -> CmdlineCategory.CONSOLE
            lower.startsWith("root") || lower == "rootwait" || lower.startsWith("rootflags") -> CmdlineCategory.ROOT
            lower.contains("selinux") || lower == "enforcing" -> CmdlineCategory.SELINUX
            lower.startsWith("androidboot.verifiedbootstate") || lower.startsWith("androidboot.veritymode") || lower.startsWith("androidboot.secure_boot") -> CmdlineCategory.SECURITY
            lower.startsWith("androidboot") -> CmdlineCategory.ANDROIDBOOT
            lower.contains("debug") || lower.startsWith("loglevel") || lower.startsWith("printk") || lower.contains("ramoops") -> CmdlineCategory.DEBUG
            lower.startsWith("mem") || lower.startsWith("vmalloc") || lower.startsWith("cma") || lower.startsWith("ion") -> CmdlineCategory.MEMORY
            lower.contains("video") || lower.contains("fb") || lower.contains("display") -> CmdlineCategory.DISPLAY
            lower.startsWith("gpt") || lower.startsWith("loop") || lower.startsWith("block") || lower.startsWith("mtdparts") -> CmdlineCategory.STORAGE
            lower.startsWith("boot") || lower.startsWith("init") || lower.startsWith("panic") -> CmdlineCategory.BOOT
            else -> CmdlineCategory.OTHER
        }
    }

    private fun describeCmdline(key: String, value: String?): String {
        return when (key) {
            "console" -> "Kernel primary console port ($value)"
            "earlycon" -> "Early console for boot debug logs before full TTY driver ($value)"
            "androidboot.selinux" -> "SELinux mode passed to init (permissive/enforcing)"
            "androidboot.hardware" -> "Target hardware platform name ($value)"
            "androidboot.bootdevice" -> "Primary eMMC/UFS storage controller device path"
            "androidboot.verifiedbootstate" -> "Android Verified Boot status ($value)"
            "androidboot.mode" -> "Boot mode (normal, recovery, charger, meta)"
            "root" -> "Root filesystem device node ($value)"
            "rootwait" -> "Wait for root block device before trying to mount"
            "loglevel" -> "Kernel printk log verbosity level ($value)"
            "cma" -> "Contiguous Memory Allocator size pool ($value)"
            else -> ""
        }
    }
}
