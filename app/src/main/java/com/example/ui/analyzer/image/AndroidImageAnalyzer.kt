package com.example.ui.analyzer.image

import com.example.ui.analyzer.core.AnalyzerStatus

object AndroidImageAnalyzer {

    fun analyzeIssues(
        metadata: ImageMetadata,
        partitions: List<ImagePartition>,
        properties: Map<String, String>,
        elfArchs: Set<String>
    ): List<ImageIssue> {
        val issues = mutableListOf<ImageIssue>()

        // 1. Filesystem Health Issues
        if (metadata.format == ImageFormat.EXT4) {
            val stateField = metadata.flags.firstOrNull { it.startsWith("State:") }
            if (stateField != null && !stateField.contains("Clean")) {
                issues.add(
                    ImageIssue(
                        id = "EXT4_UNCLEAN_STATE",
                        severity = IssueSeverity.CRITICAL,
                        title = "Unclean EXT4 Filesystem State",
                        description = "Superblock indicates the filesystem was unmounted improperly or has uncorrected errors.",
                        recommendation = "Run e2fsck -f on the partition image before flashing.",
                        affectedPartition = metadata.mountPointHint.ifEmpty { "system" },
                        category = "Filesystem Health"
                    )
                )
            }

            if (metadata.usagePercentage > 98.0) {
                issues.add(
                    ImageIssue(
                        id = "PARTITION_NEAR_FULL",
                        severity = IssueSeverity.WARNING,
                        title = "Partition Space Nearly Exhausted (${"%.1f".format(metadata.usagePercentage)}%)",
                        description = "Free blocks count is critically low (${metadata.freeBlocks} blocks remaining).",
                        recommendation = "Resize partition or remove unused apps/fonts before repacking.",
                        affectedPartition = metadata.mountPointHint.ifEmpty { "system" },
                        category = "Capacity"
                    )
                )
            }
        }

        // 2. Android 11 / Treble Porting Compatibility (Galaxy J2 Prime focus)
        val sdkVersionStr = properties["ro.build.version.sdk"] ?: properties["ro.system.build.version.sdk"] ?: ""
        val sdkVersion = sdkVersionStr.toIntOrNull() ?: 0
        val isTreble = properties["ro.treble.enabled"]?.toBoolean() ?: false
        val isDynamic = properties["ro.boot.dynamic_partitions"]?.toBoolean() ?: (metadata.format == ImageFormat.SUPER)
        val abiList = properties["ro.product.cpu.abilist"] ?: properties["ro.product.cpu.abi"] ?: ""

        // Check 64-bit binaries on 32-bit architecture
        if (elfArchs.contains("AArch64") || elfArchs.contains("x86_64")) {
            val is32BitDevice = abiList.contains("armeabi") && !abiList.contains("arm64")
            if (is32BitDevice || abiList.isEmpty()) {
                issues.add(
                    ImageIssue(
                        id = "ABI_64BIT_MISMATCH",
                        severity = IssueSeverity.CRITICAL,
                        title = "64-Bit ELF Binaries Found on 32-Bit Target",
                        description = "Detected 64-bit ELF shared libraries/binaries (AArch64). Galaxy J2 Prime (Exynos 7570 / MT6737T) uses 32-bit ARMv7 userspace.",
                        recommendation = "Use 32-bit (armeabi-v7a) arm32_binder64 or standard arm32 GSI builds.",
                        affectedPartition = "system",
                        category = "Architecture Compatibility"
                    )
                )
            }
        }

        // Check Android 11+ requirements
        if (sdkVersion >= 30) {
            if (metadata.format == ImageFormat.EROFS) {
                issues.add(
                    ImageIssue(
                        id = "EROFS_KERNEL_SUPPORT",
                        severity = IssueSeverity.WARNING,
                        title = "EROFS Filesystem on Legacy 3.18 Kernel",
                        description = "Target partition is formatted as EROFS. Stock Galaxy J2 Prime kernel (3.18.14) lacks CONFIG_EROFS_FS support.",
                        recommendation = "Convert EROFS image to EXT4 or backport CONFIG_EROFS_FS to the custom kernel.",
                        affectedPartition = metadata.mountPointHint.ifEmpty { "system" },
                        category = "Kernel Compatibility"
                    )
                )
            }

            if (!isDynamic && metadata.format == ImageFormat.SUPER) {
                issues.add(
                    ImageIssue(
                        id = "SUPER_DYNAMIC_FSTAB_MISSING",
                        severity = IssueSeverity.WARNING,
                        title = "Dynamic Partitions in Non-Dynamic Partition Device",
                        description = "ROM uses super.img container, but device vendor fstab may lack dynamic partition entries.",
                        recommendation = "Extract individual system.img / vendor.img from super.img and flash via custom recovery.",
                        affectedPartition = "super",
                        category = "Dynamic Partitions"
                    )
                )
            }

            // Check VNDK Version
            val vndkVersion = properties["ro.vndk.version"] ?: ""
            if (vndkVersion.isNotEmpty() && vndkVersion != "30" && vndkVersion != "current") {
                issues.add(
                    ImageIssue(
                        id = "VNDK_VERSION_MISMATCH",
                        severity = IssueSeverity.INFO,
                        title = "VNDK Version Configuration ($vndkVersion)",
                        description = "System expects VNDK version $vndkVersion. Ensure vendor image provides matching VNDK runtime libs.",
                        recommendation = "Verify /vendor/lib/vndk-sp and /system/lib/vndk-$vndkVersion.",
                        affectedPartition = "vendor",
                        category = "Treble"
                    )
                )
            }
        }

        // 3. Super image partition verification
        if (metadata.format == ImageFormat.SUPER) {
            val systemPart = partitions.firstOrNull { it.name.startsWith("system") }
            if (systemPart == null) {
                issues.add(
                    ImageIssue(
                        id = "SUPER_NO_SYSTEM",
                        severity = IssueSeverity.CRITICAL,
                        title = "No System Partition in Super Image",
                        description = "Super metadata contains no partition named 'system' or 'system_a'.",
                        recommendation = "Ensure system partition is included in the LP metadata build script (lpmake).",
                        affectedPartition = "super",
                        category = "Dynamic Partitions"
                    )
                )
            }
        }

        // 4. Sparse image integrity check
        if (metadata.format == ImageFormat.SPARSE) {
            val dontCareBlocks = metadata.freeBlocks
            val usedBlocks = metadata.usedBlocks
            if (usedBlocks == 0L) {
                issues.add(
                    ImageIssue(
                        id = "SPARSE_EMPTY_PAYLOAD",
                        severity = IssueSeverity.CRITICAL,
                        title = "Empty Sparse Image Payload",
                        description = "Sparse image contains 0 data/fill blocks.",
                        recommendation = "Check source sparse image creation tool.",
                        affectedPartition = metadata.fileName,
                        category = "Integrity"
                    )
                )
            }
        }

        return issues
    }
}
