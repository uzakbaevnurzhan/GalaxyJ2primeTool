package com.example.ui.analyzer.boot

object BootIssueDetector {

    fun detectAllIssues(
        header: BootHeaderInfo?,
        kernel: KernelDetailsInfo?,
        ramdisk: RamdiskDetailsInfo?,
        init: InitAnalysisInfo?,
        fstab: FstabAnalysisInfo?,
        treble: TrebleStatusInfo?,
        ab: AbSlotStatusInfo?,
        arch: ArchitectureCheckInfo?,
        versions: AndroidVersionAnalysisInfo?,
        vendor: VendorDetailsInfo?,
        rawLogs: String? = null
    ): List<BootIssue> {
        val issues = mutableListOf<BootIssue>()

        // 1. Boot header checks
        if (header == null || !header.isValid) {
            issues.add(
                BootIssue(
                    type = BootIssueType.INVALID_HEADER,
                    severity = BootIssueSeverity.CRITICAL,
                    title = "Invalid or Missing Boot Image Header",
                    description = header?.notes ?: "Boot image does not have standard ANDROID! magic signature.",
                    evidence = header?.magic ?: "NULL",
                    possibleCause = "Corrupted boot.img, wrong image format, or proprietary unsigned header.",
                    recommendedFix = "Ensure the image is a valid Android boot.img and not raw compressed payload.",
                    confidence = "HIGH"
                )
            )
        }

        // 2. Kernel checks
        if (kernel != null) {
            if (kernel.detectedFormat == "missing" || kernel.kernelSize == 0L) {
                issues.add(
                    BootIssue(
                        type = BootIssueType.KERNEL_MISSING,
                        severity = BootIssueSeverity.CRITICAL,
                        title = "Kernel binary payload is empty",
                        description = "The boot image header specifies 0 bytes for kernel payload.",
                        evidence = "Kernel size: 0",
                        possibleCause = "Split boot image where kernel resides in separate partition.",
                        confidence = "HIGH"
                    )
                )
            }
        }

        // 3. Ramdisk checks
        if (ramdisk != null && ramdisk.present && ramdisk.isCorrupt) {
            issues.add(
                BootIssue(
                    type = BootIssueType.RAMDISK_CORRUPT,
                    severity = BootIssueSeverity.ERROR,
                    title = "Ramdisk CPIO archive corrupted or unreadable",
                    description = ramdisk.notes,
                    evidence = "Compression format: ${ramdisk.compression}",
                    possibleCause = "Damaged compression headers, non-standard LZ4 framing, or unaligned CPIO.",
                    recommendedFix = "Repack ramdisk with standard gzip/cpio newc formatting.",
                    confidence = "HIGH"
                )
            )
        }

        // 4. Init issues
        init?.issuesFound?.let { issues.addAll(it) }

        // 5. Fstab issues
        fstab?.issuesFound?.let { issues.addAll(it) }

        // 6. Architecture / ABI consistency
        if (arch != null && !arch.isConsistent) {
            issues.add(
                BootIssue(
                    type = BootIssueType.ARCH_32_64_CONFLICT,
                    severity = BootIssueSeverity.ERROR,
                    title = "Architecture / ABI Mismatch Across Subsystems",
                    description = "Detected conflicting CPU architectures between kernel (${arch.kernelArch}), init (${arch.initArch}), system (${arch.systemArch}) or vendor (${arch.vendorArch}).",
                    evidence = "Kernel: ${arch.kernelArch}, Init: ${arch.initArch}, System: ${arch.systemArch}, Vendor: ${arch.vendorArch}",
                    possibleCause = "Attempting to boot 64-bit userland on 32-bit kernel or mixing 32-bit vendor HALs with 64-bit framework.",
                    recommendedFix = "Ensure matching 32-bit or 64-bit architecture throughout the entire stack.",
                    confidence = "HIGH"
                )
            )
        }

        // 7. Version conflicts
        if (versions != null && versions.hasConflict) {
            issues.add(
                BootIssue(
                    type = BootIssueType.PROPERTY_VERSION_CONFLICT,
                    severity = BootIssueSeverity.WARNING,
                    title = "Android OS Version Discrepancy",
                    description = versions.conflictDetails ?: "Discrepancy detected between boot header OS version and system build.prop properties.",
                    evidence = "Boot Header: ${versions.bootHeaderVersion}, build.prop: ${versions.buildPropVersion}, prop.default: ${versions.defaultPropVersion}",
                    possibleCause = "Ported ROM using older base kernel with newer system image, or mismatched build.prop.",
                    recommendedFix = "Align os_version in boot header and ro.build.version.release.",
                    confidence = "MEDIUM"
                )
            )
        }

        // 8. Treble incompatibilities
        if (treble != null && !treble.isTreble && vendor?.vendorPresent == true) {
            issues.add(
                BootIssue(
                    type = BootIssueType.TREBLE_INCOMPATIBILITY,
                    severity = BootIssueSeverity.WARNING,
                    title = "Non-Treble Structure with Separate Vendor Partition",
                    description = "A separate vendor partition is present but Treble properties (ro.treble.enabled) and VNDK isolation are absent.",
                    evidence = "Treble enabled: ${treble.roTrebleProperty}, VNDK: ${treble.hasVndkProps}",
                    possibleCause = "Legacy Non-Treble ROM or early port stage.",
                    confidence = "MEDIUM"
                )
            )
        }

        // 9. Kernel panic detection in raw logs
        if (rawLogs != null) {
            if (rawLogs.contains("Kernel panic - not syncing", ignoreCase = true)) {
                val panicLine = rawLogs.lines().firstOrNull { it.contains("Kernel panic", ignoreCase = true) } ?: "Kernel panic"
                issues.add(
                    BootIssue(
                        type = BootIssueType.KERNEL_PANIC_SIGNATURE,
                        severity = BootIssueSeverity.CRITICAL,
                        title = "Kernel Panic Detected in Log Stream",
                        description = "Kernel execution halted with a non-syncing fatal panic.",
                        evidence = panicLine,
                        possibleCause = "Null pointer dereference, invalid device tree, missing driver, or memory fault.",
                        confidence = "HIGH"
                    )
                )
            }

            if (rawLogs.contains("avc: denied", ignoreCase = true)) {
                val denialCount = rawLogs.split("avc: denied").size - 1
                issues.add(
                    BootIssue(
                        type = BootIssueType.SELINUX_DENIAL_RISK,
                        severity = BootIssueSeverity.WARNING,
                        title = "SELinux AVC Denials Detected ($denialCount occurrences)",
                        description = "Services or HALs encountered permission denials under current SELinux policy.",
                        evidence = "Found $denialCount 'avc: denied' log entries",
                        possibleCause = "Missing sepolicy rules or unlabeled files in system/vendor.",
                        recommendedFix = "Inspect AVC denials using SELinux Analyzer and add required allow rules or file_contexts.",
                        confidence = "HIGH"
                    )
                )
            }
        }

        return issues
    }
}
