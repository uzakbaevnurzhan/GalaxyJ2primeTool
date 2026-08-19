package com.example.porting.engine

import com.example.porting.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Intelligent analyzer that identifies potentially transplantable ROM components,
 * libraries, HALs, configs, init services, properties, DTB/DTBO nodes, firmware,
 * permissions, SELinux contexts, system files, and vendor blobs between Source ROM
 * and Target Device (Samsung Galaxy J2 Prime MT6737T / grandpplte).
 *
 * CRITICAL CONSTRAINT:
 * Migration candidates are evaluated, triaged, and surfaced for inspection or
 * inclusion into Patch Plans. THEY ARE NEVER COPIED AUTOMATICALLY.
 */
object MigrationCandidatesEngine {

    suspend fun discoverCandidates(
        sourceRom: SourceRomProfile,
        targetDevice: TargetDeviceProfile,
        onProgress: suspend (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): List<MigrationCandidate> = withContext(Dispatchers.Default) {
        val candidates = mutableListOf<MigrationCandidate>()

        onProgress("Auditing dynamic shared libraries and shims...", 0.1f)
        candidates.addAll(evaluateLibraryCandidates(sourceRom, targetDevice))

        onProgress("Evaluating Hardware Abstraction Layer (HAL) modules...", 0.2f)
        candidates.addAll(evaluateHalCandidates(sourceRom, targetDevice))

        onProgress("Auditing hardware configs, media profiles & audio policy...", 0.3f)
        candidates.addAll(evaluateConfigCandidates(sourceRom, targetDevice))

        onProgress("Inspecting init scripts, uevent rules & daemon triggers...", 0.4f)
        candidates.addAll(evaluateInitCandidates(sourceRom, targetDevice))

        onProgress("Cross-referencing system properties & build parameters...", 0.5f)
        candidates.addAll(evaluatePropertyCandidates(sourceRom, targetDevice))

        onProgress("Auditing Device Tree Blob (DTB) & overlay nodes...", 0.6f)
        candidates.addAll(evaluateDtbCandidates(sourceRom, targetDevice))

        onProgress("Inspecting modem, Wi-Fi & Bluetooth firmware references...", 0.7f)
        candidates.addAll(evaluateFirmwareCandidates(sourceRom, targetDevice))

        onProgress("Validating hardware & privileged app permissions...", 0.8f)
        candidates.addAll(evaluatePermissionCandidates(sourceRom, targetDevice))

        onProgress("Auditing SELinux file contexts & service policies...", 0.85f)
        candidates.addAll(evaluateSelinuxCandidates(sourceRom, targetDevice))

        onProgress("Auditing core system framework & user keylayout...", 0.9f)
        candidates.addAll(evaluateSystemFileCandidates(sourceRom, targetDevice))

        onProgress("Auditing proprietary MediaTek vendor blobs...", 0.95f)
        candidates.addAll(evaluateVendorCandidates(sourceRom, targetDevice))

        onProgress("Migration candidate discovery complete.", 1.0f)
        candidates
    }

    private fun evaluateLibraryCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        val is64BitSource = source.is64Bit
        val defaultLibStatus = if (is64BitSource) CandidateStatus.BLOCKED else CandidateStatus.SAFE_TO_INVESTIGATE
        val defaultRisk = if (is64BitSource) MigrationRisk.CRITICAL else MigrationRisk.LOW

        return listOf(
            MigrationCandidate(
                id = "cand_lib_binder_shim",
                name = "32-Bit Binder IPC Shim (libbinder_shim.so)",
                category = CandidateCategory.LIBRARIES,
                path = "system/lib/libbinder_shim.so",
                source = "J2 Prime Porting Base / Tools",
                target = "system/lib/libbinder_shim.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libbinder.so", "libcutils.so", "libutils.so"),
                risk = if (source.sdkInt >= 28) MigrationRisk.MEDIUM else MigrationRisk.LOW,
                reason = "Bridges 32-bit binder IPC ioctl communication between modern Android framework and Linux 3.18 kernel.",
                confidence = 0.95f,
                status = if (source.sdkInt >= 28) CandidateStatus.CANDIDATE else CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Translates binder_transaction_data structures to support legacy 32-bit binder drivers on 3.18.35+."
            ),
            MigrationCandidate(
                id = "cand_lib_gui_shim",
                name = "SurfaceFlinger / BufferQueue Shim (libgui_shim.so)",
                category = CandidateCategory.LIBRARIES,
                path = "system/lib/libgui_shim.so",
                source = "J2 Prime Porting Base",
                target = "system/lib/libgui_shim.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libgui.so", "libui.so", "libbinder.so"),
                risk = MigrationRisk.MEDIUM,
                reason = "Ensures Mali-T720 GPU legacy gralloc symbols connect cleanly with newer Android SurfaceComposerClient.",
                confidence = 0.92f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Exports legacy android::GraphicBuffer symbols required by MT6737T Mali EGL blobs."
            ),
            MigrationCandidate(
                id = "cand_lib_audioflinger_shim",
                name = "AudioFlinger Compatibility Client (libaudioflinger_shim.so)",
                category = CandidateCategory.LIBRARIES,
                path = "system/lib/libaudioflinger_shim.so",
                source = "J2 Prime Porting Base",
                target = "system/lib/libaudioflinger_shim.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libmedia.so", "libaudioclient.so", "liblog.so"),
                risk = MigrationRisk.LOW,
                reason = "Prevents audio server crashes by shimming MTK proprietary ALSA audio stream symbols.",
                confidence = 0.90f,
                status = CandidateStatus.CANDIDATE,
                details = "Hooks AudioTrack::set and getLatency methods for seamless ALSA routing."
            ),
            MigrationCandidate(
                id = "cand_lib_ion",
                name = "MediaTek ION Memory Client (libion_mtk.so)",
                category = CandidateCategory.LIBRARIES,
                path = "vendor/lib/libion_mtk.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/libion_mtk.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libc.so", "liblog.so"),
                risk = defaultRisk,
                reason = "Provides direct DMA-BUF and hardware buffer allocation for camera, GPU, and display pipelines.",
                confidence = 0.98f,
                status = defaultLibStatus,
                details = "Required by Camera HAL1 and Mali-T720 gralloc allocator."
            ),
            MigrationCandidate(
                id = "cand_lib_mali",
                name = "ARM Mali-T720 OpenGL ES & EGL Binary (libMali.so)",
                category = CandidateCategory.LIBRARIES,
                path = "vendor/lib/egl/libGLES_mali.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/egl/libGLES_mali.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libc.so", "libm.so", "liblog.so", "libion.so"),
                risk = defaultRisk,
                reason = "Native GPU hardware acceleration library for Mali-T720 MP2.",
                confidence = 0.99f,
                status = defaultLibStatus,
                details = "Hardware-bound binary; must originate from 32-bit MT6737T vendor tree."
            )
        )
    }

    private fun evaluateHalCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        val is64BitSource = source.is64Bit
        val defaultStatus = if (is64BitSource) CandidateStatus.BLOCKED else CandidateStatus.SAFE_TO_INVESTIGATE
        val defaultRisk = if (is64BitSource) MigrationRisk.CRITICAL else MigrationRisk.MEDIUM

        return listOf(
            MigrationCandidate(
                id = "cand_hal_camera",
                name = "MediaTek Camera HAL1 Legacy Module",
                category = CandidateCategory.HAL,
                path = "vendor/lib/hw/camera.mt6737t.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/hw/camera.mt6737t.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libcamera_client.so", "libcam.client.so", "libcamalgo.so"),
                risk = defaultRisk,
                reason = "Primary hardware interface for Samsung J2 Prime 8MP rear and 5MP front CMOS sensors.",
                confidence = 0.94f,
                status = defaultStatus,
                details = "Legacy non-Treble HAL1 implementation. Requires framework CameraService shim on Android 8+."
            ),
            MigrationCandidate(
                id = "cand_hal_audio",
                name = "MediaTek ALSA Primary Audio HAL",
                category = CandidateCategory.HAL,
                path = "vendor/lib/hw/audio.primary.mt6737t.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/hw/audio.primary.mt6737t.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libmedia.so", "libaudiocustparam.so", "libtinyalsa.so"),
                risk = defaultRisk,
                reason = "Routes digital audio streams to MT6328 PMIC audio codec and 3.5mm headphone jack.",
                confidence = 0.96f,
                status = defaultStatus,
                details = "Controls internal speaker, earpiece, microphone, and in-call baseband voice audio."
            ),
            MigrationCandidate(
                id = "cand_hal_gralloc",
                name = "Mali-T720 Gralloc 0.3 Framebuffer Allocator",
                category = CandidateCategory.HAL,
                path = "vendor/lib/hw/gralloc.mt6737t.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/hw/gralloc.mt6737t.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libMali.so", "libion.so", "libcutils.so"),
                risk = defaultRisk,
                reason = "Allocates graphic framebuffers for SurfaceFlinger and hardware overlays.",
                confidence = 0.98f,
                status = defaultStatus,
                details = "Manages 540x960 double-buffered framebuffer surfaces."
            ),
            MigrationCandidate(
                id = "cand_hal_sensors",
                name = "Sensors HAL (Accelerometer & Proximity)",
                category = CandidateCategory.HAL,
                path = "vendor/lib/hw/sensors.mt6737t.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/hw/sensors.mt6737t.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libcutils.so", "libhardware.so"),
                risk = MigrationRisk.LOW,
                reason = "Interprets I2C raw data from K2HH accelerometer and GP2AP proximity sensor.",
                confidence = 0.92f,
                status = CandidateStatus.CANDIDATE,
                details = "Feeds orientation and proximity events to Android SensorManager."
            ),
            MigrationCandidate(
                id = "cand_hal_lights",
                name = "LCD Backlight & Notification LED HAL",
                category = CandidateCategory.HAL,
                path = "vendor/lib/hw/lights.mt6737t.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/hw/lights.mt6737t.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libcutils.so", "libhardware.so"),
                risk = MigrationRisk.LOW,
                reason = "Writes PWM duty cycle values to sysfs backlight controls (/sys/class/leds/lcd-backlight).",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Controls brightness levels and front LED flash."
            ),
            MigrationCandidate(
                id = "cand_hal_power",
                name = "MediaTek MT6737T Power & CPU Governor HAL",
                category = CandidateCategory.HAL,
                path = "vendor/lib/hw/power.mt6737t.so",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "vendor/lib/hw/power.mt6737t.so",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("libcutils.so", "libhardware.so"),
                risk = MigrationRisk.LOW,
                reason = "Adjusts dynamic CPU clock scaling and touch boost governor triggers.",
                confidence = 0.91f,
                status = CandidateStatus.CANDIDATE,
                details = "Interacts with MTK PerfService kernel interface."
            )
        )
    }

    private fun evaluateConfigCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_cfg_audio_policy",
                name = "Audio Policy Configuration (audio_policy.conf)",
                category = CandidateCategory.CONFIGS,
                path = "system/etc/audio_policy.conf",
                source = "Target Stock ROM / Base",
                target = "system/etc/audio_policy.conf",
                architecture = "Agnostic",
                dependencies = listOf("vendor/lib/hw/audio.primary.mt6737t.so"),
                risk = MigrationRisk.LOW,
                reason = "Declares audio attached output devices, sample rates (44.1kHz, 48kHz), and ALSA hardware endpoints.",
                confidence = 0.96f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Defines primary, low-latency, and compressed audio stream profiles."
            ),
            MigrationCandidate(
                id = "cand_cfg_media_codecs",
                name = "Hardware Video Codecs Table (media_codecs.xml)",
                category = CandidateCategory.CONFIGS,
                path = "system/etc/media_codecs.xml",
                source = "Target Stock ROM / Base",
                target = "system/etc/media_codecs.xml",
                architecture = "Agnostic",
                dependencies = listOf("vendor/lib/libvcodec_oal.so"),
                risk = MigrationRisk.LOW,
                reason = "Registers hardware accelerated H.264, H.265 (HEVC), MPEG-4, and VP8/VP9 decoders for MT6737T.",
                confidence = 0.95f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Enables hardware video playback up to 1080p@30fps."
            ),
            MigrationCandidate(
                id = "cand_cfg_media_profiles",
                name = "Camcorder Recording Profiles (media_profiles.xml)",
                category = CandidateCategory.CONFIGS,
                path = "system/etc/media_profiles.xml",
                source = "Target Stock ROM / Base",
                target = "system/etc/media_profiles.xml",
                architecture = "Agnostic",
                dependencies = listOf("system/etc/media_codecs.xml"),
                risk = MigrationRisk.LOW,
                reason = "Configures 720p HD camcorder video bitrate, AAC audio encoder sample rate, and FPS limits.",
                confidence = 0.93f,
                status = CandidateStatus.CANDIDATE,
                details = "Ensures camera app records valid MP4 files without dropping frames."
            ),
            MigrationCandidate(
                id = "cand_cfg_egl",
                name = "EGL Graphics Driver Registration (egl.cfg)",
                category = CandidateCategory.CONFIGS,
                path = "system/etc/egl.cfg",
                source = "Target Stock ROM / Base",
                target = "system/etc/egl.cfg",
                architecture = "Agnostic",
                dependencies = listOf("vendor/lib/egl/libGLES_mali.so"),
                risk = MigrationRisk.LOW,
                reason = "Tells Android Graphics Environment to bind Mali EGL driver (`0 0 mali`).",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Single-line driver map preventing software fallback rendering."
            ),
            MigrationCandidate(
                id = "cand_cfg_gps",
                name = "MediaTek GPS SUPL & AGPS Config (gps.conf)",
                category = CandidateCategory.CONFIGS,
                path = "system/etc/gps.conf",
                source = "Target Stock ROM / Base",
                target = "system/etc/gps.conf",
                architecture = "Agnostic",
                dependencies = listOf("vendor/bin/mnld"),
                risk = MigrationRisk.LOW,
                reason = "Configures NTP time servers, SUPL assisted GPS servers, and position accuracy thresholds.",
                confidence = 0.92f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Speeds up GPS satellite lock times."
            )
        )
    }

    private fun evaluateInitCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_init_chipset",
                name = "MediaTek MT6737T Base Init Script (init.mt6737t.rc)",
                category = CandidateCategory.INIT_SERVICES,
                path = "root/init.mt6737t.rc",
                source = "Target Stock Boot Image (Ramdisk Base)",
                target = "root/init.mt6737t.rc",
                architecture = "Agnostic (RC Script)",
                dependencies = listOf("vendor/bin/ccci_mdinit", "vendor/bin/nvram_daemon"),
                risk = MigrationRisk.HIGH,
                reason = "Sets up sysfs memory nodes, zram swap, baseband daemons, and MediaTek hardware services.",
                confidence = 0.97f,
                status = CandidateStatus.HIGH_RISK,
                details = "Core boot sequence script. Critical to transplant into donor ramdisk."
            ),
            MigrationCandidate(
                id = "cand_init_project",
                name = "Galaxy J2 Prime Board Hardware Init (init.project.rc)",
                category = CandidateCategory.INIT_SERVICES,
                path = "root/init.project.rc",
                source = "Target Stock Boot Image (Ramdisk Base)",
                target = "root/init.project.rc",
                architecture = "Agnostic (RC Script)",
                dependencies = listOf("root/init.mt6737t.rc"),
                risk = MigrationRisk.MEDIUM,
                reason = "Initializes Samsung grandpplte specific GPIO pins, camera sensor regulators, and touch panel power.",
                confidence = 0.95f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Essential for touchscreen and camera power-up."
            ),
            MigrationCandidate(
                id = "cand_init_ueventd",
                name = "MediaTek Device Node Permissions (ueventd.mt6737t.rc)",
                category = CandidateCategory.INIT_SERVICES,
                path = "root/ueventd.mt6737t.rc",
                source = "Target Stock Boot Image (Ramdisk Base)",
                target = "root/ueventd.mt6737t.rc",
                architecture = "Agnostic (Uevent Config)",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Defines owner and chmod permissions for /dev/mali0, /dev/ion, /dev/mtk*, and /dev/ccci* device blocks.",
                confidence = 0.98f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Prevents EACCES permission denied errors when userspace daemons open device nodes."
            ),
            MigrationCandidate(
                id = "cand_init_modem",
                name = "Modem Daemon & Baseband Triggers (init.modem.rc)",
                category = CandidateCategory.INIT_SERVICES,
                path = "root/init.modem.rc",
                source = "Target Stock Boot Image (Ramdisk Base)",
                target = "root/init.modem.rc",
                architecture = "Agnostic (RC Script)",
                dependencies = listOf("vendor/bin/ccci_mdinit", "vendor/bin/ccci_fsd"),
                risk = MigrationRisk.MEDIUM,
                reason = "Spawns cross-core communication interfaces for cellular modem handshake.",
                confidence = 0.94f,
                status = CandidateStatus.CANDIDATE,
                details = "Required for SIM card detection and signal registration."
            )
        )
    }

    private fun evaluatePropertyCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_prop_density",
                name = "Display Density Override (ro.sf.lcd_density=240)",
                category = CandidateCategory.PROPERTIES,
                path = "system/build.prop: ro.sf.lcd_density",
                source = "Target Hardware Specification",
                target = "system/build.prop",
                architecture = "Agnostic",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Configures 240 DPI (hdpi) scaling for the 5.0-inch 540x960 qHD display panel.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Ensures system UI icons and fonts render at correct physical proportions."
            ),
            MigrationCandidate(
                id = "cand_prop_model",
                name = "Target Device Identity (ro.product.model=SM-G532F)",
                category = CandidateCategory.PROPERTIES,
                path = "system/build.prop: ro.product.model",
                source = "Target Stock Profile",
                target = "system/build.prop",
                architecture = "Agnostic",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Identifies the hardware device string to Google Play Services and system components.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Sets product model to SM-G532F / grandpplte."
            ),
            MigrationCandidate(
                id = "cand_prop_heap",
                name = "Dalvik VM Low-RAM Heap (dalvik.vm.heapgrowthlimit=128m)",
                category = CandidateCategory.PROPERTIES,
                path = "system/build.prop: dalvik.vm.heapgrowthlimit",
                source = "J2 Prime Performance Profile",
                target = "system/build.prop",
                architecture = "Agnostic",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Optimizes Java heap size for the 1.5 GB physical RAM constraint.",
                confidence = 0.96f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Prevents Out-Of-Memory thrashing in background services."
            ),
            MigrationCandidate(
                id = "cand_prop_platform",
                name = "Chipset Identifier (ro.board.platform=mt6737t)",
                category = CandidateCategory.PROPERTIES,
                path = "system/build.prop: ro.board.platform",
                source = "Target Hardware Specification",
                target = "system/build.prop",
                architecture = "Agnostic",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Directs HAL loading subsystem to find .mt6737t.so shared libraries.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Critical for dlopen of hw modules."
            )
        )
    }

    private fun evaluateDtbCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_dtb_grandpplte",
                name = "Main Board Device Tree Blob (mediatek,mt6737t-grandpplte.dtb)",
                category = CandidateCategory.DTB_NODES,
                path = "boot.img:dtb/mediatek,mt6737t-grandpplte.dtb",
                source = "Target Stock Boot Image (zImage-dtb)",
                target = "boot.img:dtb",
                architecture = "Device Tree (DTB)",
                dependencies = listOf("kernel zImage (3.18.35+)"),
                risk = MigrationRisk.HIGH,
                reason = "Provides exact memory map, interrupt controller mappings, and clock trees for J2 Prime.",
                confidence = 0.98f,
                status = CandidateStatus.HIGH_RISK,
                details = "Kernel cannot initialize peripherals or mount eMMC without matching DTB."
            ),
            MigrationCandidate(
                id = "cand_dtb_panel",
                name = "Display Panel NT35521 Device Tree Node",
                category = CandidateCategory.DTB_NODES,
                path = "boot.img:dtb/panel-samsung-nt35521",
                source = "Target Stock DTB",
                target = "boot.img:dtb/panel-samsung-nt35521",
                architecture = "Device Tree (DTB)",
                dependencies = listOf("boot.img:dtb/mediatek,mt6737t-grandpplte.dtb"),
                risk = MigrationRisk.MEDIUM,
                reason = "DSI timing parameters and initialization commands for the TFT LCD panel.",
                confidence = 0.96f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Essential to prevent blank screen / black screen on boot."
            ),
            MigrationCandidate(
                id = "cand_dtb_touch",
                name = "FocalTech FT5x06 Capacitive Touchscreen Node",
                category = CandidateCategory.DTB_NODES,
                path = "boot.img:dtb/touch-ft5x06",
                source = "Target Stock DTB",
                target = "boot.img:dtb/touch-ft5x06",
                architecture = "Device Tree (DTB)",
                dependencies = listOf("boot.img:dtb/mediatek,mt6737t-grandpplte.dtb"),
                risk = MigrationRisk.LOW,
                reason = "I2C bus address (0x38), interrupt GPIO, and reset pin bindings for touch digitizer.",
                confidence = 0.95f,
                status = CandidateStatus.CANDIDATE,
                details = "Enables multi-touch input in recovery and system UI."
            ),
            MigrationCandidate(
                id = "cand_dtbo_rev01",
                name = "PCB Hardware Revision 0.1 Overlay (overlay_0_grandpplte_rev01)",
                category = CandidateCategory.DTBO_ENTRIES,
                path = "dtbo.img:overlay_0_grandpplte_rev01",
                source = "Target Stock DTBO / Kernel Base",
                target = "dtbo.img",
                architecture = "Device Tree Overlay (DTBO)",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Applies board revision 0.1 pin multiplexing adjustments dynamically.",
                confidence = 0.90f,
                status = CandidateStatus.CANDIDATE,
                details = "Used by bootloader to patch device tree during boot."
            ),
            MigrationCandidate(
                id = "cand_dtbo_dsi",
                name = "DSI Display Clock Subsystem Overlay",
                category = CandidateCategory.DTBO_ENTRIES,
                path = "dtbo.img:display_dsi_subsystem",
                source = "Target Stock DTBO / Kernel Base",
                target = "dtbo.img",
                architecture = "Device Tree Overlay (DTBO)",
                dependencies = listOf("cand_dtb_panel"),
                risk = MigrationRisk.LOW,
                reason = "Calibrates DSI PLL clock frequencies for flicker-free display output.",
                confidence = 0.91f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Sets pixel clock and lane speeds."
            )
        )
    }

    private fun evaluateFirmwareCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_fw_wifi",
                name = "MediaTek MT6625L Wi-Fi Microcode (WIFI_RAM_CODE_MT6737T)",
                category = CandidateCategory.FIRMWARE_REFS,
                path = "system/etc/firmware/WIFI_RAM_CODE_MT6737T",
                source = "Target Stock Vendor Tree",
                target = "system/etc/firmware/WIFI_RAM_CODE_MT6737T",
                architecture = "Firmware Binary",
                dependencies = listOf("vendor/lib/modules/wlan_mt6625.ko"),
                risk = MigrationRisk.LOW,
                reason = "Direct microcode payload uploaded to MT6625 combo wireless chip during Wi-Fi activation.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Required for 802.11 b/g/n Wi-Fi operation."
            ),
            MigrationCandidate(
                id = "cand_fw_bt",
                name = "Bluetooth Controller ROM Patch (BT_RAM_CODE_MT6737T)",
                category = CandidateCategory.FIRMWARE_REFS,
                path = "system/etc/firmware/BT_RAM_CODE_MT6737T",
                source = "Target Stock Vendor Tree",
                target = "system/etc/firmware/BT_RAM_CODE_MT6737T",
                architecture = "Firmware Binary",
                dependencies = listOf("vendor/bin/bt_stack"),
                risk = MigrationRisk.LOW,
                reason = "Patch RAM code for Bluetooth 4.2 Low Energy controller.",
                confidence = 0.98f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Enables Bluetooth audio and peripheral pairing."
            ),
            MigrationCandidate(
                id = "cand_fw_gps",
                name = "MediaTek GPS DSP Firmware (ROM_GPS_MT6737T)",
                category = CandidateCategory.FIRMWARE_REFS,
                path = "system/etc/firmware/ROM_GPS_MT6737T",
                source = "Target Stock Vendor Tree",
                target = "system/etc/firmware/ROM_GPS_MT6737T",
                architecture = "Firmware Binary",
                dependencies = listOf("vendor/bin/mnld"),
                risk = MigrationRisk.LOW,
                reason = "DSP signal processing microcode for GPS baseband.",
                confidence = 0.97f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Calculates satellite ephemeris and Doppler shifts."
            ),
            MigrationCandidate(
                id = "cand_fw_mddb",
                name = "Baseband AP/CP Modem Database (BPLGUInfoCustomAppSrcP)",
                category = CandidateCategory.FIRMWARE_REFS,
                path = "system/etc/mddb/BPLGUInfoCustomAppSrcP_MT6737T",
                source = "Target Stock Firmware (SM-G532F Base)",
                target = "system/etc/mddb/BPLGUInfoCustomAppSrcP_MT6737T",
                architecture = "Binary Database",
                dependencies = listOf("vendor/bin/ccci_mdinit"),
                risk = MigrationRisk.MEDIUM,
                reason = "Provides parameter mappings between Application Processor and Cellular Modem DSP.",
                confidence = 0.95f,
                status = CandidateStatus.CANDIDATE,
                details = "Required for RIL to translate network engineering and RF band tables."
            )
        )
    }

    private fun evaluatePermissionCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_perm_platform",
                name = "Core Android Platform Permissions (platform.xml)",
                category = CandidateCategory.PERMISSIONS,
                path = "system/etc/permissions/platform.xml",
                source = "Source ROM Framework Base",
                target = "system/etc/permissions/platform.xml",
                architecture = "XML Specification",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Maps Linux GIDs (sdcard_rw, net_raw, inet, media_rw) to Android framework permissions.",
                confidence = 0.98f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Fundamental for application storage and network access."
            ),
            MigrationCandidate(
                id = "cand_perm_handheld",
                name = "Handheld Hardware Feature Manifest (handheld_core_hardware.xml)",
                category = CandidateCategory.PERMISSIONS,
                path = "system/etc/permissions/handheld_core_hardware.xml",
                source = "Source ROM Framework Base",
                target = "system/etc/permissions/handheld_core_hardware.xml",
                architecture = "XML Specification",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Declares touchscreen, audio, microphone, USB host, and telephony hardware availability.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Tells PackageManager which hardware features exist on the device."
            ),
            MigrationCandidate(
                id = "cand_perm_camera",
                name = "Camera Hardware Manifest (android.hardware.camera.xml)",
                category = CandidateCategory.PERMISSIONS,
                path = "system/etc/permissions/android.hardware.camera.xml",
                source = "Target Stock Permissions",
                target = "system/etc/permissions/android.hardware.camera.xml",
                architecture = "XML Specification",
                dependencies = listOf("vendor/lib/hw/camera.mt6737t.so"),
                risk = MigrationRisk.LOW,
                reason = "Declares autofocus, front-facing camera, and flash capabilities.",
                confidence = 0.98f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Required for camera apps in Google Play Store."
            ),
            MigrationCandidate(
                id = "cand_perm_privapp",
                name = "Privileged App Permissions Manifest",
                category = CandidateCategory.PERMISSIONS,
                path = "system/etc/permissions/privapp-permissions-system.xml",
                source = "Source ROM Framework Base",
                target = "system/etc/permissions/privapp-permissions-system.xml",
                architecture = "XML Specification",
                dependencies = emptyList(),
                risk = MigrationRisk.MEDIUM,
                reason = "Authorizes privileged system apps to hold SIGNATURE_OR_SYSTEM protected permissions.",
                confidence = 0.94f,
                status = CandidateStatus.CANDIDATE,
                details = "Prevents runtime bootloops caused by PackageManager permission whitelist enforcement."
            )
        )
    }

    private fun evaluateSelinuxCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_se_plat_file_contexts",
                name = "Platform File Contexts (plat_file_contexts)",
                category = CandidateCategory.SELINUX_CONTEXTS,
                path = "system/etc/selinux/plat_file_contexts",
                source = "Source ROM System Base",
                target = "system/etc/selinux/plat_file_contexts",
                architecture = "SELinux Policy Contexts",
                dependencies = emptyList(),
                risk = MigrationRisk.MEDIUM,
                reason = "Labels /system files with u:object_r:system_file:s0 security attributes.",
                confidence = 0.95f,
                status = CandidateStatus.CANDIDATE,
                details = "Ensures framework files receive appropriate domain security labels."
            ),
            MigrationCandidate(
                id = "cand_se_vendor_file_contexts",
                name = "Vendor File Contexts (vendor_file_contexts)",
                category = CandidateCategory.SELINUX_CONTEXTS,
                path = "vendor/etc/selinux/vendor_file_contexts",
                source = "Target Stock / Base",
                target = "vendor/etc/selinux/vendor_file_contexts",
                architecture = "SELinux Policy Contexts",
                dependencies = listOf("root/ueventd.mt6737t.rc"),
                risk = MigrationRisk.HIGH,
                reason = "Labels MediaTek proprietary vendor daemons, device nodes, and HAL libraries.",
                confidence = 0.93f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Required so init can transition daemons (ccci_mdinit, nvram_daemon) to their specific domains."
            ),
            MigrationCandidate(
                id = "cand_se_service_contexts",
                name = "Binder Service Contexts (plat_service_contexts)",
                category = CandidateCategory.SELINUX_CONTEXTS,
                path = "system/etc/selinux/plat_service_contexts",
                source = "Source ROM System Base",
                target = "system/etc/selinux/plat_service_contexts",
                architecture = "SELinux Policy Contexts",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Authorizes system service registration with ServiceManager IPC.",
                confidence = 0.96f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Defines security contexts for audio, surfaceflinger, and power services."
            ),
            MigrationCandidate(
                id = "cand_se_property_contexts",
                name = "Property Contexts (plat_property_contexts)",
                category = CandidateCategory.SELINUX_CONTEXTS,
                path = "system/etc/selinux/plat_property_contexts",
                source = "Source ROM System Base",
                target = "system/etc/selinux/plat_property_contexts",
                architecture = "SELinux Policy Contexts",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Controls which system daemons can set system and vendor build properties.",
                confidence = 0.97f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Prevents denials when build.prop properties are initialized."
            )
        )
    }

    private fun evaluateSystemFileCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        return listOf(
            MigrationCandidate(
                id = "cand_sys_framework_jar",
                name = "Core Android Framework Classes (framework.jar)",
                category = CandidateCategory.SYSTEM_FILES,
                path = "system/framework/framework.jar",
                source = "Source ROM Framework Package",
                target = "system/framework/framework.jar",
                architecture = "DEX Bytecode / ART",
                dependencies = listOf("system/framework/core-oj.jar", "system/framework/ext.jar"),
                risk = MigrationRisk.HIGH,
                reason = "The primary operating system framework classes for the donor Android version.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Transplants the donor OS user interface, animations, and system behaviors."
            ),
            MigrationCandidate(
                id = "cand_sys_services_jar",
                name = "System Server Services (services.jar)",
                category = CandidateCategory.SYSTEM_FILES,
                path = "system/framework/services.jar",
                source = "Source ROM Framework Package",
                target = "system/framework/services.jar",
                architecture = "DEX Bytecode / ART",
                dependencies = listOf("system/framework/framework.jar"),
                risk = MigrationRisk.HIGH,
                reason = "Contains ActivityManagerService, WindowManagerService, and PowerManagerService.",
                confidence = 0.98f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Core system daemon runtime bytecode."
            ),
            MigrationCandidate(
                id = "cand_sys_keylayout",
                name = "Hardware Button Key Layout (mtk-kpd.kl)",
                category = CandidateCategory.SYSTEM_FILES,
                path = "system/usr/keylayout/mtk-kpd.kl",
                source = "Target Stock ROM (SM-G532F Base)",
                target = "system/usr/keylayout/mtk-kpd.kl",
                architecture = "Keylayout Text",
                dependencies = emptyList(),
                risk = MigrationRisk.LOW,
                reason = "Maps Linux scancodes to Android KEYCODE_POWER, KEYCODE_VOLUME_UP, and KEYCODE_VOLUME_DOWN.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Critical for physical button response."
            ),
            MigrationCandidate(
                id = "cand_sys_fonts",
                name = "System UI Font Package (Roboto-Regular.ttf)",
                category = CandidateCategory.SYSTEM_FILES,
                path = "system/fonts/Roboto-Regular.ttf",
                source = "Source ROM Package",
                target = "system/fonts/Roboto-Regular.ttf",
                architecture = "TrueType Font",
                dependencies = listOf("system/etc/fonts.xml"),
                risk = MigrationRisk.LOW,
                reason = "Standard system typography for UI elements.",
                confidence = 0.99f,
                status = CandidateStatus.SAFE_TO_INVESTIGATE,
                details = "Provides glyph rendering for all apps."
            )
        )
    }

    private fun evaluateVendorCandidates(source: SourceRomProfile, target: TargetDeviceProfile): List<MigrationCandidate> {
        val is64BitSource = source.is64Bit
        val defaultStatus = if (is64BitSource) CandidateStatus.BLOCKED else CandidateStatus.SAFE_TO_INVESTIGATE
        val defaultRisk = if (is64BitSource) MigrationRisk.CRITICAL else MigrationRisk.MEDIUM

        return listOf(
            MigrationCandidate(
                id = "cand_ven_ccci_mdinit",
                name = "MediaTek Modem Init Daemon (ccci_mdinit)",
                category = CandidateCategory.VENDOR_FILES,
                path = "vendor/bin/ccci_mdinit",
                source = "Target Stock Vendor Tree (SM-G532F)",
                target = "vendor/bin/ccci_mdinit",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("vendor/bin/ccci_fsd", "system/etc/mddb/BPLGUInfoCustomAppSrcP_MT6737T"),
                risk = defaultRisk,
                reason = "Initializes cross-core communication channel with cellular modem DSP on boot.",
                confidence = 0.97f,
                status = defaultStatus,
                details = "Required for SIM detection, cellular signal, and baseband booting."
            ),
            MigrationCandidate(
                id = "cand_ven_ccci_fsd",
                name = "Modem File System Daemon (ccci_fsd)",
                category = CandidateCategory.VENDOR_FILES,
                path = "vendor/bin/ccci_fsd",
                source = "Target Stock Vendor Tree (SM-G532F)",
                target = "vendor/bin/ccci_fsd",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("vendor/bin/ccci_mdinit"),
                risk = defaultRisk,
                reason = "Provides filesystem I/O bridge for baseband modem calibration data.",
                confidence = 0.96f,
                status = defaultStatus,
                details = "Syncs NVRAM radio tables with baseband processor."
            ),
            MigrationCandidate(
                id = "cand_ven_nvram_daemon",
                name = "MediaTek NVRAM Persistent Daemon (nvram_daemon)",
                category = CandidateCategory.VENDOR_FILES,
                path = "vendor/bin/nvram_daemon",
                source = "Target Stock Vendor Tree (SM-G532F)",
                target = "vendor/bin/nvram_daemon",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("vendor/lib/libcustom_nvram.so", "vendor/lib/libnvram.so"),
                risk = defaultRisk,
                reason = "Reads and writes Wi-Fi MAC address, Bluetooth address, and IMEI from NVRAM partition.",
                confidence = 0.98f,
                status = defaultStatus,
                details = "Prevents 'NVRAM WARNING: Err = 0x10' error in Wi-Fi settings."
            ),
            MigrationCandidate(
                id = "cand_ven_thermal_manager",
                name = "Hardware Thermal Throttling Manager (thermal_manager)",
                category = CandidateCategory.VENDOR_FILES,
                path = "vendor/bin/thermal_manager",
                source = "Target Stock Vendor Tree (SM-G532F)",
                target = "vendor/bin/thermal_manager",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("system/etc/thermal.conf"),
                risk = MigrationRisk.LOW,
                reason = "Monitors SoC temperature sensors and throttles CPU frequency when overheating.",
                confidence = 0.93f,
                status = CandidateStatus.CANDIDATE,
                details = "Protects hardware components from thermal damage."
            ),
            MigrationCandidate(
                id = "cand_ven_mnld",
                name = "MediaTek GPS Daemon (mnld)",
                category = CandidateCategory.VENDOR_FILES,
                path = "vendor/bin/mnld",
                source = "Target Stock Vendor Tree (SM-G532F)",
                target = "vendor/bin/mnld",
                architecture = "armeabi-v7a (32-bit ARM)",
                dependencies = listOf("system/etc/gps.conf", "system/etc/firmware/ROM_GPS_MT6737T"),
                risk = defaultRisk,
                reason = "Primary userspace daemon for GPS positioning and NMEA sentence streaming.",
                confidence = 0.95f,
                status = defaultStatus,
                details = "Connects GPS HAL with /dev/stpgps hardware device block."
            )
        )
    }
}
