package com.example.ui.analyzer.kernel.studio.compatibility

import com.example.ui.analyzer.kernel.studio.models.DtbHardwareNode
import com.example.ui.analyzer.kernel.studio.models.KernelConfig
import com.example.ui.analyzer.kernel.studio.models.KernelInfo
import com.example.ui.analyzer.kernel.studio.models.KernelIssue
import com.example.ui.analyzer.kernel.studio.models.KernelIssueSeverity
import com.example.ui.analyzer.kernel.studio.models.KernelIssueType

object KernelHardwareAnalyzer {

    data class J2PrimeEvaluation(
        val isMatch: Boolean,
        val detectedChipset: String,
        val detectedArch: String,
        val notes: List<String>
    )

    fun evaluateGalaxyJ2PrimeProfile(
        kernelInfo: KernelInfo?,
        dtbCompatible: List<String>,
        hardwareNodes: List<DtbHardwareNode>
    ): J2PrimeEvaluation {
        val notes = mutableListOf<String>()
        var isMatch = true
        var detectedChipset = "Unknown"
        var detectedArch = kernelInfo?.architecture ?: "unknown"

        val hasMtk = dtbCompatible.any { it.contains("mediatek", ignoreCase = true) || it.contains("mt6737", ignoreCase = true) || it.contains("mt6735", ignoreCase = true) }
        val hasGrandpplte = dtbCompatible.any { it.contains("grandpplte", ignoreCase = true) || it.contains("g532", ignoreCase = true) }

        if (hasGrandpplte) {
            detectedChipset = "MediaTek MT6737T (Samsung Galaxy J2 Prime SM-G532F)"
            notes.add("Found Samsung Galaxy J2 Prime board model in Device Tree compatible list")
        } else if (hasMtk) {
            detectedChipset = "MediaTek MT6735 / MT6737 Series"
            notes.add("Found MediaTek SoC compatible nodes in Device Tree")
        } else if (dtbCompatible.isNotEmpty()) {
            detectedChipset = "Other SoC (${dtbCompatible.firstOrNull()})"
            isMatch = false
            notes.add("Device Tree compatible list does not match Samsung J2 Prime / MediaTek MT6737")
        }

        if (detectedArch == "ARM64") {
            isMatch = false
            notes.add("Architecture is ARM64, while Galaxy J2 Prime SM-G532F uses 32-bit ARM (armv7-a) kernel")
        } else if (detectedArch == "ARM32") {
            notes.add("Architecture matches Galaxy J2 Prime (32-bit ARM)")
        }

        return J2PrimeEvaluation(
            isMatch = isMatch,
            detectedChipset = detectedChipset,
            detectedArch = detectedArch,
            notes = notes
        )
    }

    fun generateHardwareIssues(
        kernelInfo: KernelInfo?,
        configs: List<KernelConfig>,
        dtbHardware: List<DtbHardwareNode>,
        dtbCompatible: List<String>
    ): List<KernelIssue> {
        val issues = mutableListOf<KernelIssue>()
        val configMap = configs.associateBy { it.name }

        // Check 1: Architecture Conflict
        if (kernelInfo?.architecture == "ARCHITECTURE_CONFLICT") {
            issues.add(
                KernelIssue(
                    type = KernelIssueType.ARCHITECTURE_MISMATCH,
                    severity = KernelIssueSeverity.ERROR,
                    message = "Contradictory architecture signatures found in kernel image",
                    evidence = "Mixed ARM32 / ARM64 header or string symbols detected",
                    source = "Kernel Header & String Analyzer"
                )
            )
        }

        // Check 2: Missing Essential Android Drivers
        if (configs.isNotEmpty() && !configMap.containsKey("CONFIG_ANDROID_BINDER_IPC")) {
            issues.add(
                KernelIssue(
                    type = KernelIssueType.MISSING_CONFIG,
                    severity = KernelIssueSeverity.CRITICAL,
                    message = "CONFIG_ANDROID_BINDER_IPC is missing from kernel configuration",
                    evidence = "Binder driver is mandatory for Android runtime execution",
                    source = "Kernel Config Analyzer"
                )
            )
        }

        // Check 3: Wi-Fi driver mismatch
        val hasWifiDtb = dtbHardware.any { it.category == "Wi-Fi" }
        val hasWirelessConfig = configs.any { it.name == "CONFIG_WIRELESS" || it.name == "CONFIG_WLAN" || it.name.contains("BCMDHD") }
        if (hasWifiDtb && configs.isNotEmpty() && !hasWirelessConfig) {
            issues.add(
                KernelIssue(
                    type = KernelIssueType.DTB_CONFLICT,
                    severity = KernelIssueSeverity.WARNING,
                    message = "Wi-Fi hardware node is described in Device Tree, but CONFIG_WLAN / CONFIG_WIRELESS was not found in kernel configs",
                    evidence = "DTB node: ${dtbHardware.firstOrNull { it.category == "Wi-Fi" }?.path}",
                    source = "DTB / Config Cross-Check"
                )
            )
        }

        // Check 4: Module vermagic compatibility
        for (mod in kernelInfo?.modules ?: emptyList()) {
            if (mod.vermagic != "UNKNOWN" && kernelInfo?.versionInfo?.major != null && kernelInfo.versionInfo.major > 0) {
                val kernelVerPrefix = "${kernelInfo.versionInfo.major}.${kernelInfo.versionInfo.minor}"
                if (!mod.vermagic.startsWith(kernelVerPrefix)) {
                    issues.add(
                        KernelIssue(
                            type = KernelIssueType.MODULE_MISMATCH,
                            severity = KernelIssueSeverity.WARNING,
                            message = "Kernel module '${mod.name}' vermagic (${mod.vermagic}) does not match kernel version ($kernelVerPrefix)",
                            evidence = "Module path: ${mod.path}",
                            source = "Kernel Module Analyzer"
                        )
                    )
                }
            }
        }

        return issues
    }
}
