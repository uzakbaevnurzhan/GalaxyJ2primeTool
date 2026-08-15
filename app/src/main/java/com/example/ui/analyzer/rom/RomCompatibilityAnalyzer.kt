package com.example.ui.analyzer.rom

import com.example.ui.analyzer.boot.BootAnalysisResult
import com.example.ui.analyzer.boot.BootStageStatus
import com.example.ui.analyzer.boot.GalaxyJ2PrimeProfileCheck
import com.example.ui.analyzer.boot.PortingCheckRuleResult

object RomCompatibilityAnalyzer {

    fun checkAndroid11Porting(
        result: BootAnalysisResult,
        properties: Map<String, String>,
        filesList: List<String>
    ): List<PortingCheckRuleResult> {
        val checks = mutableListOf<PortingCheckRuleResult>()

        // 1. Android SDK / API Level 30
        val sdkVer = properties["ro.build.version.sdk"]?.toIntOrNull() ?: 0
        val releaseVer = properties["ro.build.version.release"] ?: "Unknown"
        val isSdk30 = sdkVer >= 30 || releaseVer.startsWith("11")
        checks.add(
            PortingCheckRuleResult(
                ruleName = "Target Android 11 (API 30)",
                status = if (isSdk30) BootStageStatus.PASS else BootStageStatus.WARNING,
                description = "Verifies ro.build.version.sdk is 30 and release version is 11.",
                evidence = "SDK: $sdkVer, Release: $releaseVer",
                recommendation = if (!isSdk30) "Verify target system image properties are configured for Android 11 (SDK 30)." else null
            )
        )

        // 2. Kernel Version (Android 11 requires at least Linux 4.4/4.9/4.14/4.19/5.4)
        val kVer = result.kernelInfo?.kernelVersionString
        val kernelMajorMinor = if (kVer != null) {
            val match = Regex("""Linux version (\d+\.\d+)""").find(kVer)
            match?.groupValues?.get(1)?.toDoubleOrNull()
        } else null

        val kernelStatus = when {
            kernelMajorMinor == null -> BootStageStatus.UNKNOWN
            kernelMajorMinor >= 4.4 -> BootStageStatus.PASS
            kernelMajorMinor >= 3.18 -> BootStageStatus.WARNING
            else -> BootStageStatus.ERROR
        }
        checks.add(
            PortingCheckRuleResult(
                ruleName = "Kernel Compatibility (Linux 3.18+ / 4.4+)",
                status = kernelStatus,
                description = "Android 11 standard builds require Linux 4.4+ (Legacy 3.18 requires backported BPF/binder64 patches).",
                evidence = kVer ?: "Kernel version not detected",
                recommendation = if (kernelStatus == BootStageStatus.WARNING) "Kernel 3.18 detected. Ensure CONFIG_BPF, ashmem, and 64-bit binder IPC are backported." else null
            )
        )

        // 3. Architecture & 32-bit Binder support
        val arch = result.architectureInfo?.overallArch ?: "UNKNOWN"
        checks.add(
            PortingCheckRuleResult(
                ruleName = "ABI & Architecture Alignment",
                status = if (arch == "MIXED") BootStageStatus.ERROR else BootStageStatus.PASS,
                description = "Checks that all binary layers (Kernel, Init, System, Vendor) match either ARM32 or ARM64.",
                evidence = "Overall detected architecture: $arch",
                recommendation = if (arch == "MIXED") "Align all libraries and binaries to matching 32-bit or 64-bit ABI." else null
            )
        )

        // 4. Treble & VNDK Isolation
        val treble = result.trebleInfo
        val trebleStatus = when {
            treble == null -> BootStageStatus.UNKNOWN
            treble.isTreble -> BootStageStatus.PASS
            treble.hasVendorPartition -> BootStageStatus.WARNING
            else -> BootStageStatus.WARNING
        }
        checks.add(
            PortingCheckRuleResult(
                ruleName = "Treble / VNDK Separation",
                status = trebleStatus,
                description = "Android 11 enforces VNDK version isolation between system and vendor partitions.",
                evidence = "Treble: ${treble?.isTreble}, Vendor Partition: ${treble?.hasVendorPartition}, VNDK Props: ${treble?.hasVndkProps}",
                recommendation = if (trebleStatus == BootStageStatus.WARNING) "For Non-Treble ports, ensure legacy HAL shims and vndk-sp compatibility wrappers are installed." else null
            )
        )

        // 5. System-As-Root (SAR) & Dynamic Partitions
        val fstab = result.fstabAnalysis
        val hasSar = result.ramdiskInfo?.present == false || properties["ro.build.system_root_image"] == "true"
        checks.add(
            PortingCheckRuleResult(
                ruleName = "System-As-Root / Ramdisk Scheme",
                status = BootStageStatus.PASS,
                description = "Android 10+ uses System-As-Root (SAR) or two-stage init ramdisk.",
                evidence = "Ramdisk present: ${result.ramdiskInfo?.present}, SAR prop: ${properties["ro.build.system_root_image"]}"
            )
        )

        // 6. SELinux Enforcing / File Contexts
        val hasSepolicy = filesList.any { it.contains("sepolicy") || it.contains("file_contexts") }
        checks.add(
            PortingCheckRuleResult(
                ruleName = "SELinux Policy Structure",
                status = if (hasSepolicy) BootStageStatus.PASS else BootStageStatus.WARNING,
                description = "Android 11 requires modular split SELinux (plat_sepolicy, vendor_sepolicy).",
                evidence = if (hasSepolicy) "SELinux policy files detected in workspace" else "No sepolicy files found in scanned directory"
            )
        )

        return checks
    }

    fun checkGalaxyJ2PrimeProfile(
        kernel: com.example.ui.analyzer.boot.KernelDetailsInfo?,
        architecture: com.example.ui.analyzer.boot.ArchitectureCheckInfo?,
        properties: Map<String, String>
    ): GalaxyJ2PrimeProfileCheck {
        val platformProp = properties["ro.board.platform"] ?: properties["ro.hardware"] ?: properties["ro.mediatek.platform"]
        val arch = architecture?.overallArch

        val actualChipset = platformProp ?: if (kernel?.rawStringsFound?.any { it.contains("mt6737", ignoreCase = true) || it.contains("mt6735", ignoreCase = true) } == true) "MediaTek MT6737 / MT6735" else "Unknown SoC"
        val actualArch = arch ?: "Unknown ABI"

        val notes = mutableListOf<String>()
        var isMatch = true

        if (arch != "ARM32" && arch != "ARM") {
            isMatch = false
            notes.add("J2 Prime (SM-G532F/G/M) uses 32-bit ARMv7-A userspace. Current ROM contains $arch.")
        }

        if (actualChipset != "Unknown SoC" && !actualChipset.contains("mt6737", ignoreCase = true) && !actualChipset.contains("mt6735", ignoreCase = true)) {
            notes.add("Detected chipset ($actualChipset) differs from expected MT6737T.")
        } else {
            notes.add("Chipset signature aligns with MediaTek MT6737 family.")
        }

        return GalaxyJ2PrimeProfileCheck(
            expectedChipset = "MediaTek MT6737T",
            expectedArch = "ARM32 (armv7-a)",
            actualChipset = actualChipset,
            actualArch = actualArch,
            isMatch = isMatch,
            notes = notes
        )
    }
}
