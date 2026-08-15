package com.example.ui.analyzer.flash

import com.example.ui.analyzer.partition.PartitionIssue
import com.example.ui.analyzer.partition.PartitionIssueSeverity

object FlashCompatibilityChecker {

    fun checkCompatibility(
        plan: FlashPlan,
        profile: DeviceProfile
    ): List<PartitionIssue> {
        val issues = mutableListOf<PartitionIssue>()
        val flashingItems = plan.items.filter { it.matchedImageFile != null }
        val flashingPartNames = flashingItems.map { it.partition.name.lowercase() }.toSet()

        // 1. Missing boot when flashing system
        if (flashingPartNames.contains("system") && !flashingPartNames.contains("boot")) {
            issues.add(
                PartitionIssue(
                    id = "FLASH_SYSTEM_WITHOUT_BOOT",
                    severity = PartitionIssueSeverity.WARNING,
                    title = "Flashing System Without Updated Boot Image",
                    description = "Custom ROM system image might require matching kernel ramdisk changes (init.rc / fstab) in boot.img.",
                    recommendation = "Verify if current kernel supports the new system OS version or flash compatible boot.img.",
                    affectedPartition = "boot, system",
                    category = "ROM Compatibility"
                )
            )
        }

        // 2. Android 11 specific porting checks for Galaxy J2 Prime
        if (profile.id == DeviceProfile.GALAXY_J2_PRIME.id) {
            val systemItem = flashingItems.find { it.partition.name.equals("system", ignoreCase = true) }
            if (systemItem != null) {
                issues.add(
                    PartitionIssue(
                        id = "J2_PRIME_A11_PORTING_NOTICE",
                        severity = PartitionIssueSeverity.INFO,
                        title = "Galaxy J2 Prime Porting Check (MT6737T / ARM32)",
                        description = "J2 Prime has 1.5GB RAM, 3.18.35+ kernel, and ARM32 binder architecture. Android 11 requires Binder IPC 64-bit compatibility flags or 32-bit binder backport in kernel.",
                        recommendation = "Use unbloated GSI (ARM32/arm_a64) and ensure ZRAM / LMK / Low-RAM flags are enabled in build.prop.",
                        affectedPartition = "system",
                        category = "Galaxy J2 Prime Android 11"
                    )
                )
            }
        }

        // 3. Userdata wipe check
        if (flashingPartNames.contains("system") && !flashingPartNames.contains("userdata") && !flashingPartNames.contains("cache")) {
            issues.add(
                PartitionIssue(
                    id = "FLASH_DATA_WIPE_RECOMMENDED",
                    severity = PartitionIssueSeverity.INFO,
                    title = "Data Wipe Recommended for Clean Install",
                    description = "Upgrading Android versions across major releases requires formatting /data and /cache to avoid bootloop.",
                    recommendation = "Perform factory reset / wipe in TWRP Recovery after flashing.",
                    affectedPartition = "userdata",
                    category = "Post-Flash Guidance"
                )
            )
        }

        return issues
    }
}
