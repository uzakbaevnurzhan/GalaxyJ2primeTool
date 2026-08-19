package com.example.porting.engine

import com.example.porting.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the comprehensive 11-section Structured Port Plan for Samsung Galaxy J2 Prime
 * (MT6737T / grandpplte) from evaluated Source ROM and Target Device profiles.
 *
 * Spans: KERNEL, BOOT, DTB, SYSTEM, VENDOR, HAL, RIL, SELINUX, PROPERTIES, INIT, PARTITIONS.
 *
 * Each task has title, description, dependencies, risk, status, action commands, and links
 * into existing patch, compare, and analyzer engines.
 */
object PortPlanBuilderEngine {

    suspend fun buildStructuredPlan(
        sourceRom: SourceRomProfile,
        targetDevice: TargetDeviceProfile,
        candidates: List<MigrationCandidate> = emptyList(),
        blockers: List<PortBlocker> = emptyList()
    ): StructuredPortPlan = withContext(Dispatchers.Default) {
        val has64BitBlocker = blockers.any { it.rootCauseType == RootCauseType.ABI_MISMATCH } || sourceRom.is64Bit
        val hasOverflowBlocker = blockers.any { it.rootCauseType == RootCauseType.INVALID_PARTITION } ||
                (sourceRom.systemSizeBytes > targetDevice.maxSystemPartitionBytes)

        val sections = listOf(
            buildKernelSection(sourceRom, targetDevice, has64BitBlocker),
            buildBootSection(sourceRom, targetDevice),
            buildDtbSection(sourceRom, targetDevice),
            buildSystemSection(sourceRom, targetDevice, hasOverflowBlocker, candidates),
            buildVendorSection(sourceRom, targetDevice, has64BitBlocker, candidates),
            buildHalSection(sourceRom, targetDevice, candidates),
            buildRilSection(sourceRom, targetDevice, candidates),
            buildSelinuxSection(sourceRom, targetDevice),
            buildPropertiesSection(sourceRom, targetDevice),
            buildInitSection(sourceRom, targetDevice, candidates),
            buildPartitionsSection(sourceRom, targetDevice, hasOverflowBlocker)
        )

        StructuredPortPlan(
            id = "plan_${System.currentTimeMillis()}",
            title = "ROM Port Plan: ${sourceRom.name} -> ${targetDevice.name}",
            sourceRomName = sourceRom.name,
            targetDeviceName = targetDevice.name,
            createdAt = System.currentTimeMillis(),
            sections = sections
        )
    }

    private fun buildKernelSection(source: SourceRomProfile, target: TargetDeviceProfile, is64Bit: Boolean): PortPlanSection {
        val kernelStatus = if (is64Bit) PortTaskStatus.BLOCKED else PortTaskStatus.READY_TO_APPLY
        return PortPlanSection(
            sectionType = PortPlanSectionType.KERNEL,
            title = "Linux Kernel & Binder IPC Subsystem",
            description = "Linux 3.18.35+ MT6737T grandpplte defconfig, 32-bit Binder IPC ioctls, and kernel cmdline parameters.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_kernel_inject",
                    section = PortPlanSectionType.KERNEL,
                    title = "Inject Stock Linux 3.18.35+ MT6737T Kernel Binary (zImage)",
                    description = "Transplant the verified 32-bit ARMv7-A zImage kernel binary compiled with MT6737T SoC platform drivers.",
                    dependencies = listOf("boot.img", "arch/arm/boot/zImage"),
                    risk = if (is64Bit) MigrationRisk.CRITICAL else MigrationRisk.HIGH,
                    status = kernelStatus,
                    targetPath = "boot.img/kernel",
                    actionCommandHint = "tools/unpackbootimg -i boot.img -o boot_out && cp base_zImage boot_out/kernel"
                ),
                PortPlanTask(
                    id = "task_kernel_cmdline",
                    section = PortPlanSectionType.KERNEL,
                    title = "Configure Kernel Boot Arguments (bootopt & selinux)",
                    description = "Set 'bootopt=64S3,32N2,32N2 androidboot.selinux=permissive' to allow legacy 32-bit driver execution and permissive bringup logging.",
                    dependencies = listOf("boot.img header", "cmdline"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "boot.img/cmdline",
                    actionCommandHint = "echo 'bootopt=64S3,32N2,32N2 androidboot.selinux=permissive' > boot_out/cmdline"
                ),
                PortPlanTask(
                    id = "task_kernel_binder",
                    section = PortPlanSectionType.KERNEL,
                    title = "Verify 32-Bit Binder IPC & Driver ioctl Compatibility",
                    description = "Ensure Android framework uses 32-bit binder structure size or loads libbinder_shim.so to communicate with 3.18 kernel binder driver.",
                    dependencies = listOf("libbinder_shim.so", "system/lib/libbinder.so"),
                    risk = MigrationRisk.HIGH,
                    status = if (source.sdkInt >= 28) PortTaskStatus.READY_TO_APPLY else PortTaskStatus.COMPLETED,
                    targetPath = "system/lib/libbinder_shim.so",
                    actionCommandHint = "cp libbinder_shim.so workspace/system/lib/libbinder_shim.so"
                )
            )
        )
    }

    private fun buildBootSection(source: SourceRomProfile, target: TargetDeviceProfile): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.BOOT,
            title = "Boot Image Structure & Ramdisk Pack",
            description = "boot.img header v1/v2, 2048-byte page size, base ramdisk nodes, and packaging parameters.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_boot_unpack",
                    section = PortPlanSectionType.BOOT,
                    title = "Unpack Donor and Base boot.img Ramdisk Payloads",
                    description = "Extract kernel, ramdisk.cpio.gz, DTB, and header metadata from source and base boot images.",
                    dependencies = listOf("boot.img"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    actionCommandHint = "magiskboot unpack boot.img"
                ),
                PortPlanTask(
                    id = "task_boot_header",
                    section = PortPlanSectionType.BOOT,
                    title = "Align 2048-Byte Page Size & MTK Memory Load Addresses",
                    description = "Configure base address 0x40000000, kernel offset 0x00008000, ramdisk offset 0x04000000, and tags offset 0x0e000000.",
                    dependencies = listOf("mkbootimg parameters"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    actionCommandHint = "mkbootimg --base 0x40000000 --pagesize 2048 --kernel boot_out/kernel --ramdisk boot_out/ramdisk.cpio.gz -o new_boot.img"
                ),
                PortPlanTask(
                    id = "task_boot_repack",
                    section = PortPlanSectionType.BOOT,
                    title = "Repack Custom boot.img with MT6737T Ramdisk Injections",
                    description = "Build final flashable boot.img with transplanted J2 Prime init scripts and kernel binary (max 16 MB).",
                    dependencies = listOf("task_boot_unpack", "task_boot_header", "task_kernel_inject"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.PENDING,
                    targetPath = "boot.img",
                    actionCommandHint = "magiskboot repack boot.img"
                )
            )
        )
    }

    private fun buildDtbSection(source: SourceRomProfile, target: TargetDeviceProfile): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.DTB,
            title = "Device Tree Blob (DTB / DTBO)",
            description = "Device Tree Bindings, MT6737T grandpplte node trees, NT35521 display timings, and I2C touch pins.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_dtb_extract",
                    section = PortPlanSectionType.DTB,
                    title = "Extract & Validate mediatek,mt6737t-grandpplte DTB",
                    description = "Verify SoC compatibility string 'mediatek,mt6737t' and board ID 'grandpplte' in Device Tree binary.",
                    dependencies = listOf("boot.img:dtb", "dtc tool"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.READY_TO_APPLY,
                    actionCommandHint = "dtc -I dtb -O dts -o grandpplte.dts boot_out/dtb"
                ),
                PortPlanTask(
                    id = "task_dtb_panel",
                    section = PortPlanSectionType.DTB,
                    title = "Validate Display Panel NT35521 DSI Controller Node",
                    description = "Ensure DSI controller node has correct 540x960 resolution, refresh rate 60Hz, and lane count (2-lane MIPI).",
                    dependencies = listOf("dtb/panel-samsung-nt35521"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY
                ),
                PortPlanTask(
                    id = "task_dtb_touch",
                    section = PortPlanSectionType.DTB,
                    title = "Verify FocalTech FT5x06 Touch Digitizer I2C Pinmux",
                    description = "Check I2C bus 1 (address 0x38), interrupt IRQ line, and reset GPIO pin assignments.",
                    dependencies = listOf("dtb/touch-ft5x06"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY
                )
            )
        )
    }

    private fun buildSystemSection(
        source: SourceRomProfile,
        target: TargetDeviceProfile,
        hasOverflow: Boolean,
        candidates: List<MigrationCandidate>
    ): PortPlanSection {
        val budgetStatus = if (hasOverflow) PortTaskStatus.BLOCKED else PortTaskStatus.READY_TO_APPLY
        return PortPlanSection(
            sectionType = PortPlanSectionType.SYSTEM,
            title = "Android System Framework & Storage Budget",
            description = "eMMC partition budget (< 1.6 GB), system framework JARs, user keylayout, and audio/graphics shims.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_sys_budget_audit",
                    section = PortPlanSectionType.SYSTEM,
                    title = "Verify 1.6 GB eMMC Storage Budget & Debloat Unused Apps",
                    description = "Ensure uncompressed system payload remains below 1,600 MB (1,719,664,640 bytes max eMMC partition size).",
                    dependencies = listOf("system.img size audit"),
                    risk = if (hasOverflow) MigrationRisk.CRITICAL else MigrationRisk.MEDIUM,
                    status = budgetStatus,
                    actionCommandHint = "du -sh workspace/system && rm -rf workspace/system/priv-app/UnneededBloat"
                ),
                PortPlanTask(
                    id = "task_sys_framework",
                    section = PortPlanSectionType.SYSTEM,
                    title = "Transplant Core Framework JARs (framework.jar, services.jar)",
                    description = "Copy donor operating system framework bytecode and dexopt runtime configurations.",
                    dependencies = listOf("system/framework/framework.jar", "system/framework/services.jar"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.PENDING,
                    targetPath = "system/framework"
                ),
                PortPlanTask(
                    id = "task_sys_shims",
                    section = PortPlanSectionType.SYSTEM,
                    title = "Inject AudioFlinger and BufferQueue Compatibility Shims",
                    description = "Place libaudioflinger_shim.so and libgui_shim.so in system/lib/ to bridge legacy MTK blobs with donor SurfaceFlinger.",
                    dependencies = listOf("libaudioflinger_shim.so", "libgui_shim.so"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/lib"
                ),
                PortPlanTask(
                    id = "task_sys_keylayout",
                    section = PortPlanSectionType.SYSTEM,
                    title = "Inject Hardware Key Layout (mtk-kpd.kl)",
                    description = "Place mtk-kpd.kl in system/usr/keylayout/ to support Power, Home, and Volume physical buttons.",
                    dependencies = listOf("mtk-kpd.kl"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/usr/keylayout/mtk-kpd.kl"
                )
            )
        )
    }

    private fun buildVendorSection(
        source: SourceRomProfile,
        target: TargetDeviceProfile,
        is64Bit: Boolean,
        candidates: List<MigrationCandidate>
    ): PortPlanSection {
        val vendorStatus = if (is64Bit) PortTaskStatus.BLOCKED else PortTaskStatus.READY_TO_APPLY
        return PortPlanSection(
            sectionType = PortPlanSectionType.VENDOR,
            title = "MediaTek Proprietary Vendor Blobs",
            description = "MT6737T hardware proprietary libraries, Mali-T720 EGL binaries, NVRAM daemon, and radio interfaces.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_ven_transplant",
                    section = PortPlanSectionType.VENDOR,
                    title = "Transplant Stock SM-G532F MediaTek MT6737T Vendor Tree",
                    description = "Inject all 32-bit hardware proprietary binaries, firmware, and shared libraries from J2 Prime stock base.",
                    dependencies = listOf("vendor/lib", "vendor/bin", "vendor/etc"),
                    risk = if (is64Bit) MigrationRisk.CRITICAL else MigrationRisk.HIGH,
                    status = vendorStatus,
                    targetPath = "vendor/",
                    actionCommandHint = "cp -r base_stock/vendor/* workspace/vendor/"
                ),
                PortPlanTask(
                    id = "task_ven_mali_egl",
                    section = PortPlanSectionType.VENDOR,
                    title = "Inject Mali-T720 OpenGL ES 3.1 & EGL Graphics Acceleration Blobs",
                    description = "Place libGLES_mali.so in vendor/lib/egl/ and register driver mapping in egl.cfg.",
                    dependencies = listOf("vendor/lib/egl/libGLES_mali.so", "system/etc/egl.cfg"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "vendor/lib/egl/libGLES_mali.so"
                ),
                PortPlanTask(
                    id = "task_ven_nvram",
                    section = PortPlanSectionType.VENDOR,
                    title = "Transplant NVRAM Calibration Files & nvram_daemon",
                    description = "Install nvram_daemon and libcustom_nvram.so to manage Wi-Fi MAC address and IMEI calibration persistence.",
                    dependencies = listOf("vendor/bin/nvram_daemon", "vendor/lib/libcustom_nvram.so"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "vendor/bin/nvram_daemon"
                )
            )
        )
    }

    private fun buildHalSection(source: SourceRomProfile, target: TargetDeviceProfile, candidates: List<MigrationCandidate>): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.HAL,
            title = "Hardware Abstraction Layer (HAL)",
            description = "Camera HAL1 legacy adaptations, MediaTek ALSA audio routing, Gralloc 0.3 allocator, and sensor modules.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_hal_camera",
                    section = PortPlanSectionType.HAL,
                    title = "Adapt Camera HAL1 Legacy Module for Donor Framework",
                    description = "Inject camera.mt6737t.so and apply CameraService client wrapper to route HAL1 buffers on modern Android framework.",
                    dependencies = listOf("camera.mt6737t.so", "libcamera_client.so"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.PENDING,
                    targetPath = "vendor/lib/hw/camera.mt6737t.so"
                ),
                PortPlanTask(
                    id = "task_hal_audio",
                    section = PortPlanSectionType.HAL,
                    title = "Configure MediaTek ALSA Primary Audio HAL & audio_policy.conf",
                    description = "Install audio.primary.mt6737t.so and audio_policy.conf to enable speaker, earpiece, mic, and in-call baseband voice audio.",
                    dependencies = listOf("audio.primary.mt6737t.so", "system/etc/audio_policy.conf"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "vendor/lib/hw/audio.primary.mt6737t.so"
                ),
                PortPlanTask(
                    id = "task_hal_gralloc",
                    section = PortPlanSectionType.HAL,
                    title = "Transplant Mali-T720 Gralloc 0.3 Framebuffer Allocator",
                    description = "Install gralloc.mt6737t.so to manage 540x960 double-buffered framebuffer surfaces for SurfaceFlinger.",
                    dependencies = listOf("gralloc.mt6737t.so", "libion_mtk.so"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "vendor/lib/hw/gralloc.mt6737t.so"
                ),
                PortPlanTask(
                    id = "task_hal_sensors",
                    section = PortPlanSectionType.HAL,
                    title = "Configure K2HH Accelerometer & GP2AP Proximity Sensor HALs",
                    description = "Install sensors.mt6737t.so to stream rotation, gravity, and screen-off proximity events.",
                    dependencies = listOf("sensors.mt6737t.so"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "vendor/lib/hw/sensors.mt6737t.so"
                )
            )
        )
    }

    private fun buildRilSection(source: SourceRomProfile, target: TargetDeviceProfile, candidates: List<MigrationCandidate>): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.RIL,
            title = "Radio Interface Layer (RIL) & Telephony",
            description = "Samsung SEC RIL / MTK CCK baseband IPC, modem init daemons, and dual-SIM / single-SIM configurations.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_ril_baseband",
                    section = PortPlanSectionType.RIL,
                    title = "Configure MTK CCK Telephony Driver & librilmtk.so",
                    description = "Install librilmtk.so and spawn ccci_mdinit daemon to handle AT commands and LTE packet data calls.",
                    dependencies = listOf("librilmtk.so", "vendor/bin/ccci_mdinit"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.PENDING,
                    targetPath = "vendor/lib/librilmtk.so"
                ),
                PortPlanTask(
                    id = "task_ril_mddb",
                    section = PortPlanSectionType.RIL,
                    title = "Inject AP/CP Baseband Modem Database (BPLGUInfo)",
                    description = "Place BPLGUInfoCustomAppSrcP database in system/etc/mddb/ to translate cellular frequency bands.",
                    dependencies = listOf("system/etc/mddb/BPLGUInfoCustomAppSrcP_MT6737T"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/etc/mddb"
                ),
                PortPlanTask(
                    id = "task_ril_multisim",
                    section = PortPlanSectionType.RIL,
                    title = "Configure Multi-SIM / Single-SIM Telephony Properties",
                    description = "Set 'persist.radio.multisim.config=dsds' (for SM-G532G/DS) or 'none' (for SM-G532F Single-SIM) in build.prop.",
                    dependencies = listOf("system/build.prop"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/build.prop"
                )
            )
        )
    }

    private fun buildSelinuxSection(source: SourceRomProfile, target: TargetDeviceProfile): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.SELINUX,
            title = "SELinux Policy & Contexts",
            description = "Platform and vendor file_contexts, service_contexts, permissive bringup audit, and domain rules.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_se_file_contexts",
                    section = PortPlanSectionType.SELINUX,
                    title = "Compile Platform and Vendor file_contexts",
                    description = "Ensure MediaTek proprietary daemon binaries (ccci_mdinit, nvram_daemon) and /dev/mtk* device nodes are labeled.",
                    dependencies = listOf("plat_file_contexts", "vendor_file_contexts"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/etc/selinux/plat_file_contexts"
                ),
                PortPlanTask(
                    id = "task_se_services",
                    section = PortPlanSectionType.SELINUX,
                    title = "Audit Service Contexts & Binder IPC Domain Associations",
                    description = "Register hardware services in plat_service_contexts and hwservice_contexts to permit ServiceManager access.",
                    dependencies = listOf("plat_service_contexts", "hwservice_contexts"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/etc/selinux/plat_service_contexts"
                ),
                PortPlanTask(
                    id = "task_se_permissive",
                    section = PortPlanSectionType.SELINUX,
                    title = "Set Initial Bringup SELinux Mode to Permissive",
                    description = "Verify kernel command line includes 'androidboot.selinux=permissive' to capture audit denial logs without blocking init.",
                    dependencies = listOf("boot.img cmdline"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY
                )
            )
        )
    }

    private fun buildPropertiesSection(source: SourceRomProfile, target: TargetDeviceProfile): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.PROPERTIES,
            title = "System Properties & build.prop Overrides",
            description = "Model identification (SM-G532F), 240 DPI screen density, low-RAM Dalvik VM tuning, and 64-bit property cleanup.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_prop_model",
                    section = PortPlanSectionType.PROPERTIES,
                    title = "Set Target Model Properties (ro.product.model=SM-G532F)",
                    description = "Configure ro.product.model=SM-G532F, ro.product.board=grandpplte, and ro.board.platform=mt6737t.",
                    dependencies = listOf("system/build.prop"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/build.prop"
                ),
                PortPlanTask(
                    id = "task_prop_density",
                    section = PortPlanSectionType.PROPERTIES,
                    title = "Configure Display Density (ro.sf.lcd_density=240)",
                    description = "Set 240 DPI LCD density for the 5.0-inch 540x960 qHD screen.",
                    dependencies = listOf("system/build.prop"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/build.prop"
                ),
                PortPlanTask(
                    id = "task_prop_strip_64bit",
                    section = PortPlanSectionType.PROPERTIES,
                    title = "Strip Incompatible 64-Bit Overrides & CPU ABI Flags",
                    description = "Remove ro.product.cpu.abilist64 and set ro.product.cpu.abi=armeabi-v7a to enforce 32-bit zygote and application loading.",
                    dependencies = listOf("system/build.prop"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/build.prop"
                ),
                PortPlanTask(
                    id = "task_prop_heap",
                    section = PortPlanSectionType.PROPERTIES,
                    title = "Tune Dalvik VM Low-RAM Memory Parameters (1.5 GB RAM)",
                    description = "Set dalvik.vm.heapgrowthlimit=128m, dalvik.vm.heapsize=256m, and ro.config.low_ram=true.",
                    dependencies = listOf("system/build.prop"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "system/build.prop"
                )
            )
        )
    }

    private fun buildInitSection(source: SourceRomProfile, target: TargetDeviceProfile, candidates: List<MigrationCandidate>): PortPlanSection {
        return PortPlanSection(
            sectionType = PortPlanSectionType.INIT,
            title = "Init Services & Hardware Daemons",
            description = "init.mt6737t.rc service declarations, ueventd nodes, daemon triggers, and socket permissions.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_init_chipset",
                    section = PortPlanSectionType.INIT,
                    title = "Inject init.mt6737t.rc & init.project.rc into Ramdisk",
                    description = "Place chipset and board hardware initialization RC scripts into boot ramdisk root.",
                    dependencies = listOf("root/init.mt6737t.rc", "root/init.project.rc"),
                    risk = MigrationRisk.HIGH,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "root/init.mt6737t.rc"
                ),
                PortPlanTask(
                    id = "task_init_ueventd",
                    section = PortPlanSectionType.INIT,
                    title = "Configure ueventd.mt6737t.rc Device Permissions",
                    description = "Set permissions for /dev/mali0 (0666), /dev/ion (0666), and /dev/ccci* modem device blocks.",
                    dependencies = listOf("root/ueventd.mt6737t.rc"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "root/ueventd.mt6737t.rc"
                ),
                PortPlanTask(
                    id = "task_init_modem",
                    section = PortPlanSectionType.INIT,
                    title = "Declare Modem Daemons in init.modem.rc",
                    description = "Declare ccci_mdinit and ccci_fsd service blocks with appropriate socket and user privileges.",
                    dependencies = listOf("root/init.modem.rc", "vendor/bin/ccci_mdinit"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.READY_TO_APPLY,
                    targetPath = "root/init.modem.rc"
                )
            )
        )
    }

    private fun buildPartitionsSection(source: SourceRomProfile, target: TargetDeviceProfile, hasOverflow: Boolean): PortPlanSection {
        val packStatus = if (hasOverflow) PortTaskStatus.BLOCKED else PortTaskStatus.READY_TO_APPLY
        return PortPlanSection(
            sectionType = PortPlanSectionType.PARTITIONS,
            title = "Partition Image Packaging & Recovery Script",
            description = "Sparse ext4 system.img packaging, 1.6 GB filesystem budget constraint, and TWRP updater-script.",
            tasks = listOf(
                PortPlanTask(
                    id = "task_part_sparse_ext4",
                    section = PortPlanSectionType.PARTITIONS,
                    title = "Generate Sparse ext4 system.img under 1,719,664,640 Bytes",
                    description = "Create sparse ext4 filesystem image with e2fsdroid/make_ext4fs ensuring byte count is strictly within partition limits.",
                    dependencies = listOf("e2fsdroid", "system/ directory tree"),
                    risk = if (hasOverflow) MigrationRisk.CRITICAL else MigrationRisk.HIGH,
                    status = packStatus,
                    targetPath = "system.img",
                    actionCommandHint = "make_ext4fs -s -l 1719664640 -a system system.img workspace/system"
                ),
                PortPlanTask(
                    id = "task_part_updater_script",
                    section = PortPlanSectionType.PARTITIONS,
                    title = "Generate TWRP Flashable Zip (updater-script & update-binary)",
                    description = "Write META-INF/com/google/android/updater-script with package_extract_file commands for boot.img and system.img.",
                    dependencies = listOf("META-INF/com/google/android/updater-script"),
                    risk = MigrationRisk.MEDIUM,
                    status = PortTaskStatus.PENDING,
                    targetPath = "META-INF/com/google/android/updater-script"
                ),
                PortPlanTask(
                    id = "task_part_sign_zip",
                    section = PortPlanSectionType.PARTITIONS,
                    title = "Sign Flashable ZIP Package with Testkeys",
                    description = "Sign the final flashable ROM ZIP archive for seamless flashing in TWRP recovery without signature verification errors.",
                    dependencies = listOf("SignApk.jar", "testkey.x509.pem", "testkey.pk8"),
                    risk = MigrationRisk.LOW,
                    status = PortTaskStatus.PENDING,
                    actionCommandHint = "java -jar SignApk.jar testkey.x509.pem testkey.pk8 unsigned_rom.zip signed_rom.zip"
                )
            )
        )
    }
}
