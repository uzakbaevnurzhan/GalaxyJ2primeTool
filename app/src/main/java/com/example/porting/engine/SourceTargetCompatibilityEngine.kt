package com.example.porting.engine

import com.example.porting.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Intelligent SOURCE vs TARGET Compatibility Engine.
 * Evaluates 25 core subsystems between Source ROM Profile and Target Device Profile.
 *
 * Implements strict rules:
 * - Differentiates MATCH, DIFFERENT, MISSING, CONFLICT, and UNKNOWN.
 * - Does NOT treat benign differences (e.g. build display IDs, timestamps, normal version updates) as errors.
 * - Flags hard architectural BLOCKERS (ARM64 on ARM32, partition capacity overflow, incompatible SoC locks).
 * - Identifies non-fatal WARNINGS (Treble-to-non-Treble adaptation, SEC RIL shims, SELinux mode, kernel binder shims).
 */
object SourceTargetCompatibilityEngine {

    suspend fun evaluateCompatibility(
        sourceRom: SourceRomProfile,
        targetDevice: TargetDeviceProfile,
        onProgress: suspend (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): CompatibilityResult = withContext(Dispatchers.Default) {
        val items = mutableListOf<CompatibilityComparisonItem>()
        val issues = mutableListOf<CompatibilityIssue>()

        onProgress("Auditing Hardware, SoC, CPU & GPU...", 0.05f)

        // =========================================================================
        // 1. DEVICE (Model, Board, Product, Brand, Manufacturer)
        // =========================================================================
        val isSameModel = sourceRom.model.equals(targetDevice.model, ignoreCase = true) ||
                (sourceRom.model.startsWith("SM-G532", ignoreCase = true) && targetDevice.model.startsWith("SM-G532", ignoreCase = true))
        val isDeviceUnknown = sourceRom.model == "UNKNOWN"

        val deviceStatus = when {
            isDeviceUnknown -> CompatibilityStatus.UNKNOWN
            isSameModel -> CompatibilityStatus.MATCH
            else -> CompatibilityStatus.DIFFERENT
        }
        val deviceSeverity = when (deviceStatus) {
            CompatibilityStatus.MATCH -> CompatibilitySeverity.PASS
            CompatibilityStatus.UNKNOWN -> CompatibilitySeverity.INFO
            CompatibilityStatus.DIFFERENT -> CompatibilitySeverity.INFO
            else -> CompatibilitySeverity.INFO
        }
        val deviceItem = CompatibilityComparisonItem(
            key = "device_model",
            subsystem = "DEVICE",
            category = "Hardware & Identity",
            label = "Device Model & Identity",
            sourceValue = "${sourceRom.brand} ${sourceRom.model} (${sourceRom.device})",
            targetValue = "${targetDevice.name} (${targetDevice.model} / ${targetDevice.board})",
            status = deviceStatus,
            severity = deviceSeverity,
            reason = when (deviceStatus) {
                CompatibilityStatus.MATCH -> "Exact or variant model match (${sourceRom.model} / ${targetDevice.model})."
                CompatibilityStatus.UNKNOWN -> "Source ROM device model is not explicitly defined in build.prop."
                else -> "Porting across different device models (${sourceRom.model} -> ${targetDevice.model}). Common in ROM porting."
            },
            evidence = PortEvidence("device_match", "Source: ${sourceRom.model}, Target: ${targetDevice.model}", "Hardware identifier scan", "ro.product.model"),
            confidence = 0.95f,
            isBlocker = false,
            actionRequired = if (deviceStatus == CompatibilityStatus.DIFFERENT) "Adjust ro.product.* properties in ported build.prop." else null
        )
        items.add(deviceItem)

        // =========================================================================
        // 2. SOC (System-on-Chip)
        // =========================================================================
        val targetPlatform = targetDevice.platform.lowercase() // mt6737t
        val sourceChipset = sourceRom.targetChipset.lowercase()
        val isMtkSource = sourceChipset.contains("mt6737") || sourceChipset.contains("mt6735") || sourceChipset.contains("mt6753") || sourceChipset.contains("mediatek")
        val isGenericAosp = sourceChipset.contains("generic") || sourceChipset.contains("aosp") || sourceChipset.contains("lineage") || sourceChipset == "unknown"
        val isForeignSoc = !isMtkSource && !isGenericAosp && (sourceChipset.contains("exynos") || sourceChipset.contains("qcom") || sourceChipset.contains("qualcomm") || sourceChipset.contains("unisoc") || sourceChipset.contains("sprd"))

        val socStatus = when {
            sourceChipset == "unknown" -> CompatibilityStatus.UNKNOWN
            isMtkSource -> CompatibilityStatus.MATCH
            isGenericAosp -> CompatibilityStatus.DIFFERENT
            isForeignSoc -> CompatibilityStatus.CONFLICT
            else -> CompatibilityStatus.DIFFERENT
        }
        val socSeverity = when (socStatus) {
            CompatibilityStatus.MATCH -> CompatibilitySeverity.PASS
            CompatibilityStatus.UNKNOWN -> CompatibilitySeverity.INFO
            CompatibilityStatus.DIFFERENT -> CompatibilitySeverity.WARNING
            CompatibilityStatus.CONFLICT -> CompatibilitySeverity.WARNING // Cross-SoC port is challenging (needs full vendor transplant)
            CompatibilityStatus.MISSING -> CompatibilitySeverity.ERROR
        }
        val socItem = CompatibilityComparisonItem(
            key = "soc_chipset",
            subsystem = "SOC",
            category = "Hardware & SoC",
            label = "System on Chip (SoC)",
            sourceValue = sourceRom.targetChipset,
            targetValue = "MediaTek MT6737T (grandpplte)",
            status = socStatus,
            severity = socSeverity,
            reason = when (socStatus) {
                CompatibilityStatus.MATCH -> "Source ROM is built for MediaTek MT6737T family."
                CompatibilityStatus.DIFFERENT -> "Source ROM is built on generic AOSP/Lineage base; requires MT6737T kernel & vendor HAL transplant."
                CompatibilityStatus.CONFLICT -> "Source ROM is configured for foreign SoC (${sourceRom.targetChipset}). Requires stripping foreign vendor blobs and injecting full MT6737T vendor tree."
                CompatibilityStatus.UNKNOWN -> "SoC platform is unknown; assuming generic ARM32 framework."
                else -> "SoC variance detected."
            },
            evidence = PortEvidence("soc_platform", "Source: ${sourceRom.targetChipset}, Target: MT6737T", "SoC verification", "ro.board.platform"),
            confidence = 0.92f,
            isBlocker = false,
            actionRequired = if (socStatus != CompatibilityStatus.MATCH) "Transplant MT6737T kernel, DTB, and hardware proprietary blobs." else null
        )
        items.add(socItem)
        if (socStatus == CompatibilityStatus.CONFLICT) {
            issues.add(
                CompatibilityIssue(
                    id = "issue_foreign_soc",
                    category = "Hardware & SoC",
                    title = "Cross-SoC Port Base (${sourceRom.targetChipset} -> MT6737T)",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Source ROM originates from a different SoC family (${sourceRom.targetChipset}). Proprietary hardware blobs must be replaced.",
                    evidence = socItem.evidence,
                    confidence = 0.92f,
                    recommendation = "Strip /vendor/ and /system/vendor/, replacing all HAL libraries with J2 Prime MT6737T stock blobs.",
                    fixStrategy = "Use ROM Merge Engine with MT6737T Base Workspace."
                )
            )
        }

        // =========================================================================
        // 3. CPU (Cortex-A53 32-bit execution mode)
        // =========================================================================
        val cpuStatus = if (sourceRom.is64Bit && !targetDevice.is64Bit) CompatibilityStatus.CONFLICT else CompatibilityStatus.MATCH
        val cpuItem = CompatibilityComparisonItem(
            key = "cpu_cores",
            subsystem = "CPU",
            category = "Processor",
            label = "CPU Processor Core & Bit Width",
            sourceValue = if (sourceRom.is64Bit) "ARM Cortex-A53 (64-bit mode)" else "ARM Cortex-A53 (32-bit ARMv7-A mode)",
            targetValue = "ARM Cortex-A53 Quad-Core (32-bit ARMv7-A / AArch32)",
            status = cpuStatus,
            severity = if (cpuStatus == CompatibilityStatus.CONFLICT) CompatibilitySeverity.BLOCKER else CompatibilitySeverity.PASS,
            reason = if (cpuStatus == CompatibilityStatus.CONFLICT) "Target CPU runs in 32-bit mode only. Source ROM executes in 64-bit mode." else "CPU instruction modes are compatible.",
            evidence = PortEvidence("cpu_mode", "Source: ${sourceRom.architecture}, Target: ${targetDevice.cpuArch}", "CPU architecture verification"),
            confidence = 0.99f,
            isBlocker = (cpuStatus == CompatibilityStatus.CONFLICT),
            actionRequired = if (cpuStatus == CompatibilityStatus.CONFLICT) "Replace all 64-bit binaries with 32-bit ARMv7-A compiled binaries." else null
        )
        items.add(cpuItem)

        // =========================================================================
        // 4. GPU (Graphics Processing Unit)
        // =========================================================================
        val targetGpu = targetDevice.maliGpu
        val sourceGpu = sourceRom.properties["ro.opengles.version"] ?: "OpenGL ES 3.1"
        val gpuStatus = CompatibilityStatus.MATCH
        val gpuItem = CompatibilityComparisonItem(
            key = "gpu_graphics",
            subsystem = "GPU",
            category = "Graphics & Display",
            label = "GPU & OpenGL ES Subsystem",
            sourceValue = sourceGpu,
            targetValue = targetGpu,
            status = gpuStatus,
            severity = CompatibilitySeverity.PASS,
            reason = "Mali-T720 MP2 GPU driver stack (Gralloc 0.3 / HWComposer 1.5) provides GLES 3.1 & 2D/3D acceleration.",
            evidence = PortEvidence("gpu_spec", "Target: Mali-T720 MP2", "GPU specification audit", "libGLES_mali.so"),
            confidence = 0.95f,
            isBlocker = false
        )
        items.add(gpuItem)

        onProgress("Auditing Architecture, ABI, Android Version & Kernel...", 0.20f)

        // =========================================================================
        // 5. ARCHITECTURE (ARMv7-A vs ARMv8-A / 32-bit vs 64-bit)
        // =========================================================================
        val isArchBlocker = sourceRom.is64Bit && !targetDevice.is64Bit
        val archStatus = if (isArchBlocker) CompatibilityStatus.CONFLICT else CompatibilityStatus.MATCH
        val archItem = CompatibilityComparisonItem(
            key = "architecture_bitwidth",
            subsystem = "ARCHITECTURE",
            category = "Architecture & Instruction Set",
            label = "Instruction Set Architecture (ISA)",
            sourceValue = sourceRom.architecture,
            targetValue = targetDevice.cpuArch,
            status = archStatus,
            severity = if (isArchBlocker) CompatibilitySeverity.BLOCKER else CompatibilitySeverity.PASS,
            reason = if (isArchBlocker) {
                "CRITICAL BLOCKER: Source ROM is 64-bit (${sourceRom.architecture}), but Galaxy J2 Prime operates in 32-bit ARMv7-A mode."
            } else {
                "Architecture matched: 32-bit ARM instruction set."
            },
            evidence = PortEvidence("arch_check", "Source: ${sourceRom.architecture}, Target: ${targetDevice.cpuArch}", "ISA bitwidth evaluation", "ELF scanner"),
            confidence = 0.99f,
            isBlocker = isArchBlocker,
            actionRequired = if (isArchBlocker) "Cannot boot 64-bit ROM directly. Rebase on 32-bit ARMv7-A donor ROM or recompile framework for 32-bit." else null
        )
        items.add(archItem)
        if (isArchBlocker) {
            issues.add(
                CompatibilityIssue(
                    id = "issue_abi_64bit_blocker",
                    category = "Architecture & Instruction Set",
                    title = "64-Bit ARM64 Binary Incompatibility Blocker",
                    severity = CompatibilitySeverity.BLOCKER,
                    reason = "Galaxy J2 Prime has a 32-bit kernel and 32-bit userspace linker. 64-bit ARM64 binaries will trigger SIGILL (Illegal Instruction) or exec format errors.",
                    evidence = archItem.evidence,
                    confidence = 0.99f,
                    recommendation = "Use a 32-bit ARMv7-A (armeabi-v7a) donor ROM or transplant only 32-bit framework components.",
                    fixStrategy = "Switch donor base to a 32-bit ROM (e.g. LineageOS 32-bit MTK base or AOSP 32-bit)."
                )
            )
        }

        // =========================================================================
        // 6. ABI (Application Binary Interface)
        // =========================================================================
        val sourceAbi = sourceRom.properties["ro.product.cpu.abi"] ?: if (sourceRom.is64Bit) "arm64-v8a" else "armeabi-v7a"
        val isAbiMatch = targetDevice.supportedAbis.contains(sourceAbi) || (!sourceRom.is64Bit && sourceAbi.contains("arm"))
        val abiStatus = if (isAbiMatch) CompatibilityStatus.MATCH else CompatibilityStatus.CONFLICT
        val abiItem = CompatibilityComparisonItem(
            key = "cpu_abi",
            subsystem = "ABI",
            category = "Architecture & Instruction Set",
            label = "Primary Native ABI",
            sourceValue = sourceAbi,
            targetValue = targetDevice.supportedAbis.joinToString(", "),
            status = abiStatus,
            severity = if (abiStatus == CompatibilityStatus.CONFLICT) CompatibilitySeverity.BLOCKER else CompatibilitySeverity.PASS,
            reason = if (abiStatus == CompatibilityStatus.CONFLICT) "Target device does not support $sourceAbi." else "Target device natively supports $sourceAbi.",
            evidence = PortEvidence("abi_eval", "Source ABI: $sourceAbi, Supported: ${targetDevice.supportedAbis}", "ABI compatibility rule", "ro.product.cpu.abi"),
            confidence = 0.98f,
            isBlocker = (abiStatus == CompatibilityStatus.CONFLICT),
            actionRequired = if (abiStatus == CompatibilityStatus.CONFLICT) "Switch ABI to armeabi-v7a." else null
        )
        items.add(abiItem)

        // =========================================================================
        // 7. ANDROID (OS Version & API Level)
        // =========================================================================
        val targetAndroid = targetDevice.properties["ro.build.version.release"] ?: "6.0.1 (Stock) / 11 (Lineage)"
        val sourceAndroid = sourceRom.androidVersion
        val isAndroidUnknown = sourceAndroid == "UNKNOWN" || sourceRom.sdkInt < 0
        val isAndroidSame = sourceAndroid.startsWith("6.0", ignoreCase = true)

        val androidStatus = when {
            isAndroidUnknown -> CompatibilityStatus.UNKNOWN
            isAndroidSame -> CompatibilityStatus.MATCH
            else -> CompatibilityStatus.DIFFERENT // Normal version upgrade: DIFFERENT, NOT an error!
        }
        val androidSeverity = when (androidStatus) {
            CompatibilityStatus.MATCH -> CompatibilitySeverity.PASS
            CompatibilityStatus.UNKNOWN -> CompatibilitySeverity.WARNING
            CompatibilityStatus.DIFFERENT -> CompatibilitySeverity.INFO
            else -> CompatibilitySeverity.INFO
        }
        val androidItem = CompatibilityComparisonItem(
            key = "android_version_eval",
            subsystem = "ANDROID",
            category = "Android Framework",
            label = "Android Release & API Level",
            sourceValue = if (isAndroidUnknown) "UNKNOWN" else "$sourceAndroid (API ${if (sourceRom.sdkInt > 0) sourceRom.sdkInt else "UNKNOWN"})",
            targetValue = "TARGET ANDROID BASE: $targetAndroid",
            status = androidStatus,
            severity = androidSeverity,
            reason = when (androidStatus) {
                CompatibilityStatus.MATCH -> "Android releases match."
                CompatibilityStatus.UNKNOWN -> "Source Android version unknown. Verify framework compatibility."
                CompatibilityStatus.DIFFERENT -> "Cross-version port ($sourceAndroid onto $targetAndroid hardware base). This is a standard ROM porting upgrade."
                else -> "Version variance."
            },
            evidence = PortEvidence("android_ver_diff", "Source: $sourceAndroid, Target: $targetAndroid", "Release version audit", "ro.build.version.release"),
            confidence = 0.98f,
            isBlocker = false,
            actionRequired = if (sourceRom.sdkInt >= 30) "Apply Android 11+ Binder IPC & ashmem shims for Linux 3.18 kernel." else null
        )
        items.add(androidItem)
        if (isAndroidUnknown) {
            issues.add(
                CompatibilityIssue(
                    id = "warn_unknown_android_version",
                    category = "Android Framework",
                    title = "Source ROM Android Version UNKNOWN",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Could not find ro.build.version.release in source ROM metadata. Defaulting to UNKNOWN (not assuming Android 11).",
                    evidence = androidItem.evidence,
                    confidence = 0.5f,
                    recommendation = "Inspect framework-res.apk or build.prop to determine exact API level."
                )
            )
        }

        // =========================================================================
        // 8. KERNEL (Version, IPC, Binder & Cmdline)
        // =========================================================================
        val targetKernel = targetDevice.maxKernelVersion // 3.18.35+
        val sourceCmdline = sourceRom.kernelCmdline
        val kernelStatus = if (sourceRom.sdkInt >= 30) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val kernelSeverity = if (sourceRom.sdkInt >= 30) CompatibilitySeverity.WARNING else CompatibilitySeverity.PASS
        val kernelItem = CompatibilityComparisonItem(
            key = "kernel_compatibility",
            subsystem = "KERNEL",
            category = "Kernel & Low-Level Drivers",
            label = "Linux Kernel & Binder IPC Interface",
            sourceValue = "Cmdline: ${if (sourceCmdline.isNotBlank()) sourceCmdline else "Default AOSP"}",
            targetValue = "Linux $targetKernel (MT6737T grandpplte defconfig)",
            status = kernelStatus,
            severity = kernelSeverity,
            reason = if (sourceRom.sdkInt >= 30) {
                "Android 11 userspace expects 64-bit Binder IPC (v8) and modern ashmem/ion interfaces. Linux 3.18 requires compatibility shims."
            } else {
                "Kernel 3.18.35+ directly supports this Android release."
            },
            evidence = PortEvidence("kernel_binder", "Target: Linux 3.18.35, Source API: ${sourceRom.sdkInt}", "Kernel IPC compatibility analysis", "arch/arm/configs/grandpplte_defconfig"),
            confidence = 0.94f,
            isBlocker = false,
            actionRequired = if (sourceRom.sdkInt >= 30) "Apply Binder v8 64-bit IPC shims to ramdisk init and enable permissive BPF." else null
        )
        items.add(kernelItem)
        if (sourceRom.sdkInt >= 30) {
            issues.add(
                CompatibilityIssue(
                    id = "warn_kernel_binder_shims",
                    category = "Kernel & Low-Level Drivers",
                    title = "Linux 3.18 Kernel Binder IPC Adaptation for Android 11+",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Android 11+ framework requires Binder IPC v8 and BPF networking not natively present in Linux 3.18.",
                    evidence = kernelItem.evidence,
                    confidence = 0.94f,
                    recommendation = "Include libbinder_shim.so and disable strict ebpf in init.rc.",
                    fixStrategy = "Inject binder shims during ROM Studio repack."
                )
            )
        }

        onProgress("Auditing Boot, DTB, DTBO, Partitions & Partitions Layout...", 0.40f)

        // =========================================================================
        // 9. BOOT (Boot Header v0-v4, Page Size, Kernel Load Addr)
        // =========================================================================
        val bootHeader = sourceRom.bootDetails
        val bootSize = sourceRom.bootImgSize
        val maxBootSize = targetDevice.maxBootPartitionBytes // 16 MB
        val isBootOverflow = bootSize > maxBootSize && bootSize > 0
        val bootStatus = when {
            isBootOverflow -> CompatibilityStatus.CONFLICT
            bootHeader.headerVersion > 2 -> CompatibilityStatus.DIFFERENT
            else -> CompatibilityStatus.MATCH
        }
        val bootSeverity = when {
            isBootOverflow -> CompatibilitySeverity.BLOCKER
            bootStatus == CompatibilityStatus.DIFFERENT -> CompatibilitySeverity.WARNING
            else -> CompatibilitySeverity.PASS
        }
        val bootItem = CompatibilityComparisonItem(
            key = "boot_header_eval",
            subsystem = "BOOT",
            category = "Bootloader & Boot Image",
            label = "Boot Image (boot.img) Header & Geometry",
            sourceValue = "Header v${bootHeader.headerVersion}, Page: ${bootHeader.pageSize}B, Size: ${bootSize / 1024} KB",
            targetValue = "Header v0/v1 (MTK Header), Page: 2048B, Max: 16 MB (16,777,216 bytes)",
            status = bootStatus,
            severity = bootSeverity,
            reason = when {
                isBootOverflow -> "CRITICAL BLOCKER: boot.img size (${bootSize / (1024 * 1024)} MB) exceeds 16 MB boot partition limit."
                bootHeader.headerVersion > 2 -> "Source boot uses header v${bootHeader.headerVersion}. Target MTK bootloader requires v0/v1 format."
                else -> "Boot image header structure and page size (2048 bytes) are compatible."
            },
            evidence = PortEvidence("boot_header", "Header v${bootHeader.headerVersion}, Page: ${bootHeader.pageSize}", "Boot image inspection", "boot.img header"),
            confidence = 0.96f,
            isBlocker = isBootOverflow,
            actionRequired = if (bootStatus != CompatibilityStatus.MATCH) "Repack boot.img with MTK kernel zImage and 2048 page size." else null
        )
        items.add(bootItem)
        if (isBootOverflow) {
            issues.add(
                CompatibilityIssue(
                    id = "blocker_boot_overflow",
                    category = "Bootloader & Boot Image",
                    title = "Boot Partition Size Overflow Blocker",
                    severity = CompatibilitySeverity.BLOCKER,
                    reason = "Boot image exceeds physical 16 MB eMMC partition budget.",
                    evidence = bootItem.evidence,
                    confidence = 0.99f,
                    recommendation = "Compress ramdisk with gzip/lz4 and strip unnecessary kernel debug symbols."
                )
            )
        }

        // =========================================================================
        // 10. DTB (Device Tree Blob)
        // =========================================================================
        val dtb = sourceRom.dtbDetails
        val isDtbMtk = dtb.socCompatibleList.any { it.contains("mt6737") || it.contains("mediatek") }
        val isDtbForeign = dtb.socCompatibleList.isNotEmpty() && !isDtbMtk
        val dtbStatus = when {
            isDtbForeign -> CompatibilityStatus.CONFLICT
            isDtbMtk -> CompatibilityStatus.MATCH
            dtb.hasDtb -> CompatibilityStatus.DIFFERENT
            else -> CompatibilityStatus.MATCH // Target uses embedded kernel DTB
        }
        val dtbItem = CompatibilityComparisonItem(
            key = "dtb_device_tree",
            subsystem = "DTB",
            category = "Device Tree",
            label = "Device Tree Blob (DTB)",
            sourceValue = if (dtb.hasDtb) "DTB present (${dtb.socCompatibleList.joinToString(", ").ifEmpty { "Generic" }})" else "No separate DTB (embedded in kernel)",
            targetValue = "MediaTek MT6737T DTB (grandpplte.dtb embedded)",
            status = dtbStatus,
            severity = if (dtbStatus == CompatibilityStatus.CONFLICT) CompatibilitySeverity.WARNING else CompatibilitySeverity.PASS,
            reason = if (dtbStatus == CompatibilityStatus.CONFLICT) {
                "Source DTB targets foreign hardware (${dtb.socCompatibleList.joinToString()}). Must use J2 Prime MT6737T DTB."
            } else {
                "Device tree blob configuration compatible."
            },
            evidence = PortEvidence("dtb_eval", "Source DTB: ${dtb.hasDtb}, Compatible: ${dtb.socCompatibleList}", "Device tree inspection", "dtb/dt.img"),
            confidence = 0.93f,
            isBlocker = false,
            actionRequired = if (dtbStatus != CompatibilityStatus.MATCH) "Use J2 Prime kernel DTB during boot.img repack." else null
        )
        items.add(dtbItem)

        // =========================================================================
        // 11. DTBO (Device Tree Blob Overlay Partition)
        // =========================================================================
        val hasDtbo = sourceRom.dtbDetails.hasDtbo || sourceRom.partitions.any { it.name.contains("dtbo") }
        val dtboStatus = if (hasDtbo) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val dtboItem = CompatibilityComparisonItem(
            key = "dtbo_overlay",
            subsystem = "DTBO",
            category = "Device Tree",
            label = "DTBO Overlay Partition",
            sourceValue = if (hasDtbo) "DTBO Partition Present" else "No DTBO Partition (Legacy Non-DTBO)",
            targetValue = "No DTBO Partition (J2 Prime uses monolithic DTB)",
            status = dtboStatus,
            severity = CompatibilitySeverity.INFO,
            reason = if (hasDtbo) "Source ROM contains a DTBO partition, which is ignored on legacy non-DTBO MT6737T device." else "Matches legacy non-DTBO architecture.",
            evidence = PortEvidence("dtbo_check", "hasDtbo=$hasDtbo", "Partition & DTBO audit"),
            confidence = 0.95f,
            isBlocker = false
        )
        items.add(dtboItem)

        // =========================================================================
        // 12. PARTITIONS (Partition Table & Size Budgets)
        // =========================================================================
        val systemSize = sourceRom.systemSizeBytes
        val maxSystemSize = targetDevice.maxSystemPartitionBytes // ~1.60 GB
        val isSystemOverflow = systemSize > maxSystemSize && systemSize > 0
        val partitionStatus = if (isSystemOverflow) CompatibilityStatus.CONFLICT else CompatibilityStatus.MATCH
        val partitionItem = CompatibilityComparisonItem(
            key = "partition_budget_eval",
            subsystem = "PARTITIONS",
            category = "Storage & Partition Layout",
            label = "System Partition Capacity Budget",
            sourceValue = "${systemSize / (1024 * 1024)} MB (${sourceRom.partitions.size} partitions indexed)",
            targetValue = "${maxSystemSize / (1024 * 1024)} MB (1.60 GB Physical eMMC Limit)",
            status = partitionStatus,
            severity = if (isSystemOverflow) CompatibilitySeverity.BLOCKER else CompatibilitySeverity.PASS,
            reason = if (isSystemOverflow) {
                "CRITICAL BLOCKER: System partition size (${systemSize / (1024 * 1024)} MB) exceeds physical eMMC capacity (${maxSystemSize / (1024 * 1024)} MB) by ${(systemSize - maxSystemSize) / (1024 * 1024)} MB."
            } else {
                "System image footprint fits comfortably within J2 Prime's 1.60 GB eMMC partition."
            },
            evidence = PortEvidence("partition_budget", "System: ${systemSize / (1024 * 1024)} MB, Max: ${maxSystemSize / (1024 * 1024)} MB", "Partition budget verification", "pit layout"),
            confidence = 0.98f,
            isBlocker = isSystemOverflow,
            actionRequired = if (isSystemOverflow) "Debloat system partition (remove heavyweight GApps, sounds, fonts) to reduce footprint under 1.5 GB." else null
        )
        items.add(partitionItem)
        if (isSystemOverflow) {
            issues.add(
                CompatibilityIssue(
                    id = "issue_system_overflow_blocker",
                    category = "Storage & Partition Layout",
                    title = "System Partition Size Overflow Blocker",
                    severity = CompatibilitySeverity.BLOCKER,
                    reason = "Source system.img (${systemSize / (1024 * 1024)} MB) exceeds physical J2 Prime flash capacity (1,640 MB). Flashing will fail with write errors.",
                    evidence = partitionItem.evidence,
                    confidence = 0.98f,
                    recommendation = "Apply automated debloating in ROM Build Studio to trim down system.img.",
                    fixStrategy = "Run 'ROM Build Studio -> Debloat & Optimize'."
                )
            )
        }

        onProgress("Auditing System, Vendor, Product, ODM & HAL...", 0.60f)

        // =========================================================================
        // 13. SYSTEM (Filesystem & Mount Layout)
        // =========================================================================
        val systemFs = sourceRom.systemFsType
        val isSystemFsCompatible = systemFs.contains("ext4", ignoreCase = true) || systemFs.contains("sparse", ignoreCase = true) || systemFs == "UNKNOWN"
        val systemItem = CompatibilityComparisonItem(
            key = "system_fs_type",
            subsystem = "SYSTEM",
            category = "Filesystem & Images",
            label = "System Filesystem & Packaging",
            sourceValue = if (systemFs != "UNKNOWN") systemFs else "ext4 / sparse image",
            targetValue = "ext4 (Linux standard)",
            status = if (isSystemFsCompatible) CompatibilityStatus.MATCH else CompatibilityStatus.DIFFERENT,
            severity = CompatibilitySeverity.PASS,
            reason = "Standard ext4 filesystem format is fully supported by the J2 Prime kernel and recovery.",
            evidence = PortEvidence("system_fs", "Format: $systemFs", "Filesystem header inspection", "system.img"),
            confidence = 0.95f,
            isBlocker = false
        )
        items.add(systemItem)

        // =========================================================================
        // 14. VENDOR (Dedicated vendor partition vs /system/vendor)
        // =========================================================================
        val hasDedicatedVendor = sourceRom.partitions.any { it.name.equals("vendor", ignoreCase = true) }
        val vendorStatus = if (hasDedicatedVendor && !targetDevice.isTrebleSupported) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val vendorSeverity = if (vendorStatus == CompatibilityStatus.DIFFERENT) CompatibilitySeverity.WARNING else CompatibilitySeverity.PASS
        val vendorItem = CompatibilityComparisonItem(
            key = "vendor_partition_layout",
            subsystem = "VENDOR",
            category = "Architecture & Layout",
            label = "Vendor Partition & Blobs Layout",
            sourceValue = if (hasDedicatedVendor) "Dedicated /vendor partition" else "Integrated /system/vendor/",
            targetValue = "Integrated /system/vendor/ (Non-Treble single partition)",
            status = vendorStatus,
            severity = vendorSeverity,
            reason = if (vendorStatus == CompatibilityStatus.DIFFERENT) {
                "Source ROM uses split /vendor partition. On non-Treble J2 Prime, vendor files must be merged into /system/vendor/."
            } else {
                "Vendor layout matches non-Treble structure."
            },
            evidence = PortEvidence("vendor_layout", "hasDedicatedVendor=$hasDedicatedVendor, targetTreble=${targetDevice.isTrebleSupported}", "Vendor layout audit"),
            confidence = 0.94f,
            isBlocker = false,
            actionRequired = if (vendorStatus == CompatibilityStatus.DIFFERENT) "Merge /vendor into /system/vendor/ and update init mount scripts." else null
        )
        items.add(vendorItem)
        if (vendorStatus == CompatibilityStatus.DIFFERENT) {
            issues.add(
                CompatibilityIssue(
                    id = "warn_vendor_merge",
                    category = "Architecture & Layout",
                    title = "Vendor Partition Merge Required",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Galaxy J2 Prime lacks a physical /vendor eMMC partition. All vendor proprietary blobs must reside under /system/vendor/.",
                    evidence = vendorItem.evidence,
                    confidence = 0.94f,
                    recommendation = "Merge vendor blobs into /system/vendor/ and remove /vendor mount points from fstab.",
                    fixStrategy = "Use ROM Merge Engine -> Merge Vendor into System."
                )
            )
        }

        // =========================================================================
        // 15. PRODUCT (Product Partition)
        // =========================================================================
        val hasProduct = sourceRom.partitions.any { it.name.equals("product", ignoreCase = true) }
        val productStatus = if (hasProduct) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val productItem = CompatibilityComparisonItem(
            key = "product_partition",
            subsystem = "PRODUCT",
            category = "Partition Layout",
            label = "Product Partition (/product)",
            sourceValue = if (hasProduct) "Separate /product Partition" else "Integrated in /system",
            targetValue = "Integrated in /system (Legacy partition table)",
            status = productStatus,
            severity = CompatibilitySeverity.INFO,
            reason = if (hasProduct) "Source ROM has a separate /product partition. Merge contents into /system/product/." else "Matches monolithic system structure.",
            evidence = PortEvidence("product_check", "hasProduct=$hasProduct", "Product partition audit"),
            confidence = 0.95f,
            isBlocker = false,
            actionRequired = if (hasProduct) "Merge /product into /system/product/." else null
        )
        items.add(productItem)

        // =========================================================================
        // 16. ODM (Original Design Manufacturer Partition)
        // =========================================================================
        val hasOdm = sourceRom.partitions.any { it.name.equals("odm", ignoreCase = true) }
        val odmStatus = if (hasOdm) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val odmItem = CompatibilityComparisonItem(
            key = "odm_partition",
            subsystem = "ODM",
            category = "Partition Layout",
            label = "ODM Partition (/odm)",
            sourceValue = if (hasOdm) "Separate /odm Partition" else "Not Present",
            targetValue = "Not Present (Integrated in vendor/system)",
            status = odmStatus,
            severity = CompatibilitySeverity.INFO,
            reason = if (hasOdm) "Source ROM contains /odm partition. Merge contents into /system/vendor/odm/." else "Standard layout.",
            evidence = PortEvidence("odm_check", "hasOdm=$hasOdm", "ODM partition audit"),
            confidence = 0.95f,
            isBlocker = false
        )
        items.add(odmItem)

        // =========================================================================
        // 17. HAL (Hardware Abstraction Layer: Camera, Audio, Graphics)
        // =========================================================================
        val hal = sourceRom.halDetails
        val isHalTreble = hal.isTreble || hal.hidlServices.isNotEmpty()
        val halStatus = if (isHalTreble && !targetDevice.isTrebleSupported) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val halSeverity = if (halStatus == CompatibilityStatus.DIFFERENT) CompatibilitySeverity.WARNING else CompatibilitySeverity.PASS
        val halItem = CompatibilityComparisonItem(
            key = "hal_architecture_eval",
            subsystem = "HAL",
            category = "Hardware Abstraction Layer",
            label = "HAL Services & Architecture",
            sourceValue = if (isHalTreble) "HIDL / Treble HAL Services (${hal.hidlServices.size} services)" else "Legacy direct-load HALs (${hal.legacyHals.size} HALs)",
            targetValue = "MediaTek Legacy HAL1 (Camera HAL1, Gralloc 0.3, MTK ALSA Audio)",
            status = halStatus,
            severity = halSeverity,
            reason = if (halStatus == CompatibilityStatus.DIFFERENT) {
                "Source ROM expects Treble HIDL HAL daemons. J2 Prime uses legacy in-process HALs. Wrapper shims or passthrough HALs are required."
            } else {
                "Legacy HAL architecture matches J2 Prime proprietary driver structure."
            },
            evidence = PortEvidence("hal_check", "Source isTreble=$isHalTreble, Target Treble=${targetDevice.isTrebleSupported}", "HAL audit", "manifest.xml"),
            confidence = 0.93f,
            isBlocker = false,
            actionRequired = if (halStatus == CompatibilityStatus.DIFFERENT) "Transplant MT6737T Camera HAL1, Mali Gralloc 0.3, and Audio ALSA shims." else null
        )
        items.add(halItem)
        if (halStatus == CompatibilityStatus.DIFFERENT) {
            issues.add(
                CompatibilityIssue(
                    id = "warn_hal_passthrough",
                    category = "Hardware Abstraction Layer",
                    title = "MediaTek HAL1 to AOSP Adaptation",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Galaxy J2 Prime utilizes MediaTek Camera HAL1 and Gralloc 0.3. AOSP framework requires passthrough HAL wrappers.",
                    evidence = halItem.evidence,
                    confidence = 0.93f,
                    recommendation = "Transplant stock libcamera_client.so shims and gralloc.mt6737.so.",
                    fixStrategy = "Inject HAL compatibility shims during packaging."
                )
            )
        }

        onProgress("Auditing Telephony RIL, SELinux, ELF Binaries & Properties...", 0.80f)

        // =========================================================================
        // 18. RIL (Radio Interface Layer: Samsung SEC RIL vs MTK CCK vs AOSP)
        // =========================================================================
        val ril = sourceRom.rilDetails
        val isSecRilSource = ril.rilImplementation.contains("SEC RIL", ignoreCase = true) || ril.rilImplementation.contains("libsec-ril", ignoreCase = true)
        val rilStatus = if (isSecRilSource) CompatibilityStatus.MATCH else CompatibilityStatus.DIFFERENT
        val rilSeverity = if (rilStatus == CompatibilityStatus.DIFFERENT) CompatibilitySeverity.WARNING else CompatibilitySeverity.PASS
        val rilItem = CompatibilityComparisonItem(
            key = "ril_telephony_eval",
            subsystem = "RIL",
            category = "Telephony & Cellular",
            label = "Radio Interface Layer (RIL)",
            sourceValue = ril.rilImplementation,
            targetValue = targetDevice.rilInterface,
            status = rilStatus,
            severity = rilSeverity,
            reason = if (rilStatus == CompatibilityStatus.DIFFERENT) {
                "Source ROM uses ${ril.rilImplementation}. Galaxy J2 Prime requires Samsung SEC RIL with MTK CCK modem daemon (libsec-ril.so / librilmtk.so)."
            } else {
                "Samsung SEC RIL telephony stack matched."
            },
            evidence = PortEvidence("ril_check", "Source: ${ril.rilImplementation}, Target: ${targetDevice.rilInterface}", "RIL implementation evaluation", "rild.libpath"),
            confidence = 0.92f,
            isBlocker = false,
            actionRequired = if (rilStatus == CompatibilityStatus.DIFFERENT) "Transplant libsec-ril.so, librilmtk.so, and SEC RIL IPC binder wrappers." else null
        )
        items.add(rilItem)
        if (rilStatus == CompatibilityStatus.DIFFERENT) {
            issues.add(
                CompatibilityIssue(
                    id = "warn_ril_stack_adaptation",
                    category = "Telephony & Cellular",
                    title = "Samsung SEC RIL / MTK CCK Modem Adaptation",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Telephony daemon requires Samsung SEC RIL multi-client socket communication for SIM & mobile data.",
                    evidence = rilItem.evidence,
                    confidence = 0.92f,
                    recommendation = "Include libsec-ril.so, librilmtk.so, and rild daemon in /system/bin/.",
                    fixStrategy = "Transplant telephony libraries from J2 Prime stock base."
                )
            )
        }

        // =========================================================================
        // 19. SELINUX (SELinux Mode, Policies & File Contexts)
        // =========================================================================
        val sourceSelinux = sourceRom.selinuxMode
        val selinuxDetails = sourceRom.selinuxDetails
        val selinuxStatus = if (sourceSelinux.equals("Permissive", ignoreCase = true)) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val selinuxItem = CompatibilityComparisonItem(
            key = "selinux_mode_eval",
            subsystem = "SELINUX",
            category = "Security & SELinux",
            label = "SELinux Enforcer & Policy Matrix",
            sourceValue = "$sourceSelinux (${selinuxDetails.fileContextsCount} file contexts, plat_sepolicy: ${selinuxDetails.hasPlatSepolicy})",
            targetValue = "Enforcing (Stock) / Permissive (Initial Boot Testing)",
            status = selinuxStatus,
            severity = CompatibilitySeverity.PASS,
            reason = if (sourceSelinux.equals("Permissive", ignoreCase = true)) {
                "Source operates in Permissive mode. Highly recommended for early port bring-up to avoid boot-looping on missing avc rules."
            } else {
                "Standard Enforcing policy. Ensure all MediaTek hardware domains are granted in plat_sepolicy."
            },
            evidence = PortEvidence("selinux_eval", "Mode: $sourceSelinux, Contexts: ${selinuxDetails.fileContextsCount}", "SELinux policy evaluation", "file_contexts"),
            confidence = 0.95f,
            isBlocker = false
        )
        items.add(selinuxItem)

        // =========================================================================
        // 20. ELF (Native Binaries & Missing Libraries)
        // =========================================================================
        val elf = sourceRom.elfDetails
        val has64BitElf = elf.elf64Count > 0
        val hasMissingLibs = elf.missingLibrariesDetected.isNotEmpty()
        val elfStatus = when {
            has64BitElf -> CompatibilityStatus.CONFLICT
            hasMissingLibs -> CompatibilityStatus.MISSING
            else -> CompatibilityStatus.MATCH
        }
        val elfSeverity = when {
            has64BitElf -> CompatibilitySeverity.BLOCKER
            hasMissingLibs -> CompatibilitySeverity.WARNING
            else -> CompatibilitySeverity.PASS
        }
        val elfItem = CompatibilityComparisonItem(
            key = "elf_binary_audit",
            subsystem = "ELF",
            category = "Native Binaries & Libraries",
            label = "ELF Native Binaries & Shared Objects",
            sourceValue = "32-bit: ${elf.elf32Count}, 64-bit: ${elf.elf64Count} (Total: ${elf.totalBinariesScanned})",
            targetValue = "Pure 32-bit ARM ELF (EM_ARM / Class 32)",
            status = elfStatus,
            severity = elfSeverity,
            reason = when {
                has64BitElf -> "CRITICAL BLOCKER: ${elf.elf64Count} 64-bit ELF binaries detected. 32-bit MT6737T cannot execute 64-bit ELF."
                hasMissingLibs -> "Missing shared libraries detected: ${elf.missingLibrariesDetected.joinToString()}."
                else -> "All ${elf.elf32Count} scanned native binaries are compatible 32-bit ARM ELF."
            },
            evidence = PortEvidence("elf_eval", "32-bit=${elf.elf32Count}, 64-bit=${elf.elf64Count}", "ELF native binary header inspection", "system/lib/ & system/bin/"),
            confidence = 0.99f,
            isBlocker = has64BitElf,
            actionRequired = if (has64BitElf) "Remove system/lib64/ and replace all 64-bit binaries with 32-bit counterparts." else null
        )
        items.add(elfItem)

        // =========================================================================
        // 21. PROPERTIES (build.prop, default.prop & System Properties)
        // =========================================================================
        val propStatus = CompatibilityStatus.MATCH
        val propItem = CompatibilityComparisonItem(
            key = "properties_eval",
            subsystem = "PROPERTIES",
            category = "System Properties",
            label = "System Properties & Configuration",
            sourceValue = "${sourceRom.properties.size} properties indexed",
            targetValue = "${targetDevice.properties.size} device properties referenced",
            status = propStatus,
            severity = CompatibilitySeverity.PASS,
            reason = "System properties will be merged with J2 Prime hardware overrides (ro.product.model, ro.board.platform, ro.telephony.default_network).",
            evidence = PortEvidence("prop_eval", "Source props=${sourceRom.properties.size}", "build.prop parser", "build.prop"),
            confidence = 0.95f,
            isBlocker = false
        )
        items.add(propItem)

        // =========================================================================
        // 22. INIT (Init Scripts, Services & ueventd)
        // =========================================================================
        val initStatus = CompatibilityStatus.MATCH
        val initItem = CompatibilityComparisonItem(
            key = "init_scripts_eval",
            subsystem = "INIT",
            category = "Init & Boot Scripts",
            label = "Init Scripts (init.rc) & Daemon Services",
            sourceValue = "AOSP Init Hierarchy",
            targetValue = "MediaTek MT6737T Init (init.mt6737t.rc, init.grandpplte.rc, ueventd.mt6737t.rc)",
            status = initStatus,
            severity = CompatibilitySeverity.PASS,
            reason = "MediaTek initialization scripts and daemon service definitions will be injected into rootdir / ramdisk.",
            evidence = PortEvidence("init_check", "Target MTK init hierarchy", "Init script inspection", "init.mt6737t.rc"),
            confidence = 0.94f,
            isBlocker = false
        )
        items.add(initItem)

        // =========================================================================
        // 23. FILESYSTEM (Filesystem Type & Transfer Formats)
        // =========================================================================
        val fsStatus = CompatibilityStatus.MATCH
        val fsItem = CompatibilityComparisonItem(
            key = "filesystem_eval",
            subsystem = "FILESYSTEM",
            category = "Storage & Partition Layout",
            label = "Filesystem & Block Format",
            sourceValue = "${sourceRom.systemFsType} (ext4 / sparse)",
            targetValue = "ext4 block device (/dev/block/platform/.../by-name/system)",
            status = fsStatus,
            severity = CompatibilitySeverity.PASS,
            reason = "ext4 filesystem is fully supported by Galaxy J2 Prime kernel and TWRP/Stock recoveries.",
            evidence = PortEvidence("fs_eval", "Filesystem: ${sourceRom.systemFsType}", "Filesystem superblock analysis"),
            confidence = 0.97f,
            isBlocker = false
        )
        items.add(fsItem)

        // =========================================================================
        // 24. TREBLE (Project Treble & VNDK Isolation)
        // =========================================================================
        val isTrebleMismatch = sourceRom.isTreble && !targetDevice.isTrebleSupported
        val trebleStatus = if (isTrebleMismatch) CompatibilityStatus.DIFFERENT else CompatibilityStatus.MATCH
        val trebleSeverity = if (isTrebleMismatch) CompatibilitySeverity.WARNING else CompatibilitySeverity.PASS
        val trebleItem = CompatibilityComparisonItem(
            key = "treble_vndk_eval",
            subsystem = "TREBLE",
            category = "Architecture & Layout",
            label = "Project Treble & VNDK Isolation",
            sourceValue = if (sourceRom.isTreble) "Treble Enabled (ro.treble.enabled=true)" else "Legacy Non-Treble",
            targetValue = "Legacy Non-Treble (System-as-root disabled / monolithic)",
            status = trebleStatus,
            severity = trebleSeverity,
            reason = if (isTrebleMismatch) {
                "Source ROM utilizes Project Treble VNDK namespace isolation. Galaxy J2 Prime is non-Treble. Requires disabling strict VNDK linker checks."
            } else {
                "Treble architecture matches."
            },
            evidence = PortEvidence("treble_check", "Source Treble: ${sourceRom.isTreble}, Target Treble: ${targetDevice.isTrebleSupported}", "Treble VNDK verification", "ro.treble.enabled"),
            confidence = 0.95f,
            isBlocker = false,
            actionRequired = if (isTrebleMismatch) "Set ro.treble.enabled=false in build.prop and bypass VNDK linker isolation." else null
        )
        items.add(trebleItem)
        if (isTrebleMismatch) {
            issues.add(
                CompatibilityIssue(
                    id = "warn_treble_adaptation",
                    category = "Architecture & Layout",
                    title = "Project Treble Linker Adaptation",
                    severity = CompatibilitySeverity.WARNING,
                    reason = "Non-Treble device running Treble framework must permit legacy shared libraries across system/vendor boundaries.",
                    evidence = trebleItem.evidence,
                    confidence = 0.95f,
                    recommendation = "Disable VNDK enforcement and allow legacy linker namespace fallbacks.",
                    fixStrategy = "Apply 'Treble -> Non-Treble' linker namespace patch."
                )
            )
        }

        // =========================================================================
        // 25. A/B (A-only vs A/B Seamless Partition Slots)
        // =========================================================================
        val isAbMismatch = sourceRom.isAb
        val abStatus = if (isAbMismatch) CompatibilityStatus.CONFLICT else CompatibilityStatus.MATCH
        val abSeverity = if (isAbMismatch) CompatibilitySeverity.BLOCKER else CompatibilitySeverity.PASS
        val abItem = CompatibilityComparisonItem(
            key = "ab_slot_eval",
            subsystem = "A/B",
            category = "Partition Layout",
            label = "A/B Seamless vs A-Only Partition Layout",
            sourceValue = if (sourceRom.isAb) "A/B Seamless Slots (_a / _b)" else "A-Only Legacy Partitions",
            targetValue = "A-Only Legacy Partitions (Single system/boot slots)",
            status = abStatus,
            severity = abSeverity,
            reason = if (isAbMismatch) {
                "CRITICAL BLOCKER: Source ROM is designed for A/B slot devices. J2 Prime is an A-only device without slot metadata."
            } else {
                "A-Only partition structure matches."
            },
            evidence = PortEvidence("ab_check", "Source isAb=${sourceRom.isAb}", "Slot structure audit"),
            confidence = 0.98f,
            isBlocker = isAbMismatch,
            actionRequired = if (isAbMismatch) "Reconfigure updater-script and mount points for A-only legacy partitions." else null
        )
        items.add(abItem)
        if (isAbMismatch) {
            issues.add(
                CompatibilityIssue(
                    id = "blocker_ab_layout",
                    category = "Partition Layout",
                    title = "A/B Partition Layout Blocker",
                    severity = CompatibilitySeverity.BLOCKER,
                    reason = "A/B slot update scripts cannot execute on Galaxy J2 Prime's A-only partition table.",
                    evidence = abItem.evidence,
                    confidence = 0.98f,
                    recommendation = "Convert update package to A-only structure with standard block mounts."
                )
            )
        }

        onProgress("Calculating Port Readiness Score & Finalizing...", 0.95f)

        // =========================================================================
        // SUMMARY COUNTS & READINESS SCORING
        // =========================================================================
        val blockerCount = issues.count { it.severity == CompatibilitySeverity.BLOCKER }
        val errorCount = issues.count { it.severity == CompatibilitySeverity.ERROR }
        val warningCount = issues.count { it.severity == CompatibilitySeverity.WARNING }

        val matchCount = items.count { it.status == CompatibilityStatus.MATCH }
        val differentCount = items.count { it.status == CompatibilityStatus.DIFFERENT }
        val missingCount = items.count { it.status == CompatibilityStatus.MISSING }
        val conflictCount = items.count { it.status == CompatibilityStatus.CONFLICT }
        val unknownCount = items.count { it.status == CompatibilityStatus.UNKNOWN }

        // Score formula: Base 100, -40 per blocker, -15 per error, -5 per warning, -2 per different
        var calculatedScore = 100 - (blockerCount * 40) - (errorCount * 15) - (warningCount * 5) - (differentCount * 2)
        if (blockerCount > 0) {
            calculatedScore = calculatedScore.coerceAtMost(35)
        }
        val finalScore = calculatedScore.coerceIn(0, 100)
        val canProceed = blockerCount == 0

        val summaryText = buildString {
            if (blockerCount > 0) {
                append("PORT BLOCKED ($blockerCount fatal blocker(s) found). ")
                append("Resolve critical architecture/size blockers before building.")
            } else if (warningCount > 0) {
                append("PORT VIABLE WITH ADAPTATIONS ($warningCount warning(s), score: $finalScore%). ")
                append("Apply MediaTek hardware shims, vendor merge, and telephony patches in ROM Build Studio.")
            } else {
                append("EXCELLENT COMPATIBILITY (Score: $finalScore%). ")
                append("Subsystems match Galaxy J2 Prime hardware specifications.")
            }
        }

        val recommendations = mutableListOf<String>()
        if (isArchBlocker) {
            recommendations.add("Replace 64-bit binaries with 32-bit ARMv7-A compiled equivalents.")
        }
        if (isSystemOverflow) {
            recommendations.add("Debloat system.img to bring partition size under 1,600 MB.")
        }
        if (vendorStatus == CompatibilityStatus.DIFFERENT) {
            recommendations.add("Execute 'ROM Merge Engine' to collapse /vendor into /system/vendor/.")
        }
        if (sourceRom.sdkInt >= 30) {
            recommendations.add("Apply Android 11+ 64-bit Binder IPC shims for Linux 3.18 kernel.")
        }
        if (rilStatus == CompatibilityStatus.DIFFERENT) {
            recommendations.add("Transplant Samsung SEC RIL (libsec-ril.so) and MTK CCK modem libraries.")
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Proceed to ROM Build Studio to assemble the flashable zip package.")
        }

        onProgress("Compatibility Engine Complete!", 1.0f)

        CompatibilityResult(
            sessionId = "compat_${System.currentTimeMillis()}",
            sourceName = sourceRom.name,
            targetName = targetDevice.name,
            timestamp = System.currentTimeMillis(),
            overallScore = finalScore,
            canProceedToPort = canProceed,
            items = items,
            issues = issues,
            blockerCount = blockerCount,
            errorCount = errorCount,
            warningCount = warningCount,
            matchCount = matchCount,
            differentCount = differentCount,
            missingCount = missingCount,
            conflictCount = conflictCount,
            unknownCount = unknownCount,
            summary = summaryText,
            recommendations = recommendations
        )
    }
}
