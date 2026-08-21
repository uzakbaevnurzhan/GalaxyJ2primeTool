package com.example.porting.engine

import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.data.manager.ReportGeneratorEngine
import com.example.data.model.ReportFormat
import com.example.data.model.ReportType
import com.example.porting.model.*
import com.example.ui.studio.workspace.RomProject
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RomPortAssistantEngine {

    // ==========================================
    // 1. REFERENCE PROFILES FOR J2 PRIME
    // ==========================================

    val REFERENCE_TARGET_DEVICES = listOf(
        TargetDeviceProfile(
            id = "target_j2prime_sm_g532f_stock",
            name = "Samsung Galaxy J2 Prime Global (SM-G532F)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "SM-G532F",
            board = "grandpplte",
            platform = "mt6737t",
            cpuArch = "armv7-a-neon (32-bit ARM)",
            is64Bit = false,
            maxKernelVersion = "3.18.35+",
            isTrebleSupported = false,
            maxSystemPartitionBytes = 1719664640L, // 1.60 GB (1,640 MB)
            maxBootPartitionBytes = 16777216L, // 16 MB
            selinuxMode = "Enforcing",
            rootAvailable = true,
            supportedAbis = listOf("armeabi-v7a", "armeabi"),
            maliGpu = "ARM Mali-T720 MP2 (Gralloc 0.3 / HWComposer 1.5)",
            rilInterface = "Samsung SEC RIL Single-SIM (IPC) / MTK CCK",
            audioDriver = "MediaTek ALSA (mt6737)",
            cameraHal = "MediaTek Camera HAL1 (Legacy non-Treble)",
            mountPoints = mapOf(
                "/system" to "/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/system",
                "/boot" to "/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/boot",
                "/data" to "/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/userdata",
                "/cache" to "/dev/block/platform/mtk-msdc.0/11230000.msdc0/by-name/cache"
            ),
            properties = mapOf(
                "ro.product.model" to "SM-G532F",
                "ro.product.board" to "grandpplte",
                "ro.board.platform" to "mt6737t",
                "ro.product.cpu.abi" to "armeabi-v7a",
                "ro.treble.enabled" to "false",
                "ro.build.version.release" to "6.0.1",
                "ro.build.version.sdk" to "23"
            ),
            evidenceList = listOf(
                PortEvidence("model_spec", "SM-G532F", "Factory Samsung Stock Firmware G532FXWU1ARF1", "build.prop"),
                PortEvidence("partition_budget", "1.60 GB (1,719,664,640 bytes)", "eMMC Partition Table Header", "pit_manifest.xml"),
                PortEvidence("kernel_limit", "Linux 3.18.35+ (Binder 32-bit IPC v7/v8)", "Kernel defconfig", "arch/arm/configs/grandpplte_defconfig")
            )
        ),
        TargetDeviceProfile(
            id = "target_j2prime_sm_g532g_duos",
            name = "Samsung Galaxy J2 Prime Dual-SIM (SM-G532G/DS)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "SM-G532G",
            board = "grandpplte",
            platform = "mt6737t",
            cpuArch = "armv7-a-neon (32-bit ARM)",
            is64Bit = false,
            maxKernelVersion = "3.18.35+",
            isTrebleSupported = false,
            maxSystemPartitionBytes = 1719664640L,
            maxBootPartitionBytes = 16777216L,
            selinuxMode = "Enforcing",
            rootAvailable = true,
            supportedAbis = listOf("armeabi-v7a", "armeabi"),
            maliGpu = "ARM Mali-T720 MP2",
            rilInterface = "Samsung SEC RIL Dual-SIM (IPC)",
            audioDriver = "MediaTek ALSA (mt6737)",
            cameraHal = "MediaTek Camera HAL1",
            properties = mapOf(
                "ro.product.model" to "SM-G532G",
                "ro.product.board" to "grandpplte",
                "ro.board.platform" to "mt6737t",
                "ro.product.cpu.abi" to "armeabi-v7a",
                "ro.telephony.default_network" to "9,9",
                "persist.radio.multisim.config" to "dsds"
            ),
            evidenceList = listOf(
                PortEvidence("dual_sim", "dsds", "Dual SIM Multi-RIL spec", "build.prop: persist.radio.multisim.config"),
                PortEvidence("cpu_arch", "armv7-a-neon (32-bit)", "Hardware SoC MT6737T", "/proc/cpuinfo")
            )
        ),
        TargetDeviceProfile(
            id = "target_j2prime_sm_g532m_latam",
            name = "Samsung Galaxy J2 Prime LATAM (SM-G532M)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "SM-G532M",
            board = "grandpplte",
            platform = "mt6737t",
            cpuArch = "armv7-a-neon (32-bit ARM)",
            is64Bit = false,
            maxKernelVersion = "3.18.35+",
            isTrebleSupported = false,
            maxSystemPartitionBytes = 1719664640L,
            maxBootPartitionBytes = 16777216L,
            selinuxMode = "Enforcing",
            rootAvailable = true,
            supportedAbis = listOf("armeabi-v7a", "armeabi"),
            maliGpu = "ARM Mali-T720 MP2",
            rilInterface = "Samsung SEC RIL Single-SIM (IPC)",
            audioDriver = "MediaTek ALSA",
            cameraHal = "MediaTek Camera HAL1",
            properties = mapOf(
                "ro.product.model" to "SM-G532M",
                "ro.product.board" to "grandpplte",
                "ro.board.platform" to "mt6737t",
                "ro.product.cpu.abi" to "armeabi-v7a"
            ),
            evidenceList = listOf(
                PortEvidence("model", "SM-G532M", "LATAM Baseband Reference", "build.prop")
            )
        )
    )

    val REFERENCE_SOURCE_ROMS = listOf(
        SourceRomProfile(
            id = "source_lineageos_18_1_g532",
            name = "LineageOS 18.1 (Android 11 - G532 MTK32 Base)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "SM-G532F",
            device = "grandpplte",
            brand = "LineageOS",
            manufacturer = "samsung",
            androidVersion = "11.0.0 (Android 11)",
            sdkInt = 30,
            securityPatch = "2022-04-05",
            architecture = "armeabi-v7a (32-bit ARM)",
            is64Bit = false,
            isTreble = false,
            isAb = false,
            systemFsType = "ext4",
            systemSizeBytes = 943718400L, // ~900 MB
            bootImgSize = 12582912L, // 12 MB
            kernelCmdline = "bootopt=64S3,32N2,32N2 androidboot.selinux=permissive",
            targetChipset = "MediaTek MT6737T",
            buildDisplayId = "lineage_grandpplte-userdebug 11 RQ3A.211001.001",
            fingerprint = "samsung/grandppltexx/grandpplte:6.0.1/MMB29T/G532FXWU1ARF1:user/release-keys",
            selinuxMode = "Permissive",
            partitions = listOf(
                PartitionInfo("system", "system.img", 943718400L, "ext4"),
                PartitionInfo("boot", "boot.img", 12582912L, "android_boot_v1")
            ),
            bootDetails = BootImageDetails(
                headerVersion = 1,
                pageSize = 2048,
                kernelSizeBytes = 8388608L,
                ramdiskSizeBytes = 3145728L,
                cmdline = "bootopt=64S3,32N2,32N2 androidboot.selinux=permissive",
                osVersion = "11.0.0",
                osPatchLevel = "2022-04-05"
            ),
            dtbDetails = DtbInfo(hasDtb = true, hasDtbo = false, totalDtbSizeBytes = 45056L, socCompatibleList = listOf("mediatek,mt6737t")),
            halDetails = HalSummary(
                isTreble = false,
                vndkVersion = "None (Legacy non-Treble)",
                hidlServices = listOf("android.hardware.graphics.allocator@2.0", "android.hardware.audio@6.0", "android.hardware.wifi@1.0"),
                legacyHals = listOf("gralloc.mt6737.so", "hwcomposer.mt6737.so", "camera.mt6737.so")
            ),
            rilDetails = RilSummary(rilImplementation = "Samsung SEC RIL (IPC)", multiSimConfig = "dsds"),
            selinuxDetails = SelinuxSummary(defaultMode = "Permissive", hasPlatSepolicy = true, fileContextsCount = 420),
            elfDetails = ElfSummary(totalBinariesScanned = 180, elf32Count = 180, elf64Count = 0, isPure32Bit = true),
            auditedFields = listOf(
                SourceFieldAudit("model", "Device Model", "SM-G532F", "build.prop: ro.product.model", 0.99f, false, "Hardware"),
                SourceFieldAudit("android_version", "Android Release Version", "11.0.0", "build.prop: ro.build.version.release", 0.99f, false, "Android OS"),
                SourceFieldAudit("sdk_int", "API / SDK Level", "API 30", "build.prop: ro.build.version.sdk", 0.99f, false, "Android OS"),
                SourceFieldAudit("cpu_abi", "CPU Native ABI", "armeabi-v7a (32-bit ARM)", "ELF scan & build.prop", 0.99f, false, "Architecture & ABI")
            ),
            halServices = listOf(
                "android.hardware.graphics.allocator@2.0",
                "android.hardware.graphics.composer@2.1",
                "android.hardware.audio@6.0",
                "android.hardware.wifi@1.0"
            ),
            properties = mapOf(
                "ro.build.version.release" to "11",
                "ro.build.version.sdk" to "30",
                "ro.product.cpu.abi" to "armeabi-v7a",
                "ro.lineage.version" to "18.1",
                "ro.treble.enabled" to "false"
            ),
            evidenceList = listOf(
                PortEvidence("rom_manifest", "LineageOS 18.1 Android 11", "Reference Port Tree Manifest", "system/build.prop"),
                PortEvidence("abi_check", "armeabi-v7a (Pure 32-bit)", "No lib64 directories present", "system/lib/"),
                PortEvidence("size_budget", "900 MB (Within 1.6GB limit)", "system.img ext4 footprint", "system.img")
            )
        ),
        SourceRomProfile(
            id = "source_aosp_7_1_2_mt6737",
            name = "AOSP 7.1.2 Nougat (MT6737 32-bit Base)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "AOSP MT6737",
            device = "generic_arm",
            brand = "Android",
            manufacturer = "Google/AOSP",
            androidVersion = "7.1.2 (Nougat)",
            sdkInt = 25,
            securityPatch = "2019-10-05",
            architecture = "armeabi-v7a (32-bit ARM)",
            is64Bit = false,
            isTreble = false,
            isAb = false,
            systemFsType = "ext4",
            systemSizeBytes = 681574400L, // ~650 MB
            bootImgSize = 10485760L,
            kernelCmdline = "bootopt=64S3,32N2,32N2",
            targetChipset = "MediaTek MT6737",
            buildDisplayId = "aosp_mt6737-userdebug 7.1.2 N2G48H",
            fingerprint = "google/aosp_arm/generic:7.1.2/N2G48H/4402370:userdebug/test-keys",
            selinuxMode = "Enforcing",
            partitions = listOf(
                PartitionInfo("system", "system.img", 681574400L, "ext4"),
                PartitionInfo("boot", "boot.img", 10485760L, "android_boot_v0")
            ),
            bootDetails = BootImageDetails(headerVersion = 0, pageSize = 2048, osVersion = "7.1.2", osPatchLevel = "2019-10-05"),
            dtbDetails = DtbInfo(hasDtb = true, totalDtbSizeBytes = 32768L, socCompatibleList = listOf("mediatek,mt6737")),
            halDetails = HalSummary(isTreble = false, legacyHals = listOf("gralloc.default.so", "hwcomposer.default.so")),
            rilDetails = RilSummary(rilImplementation = "Generic AOSP RIL (libril.so)"),
            selinuxDetails = SelinuxSummary(defaultMode = "Enforcing", hasPlatSepolicy = true),
            elfDetails = ElfSummary(totalBinariesScanned = 120, elf32Count = 120, elf64Count = 0, isPure32Bit = true),
            auditedFields = listOf(
                SourceFieldAudit("model", "Device Model", "AOSP MT6737", "build.prop: ro.product.model", 0.98f, false, "Hardware"),
                SourceFieldAudit("android_version", "Android Release Version", "7.1.2", "build.prop: ro.build.version.release", 0.99f, false, "Android OS"),
                SourceFieldAudit("sdk_int", "API / SDK Level", "API 25", "build.prop: ro.build.version.sdk", 0.99f, false, "Android OS")
            ),
            halServices = listOf("android.hardware.graphics.allocator@2.0"),
            properties = mapOf(
                "ro.build.version.release" to "7.1.2",
                "ro.build.version.sdk" to "25",
                "ro.product.cpu.abi" to "armeabi-v7a",
                "ro.treble.enabled" to "false"
            ),
            evidenceList = listOf(
                PortEvidence("rom_base", "AOSP 7.1.2 MT6737", "AOSP Vanilla Manifest", "build.prop"),
                PortEvidence("system_size", "650 MB", "Minimal system footprint", "system.img")
            )
        ),
        SourceRomProfile(
            id = "source_havoc_3_12_q",
            name = "Havoc-OS 3.12 (Android 10 - MT6737 32-bit)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "SM-G532F",
            device = "grandpplte",
            brand = "Havoc-OS",
            manufacturer = "samsung",
            androidVersion = "10.0.0 (Android 10 Q)",
            sdkInt = 29,
            securityPatch = "2020-12-05",
            architecture = "armeabi-v7a (32-bit ARM)",
            is64Bit = false,
            isTreble = false,
            isAb = false,
            systemFsType = "ext4",
            systemSizeBytes = 891289600L, // ~850 MB
            bootImgSize = 12582912L,
            kernelCmdline = "bootopt=64S3,32N2,32N2 androidboot.selinux=permissive",
            targetChipset = "MediaTek MT6737T",
            buildDisplayId = "Havoc-OS-v3.12-20201215-grandpplte-Official",
            fingerprint = "samsung/grandppltexx/grandpplte:6.0.1/MMB29T/G532FXWU1ARF1:user/release-keys",
            selinuxMode = "Permissive",
            partitions = listOf(
                PartitionInfo("system", "system.img", 891289600L, "ext4"),
                PartitionInfo("boot", "boot.img", 12582912L, "android_boot_v1")
            ),
            bootDetails = BootImageDetails(headerVersion = 1, pageSize = 2048, osVersion = "10.0.0", osPatchLevel = "2020-12-05"),
            halDetails = HalSummary(isTreble = false, hidlServices = listOf("android.hardware.graphics.allocator@2.0", "android.hardware.audio@4.0")),
            rilDetails = RilSummary(rilImplementation = "Samsung SEC RIL (IPC)"),
            selinuxDetails = SelinuxSummary(defaultMode = "Permissive", hasPlatSepolicy = true),
            elfDetails = ElfSummary(totalBinariesScanned = 150, elf32Count = 150, elf64Count = 0, isPure32Bit = true),
            auditedFields = listOf(
                SourceFieldAudit("model", "Device Model", "SM-G532F", "build.prop: ro.product.model", 0.99f, false, "Hardware"),
                SourceFieldAudit("android_version", "Android Release Version", "10.0.0", "build.prop: ro.build.version.release", 0.99f, false, "Android OS"),
                SourceFieldAudit("sdk_int", "API / SDK Level", "API 29", "build.prop: ro.build.version.sdk", 0.99f, false, "Android OS")
            ),
            halServices = listOf(
                "android.hardware.graphics.allocator@2.0",
                "android.hardware.audio@4.0"
            ),
            properties = mapOf(
                "ro.build.version.release" to "10",
                "ro.build.version.sdk" to "29",
                "ro.product.cpu.abi" to "armeabi-v7a",
                "ro.havoc.version" to "3.12"
            ),
            evidenceList = listOf(
                PortEvidence("source_q", "Havoc-OS Android 10", "Havoc Source Manifest", "build.prop")
            )
        ),
        SourceRomProfile(
            id = "source_oneui_g570f_port",
            name = "Samsung OneUI 1.0 (Exynos 7570 / G570F Port Base)",
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = "SM-G570F",
            device = "on5xelte",
            brand = "samsung",
            manufacturer = "samsung",
            androidVersion = "9.0.0 (Android 9 Pie)",
            sdkInt = 28,
            securityPatch = "2020-03-01",
            architecture = "arm64-v8a (64-bit ARM)",
            is64Bit = true, // Contains 64-bit blobs -> will trigger BLOCKER demonstration!
            isTreble = true,
            isAb = false,
            systemFsType = "ext4",
            systemSizeBytes = 1939865600L, // ~1.85 GB (Exceeds 1.6GB -> will trigger size BLOCKER!)
            bootImgSize = 16777216L,
            kernelCmdline = "console=null androidboot.selinux=enforcing",
            targetChipset = "Samsung Exynos 7570",
            buildDisplayId = "PPR1.180610.011.G570FXXU3CSJ1",
            fingerprint = "samsung/on5xeltejx/on5xelte:9/PPR1.180610.011/G570FXXU3CSJ1:user/release-keys",
            selinuxMode = "Enforcing",
            partitions = listOf(
                PartitionInfo("system", "system.img", 1939865600L, "ext4"),
                PartitionInfo("vendor", "vendor.img", 314572800L, "ext4"),
                PartitionInfo("boot", "boot.img", 16777216L, "android_boot_v2")
            ),
            bootDetails = BootImageDetails(headerVersion = 2, pageSize = 2048, osVersion = "9.0.0", osPatchLevel = "2020-03-01"),
            halDetails = HalSummary(isTreble = true, vndkVersion = "28", hidlServices = listOf("android.hardware.graphics.allocator@2.0", "vendor.samsung.hardware.camera@1.0")),
            rilDetails = RilSummary(rilImplementation = "Samsung SEC RIL (IPC)"),
            selinuxDetails = SelinuxSummary(defaultMode = "Enforcing", hasPlatSepolicy = true, hasVendorSepolicy = true),
            elfDetails = ElfSummary(
                totalBinariesScanned = 340,
                elf32Count = 120,
                elf64Count = 220,
                isPure32Bit = false,
                contains64BitBlobs = true,
                sample64BitBinaries = listOf("system/lib64/libc.so", "system/lib64/libm.so", "vendor/lib64/hw/camera.exynos7570.so")
            ),
            auditedFields = listOf(
                SourceFieldAudit("model", "Device Model", "SM-G570F", "build.prop: ro.product.model", 0.99f, false, "Hardware"),
                SourceFieldAudit("android_version", "Android Release Version", "9.0.0", "build.prop: ro.build.version.release", 0.99f, false, "Android OS"),
                SourceFieldAudit("sdk_int", "API / SDK Level", "API 28", "build.prop: ro.build.version.sdk", 0.99f, false, "Android OS"),
                SourceFieldAudit("cpu_abi", "CPU Native ABI", "arm64-v8a (64-bit ARM)", "ELF scan & build.prop", 0.99f, false, "Architecture & ABI")
            ),
            halServices = listOf(
                "android.hardware.graphics.allocator@2.0",
                "vendor.samsung.hardware.camera@1.0"
            ),
            properties = mapOf(
                "ro.build.version.release" to "9",
                "ro.build.version.sdk" to "28",
                "ro.product.cpu.abi" to "arm64-v8a",
                "ro.treble.enabled" to "true"
            ),
            evidenceList = listOf(
                PortEvidence("abi_conflict", "arm64-v8a (64-bit)", "Exynos 7570 64-bit Architecture", "build.prop"),
                PortEvidence("size_overflow", "1.85 GB (Over J2 Prime 1.6GB limit)", "System dump size", "system.img"),
                PortEvidence("treble_mismatch", "ro.treble.enabled=true", "Treble VNDK architecture", "build.prop")
            )
        )
    )

    // ==========================================
    // 2. LIVE DEVICE & IMPORTED EXTRACTORS
    // ==========================================

    suspend fun extractLiveDeviceProfile(
        context: Context,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): TargetDeviceProfile {
        return TargetDeviceAnalyzerEngine.analyzeLiveDevice(context, onProgress)
    }

    suspend fun extractSourceRomFromZip(
        context: Context,
        uri: Uri,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): SourceRomProfile {
        return SourceRomAnalyzerEngine.analyzeFromZip(context, uri, onProgress)
    }

    suspend fun analyzeFromFolder(
        folder: File,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): SourceRomProfile {
        return SourceRomAnalyzerEngine.analyzeFromFolder(folder, onProgress)
    }

    suspend fun analyzeSingleFile(
        context: Context,
        uri: Uri,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): SourceRomProfile {
        return SourceRomAnalyzerEngine.analyzeSingleFile(context, uri, onProgress)
    }

    suspend fun loadSourceRomFromProject(
        project: RomProject,
        context: Context
    ): SourceRomProfile {
        return SourceRomAnalyzerEngine.analyzeFromProject(project) { _, _ -> }
    }

    // ==========================================
    // 3. INTELLIGENT PORT ANALYSIS ENGINE
    // ==========================================

    suspend fun analyzePortCompatibility(
        sourceRom: SourceRomProfile,
        targetDevice: TargetDeviceProfile,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): PortAnalysisResult = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        onProgress("Executing 25-Subsystem Source vs Target Compatibility Engine...", 0.1f)
        val compatResult = SourceTargetCompatibilityEngine.evaluateCompatibility(
            sourceRom = sourceRom,
            targetDevice = targetDevice,
            onProgress = { stage, p -> onProgress(stage, 0.1f + p * 0.4f) }
        )

        onProgress("Running Root Cause & Forensic Blocker Engine...", 0.55f)
        val rootCauseAudit = RootCauseBlockerEngine.analyzeRootCauses(
            sourceRom = sourceRom,
            targetDevice = targetDevice,
            compatResult = compatResult,
            onProgress = { stage, p -> onProgress(stage, 0.55f + p * 0.35f) }
        )

        val evaluatedProperties = compatResult.items.map { item ->
            val portStatus = when (item.status) {
                CompatibilityStatus.MATCH -> PortStatus.PASS
                CompatibilityStatus.DIFFERENT -> if (item.severity == CompatibilitySeverity.WARNING) PortStatus.WARNING else PortStatus.PASS
                CompatibilityStatus.MISSING -> PortStatus.WARNING
                CompatibilityStatus.CONFLICT -> if (item.isBlocker) PortStatus.BLOCKER else PortStatus.WARNING
                CompatibilityStatus.UNKNOWN -> PortStatus.UNKNOWN
            }
            PortEvaluatedProperty(
                key = item.key,
                category = item.category,
                label = item.label,
                value = "${item.subsystem}: ${item.sourceValue} vs ${item.targetValue}",
                source = sourceRom.source,
                evidence = item.evidence,
                confidence = item.confidence,
                status = portStatus,
                notes = item.reason
            )
        }

        val blockers = compatResult.issues.filter { it.severity == CompatibilitySeverity.BLOCKER }.map { issue ->
            PortIssue(
                id = issue.id,
                title = issue.title,
                description = issue.reason,
                category = issue.category,
                status = PortStatus.BLOCKER,
                isBlocker = true,
                value = issue.evidence.rawValue,
                source = sourceRom.source,
                evidence = issue.evidence,
                confidence = issue.confidence,
                recommendation = issue.recommendation,
                fixStrategy = issue.fixStrategy
            )
        }

        val warnings = compatResult.issues.filter { it.severity == CompatibilitySeverity.WARNING }.map { issue ->
            PortIssue(
                id = issue.id,
                title = issue.title,
                description = issue.reason,
                category = issue.category,
                status = PortStatus.WARNING,
                isBlocker = false,
                value = issue.evidence.rawValue,
                source = sourceRom.source,
                evidence = issue.evidence,
                confidence = issue.confidence,
                recommendation = issue.recommendation,
                fixStrategy = issue.fixStrategy
            )
        }

        val errors = compatResult.issues.filter { it.severity == CompatibilitySeverity.ERROR }.map { issue ->
            PortIssue(
                id = issue.id,
                title = issue.title,
                description = issue.reason,
                category = issue.category,
                status = PortStatus.ERROR,
                isBlocker = false,
                value = issue.evidence.rawValue,
                source = sourceRom.source,
                evidence = issue.evidence,
                confidence = issue.confidence,
                recommendation = issue.recommendation,
                fixStrategy = issue.fixStrategy
            )
        }

        val passes = compatResult.items.filter { it.status == CompatibilityStatus.MATCH }.map { item ->
            PortIssue(
                id = "pass_${item.key}",
                title = "${item.label} Compatible",
                description = item.reason,
                category = item.category,
                status = PortStatus.PASS,
                isBlocker = false,
                value = item.sourceValue,
                source = sourceRom.source,
                evidence = item.evidence,
                confidence = item.confidence,
                recommendation = "Compatible."
            )
        }

        val portPlan = generatePortPlan(sourceRom, targetDevice, blockers, warnings)

        onProgress("Discovering Migration Candidates across 12 Subsystem Categories...", 0.92f)
        val migrationCandidates = MigrationCandidatesEngine.discoverCandidates(sourceRom, targetDevice)

        onProgress("Building Structured 11-Section Port Plan...", 0.96f)
        val structuredPlan = PortPlanBuilderEngine.buildStructuredPlan(
            sourceRom = sourceRom,
            targetDevice = targetDevice,
            candidates = migrationCandidates,
            blockers = rootCauseAudit.blockers
        )

        onProgress("Port Compatibility Audit Finished!", 1.0f)

        PortAnalysisResult(
            sessionId = "session_${timestamp}",
            timestamp = timestamp,
            sourceRom = sourceRom,
            targetDevice = targetDevice,
            evaluatedProperties = evaluatedProperties,
            blockers = blockers,
            warnings = warnings,
            errors = errors,
            passes = passes,
            readiness = rootCauseAudit.readiness,
            generatedPortPlan = portPlan,
            compatibilityResult = compatResult,
            portBlockers = rootCauseAudit.blockers,
            portWarnings = rootCauseAudit.warnings,
            rootCauses = rootCauseAudit.rootCauses,
            whatToFixFirst = rootCauseAudit.whatToFixFirst,
            migrationCandidates = migrationCandidates,
            structuredPortPlan = structuredPlan
        )
    }

    // ==========================================
    // 4. PORTING PLAN GENERATOR
    // ==========================================

    private fun generatePortPlan(
        sourceRom: SourceRomProfile,
        targetDevice: TargetDeviceProfile,
        blockers: List<PortIssue>,
        warnings: List<PortIssue>
    ): PortPlan {
        val steps = mutableListOf<PortPlanStep>()
        var stepNum = 1

        if (blockers.any { it.id == "issue_abi_64bit_blocker" || it.id == "issue_source_64bit_blobs" }) {
            steps.add(
                PortPlanStep(
                    stepNumber = stepNum++,
                    title = "Strip 64-bit Binaries & Libraries",
                    description = "Remove system/lib64, vendor/lib64, and any 64-bit ELF binaries. Replace with 32-bit ARMv7-A compiled equivalents.",
                    category = "Architecture Fix",
                    automatedActionType = "STRIP_64BIT",
                    isRequired = true,
                    estimatedRisk = "High",
                    commandHint = "rm -rf workspace/system/lib64 workspace/system/vendor/lib64"
                )
            )
        }

        if (blockers.any { it.id == "issue_system_overflow_blocker" }) {
            steps.add(
                PortPlanStep(
                    stepNumber = stepNum++,
                    title = "Debloat System Partition to Fit 1.6GB Budget",
                    description = "Remove unnecessary prebuilts, ringtones, TTS data, and large Google apps until system size is under 1,450 MB.",
                    category = "Partition Size Optimization",
                    automatedActionType = "DEBLOAT_SYSTEM",
                    isRequired = true,
                    estimatedRisk = "Medium",
                    commandHint = "rm -rf workspace/system/app/Chrome workspace/system/priv-app/Velvet workspace/system/tts"
                )
            )
        }

        steps.add(
            PortPlanStep(
                stepNumber = stepNum++,
                title = "Inject MediaTek MT6737T Proprietary Blobs",
                description = "Copy 32-bit GPU Mali-T720 drivers (libGLES_mali.so, gralloc.mt6737.so, hwcomposer.mt6737.so), MediaTek Audio HAL, and Camera HAL1 blobs from J2 Prime stock base.",
                category = "Driver Injection",
                automatedActionType = "INJECT_VENDOR_BLOBS",
                isRequired = true,
                estimatedRisk = "Medium",
                commandHint = "cp -r base_blobs/lib/* workspace/system/lib/"
            )
        )

        steps.add(
            PortPlanStep(
                stepNumber = stepNum++,
                title = "Adapt Kernel & Ramdisk (boot.img)",
                description = "Unpack source boot.img, replace kernel zImage with J2 Prime 3.18.35+ kernel, update init.rc mountpoints to match eMMC partition paths.",
                category = "Kernel & Boot",
                automatedActionType = "REPACK_BOOT",
                isRequired = true,
                estimatedRisk = "High",
                commandHint = "mkbootimg --kernel zImage --ramdisk ramdisk.cpio.gz --cmdline 'bootopt=64S3,32N2,32N2' -o boot.img"
            )
        )

        steps.add(
            PortPlanStep(
                stepNumber = stepNum++,
                title = "Tune build.prop & Telephony Flags",
                description = "Set ro.product.model=${targetDevice.model}, ro.product.board=${targetDevice.board}, ro.board.platform=mt6737t, ro.treble.enabled=false, and configure SEC RIL IPC.",
                category = "System Properties",
                automatedActionType = "PATCH_BUILD_PROP",
                isRequired = true,
                estimatedRisk = "Low",
                commandHint = "echo 'ro.board.platform=mt6737t' >> workspace/system/build.prop"
            )
        )

        steps.add(
            PortPlanStep(
                stepNumber = stepNum++,
                title = "Compile Flashable ZIP & Generate Signatures",
                description = "Assemble partition images into flashable update zip via ROM Build Studio Engine, calculate SHA-256 and MD5 checksums.",
                category = "Build & Repack",
                automatedActionType = "BUILD_ZIP",
                isRequired = true,
                estimatedRisk = "Low",
                commandHint = "zip -r9 update.zip META-INF system boot.img"
            )
        )

        return PortPlan(
            id = "plan_${System.currentTimeMillis()}",
            title = "ROM Porting Plan: ${sourceRom.name} -> ${targetDevice.name}",
            targetDeviceName = targetDevice.name,
            sourceRomName = sourceRom.name,
            steps = steps,
            preCheckList = listOf(
                "Verify battery level is above 50% on J2 Prime device",
                "Ensure TWRP Recovery 3.x is installed",
                "Perform a complete Nandroid Backup of EFS, BOOT, and SYSTEM partitions",
                "Verify target eMMC partition table layout"
            ),
            postInstallChecklist = listOf(
                "Perform Wipe Dalvik / ART Cache and Cache in TWRP",
                "Check initial dmesg log via ADB for kernel panic / avc denials",
                "Verify touch screen digitization, display backlight, and Mali-T720 GPU rendering",
                "Verify SIM card detection, RIL baseband version, and mobile data connectivity",
                "Test MediaTek Camera HAL1 preview and photo capture"
            )
        )
    }

    // ==========================================
    // 5. REPORT GENERATOR INTEGRATION
    // ==========================================

    suspend fun generatePortingReport(
        context: Context,
        result: PortAnalysisResult,
        format: ReportFormat = ReportFormat.MARKDOWN
    ): String = withContext(Dispatchers.IO) {
        val sections = LinkedHashMap<String, String>()

        // 1. Source
        sections["Source"] = buildString {
            appendLine("Name:             ${result.sourceRom.name}")
            appendLine("Origin:           ${result.sourceRom.source}")
            appendLine("Model:            ${result.sourceRom.model}")
            appendLine("Device / Board:   ${result.sourceRom.device}")
            appendLine("Brand / Manuf:    ${result.sourceRom.brand} / ${result.sourceRom.manufacturer}")
            appendLine("Fingerprint:      ${result.sourceRom.fingerprint}")
            appendLine("Display Build:    ${result.sourceRom.buildDisplayId}")
        }

        // 2. Target
        sections["Target"] = buildString {
            appendLine("Name:             ${result.targetDevice.name}")
            appendLine("Model:            ${result.targetDevice.model}")
            appendLine("Board / Platform: ${result.targetDevice.board} / ${result.targetDevice.platform}")
            appendLine("Chipset:          MediaTek MT6737T (64-bit Cortex-A53 running in 32-bit mode)")
            appendLine("Root Access:      ${if (result.targetDevice.rootAvailable) "Available (UID=0)" else "Not Available / ADB Only"}")
            appendLine("System Capacity:  ${result.targetDevice.maxSystemPartitionBytes / (1024 * 1024)} MB (~1.60 GB eMMC limit)")
            appendLine("Boot Capacity:    ${result.targetDevice.maxBootPartitionBytes / (1024 * 1024)} MB (16 MB)")
        }

        // 3. Android
        sections["Android"] = buildString {
            appendLine("Source Android:   ${result.sourceRom.androidVersion} (SDK API ${if (result.sourceRom.sdkInt > 0) result.sourceRom.sdkInt.toString() else "UNKNOWN"})")
            appendLine("Target Android:   ${result.targetDevice.properties["ro.build.version.release"] ?: "6.0.1 (Stock TouchWiz) / 11 (LineageOS 18.1)"}")
            appendLine("Treble Layout:    Source: ${if (result.sourceRom.isTreble) "Treble (vndk)" else "Legacy Non-Treble"} | Target: Legacy Non-Treble")
            appendLine("A/B Slot Layout:  Source: ${if (result.sourceRom.isAb) "A/B Seamless" else "A-Only"} | Target: A-Only")
        }

        // 4. Architecture
        sections["Architecture"] = buildString {
            appendLine("Source ABI:       ${result.sourceRom.architecture} (64-bit: ${result.sourceRom.is64Bit})")
            appendLine("Target ABI:       ${result.targetDevice.cpuArch} [Supported: ${result.targetDevice.supportedAbis.joinToString(", ")}]")
            appendLine("Binary Constraint: Strictly 32-bit ARM (armeabi-v7a). Any 64-bit ELF binary will fail to execute.")
        }

        // 5. Kernel
        sections["Kernel"] = buildString {
            appendLine("Target Kernel:    ${result.targetDevice.maxKernelVersion} (Linux 3.18.35+ MT6737T defconfig)")
            appendLine("Binder IPC:       32-bit BINDER_IPC_32BIT ioctl configuration mandatory")
            appendLine("Cmdline:          ${result.targetDevice.kernelCmdline.ifEmpty { "bootopt=64S3,32N2,32N2 buildvariant=user" }}")
        }

        // 6. Boot
        sections["Boot"] = buildString {
            val b = result.sourceRom.bootDetails
            appendLine("Header Version:   v${b.headerVersion}")
            appendLine("Page Size:        ${b.pageSize} bytes (Target requires 2048)")
            appendLine("Kernel Size:      ${b.kernelSizeBytes / 1024} KB")
            appendLine("Ramdisk Size:     ${b.ramdiskSizeBytes / 1024} KB")
            appendLine("DTB Size:         ${b.dtbSizeBytes / 1024} KB")
            appendLine("Boot Output:      Target 16MB boot.img packaging requires zImage replacement and ramdisk rebuild")
        }

        // 7. DTB
        sections["DTB"] = buildString {
            appendLine("Target Platform:  MT6737T Device Tree Blob (grandpplte)")
            appendLine("Nodes:            LCM NT35521 / HX8394D panel nodes, Goodix / FocalTech touch DTB nodes")
            appendLine("Status:           Requires original kernel.bin + DTB append or separate dtb partition")
        }

        // 8. Vendor
        sections["Vendor"] = buildString {
            appendLine("Vendor Structure: Legacy non-Treble system/vendor monolithic partition")
            appendLine("Proprietary MTK:  Mali-T720 GPU drivers, MediaTek RIL daemon (ccci_mdinit), audio/camera blobs")
            appendLine("NVRAM:            /data/nvram and /nvdata calibration bindings")
        }

        // 9. HAL
        sections["HAL"] = buildString {
            appendLine("Camera HAL:       ${result.targetDevice.cameraHal}")
            appendLine("Graphics GPU:     ${result.targetDevice.maliGpu} (libGLES_mali.so, Gralloc 0.3)")
            appendLine("Audio HAL:        ${result.targetDevice.audioDriver} (audio.primary.mt6737t.so)")
            appendLine("Sensors HAL:      Accelerometor, Proximity (sensors.mt6737t.so)")
        }

        // 10. RIL
        sections["RIL"] = buildString {
            appendLine("Telephony RIL:    ${result.targetDevice.rilInterface}")
            appendLine("Libraries:        ${result.targetDevice.rilSummary.telephonyLibraries.joinToString(", ").ifEmpty { "libsec-ril.so, librilmtk.so" }}")
            appendLine("Multi-SIM Config: ${result.targetDevice.rilSummary.multiSimConfig}")
        }

        // 11. SELinux
        sections["SELinux"] = buildString {
            appendLine("Source SELinux:   ${result.sourceRom.selinuxMode}")
            appendLine("Target SELinux:   ${result.targetDevice.selinuxMode}")
            appendLine("Contexts Count:   File Contexts: ${result.targetDevice.properties["file_contexts_count"] ?: "740+"}")
            appendLine("Policy Advice:    Permissive kernel cmdline (androidboot.selinux=permissive) recommended for initial bringup")
        }

        // 12. ELF
        sections["ELF"] = buildString {
            appendLine("Source 32-bit:    ${result.sourceRom.elfDetails.elf32Count} binaries audited")
            appendLine("Source 64-bit:    ${result.sourceRom.elfDetails.elf64Count} binaries audited (Pure 32-bit: ${result.sourceRom.elfDetails.isPure32Bit})")
            if (result.sourceRom.elfDetails.sample64BitBinaries.isNotEmpty()) {
                appendLine("64-bit Samples:   ${result.sourceRom.elfDetails.sample64BitBinaries.take(5).joinToString(", ")}")
            }
        }

        // 13. Partitions
        sections["Partitions"] = buildString {
            appendLine("Target Budget:    1,719,664,640 bytes (~1.60 GB eMMC)")
            appendLine("Source System:    ${result.sourceRom.systemSizeBytes / (1024 * 1024)} MB (${result.sourceRom.systemFsType})")
            val diff = result.targetDevice.maxSystemPartitionBytes - result.sourceRom.systemSizeBytes
            appendLine("Budget Margin:    ${diff / (1024 * 1024)} MB ${if (diff >= 0) "(Fits within 1.6GB)" else "(OVERFLOW - Debloat Required!)"}")
            result.sourceRom.partitions.forEach { part ->
                appendLine("• /${part.name}: ${part.sizeBytes / (1024 * 1024)} MB (${part.format})")
            }
        }

        // 14. Properties
        sections["Properties"] = buildString {
            appendLine("Build ID:         ${result.sourceRom.buildDisplayId}")
            appendLine("Fingerprint:      ${result.sourceRom.fingerprint}")
            appendLine("Target Model:     ro.product.model=SM-G532F")
            appendLine("Target Hardware:  ro.hardware=mt6737t")
        }

        // 15. Init
        sections["Init"] = buildString {
            appendLine("Init Scripts:     init.mt6737t.rc, init.grandpplte.rc, ueventd.mt6737t.rc")
            appendLine("Services:         ccci_fsd, ccci_mdinit, nvram_daemon, mobile_log_d")
        }

        // 16. Blockers
        sections["Blockers"] = buildString {
            if (result.portBlockers.isEmpty() && result.blockers.isEmpty()) {
                appendLine("No active blocking violations found. (Ready to proceed)")
            } else {
                val allBlockers = if (result.portBlockers.isNotEmpty()) result.portBlockers.map { it.title to it.recommendation } else result.blockers.map { it.title to it.recommendation }
                appendLine("Total Blockers: ${allBlockers.size}")
                allBlockers.forEachIndexed { i, (title, rec) ->
                    appendLine("[Blocker #${i + 1}] $title")
                    appendLine("Resolution: $rec")
                }
            }
        }

        // 17. Warnings
        sections["Warnings"] = buildString {
            if (result.portWarnings.isEmpty() && result.warnings.isEmpty()) {
                appendLine("No warnings recorded.")
            } else {
                val allWarns = if (result.portWarnings.isNotEmpty()) result.portWarnings.map { it.title to it.recommendation } else result.warnings.map { it.title to it.recommendation }
                appendLine("Total Warnings: ${allWarns.size}")
                allWarns.forEachIndexed { i, (title, rec) ->
                    appendLine("[Warning #${i + 1}] $title")
                    appendLine("Note: $rec")
                }
            }
        }

        // 18. Unknown
        sections["Unknown"] = buildString {
            if (result.sourceRom.unknownFieldsList.isEmpty()) {
                appendLine("All source subsystem properties resolved with high confidence.")
            } else {
                appendLine("Total Low-Confidence / Unknown Fields: ${result.sourceRom.unknownFieldsList.size}")
                result.sourceRom.unknownFieldsList.forEach { unk ->
                    appendLine("• ${unk.label}: ${unk.value} (${unk.sourceOrigin})")
                }
            }
        }

        // 19. Migration Candidates
        sections["Migration Candidates"] = buildString {
            appendLine("Total Candidates: ${result.migrationCandidates.size}")
            result.migrationCandidates.groupBy { it.category }.forEach { (cat, list) ->
                appendLine("Category ${cat.label} (${list.size}):")
                list.take(4).forEach { cand ->
                    appendLine("  • ${cand.name} -> Risk: ${cand.risk} [${cand.path}]")
                }
            }
        }

        // 20. Port Plan
        sections["Port Plan"] = buildString {
            val plan = result.structuredPortPlan
            if (plan != null) {
                appendLine("Total Plan Sections: ${plan.sections.size} | Total Tasks: ${plan.totalTasks}")
                plan.sections.forEach { sec ->
                    appendLine("Section [${sec.sectionType.label}] ${sec.title}: ${sec.tasks.size} tasks")
                    sec.tasks.forEach { t ->
                        appendLine("  [${t.status}] ${t.title} (Risk: ${t.risk})")
                    }
                }
            } else {
                result.generatedPortPlan.steps.forEach { step ->
                    appendLine("Step ${step.stepNumber}: ${step.title} (${step.category})")
                }
            }
        }

        // 21. Readiness
        sections["Readiness"] = buildString {
            appendLine("Overall Score:    ${result.readiness.score} / 100")
            appendLine("Status:           ${result.readiness.status}")
            appendLine("State:            ${result.readiness.state}")
            appendLine("Can Build:        ${if (result.readiness.canProceedToBuild) "YES (Ready to Build)" else "NO (Blocked by Critical Issues)"}")
            appendLine("Summary:          ${result.readiness.summary}")
            if (result.whatToFixFirst != null) {
                appendLine("What To Fix First: [${result.whatToFixFirst.component}] ${result.whatToFixFirst.problem} -> Tool: ${result.whatToFixFirst.tool} (${result.whatToFixFirst.nextAction})")
            }
        }

        val reportStr = ReportGeneratorEngine.generateReport(
            context = context,
            type = ReportType.ROM_PORT_REPORT,
            format = format,
            projectName = "ROM Porting Matrix (${com.example.config.AppVersionConfig.RELEASE_NAME}): ${result.sourceRom.name} -> ${result.targetDevice.model}",
            customDetails = sections
        )

        return@withContext reportStr
    }
}
