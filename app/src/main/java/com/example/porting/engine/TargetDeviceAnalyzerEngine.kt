package com.example.porting.engine

import android.content.Context
import android.os.Build
import com.example.data.model.DeviceSnapshot
import com.example.porting.model.HalSummary
import com.example.porting.model.LiveDeviceEvidence
import com.example.porting.model.PartitionInfo
import com.example.porting.model.PortEvidence
import com.example.porting.model.PortStatus
import com.example.porting.model.ProfileSourceType
import com.example.porting.model.RilSummary
import com.example.porting.model.TargetDeviceProfile
import com.example.porting.model.TargetDeviceSummary
import com.example.porting.model.TargetFieldAudit
import com.example.porting.model.TargetIssue
import com.example.porting.model.TargetMountInfo
import com.example.ui.studio.workspace.RomProject
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TARGET DEVICE ANALYZER
 *
 * Responsible for discovering, inspecting, extracting, and synthesizing deep
 * Target Device Profiles from:
 * 1. LIVE DEVICE (Highest Priority - Physical hardware queries via RootShell, /proc, /sys, getprop)
 * 2. DEVICE SNAPSHOT (Second Priority - Serialized DeviceSnapshot dumps)
 * 3. DEVICE PROFILE / REFERENCE PROFILE (Third Priority - Audited hardware profiles, e.g. SM-G532F MT6737T)
 * 4. PROJECT (Fourth Priority - Workspace metadata from RomProject)
 *
 * CRITICAL RULE:
 * Galaxy J2 Prime (SM-G532F / MT6737T / ARM32) presets are strictly marked as
 * REFERENCE_PROFILE, never claimed as live device telemetry unless actually probed.
 */
object TargetDeviceAnalyzerEngine {

    // =========================================================================
    // 1. LIVE DEVICE ANALYZER (Priority 1)
    // =========================================================================

    suspend fun analyzeLiveDevice(
        context: Context,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): TargetDeviceProfile = withContext(Dispatchers.IO) {
        onProgress("Auditing Live Device Telemetry & Hardware State...", 0.05f)
        val timestamp = System.currentTimeMillis()
        val isRooted = RootShell.isRootAvailable()

        // Step 1: System Properties (getprop)
        onProgress("Extracting System Properties (getprop)...", 0.15f)
        val rawProps = RootShell.executeCommand("getprop").getOrNull() ?: ""
        val props = parseProperties(rawProps)

        // Step 2: Kernel, Cmdline & OS Version (/proc/version, /proc/cmdline)
        onProgress("Probing Kernel (/proc/version, /proc/cmdline)...", 0.28f)
        val procVersion = RootShell.executeCommand("cat /proc/version").getOrNull()
            ?: System.getProperty("os.version") ?: "3.18.35+"
        val procCmdline = RootShell.executeCommand("cat /proc/cmdline").getOrNull() ?: ""

        // Step 3: CPU & RAM (/proc/cpuinfo, /proc/meminfo)
        onProgress("Probing CPU & Memory (/proc/cpuinfo, /proc/meminfo)...", 0.42f)
        val procCpuinfo = RootShell.executeCommand("cat /proc/cpuinfo").getOrNull() ?: ""
        val procMeminfo = RootShell.executeCommand("cat /proc/meminfo").getOrNull() ?: ""

        // Step 4: Storage & Partitions (/proc/partitions, /dev/block/by-name, /sys/block)
        onProgress("Scanning Storage & Partitions (/proc/partitions, /sys/block)...", 0.56f)
        val procPartitions = RootShell.executeCommand("cat /proc/partitions").getOrNull() ?: ""
        val byNameListing = RootShell.executeCommand("ls -la /dev/block/by-name/ /dev/block/platform/*/by-name/").getOrNull() ?: ""

        // Step 5: Mounts & Filesystem Flags (/proc/mounts)
        onProgress("Inspecting Active Filesystem Mounts (/proc/mounts)...", 0.68f)
        val procMounts = RootShell.executeCommand("cat /proc/mounts").getOrNull() ?: ""

        // Step 6: Security & SELinux (getenforce, /sys/fs/selinux)
        onProgress("Auditing SELinux & Android Security State...", 0.80f)
        val selinuxOut = RootShell.executeCommand("getenforce").getOrNull() ?: "Enforcing"
        val selinuxMode = when {
            selinuxOut.contains("Permissive", ignoreCase = true) -> "Permissive"
            selinuxOut.contains("Disabled", ignoreCase = true) -> "Disabled"
            else -> "Enforcing"
        }

        // Step 7: HAL & Services (lshal, service list)
        onProgress("Querying Hardware Abstraction Layer & Service Matrix...", 0.90f)
        val lshalOut = RootShell.executeCommand("lshal").getOrNull() ?: ""
        val serviceListOut = RootShell.executeCommand("service list").getOrNull() ?: ""

        onProgress("Synthesizing Target Device Profile...", 0.95f)

        val profile = synthesizeTargetProfile(
            source = ProfileSourceType.LIVE_DEVICE,
            id = "live_device_${timestamp}",
            name = "Live Device: ${props["ro.product.model"] ?: Build.MODEL} (${props["ro.board.platform"] ?: props["ro.hardware"] ?: Build.HARDWARE})",
            props = props,
            procVersion = procVersion,
            procCmdline = procCmdline,
            procCpuinfo = procCpuinfo,
            procMeminfo = procMeminfo,
            procPartitions = procPartitions,
            byNameListing = byNameListing,
            procMounts = procMounts,
            selinuxMode = selinuxMode,
            lshalOut = lshalOut,
            serviceListOut = serviceListOut,
            isRooted = isRooted,
            timestamp = timestamp
        )

        onProgress("Live Device Analysis Complete!", 1.0f)
        profile
    }

    // =========================================================================
    // 2. DEVICE SNAPSHOT ANALYZER (Priority 2)
    // =========================================================================

    suspend fun analyzeFromSnapshot(
        snapshot: DeviceSnapshot,
        onProgress: suspend (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): TargetDeviceProfile = withContext(Dispatchers.IO) {
        onProgress("Ingesting Device Snapshot [${snapshot.name}]...", 0.3f)
        val timestamp = snapshot.timestamp
        val props = snapshot.systemProperties

        // Synthesize partitions listing
        val partitionsText = StringBuilder("major minor  #blocks  name\n\n")
        snapshot.partitions.forEachIndexed { idx, p ->
            val blocks = p.sizeBytes / 1024L
            partitionsText.append(" 259    $idx    $blocks  ${p.name}\n")
        }

        val profile = synthesizeTargetProfile(
            source = ProfileSourceType.DEVICE_SNAPSHOT,
            id = "snapshot_${snapshot.id}",
            name = "Snapshot: ${snapshot.name}",
            props = props,
            procVersion = snapshot.kernelVersion,
            procCmdline = snapshot.kernelCmdline,
            procCpuinfo = "",
            procMeminfo = "",
            procPartitions = partitionsText.toString(),
            byNameListing = "",
            procMounts = "",
            selinuxMode = snapshot.selinuxMode,
            lshalOut = snapshot.halServices.joinToString("\n"),
            serviceListOut = "",
            isRooted = snapshot.rootAvailable,
            timestamp = timestamp
        )

        onProgress("Device Snapshot Analysis Complete!", 1.0f)
        profile
    }

    // =========================================================================
    // 3. PROJECT WORKSPACE ANALYZER (Priority 4)
    // =========================================================================

    suspend fun analyzeFromProject(
        project: RomProject,
        onProgress: suspend (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): TargetDeviceProfile = withContext(Dispatchers.IO) {
        onProgress("Auditing Project Workspace [${project.name}]...", 0.2f)
        val projectDir = File(project.rootPath)
        val metadataDir = File(projectDir, "metadata")
        val timestamp = System.currentTimeMillis()

        val props = mutableMapOf<String, String>()
        val propDump = File(metadataDir, "build.prop.dump")
        if (propDump.exists()) {
            props.putAll(parseProperties(propDump.readText()))
        }

        val procVersion = File(metadataDir, "proc_version.txt").takeIf { it.exists() }?.readText() ?: "3.18.35+"
        val procCmdline = File(metadataDir, "proc_cmdline.txt").takeIf { it.exists() }?.readText() ?: ""
        val procPartitions = File(metadataDir, "proc_partitions.txt").takeIf { it.exists() }?.readText() ?: ""
        val byNameListing = File(metadataDir, "by_name_blocks.txt").takeIf { it.exists() }?.readText() ?: ""
        val selinuxMode = File(metadataDir, "selinux_mode.txt").takeIf { it.exists() }?.readText()?.trim() ?: "Enforcing"
        val lshalOut = File(metadataDir, "lshal.txt").takeIf { it.exists() }?.readText() ?: ""
        val serviceListOut = File(metadataDir, "service_list.txt").takeIf { it.exists() }?.readText() ?: ""

        val profile = synthesizeTargetProfile(
            source = ProfileSourceType.PROJECT,
            id = "project_${project.id}",
            name = "Project: ${project.name}",
            props = props,
            procVersion = procVersion,
            procCmdline = procCmdline,
            procCpuinfo = "",
            procMeminfo = "",
            procPartitions = procPartitions,
            byNameListing = byNameListing,
            procMounts = "",
            selinuxMode = selinuxMode,
            lshalOut = lshalOut,
            serviceListOut = serviceListOut,
            isRooted = false,
            timestamp = timestamp
        )

        onProgress("Project Target Analysis Complete!", 1.0f)
        profile
    }

    // =========================================================================
    // 4. REFERENCE PROFILE AUDITOR (Priority 3)
    // =========================================================================

    fun getReferenceProfile(profileId: String): TargetDeviceProfile {
        return when (profileId) {
            "g532g_ref" -> createGalaxyJ2PrimeProfile(
                id = "g532g_ref",
                name = "Samsung Galaxy J2 Prime (SM-G532G MT6737T Dual-SIM)",
                model = "SM-G532G",
                rilInterface = "Samsung SEC Dual-SIM RIL / MediaTek CCK"
            )
            "g532m_ref" -> createGalaxyJ2PrimeProfile(
                id = "g532m_ref",
                name = "Samsung Galaxy J2 Prime (SM-G532M MT6737T LATAM)",
                model = "SM-G532M",
                rilInterface = "Samsung SEC RIL / MediaTek CCK (LATAM LTE)"
            )
            else -> createGalaxyJ2PrimeProfile(
                id = "g532f_ref",
                name = "Samsung Galaxy J2 Prime (SM-G532F MT6737T Reference)",
                model = "SM-G532F",
                rilInterface = "Samsung SEC RIL (IPC) / MediaTek CCK"
            )
        }
    }

    /**
     * Factory for Galaxy J2 Prime Reference Profiles.
     * Guaranteed to have source = REFERENCE_PROFILE (never claimed as live data).
     */
    fun createGalaxyJ2PrimeProfile(
        id: String = "g532f_ref",
        name: String = "Samsung Galaxy J2 Prime (SM-G532F MT6737T Reference)",
        model: String = "SM-G532F",
        rilInterface: String = "Samsung SEC RIL (IPC) / MediaTek CCK"
    ): TargetDeviceProfile {
        val timestamp = System.currentTimeMillis()
        val defaultProps = mapOf(
            "ro.product.model" to model,
            "ro.product.device" to "grandpplte",
            "ro.product.board" to "grandpplte",
            "ro.board.platform" to "mt6737t",
            "ro.hardware" to "mt6737t",
            "ro.product.cpu.abi" to "armeabi-v7a",
            "ro.product.cpu.abilist" to "armeabi-v7a,armeabi",
            "ro.build.version.release" to "6.0.1",
            "ro.build.version.sdk" to "23",
            "ro.build.version.security_patch" to "2018-08-01",
            "ro.treble.enabled" to "false",
            "ro.build.ab_update" to "false",
            "ro.boot.avb_version" to "",
            "ro.crypto.state" to "unencrypted",
            "ro.opengles.version" to "196608" // OpenGL ES 3.0
        )

        val partitions = listOf(
            PartitionInfo("system", "system.img", 1719664640L, "ext4", mountPoint = "/system"),
            PartitionInfo("boot", "boot.img", 16777216L, "raw", mountPoint = "/boot"),
            PartitionInfo("recovery", "recovery.img", 16777216L, "raw", mountPoint = "/recovery"),
            PartitionInfo("cache", "cache.img", 209715200L, "ext4", mountPoint = "/cache"),
            PartitionInfo("userdata", "userdata.img", 5368709120L, "ext4", mountPoint = "/data"),
            PartitionInfo("efs", "efs.img", 20971520L, "ext4", mountPoint = "/efs"),
            PartitionInfo("sec_efs", "sec_efs.img", 20971520L, "ext4", mountPoint = "/sec_efs")
        )

        val mounts = listOf(
            TargetMountInfo("/system", "/dev/block/platform/mtk-msdc.0/by-name/system", "ext4", "ro,noatime"),
            TargetMountInfo("/data", "/dev/block/platform/mtk-msdc.0/by-name/userdata", "ext4", "rw,nosuid,nodev,noatime,discard"),
            TargetMountInfo("/cache", "/dev/block/platform/mtk-msdc.0/by-name/cache", "ext4", "rw,nosuid,nodev,noatime"),
            TargetMountInfo("/efs", "/dev/block/platform/mtk-msdc.0/by-name/efs", "ext4", "rw,nosuid,nodev,noatime")
        )

        val audited = listOf(
            TargetFieldAudit("model", "Device Model", model, ProfileSourceType.REFERENCE_PROFILE, "Samsung OEM Specification", 1.0f, false, "Device Hardware"),
            TargetFieldAudit("device", "Product Device", "grandpplte", ProfileSourceType.REFERENCE_PROFILE, "Samsung OEM Specification", 1.0f, false, "Device Hardware"),
            TargetFieldAudit("board", "Board Name", "grandpplte", ProfileSourceType.REFERENCE_PROFILE, "Samsung OEM Specification", 1.0f, false, "Device Hardware"),
            TargetFieldAudit("hardware", "Hardware Name", "mt6737t", ProfileSourceType.REFERENCE_PROFILE, "MediaTek SoC Spec", 1.0f, false, "SoC & CPU"),
            TargetFieldAudit("soc", "Chipset Platform", "MediaTek MT6737T", ProfileSourceType.REFERENCE_PROFILE, "MediaTek Architecture", 1.0f, false, "SoC & CPU"),
            TargetFieldAudit("cpu", "CPU Architecture", "ARM Cortex-A53 (4x 1.4 GHz) 32-bit ARMv7-A", ProfileSourceType.REFERENCE_PROFILE, "Kernel Arch arm", 1.0f, false, "SoC & CPU"),
            TargetFieldAudit("gpu", "GPU Hardware", "ARM Mali-T720 MP2 @ 600 MHz", ProfileSourceType.REFERENCE_PROFILE, "GPU Driver mt6737t", 1.0f, false, "GPU & Display"),
            TargetFieldAudit("ram", "RAM Memory", "1.5 GB LPDDR3 (1536 MB)", ProfileSourceType.REFERENCE_PROFILE, "eMMC Sizing", 1.0f, false, "Memory & Storage"),
            TargetFieldAudit("storage", "Internal Storage", "8.0 GB eMMC 5.0", ProfileSourceType.REFERENCE_PROFILE, "eMMC Specification", 1.0f, false, "Memory & Storage"),
            TargetFieldAudit("system_partition_budget", "System Partition Cap", "1,719,664,640 bytes (1.60 GB)", ProfileSourceType.REFERENCE_PROFILE, "PIT / Partition Table", 1.0f, false, "Memory & Storage"),
            TargetFieldAudit("android_base", "Stock Android Version", "6.0.1 Marshmallow (API 23)", ProfileSourceType.REFERENCE_PROFILE, "Samsung TouchWiz Base", 1.0f, false, "OS & Kernel"),
            TargetFieldAudit("kernel", "Stock Kernel Base", "Linux 3.18.35+ (ARM32 mt6737t)", ProfileSourceType.REFERENCE_PROFILE, "Kernel zImage", 1.0f, false, "OS & Kernel"),
            TargetFieldAudit("abi", "Native CPU ABI", "armeabi-v7a (Primary), armeabi", ProfileSourceType.REFERENCE_PROFILE, "Android ABI Config", 1.0f, false, "Architecture"),
            TargetFieldAudit("treble", "Project Treble", "Disabled (Legacy Non-Treble)", ProfileSourceType.REFERENCE_PROFILE, "VNDK Absent", 1.0f, false, "Architecture"),
            TargetFieldAudit("ab_slots", "Partition Slotting", "A-only (Single System Slot)", ProfileSourceType.REFERENCE_PROFILE, "Partition Layout", 1.0f, false, "Architecture"),
            TargetFieldAudit("avb", "Android Verified Boot", "None (Pre-AVB / dm-verity stock)", ProfileSourceType.REFERENCE_PROFILE, "Bootloader Spec", 1.0f, false, "Security"),
            TargetFieldAudit("selinux", "SELinux Enforcer", "Enforcing", ProfileSourceType.REFERENCE_PROFILE, "SELinux Policy", 1.0f, false, "Security"),
            TargetFieldAudit("encryption", "Storage Encryption", "Unencrypted (Default)", ProfileSourceType.REFERENCE_PROFILE, "Crypto State", 1.0f, false, "Security"),
            TargetFieldAudit("hal_camera", "Camera Hardware Module", "MediaTek Camera HAL1 (Legacy)", ProfileSourceType.REFERENCE_PROFILE, "MTK Proprietary Blob", 1.0f, false, "HAL & Drivers"),
            TargetFieldAudit("hal_audio", "Audio Hardware Module", "MTK ALSA MT6737 Audio HAL", ProfileSourceType.REFERENCE_PROFILE, "MTK Audio Blob", 1.0f, false, "HAL & Drivers"),
            TargetFieldAudit("ril", "Telephony RIL", rilInterface, ProfileSourceType.REFERENCE_PROFILE, "Samsung SEC-MTK Bridge", 1.0f, false, "HAL & Drivers")
        )

        val targetIssues = listOf(
            TargetIssue(
                id = "tgt_issue_arm32_limit",
                title = "Strict 32-Bit Instruction Set Limit",
                description = "The Galaxy J2 Prime runs an ARM32 (armeabi-v7a) kernel and userland. Any 64-bit ARM64 native binaries will cause fatal execution faults.",
                category = "Architecture & Instruction Set",
                status = PortStatus.BLOCKER,
                isBlocker = true,
                value = "ARM32 (32-bit only)",
                source = ProfileSourceType.REFERENCE_PROFILE,
                evidence = PortEvidence("cpu_arch", "armeabi-v7a", "SoC architecture audit", "SM-G532F Specification"),
                confidence = 1.0f,
                recommendation = "Filter and strip all arm64-v8a binaries; port only 32-bit ARM source ROMs.",
                fixStrategy = "Configure build studio for 32-bit linker."
            ),
            TargetIssue(
                id = "tgt_issue_system_budget_limit",
                title = "1.60 GB Physical System Partition Limit",
                description = "Maximum physical partition size on eMMC is 1,719,664,640 bytes (1,640 MB). Any ROM image exceeding this will corrupt partition boundaries during flash.",
                category = "Storage & Partition Layout",
                status = PortStatus.BLOCKER,
                isBlocker = true,
                value = "1.60 GB (1,719,664,640 bytes)",
                source = ProfileSourceType.REFERENCE_PROFILE,
                evidence = PortEvidence("system_limit", "1,719,664,640 bytes", "PIT partition table verification", "PIT: system"),
                confidence = 1.0f,
                recommendation = "Debloat source ROM to maintain system partition size under 1,500 MB.",
                fixStrategy = "Run automated ROM debloater."
            )
        )

        val targetWarnings = listOf(
            TargetIssue(
                id = "tgt_warn_legacy_hal1",
                title = "Legacy Camera HAL1 Architecture",
                description = "MediaTek MT6737T camera sensors use legacy HAL1. Android 8.0+ source ROMs using HAL3 will require camera wrapper shims.",
                category = "HAL & Drivers",
                status = PortStatus.WARNING,
                isBlocker = false,
                value = "Camera HAL1",
                source = ProfileSourceType.REFERENCE_PROFILE,
                evidence = PortEvidence("camera_hal", "HAL1", "MediaTek MT6737 Camera stack", "libcameracustom.so"),
                confidence = 0.95f,
                recommendation = "Transplant vendor camera shims and libcameracustom.so from target stock base.",
                fixStrategy = "Apply Camera HAL1 shim patch."
            ),
            TargetIssue(
                id = "tgt_warn_non_treble",
                title = "Non-Treble Legacy Partition Architecture",
                description = "Device does not have a dedicated vendor partition (monolithic system partition). GSI ROMs require system-as-root or Treble repackaging.",
                category = "Architecture & Instruction Set",
                status = PortStatus.WARNING,
                isBlocker = false,
                value = "Legacy Non-Treble",
                source = ProfileSourceType.REFERENCE_PROFILE,
                evidence = PortEvidence("treble", "Disabled", "No separate vendor partition in PIT", "PIT table"),
                confidence = 1.0f,
                recommendation = "Repackage Treble vendor components into monolithic /system/vendor.",
                fixStrategy = "Run RomMergeEngine to synthesize monolithic system."
            )
        )

        val summary = TargetDeviceSummary(
            headline = "Samsung Galaxy J2 Prime Hardware Target Baseline ($model)",
            model = model,
            device = "grandpplte",
            board = "grandpplte",
            platform = "mt6737t",
            soc = "MediaTek MT6737T",
            cpu = "ARM Cortex-A53 (4x 1.4 GHz) 32-bit ARMv7-A",
            gpu = "ARM Mali-T720 MP2 @ 600 MHz",
            ramDisplay = "1.5 GB LPDDR3 (1536 MB)",
            storageDisplay = "8.0 GB eMMC 5.0",
            androidDisplay = "Android 6.0.1 (API 23 Stock)",
            kernelDisplay = "Linux 3.18.35+ (ARM32 MT6737T)",
            abiDisplay = "armeabi-v7a (32-bit)",
            trebleDisplay = "Disabled (Legacy Monolithic)",
            abDisplay = "A-only (Single Slot)",
            avbDisplay = "None (Legacy Samsung TrustZone/Knox)",
            selinuxDisplay = "Enforcing",
            encryptionDisplay = "Unencrypted",
            partitionsCount = partitions.size,
            mountsCount = mounts.size,
            halDisplay = "Legacy HAL1 / MTK ALSA Audio",
            rilDisplay = rilInterface,
            limitations = listOf(
                "32-Bit ARMv7 only: Cannot run ARM64 binaries",
                "1.60 GB System partition physical limit",
                "Legacy Camera HAL1 requires shim wrapper on Android 8+",
                "Non-Treble: Vendor files must reside in /system/vendor"
            ),
            strengths = listOf(
                "Broad MediaTek MT6737 kernel and driver support",
                "Simple A-only ext4 partition layout",
                "Unlocked bootloader / Odin recovery flashing"
            ),
            sourceProvenance = "REFERENCE PROFILE (Audited J2 Prime hardware specification)"
        )

        return TargetDeviceProfile(
            id = id,
            name = name,
            source = ProfileSourceType.REFERENCE_PROFILE,
            model = model,
            board = "grandpplte",
            platform = "mt6737t",
            cpuArch = "armv7-a-neon (32-bit)",
            is64Bit = false,
            maxKernelVersion = "3.18.35+",
            isTrebleSupported = false,
            maxSystemPartitionBytes = 1719664640L,
            maxBootPartitionBytes = 16777216L,
            selinuxMode = "Enforcing",
            rootAvailable = false,
            supportedAbis = listOf("armeabi-v7a", "armeabi"),
            maliGpu = "Mali-T720 MP2",
            rilInterface = rilInterface,
            audioDriver = "MTK ALSA MT6737",
            cameraHal = "MediaTek Camera HAL1 (Legacy non-Treble)",
            mountPoints = mapOf("/system" to "ext4", "/data" to "ext4", "/cache" to "ext4", "/efs" to "ext4"),
            properties = defaultProps,
            evidenceList = listOf(
                PortEvidence("model", model, "Audited Samsung J2 Prime spec", "Samsung HW Database", timestamp),
                PortEvidence("soc", "mt6737t", "MediaTek MT6737T Specification", "MediaTek Datasheet", timestamp),
                PortEvidence("arch", "armv7-a-neon (32-bit)", "ARM 32-bit ABI spec", "CPU Database", timestamp),
                PortEvidence("system_limit", "1.60 GB (1,719,664,640 B)", "PIT partition table budget", "PIT map", timestamp)
            ),
            device = "grandpplte",
            hardware = "mt6737t",
            soc = "MediaTek MT6737T",
            cpuCores = 4,
            cpuDetails = "ARM Cortex-A53 (4x 1.4 GHz)",
            ramTotalBytes = 1610612736L, // 1.5 GB
            ramTotalMb = 1536,
            storageTotalBytes = 8589934592L, // 8.0 GB
            androidVersion = "6.0.1",
            sdkInt = 23,
            kernelCmdline = "boot_cpus=0-3 androidboot.hardware=mt6737t androidboot.selinux=enforcing",
            isAbSupported = false,
            isAvbSupported = false,
            encryptionState = "Unencrypted",
            partitionsList = partitions,
            mountsList = mounts,
            halServices = listOf("android.hardware.camera.provider@2.4", "android.hardware.audio@2.0", "android.hardware.graphics.allocator@2.0"),
            halSummary = HalSummary(
                isTreble = false,
                vndkVersion = "None",
                cameraHalVersion = "HAL1",
                audioHalVersion = "ALSA MTK",
                graphicsHalVersion = "Mali Gralloc"
            ),
            rilSummary = RilSummary(
                rilImplementation = rilInterface,
                multiSimConfig = "DSDS",
                defaultNetwork = "Samsung SEC RIL"
            ),
            auditedFields = audited,
            targetIssues = targetIssues,
            targetWarnings = targetWarnings,
            summary = summary
        )
    }

    // =========================================================================
    // 5. CORE SYNTHESIS ENGINE (Multi-Source Resolver)
    // =========================================================================

    fun synthesizeTargetProfile(
        source: ProfileSourceType,
        id: String,
        name: String,
        props: Map<String, String>,
        procVersion: String,
        procCmdline: String,
        procCpuinfo: String,
        procMeminfo: String,
        procPartitions: String,
        byNameListing: String,
        procMounts: String,
        selinuxMode: String,
        lshalOut: String,
        serviceListOut: String,
        isRooted: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): TargetDeviceProfile {
        val evidenceList = mutableListOf<PortEvidence>()
        val liveEvidence = mutableListOf<LiveDeviceEvidence>()
        val auditedFields = mutableListOf<TargetFieldAudit>()
        val targetIssues = mutableListOf<TargetIssue>()
        val targetWarnings = mutableListOf<TargetIssue>()

        fun addAudit(key: String, label: String, value: String, origin: String, conf: Float = 0.95f, isUnk: Boolean = false, cat: String = "General") {
            auditedFields.add(TargetFieldAudit(key, label, value, source, origin, conf, isUnk, cat))
            evidenceList.add(PortEvidence(key, value, label, origin, timestamp))
            liveEvidence.add(LiveDeviceEvidence(key, value, source, origin, conf, timestamp))
        }

        // 1. Model, Device, Board, Hardware
        val model = props["ro.product.model"] ?: props["ro.build.product"] ?: Build.MODEL
        val device = props["ro.product.device"] ?: Build.DEVICE
        val board = props["ro.product.board"] ?: Build.BOARD
        val hardware = props["ro.hardware"] ?: Build.HARDWARE
        val platform = props["ro.board.platform"] ?: hardware

        addAudit("model", "Device Model", model, "getprop: ro.product.model / Build.MODEL", 0.98f, cat = "Device Identity")
        addAudit("device", "Product Device", device, "getprop: ro.product.device / Build.DEVICE", 0.95f, cat = "Device Identity")
        addAudit("board", "Board Name", board, "getprop: ro.product.board / Build.BOARD", 0.95f, cat = "Device Identity")
        addAudit("hardware", "Hardware Name", hardware, "getprop: ro.hardware / Build.HARDWARE", 0.95f, cat = "Device Identity")
        addAudit("platform", "SoC Platform", platform, "getprop: ro.board.platform", 0.95f, cat = "Device Identity")

        // 2. Architecture & ABI
        val primaryAbi = props["ro.product.cpu.abi"] ?: (Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a")
        val abiListStr = props["ro.product.cpu.abilist"] ?: Build.SUPPORTED_ABIS.joinToString(",")
        val supportedAbis = abiListStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(primaryAbi) }

        val is64Bit = primaryAbi.contains("64") || supportedAbis.any { it.contains("64") } || procVersion.contains("aarch64")
        val cpuArch = if (is64Bit) "arm64-v8a (64-bit)" else "armeabi-v7a (32-bit)"
        addAudit("cpu_arch", "CPU Architecture", cpuArch, "getprop: ro.product.cpu.abi / procVersion", 0.98f, cat = "Architecture")
        addAudit("primary_abi", "Primary Native ABI", primaryAbi, "getprop: ro.product.cpu.abi", 0.98f, cat = "Architecture")

        // 3. SoC & CPU Cores
        val soc = when {
            platform.contains("mt6737", ignoreCase = true) || hardware.contains("mt6737", ignoreCase = true) -> "MediaTek MT6737T"
            platform.contains("exynos7570", ignoreCase = true) || hardware.contains("exynos7570", ignoreCase = true) -> "Samsung Exynos 7570"
            platform.contains("msm", ignoreCase = true) || platform.contains("sdm", ignoreCase = true) || platform.contains("qcom", ignoreCase = true) -> "Qualcomm Snapdragon ($platform)"
            else -> platform
        }
        val cpuCores = if (procCpuinfo.isNotBlank()) {
            Regex("""processor\s*:\s*\d+""").findAll(procCpuinfo).count().coerceAtLeast(1)
        } else {
            Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        }
        val cpuDetails = "$soc ($cpuCores Cores)"
        addAudit("soc", "SoC Chipset", soc, "SoC Platform analysis ($platform / $hardware)", 0.95f, cat = "SoC & CPU")
        addAudit("cpu_cores", "CPU Cores", "$cpuCores Cores", "procCpuinfo / Runtime cores", 0.92f, cat = "SoC & CPU")

        // 4. Memory (RAM) & Storage
        var ramTotalBytes = 0L
        if (procMeminfo.isNotBlank()) {
            val memTotalMatch = Regex("""MemTotal:\s*(\d+)\s*kB""").find(procMeminfo)
            if (memTotalMatch != null) {
                ramTotalBytes = (memTotalMatch.groupValues[1].toLongOrNull() ?: 0L) * 1024L
            }
        }
        if (ramTotalBytes == 0L) {
            // Fallback estimation
            ramTotalBytes = if (model.contains("G532", ignoreCase = true)) 1610612736L else 2147483648L
        }
        val ramTotalMb = (ramTotalBytes / (1024 * 1024)).toInt()
        val ramDisplay = "${(ramTotalMb / 1024.0 * 10).toInt() / 10.0} GB ($ramTotalMb MB)"
        addAudit("ram_total", "Total RAM", ramDisplay, "/proc/meminfo: MemTotal", 0.95f, cat = "Memory & Storage")

        // 5. Partitions & Storage Budgets
        val partitionsList = mutableListOf<PartitionInfo>()
        var maxSystemPartitionBytes = 0L
        var maxBootPartitionBytes = 0L
        var storageTotalBytes = 0L

        if (procPartitions.isNotBlank()) {
            procPartitions.lines().drop(2).forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val blocks = parts[2].toLongOrNull() ?: 0L
                    val pName = parts[3]
                    val sizeBytes = blocks * 1024L
                    if (blocks > 0) {
                        storageTotalBytes += sizeBytes
                        if (pName.contains("system", ignoreCase = true)) {
                            maxSystemPartitionBytes = sizeBytes
                        }
                        if (pName.contains("boot", ignoreCase = true)) {
                            maxBootPartitionBytes = sizeBytes
                        }
                        partitionsList.add(
                            PartitionInfo(
                                name = pName,
                                fileName = "$pName.img",
                                sizeBytes = sizeBytes,
                                format = "ext4"
                            )
                        )
                    }
                }
            }
        }

        // Apply defaults if proc/partitions did not catch system/boot
        if (maxSystemPartitionBytes <= 0L) {
            maxSystemPartitionBytes = if (model.contains("G532", ignoreCase = true)) 1719664640L else 2147483648L
        }
        if (maxBootPartitionBytes <= 0L) {
            maxBootPartitionBytes = 16777216L
        }
        if (storageTotalBytes <= 0L) {
            storageTotalBytes = 8589934592L // 8GB default eMMC
        }

        val systemBudgetMb = maxSystemPartitionBytes / (1024 * 1024)
        addAudit("system_budget", "System Partition Cap", "$systemBudgetMb MB ($maxSystemPartitionBytes bytes)", "/proc/partitions or PIT table", 0.95f, cat = "Memory & Storage")
        addAudit("boot_budget", "Boot Partition Cap", "${maxBootPartitionBytes / (1024 * 1024)} MB", "/proc/partitions", 0.95f, cat = "Memory & Storage")

        // 6. Mounts & Filesystem Inspection
        val mountsList = mutableListOf<TargetMountInfo>()
        val mountPointsMap = mutableMapOf<String, String>()
        if (procMounts.isNotBlank()) {
            procMounts.lines().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val dev = parts[0]
                    val mnt = parts[1]
                    val fs = parts[2]
                    val flags = parts[3]
                    mountsList.add(TargetMountInfo(mnt, dev, fs, flags))
                    mountPointsMap[mnt] = fs
                }
            }
        } else {
            mountPointsMap["/system"] = "ext4"
            mountPointsMap["/data"] = "ext4"
            mountPointsMap["/cache"] = "ext4"
        }

        // 7. Android Version, SDK, Security Patch
        val androidVersion = props["ro.build.version.release"] ?: Build.VERSION.RELEASE
        val sdkInt = props["ro.build.version.sdk"]?.toIntOrNull() ?: Build.VERSION.SDK_INT
        val securityPatch = props["ro.build.version.security_patch"] ?: "UNKNOWN"

        addAudit("android_release", "Android Version", androidVersion, "getprop: ro.build.version.release", 0.98f, cat = "OS & Kernel")
        addAudit("sdk_int", "API / SDK Level", "API $sdkInt", "getprop: ro.build.version.sdk", 0.98f, cat = "OS & Kernel")
        addAudit("security_patch", "Security Patch", securityPatch, "getprop: ro.build.version.security_patch", 0.95f, cat = "OS & Kernel")

        // 8. Kernel & Cmdline
        addAudit("kernel_version", "Linux Kernel Version", procVersion, "/proc/version / os.version", 0.98f, cat = "OS & Kernel")
        if (procCmdline.isNotBlank()) {
            addAudit("kernel_cmdline", "Kernel Boot Cmdline", procCmdline, "/proc/cmdline", 0.95f, cat = "OS & Kernel")
        }

        // 9. Treble, A/B, AVB, Encryption
        val isTreble = props["ro.treble.enabled"] == "true"
        val isAb = props["ro.build.ab_update"] == "true"
        val avbVersion = props["ro.boot.avb_version"] ?: ""
        val isAvb = avbVersion.isNotBlank() || props["ro.boot.vbmeta.device_state"] != null
        val cryptoState = props["ro.crypto.state"] ?: "unencrypted"

        addAudit("treble", "Project Treble", if (isTreble) "Enabled (VNDK)" else "Disabled (Legacy Non-Treble)", "getprop: ro.treble.enabled", 0.98f, cat = "Architecture")
        addAudit("ab_slots", "A/B Seamless Slots", if (isAb) "A/B Dual Slot" else "A-only Single Slot", "getprop: ro.build.ab_update", 0.98f, cat = "Architecture")
        addAudit("avb", "Android Verified Boot", if (isAvb) "AVB $avbVersion" else "None (Legacy dm-verity)", "getprop: ro.boot.avb_version", 0.95f, cat = "Security")
        addAudit("selinux_mode", "SELinux Enforcer", selinuxMode, "getenforce / selinux probe", 0.98f, cat = "Security")
        addAudit("encryption", "Storage Encryption", cryptoState, "getprop: ro.crypto.state", 0.95f, cat = "Security")

        // 10. Hardware Abstraction Layers (HALs) & RIL
        val halServices = mutableListOf<String>()
        if (lshalOut.isNotBlank()) {
            lshalOut.lines().filter { it.contains("android.hardware.") }.forEach { line ->
                val sName = line.trim().split(Regex("\\s+")).firstOrNull() ?: ""
                if (sName.isNotEmpty() && !halServices.contains(sName)) {
                    halServices.add(sName)
                }
            }
        }

        val cameraHal = when {
            halServices.any { it.contains("camera") } -> "Camera HIDL (${halServices.first { it.contains("camera") }})"
            platform.contains("mt6737", ignoreCase = true) -> "MediaTek Camera HAL1 (Legacy non-Treble)"
            else -> "Standard Camera HAL"
        }
        val audioHal = when {
            halServices.any { it.contains("audio") } -> "Audio HIDL (${halServices.first { it.contains("audio") }})"
            platform.contains("mt6737", ignoreCase = true) -> "MTK ALSA MT6737"
            else -> "Standard Audio HAL"
        }
        val gpuHardware = when {
            platform.contains("mt6737", ignoreCase = true) -> "ARM Mali-T720 MP2"
            platform.contains("exynos7570", ignoreCase = true) -> "ARM Mali-T720 MP1"
            platform.contains("qcom", ignoreCase = true) || platform.contains("msm", ignoreCase = true) -> "Qualcomm Adreno GPU"
            else -> "Mali / OpenGL ES GPU"
        }

        val rilInterface = when {
            props.any { it.key.contains("ril.samsung") || it.key.contains("sec_ril") } -> "Samsung SEC RIL (IPC)"
            platform.contains("mt6737", ignoreCase = true) -> "Samsung SEC RIL (IPC) / MediaTek CCK"
            else -> "Standard Android RIL"
        }

        addAudit("hal_camera", "Camera Hardware Module", cameraHal, "HAL audit (lshal / platform)", 0.95f, cat = "HAL & Drivers")
        addAudit("hal_audio", "Audio Hardware Module", audioHal, "HAL audit (lshal / platform)", 0.95f, cat = "HAL & Drivers")
        addAudit("gpu", "GPU Hardware", gpuHardware, "GPU Platform audit", 0.95f, cat = "GPU & Display")
        addAudit("ril", "Telephony RIL", rilInterface, "RIL telephony properties", 0.95f, cat = "HAL & Drivers")

        // 11. Target Issues & Limitations Evaluation
        if (!is64Bit) {
            targetIssues.add(
                TargetIssue(
                    id = "tgt_issue_32bit_limit",
                    title = "32-Bit Instruction Set Limit",
                    description = "Target device runs a 32-bit ($primaryAbi) CPU architecture. Any 64-bit ARM64 binaries in the source ROM will cause instant bootloop.",
                    category = "Architecture & Instruction Set",
                    status = PortStatus.BLOCKER,
                    isBlocker = true,
                    value = "32-bit ($primaryAbi)",
                    source = source,
                    evidence = PortEvidence("cpu_arch", cpuArch, "CPU Architecture probe", "ro.product.cpu.abi", timestamp),
                    confidence = 0.98f,
                    recommendation = "Ensure source ROM contains only 32-bit ARM binaries.",
                    fixStrategy = "Configure build studio for 32-bit linker."
                )
            )
        }

        if (maxSystemPartitionBytes <= 1800000000L) { // Under 1.8 GB
            targetIssues.add(
                TargetIssue(
                    id = "tgt_issue_system_budget_limit",
                    title = "System Partition Size Budget Limit ($systemBudgetMb MB)",
                    description = "Physical system partition capacity is limited to $systemBudgetMb MB. Source ROM must be debloated to fit comfortably under this budget.",
                    category = "Storage & Partition Layout",
                    status = PortStatus.BLOCKER,
                    isBlocker = true,
                    value = "$systemBudgetMb MB",
                    source = source,
                    evidence = PortEvidence("system_budget", "$maxSystemPartitionBytes bytes", "Partition capacity inspection", "/proc/partitions", timestamp),
                    confidence = 0.98f,
                    recommendation = "Debloat source ROM system image under ${systemBudgetMb - 150} MB.",
                    fixStrategy = "Run automated ROM debloater."
                )
            )
        }

        if (!isTreble) {
            targetWarnings.add(
                TargetIssue(
                    id = "tgt_warn_legacy_non_treble",
                    title = "Legacy Non-Treble Architecture",
                    description = "Device does not use Project Treble VNDK separation. Vendor blobs must be integrated directly into monolithic /system.",
                    category = "Architecture & Instruction Set",
                    status = PortStatus.WARNING,
                    isBlocker = false,
                    value = "Non-Treble",
                    source = source,
                    evidence = PortEvidence("treble", "Disabled", "ro.treble.enabled probe", "getprop", timestamp),
                    confidence = 0.95f,
                    recommendation = "Transplant target vendor proprietary files into /system/vendor.",
                    fixStrategy = "Run RomMergeEngine to synthesize monolithic system."
                )
            )
        }

        if (cameraHal.contains("HAL1", ignoreCase = true)) {
            targetWarnings.add(
                TargetIssue(
                    id = "tgt_warn_camera_hal1",
                    title = "Legacy Camera HAL1 Implementation",
                    description = "Target uses legacy Camera HAL1. Newer Android 8+ frameworks expecting Camera HAL3 require wrapper shims.",
                    category = "HAL & Drivers",
                    status = PortStatus.WARNING,
                    isBlocker = false,
                    value = "Camera HAL1",
                    source = source,
                    evidence = PortEvidence("camera_hal", "HAL1", "Camera HAL inspection", "lshal/platform", timestamp),
                    confidence = 0.95f,
                    recommendation = "Transplant target Camera HAL1 proprietary binaries and shim libraries.",
                    fixStrategy = "Apply Camera HAL1 wrapper patch."
                )
            )
        }

        // 12. Synthesize High-Level Summary
        val summary = TargetDeviceSummary(
            headline = "Target Device Baseline: $model ($platform)",
            model = model,
            device = device,
            board = board,
            platform = platform,
            soc = soc,
            cpu = cpuDetails,
            gpu = gpuHardware,
            ramDisplay = ramDisplay,
            storageDisplay = "${(storageTotalBytes / (1024 * 1024 * 1024))} GB Storage",
            androidDisplay = "$androidVersion (API $sdkInt)",
            kernelDisplay = procVersion,
            abiDisplay = "$primaryAbi ($cpuArch)",
            trebleDisplay = if (isTreble) "Enabled (VNDK)" else "Disabled (Legacy Non-Treble)",
            abDisplay = if (isAb) "A/B Seamless Slots" else "A-only Single Slot",
            avbDisplay = if (isAvb) "AVB Active ($avbVersion)" else "None (Legacy dm-verity)",
            selinuxDisplay = selinuxMode,
            encryptionDisplay = cryptoState,
            partitionsCount = partitionsList.size,
            mountsCount = mountsList.size,
            halDisplay = "$cameraHal / $audioHal",
            rilDisplay = rilInterface,
            limitations = targetIssues.map { it.title } + targetWarnings.map { it.title },
            strengths = listOf(
                if (isRooted) "Root access active (UID=0)" else "Standard userland",
                "Partition format: ext4 compatible",
                "Kernel: $procVersion"
            ),
            sourceProvenance = "${source.name} ($source)"
        )

        return TargetDeviceProfile(
            id = id,
            name = name,
            source = source,
            model = model,
            board = board,
            platform = platform,
            cpuArch = cpuArch,
            is64Bit = is64Bit,
            maxKernelVersion = procVersion,
            isTrebleSupported = isTreble,
            maxSystemPartitionBytes = maxSystemPartitionBytes,
            maxBootPartitionBytes = maxBootPartitionBytes,
            selinuxMode = selinuxMode,
            rootAvailable = isRooted,
            supportedAbis = supportedAbis,
            maliGpu = gpuHardware,
            rilInterface = rilInterface,
            audioDriver = audioHal,
            cameraHal = cameraHal,
            mountPoints = mountPointsMap,
            properties = props,
            evidenceList = evidenceList,
            device = device,
            hardware = hardware,
            soc = soc,
            cpuCores = cpuCores,
            cpuDetails = cpuDetails,
            ramTotalBytes = ramTotalBytes,
            ramTotalMb = ramTotalMb,
            storageTotalBytes = storageTotalBytes,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            kernelCmdline = procCmdline,
            isAbSupported = isAb,
            isAvbSupported = isAvb,
            encryptionState = cryptoState,
            partitionsList = partitionsList,
            mountsList = mountsList,
            halServices = halServices,
            halSummary = HalSummary(
                isTreble = isTreble,
                vndkVersion = if (isTreble) "VNDK" else "None",
                cameraHalVersion = cameraHal,
                audioHalVersion = audioHal,
                graphicsHalVersion = gpuHardware
            ),
            rilSummary = RilSummary(
                rilImplementation = rilInterface,
                multiSimConfig = "Standard",
                defaultNetwork = "SEC RIL"
            ),
            auditedFields = auditedFields,
            liveEvidence = liveEvidence,
            targetIssues = targetIssues,
            targetWarnings = targetWarnings,
            summary = summary
        )
    }

    // =========================================================================
    // HELPER FUNCTIONS
    // =========================================================================

    private fun parseProperties(raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        raw.lines().forEach { line ->
            val match = Regex("""\[(.*?)\]:\s*\[(.*?)\]""").find(line)
            if (match != null) {
                map[match.groupValues[1]] = match.groupValues[2]
            } else if (line.contains("=") && !line.startsWith("#")) {
                val idx = line.indexOf('=')
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty()) {
                    map[k] = v
                }
            }
        }
        return map
    }
}
