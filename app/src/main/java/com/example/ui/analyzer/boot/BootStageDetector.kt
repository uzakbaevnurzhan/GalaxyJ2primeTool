package com.example.ui.analyzer.boot

object BootStageDetector {

    fun evaluateStages(
        header: BootHeaderInfo?,
        kernel: KernelDetailsInfo?,
        ramdisk: RamdiskDetailsInfo?,
        init: InitAnalysisInfo?,
        fstab: FstabAnalysisInfo?,
        vendor: VendorDetailsInfo?,
        allIssues: List<BootIssue>,
        rawLogs: String? = null
    ): StageEvaluationResult {
        val stageMap = mutableMapOf<BootStage, BootStageResult>()

        // 1. BOOTLOADER STAGE
        val bootloaderIssues = allIssues.filter { it.type == BootIssueType.INVALID_HEADER || it.type == BootIssueType.CORRUPTED_PAYLOAD }
        val bootloaderStatus = when {
            header == null || !header.isValid -> BootStageStatus.ERROR
            bootloaderIssues.any { it.severity == BootIssueSeverity.CRITICAL || it.severity == BootIssueSeverity.ERROR } -> BootStageStatus.ERROR
            bootloaderIssues.isNotEmpty() -> BootStageStatus.WARNING
            else -> BootStageStatus.PASS
        }
        stageMap[BootStage.BOOTLOADER] = BootStageResult(
            stage = BootStage.BOOTLOADER,
            status = bootloaderStatus,
            summary = if (header?.isValid == true) "Boot header v${header.headerVersion} verified (${header.magic})" else "Invalid Boot header",
            details = listOfNotNull(
                header?.let { "Magic: ${it.magic}, Page Size: ${it.pageSize}B, Header Size: ${it.headerSize}B" },
                header?.let { "OS Version: ${it.osVersionString}, Patch: ${it.osPatchLevelString}" }
            ),
            issues = bootloaderIssues
        )

        // 2. KERNEL STAGE
        val kernelIssues = allIssues.filter { it.type == BootIssueType.KERNEL_MISSING || it.type == BootIssueType.KERNEL_PANIC_SIGNATURE }
        val kernelStatus = when {
            bootloaderStatus == BootStageStatus.ERROR -> BootStageStatus.UNKNOWN
            kernel == null || kernel.detectedFormat == "missing" -> BootStageStatus.ERROR
            kernelIssues.any { it.severity == BootIssueSeverity.CRITICAL || it.severity == BootIssueSeverity.ERROR } -> BootStageStatus.ERROR
            kernel.detectedArch == "unknown" -> BootStageStatus.WARNING
            else -> BootStageStatus.PASS
        }
        stageMap[BootStage.KERNEL] = BootStageResult(
            stage = BootStage.KERNEL,
            status = kernelStatus,
            summary = if (kernel != null && kernel.detectedFormat != "missing") "Kernel ${kernel.detectedArch} (${kernel.detectedFormat})" else "Kernel missing or unparseable",
            details = listOfNotNull(
                kernel?.kernelVersionString?.let { "Version: $it" },
                kernel?.compilerString?.let { "Compiler: $it" },
                kernel?.let { "SMP: ${it.isSmp}, Configs found: ${it.kernelConfigCount}" }
            ),
            issues = kernelIssues
        )

        // 3. RAMDISK STAGE
        val ramdiskIssues = allIssues.filter { it.type == BootIssueType.RAMDISK_MISSING || it.type == BootIssueType.RAMDISK_CORRUPT }
        val ramdiskStatus = when {
            kernelStatus == BootStageStatus.ERROR -> BootStageStatus.UNKNOWN
            ramdisk == null || !ramdisk.present -> BootStageStatus.WARNING // Some devices use system-as-root
            ramdisk.isCorrupt -> BootStageStatus.ERROR
            else -> BootStageStatus.PASS
        }
        stageMap[BootStage.RAMDISK] = BootStageResult(
            stage = BootStage.RAMDISK,
            status = ramdiskStatus,
            summary = if (ramdisk?.present == true) "Ramdisk present (${ramdisk.compression}, ${ramdisk.cpioEntriesCount} files)" else "No ramdisk found (System-as-root mode)",
            details = ramdisk?.foundKeyFiles?.map { "Found: $it" } ?: emptyList(),
            issues = ramdiskIssues
        )

        // 4. INIT STAGE
        val initIssues = allIssues.filter { it.type == BootIssueType.INIT_PARSER_ERROR || it.type == BootIssueType.MISSING_INIT_SERVICE_BINARY }
        val initStatus = when {
            kernelStatus == BootStageStatus.ERROR -> BootStageStatus.UNKNOWN
            init == null -> BootStageStatus.UNKNOWN
            initIssues.any { it.severity == BootIssueSeverity.ERROR || it.severity == BootIssueSeverity.CRITICAL } -> BootStageStatus.ERROR
            initIssues.isNotEmpty() -> BootStageStatus.WARNING
            init.stagesFound.isEmpty() && init.services.isEmpty() -> BootStageStatus.WARNING
            else -> BootStageStatus.PASS
        }
        stageMap[BootStage.INIT] = BootStageResult(
            stage = BootStage.INIT,
            status = initStatus,
            summary = if (init != null) "${init.stagesFound.size} action stages, ${init.services.size} services defined" else "No init scripts parsed",
            details = init?.stagesFound?.keys?.map { "Action trigger: $it" } ?: emptyList(),
            issues = initIssues
        )

        // 5. MOUNT STAGE
        val mountIssues = allIssues.filter { it.type == BootIssueType.INVALID_FSTAB || it.type == BootIssueType.MISSING_MANDATORY_MOUNT || it.type == BootIssueType.MOUNT_PARTITION_MISSING }
        val mountStatus = when {
            initStatus == BootStageStatus.ERROR -> BootStageStatus.UNKNOWN
            mountIssues.any { it.severity == BootIssueSeverity.ERROR || it.severity == BootIssueSeverity.CRITICAL } -> BootStageStatus.ERROR
            mountIssues.isNotEmpty() -> BootStageStatus.WARNING
            fstab != null && fstab.entries.isNotEmpty() -> BootStageStatus.PASS
            else -> BootStageStatus.UNKNOWN
        }
        stageMap[BootStage.MOUNT] = BootStageResult(
            stage = BootStage.MOUNT,
            status = mountStatus,
            summary = if (fstab != null) "${fstab.entries.size} mount targets configured (${fstab.fileName})" else "No fstab mount table parsed",
            details = fstab?.entries?.map { "${it.mountTarget} -> ${it.filesystem} (${it.deviceSource})" } ?: emptyList(),
            issues = mountIssues
        )

        // 6. SELINUX STAGE
        val selinuxIssues = allIssues.filter { it.type == BootIssueType.SELINUX_DENIAL_RISK || it.type == BootIssueType.SELINUX_POLICY_MISSING }
        val selinuxStatus = when {
            selinuxIssues.any { it.severity == BootIssueSeverity.ERROR || it.severity == BootIssueSeverity.CRITICAL } -> BootStageStatus.ERROR
            selinuxIssues.isNotEmpty() -> BootStageStatus.WARNING
            rawLogs?.contains("avc: denied", ignoreCase = true) == true -> BootStageStatus.WARNING
            else -> BootStageStatus.PASS
        }
        stageMap[BootStage.SELINUX] = BootStageResult(
            stage = BootStage.SELINUX,
            status = selinuxStatus,
            summary = if (selinuxStatus == BootStageStatus.PASS) "SELinux context configuration valid" else "SELinux issues or denials detected",
            issues = selinuxIssues
        )

        // 7. VENDOR STAGE
        val vendorIssues = allIssues.filter { it.type == BootIssueType.VENDOR_INTEGRITY_FAIL || it.type == BootIssueType.TREBLE_INCOMPATIBILITY }
        val vendorStatus = when {
            vendor == null -> BootStageStatus.UNKNOWN
            vendorIssues.any { it.severity == BootIssueSeverity.ERROR } -> BootStageStatus.ERROR
            vendorIssues.isNotEmpty() -> BootStageStatus.WARNING
            vendor.vendorPresent -> BootStageStatus.PASS
            else -> BootStageStatus.UNKNOWN
        }
        stageMap[BootStage.VENDOR] = BootStageResult(
            stage = BootStage.VENDOR,
            status = vendorStatus,
            summary = if (vendor?.vendorPresent == true) "Vendor subsystem ready (${vendor.halList.size} HALs)" else "Vendor subsystem not detected or unseparated",
            details = vendor?.halList?.take(10)?.map { "HAL: $it" } ?: emptyList(),
            issues = vendorIssues
        )

        // 8. HAL STAGE
        val halIssues = allIssues.filter { it.type == BootIssueType.VINTF_MISMATCH }
        val halStatus = when {
            vendorStatus == BootStageStatus.ERROR -> BootStageStatus.UNKNOWN
            halIssues.isNotEmpty() -> BootStageStatus.WARNING
            vendor?.halList?.isNotEmpty() == true -> BootStageStatus.PASS
            else -> BootStageStatus.UNKNOWN
        }
        stageMap[BootStage.HAL] = BootStageResult(
            stage = BootStage.HAL,
            status = halStatus,
            summary = if (vendor?.halList?.isNotEmpty() == true) "${vendor.halList.size} Hardware Abstraction Layer services identified" else "HAL layer pending / unknown",
            issues = halIssues
        )

        // 9. ZYGOTE STAGE
        val zygoteInLogs = rawLogs?.contains("zygote", ignoreCase = true) == true
        val zygoteCrash = rawLogs?.contains("zygote crash", ignoreCase = true) == true ||
                rawLogs?.contains("Zygote: Exit", ignoreCase = true) == true
        val zygoteStatus = when {
            zygoteCrash -> BootStageStatus.ERROR
            zygoteInLogs -> BootStageStatus.PASS
            init?.services?.any { it.name.startsWith("zygote") } == true -> BootStageStatus.PASS
            else -> BootStageStatus.UNKNOWN
        }
        stageMap[BootStage.ZYGOTE] = BootStageResult(
            stage = BootStage.ZYGOTE,
            status = zygoteStatus,
            summary = if (zygoteCrash) "Zygote crash or fatal exit detected in boot logs" else "Zygote initialization status",
            issues = emptyList()
        )

        // 10. SYSTEM_SERVER STAGE
        val sysServerCrash = rawLogs?.contains("system_server crash", ignoreCase = true) == true ||
                rawLogs?.contains("SystemServer: *** FATAL EXCEPTION IN SYSTEM PROCESS", ignoreCase = false) == true
        val sysServerReady = rawLogs?.contains("SystemServer: Entered the Android system server!", ignoreCase = true) == true
        val sysServerStatus = when {
            sysServerCrash -> BootStageStatus.ERROR
            sysServerReady -> BootStageStatus.PASS
            else -> BootStageStatus.UNKNOWN
        }
        stageMap[BootStage.SYSTEM_SERVER] = BootStageResult(
            stage = BootStage.SYSTEM_SERVER,
            status = sysServerStatus,
            summary = if (sysServerCrash) "SystemServer fatal exception detected" else "SystemServer status",
            issues = emptyList()
        )

        // 11. ANDROID_FRAMEWORK STAGE
        val frameworkReady = rawLogs?.contains("BootAnimation: Boot animation finished", ignoreCase = true) == true ||
                rawLogs?.contains("ActivityManager: Boot is finished", ignoreCase = true) == true
        val frameworkStatus = when {
            frameworkReady -> BootStageStatus.PASS
            sysServerCrash || zygoteCrash -> BootStageStatus.ERROR
            else -> BootStageStatus.UNKNOWN
        }
        stageMap[BootStage.ANDROID_FRAMEWORK] = BootStageResult(
            stage = BootStage.ANDROID_FRAMEWORK,
            status = frameworkStatus,
            summary = if (frameworkReady) "Android Framework successfully completed boot cycle" else "Framework startup pending",
            issues = emptyList()
        )

        // Determine last confirmed and suspected failure
        val stagesOrder = listOf(
            BootStage.BOOTLOADER,
            BootStage.KERNEL,
            BootStage.RAMDISK,
            BootStage.INIT,
            BootStage.MOUNT,
            BootStage.SELINUX,
            BootStage.VENDOR,
            BootStage.HAL,
            BootStage.ZYGOTE,
            BootStage.SYSTEM_SERVER,
            BootStage.ANDROID_FRAMEWORK
        )

        var lastConfirmed = BootStage.BOOTLOADER
        var suspectedFailure: BootStage? = null
        var confidence = "LOW"

        for (stage in stagesOrder) {
            val res = stageMap[stage] ?: continue
            if (res.status == BootStageStatus.PASS) {
                lastConfirmed = stage
            } else if (res.status == BootStageStatus.ERROR && suspectedFailure == null) {
                suspectedFailure = stage
                confidence = if (res.issues.any { it.severity == BootIssueSeverity.CRITICAL || it.severity == BootIssueSeverity.ERROR }) "HIGH" else "MEDIUM"
            }
        }

        if (suspectedFailure == null) {
            // Check first warning stage
            for (stage in stagesOrder) {
                val res = stageMap[stage] ?: continue
                if (res.status == BootStageStatus.WARNING) {
                    suspectedFailure = stage
                    confidence = "MEDIUM"
                    break
                }
            }
        }

        return StageEvaluationResult(stageMap, lastConfirmed, suspectedFailure, confidence)
    }

    data class StageEvaluationResult(
        val stageMap: Map<BootStage, BootStageResult>,
        val lastConfirmedStage: BootStage,
        val suspectedFailureStage: BootStage?,
        val confidence: String
    )
}
