package com.example.ui.analyzer.kernel.engine

import com.example.ui.analyzer.kernel.model.*

object KernelSubsystemDetector {

    private val MTK_KEYWORDS = listOf("mtk", "mt6737", "mt6735", "mt6580", "mtk_", "mt_", "mediatek", "mali_mtk", "mtk_disp", "mtk_wcn", "mtk_ccci", "mtk_auxadc")
    private val STORAGE_KEYWORDS = listOf("mmc", "sdcard", "emmc", "sdio", "block", "ufs", "scsi", "sda", "sdb", "mmcblk", "ext4", "f2fs")
    private val DISPLAY_KEYWORDS = listOf("drm", "fb", "framebuffer", "panel", "dsi", "display", "disp", "backlight", "composer", "mdss", "mipi")
    private val GPU_KEYWORDS = listOf("mali", "kgsl", "adreno", "gpu", "render", "gallium", "gles", "pvr")
    private val WIFI_KEYWORDS = listOf("wlan", "wifi", "cfg80211", "mac80211", "wcn", "bcmdhd", "wpa_supplicant", "nl80211", "dhcpcd")
    private val BT_KEYWORDS = listOf("bluetooth", "hci", "rfcomm", "bt", "stp_bt", "bt_drv", "btm")
    private val AUDIO_KEYWORDS = listOf("sound", "alsa", "audio", "codec", "snd", "pcm", "afe", "audioserver", "tinyalsa")
    private val POWER_KEYWORDS = listOf("pmic", "battery", "charger", "power", "regulator", "spm", "gauge", "fuelgauge", "bq24", "mtk_battery")
    private val CAMERA_KEYWORDS = listOf("camera", "cam", "sensor", "isp", "v4l2", "videobuf", "cameraserver", "gc2365", "ov8856")
    private val USB_KEYWORDS = listOf("usb", "dwc3", "otg", "gadget", "ehci", "xhci", "phy_mtk_tusb", "musb")
    private val TOUCH_KEYWORDS = listOf("touchscreen", "touch", "ts", "goodix", "ft5x06", "gt9xx", "input", "evdev", "synaptics")
    private val RIL_KEYWORDS = listOf("ccci", "modem", "ril", "rild", "net_agent", "rmnet", "qmi")
    private val MEMORY_KEYWORDS = listOf("slub", "slab", "page_alloc", "vmalloc", "kmem", "swap", "zram", "oom", "out of memory", "mm_alloc")
    private val SCHEDULER_KEYWORDS = listOf("sched", "rcu", "watchdog", "workqueue", "kthread", "smp", "cpufreq", "governor", "softirq")
    private val FS_KEYWORDS = listOf("vfs", "inode", "dcache", "ext4_", "f2fs_", "sysfs", "procfs", "mount", "umount")
    private val SELINUX_KEYWORDS = listOf("avc:", "selinux", "security_compute", "policy", "scontext=", "tcontext=")

    fun detectSubsystems(
        textTokens: List<String>
    ): List<SuspectedSubsystem> {
        val detected = mutableListOf<SuspectedSubsystem>()

        checkCategory(textTokens, MTK_KEYWORDS, KernelSubsystemType.MEDIATEK_SOC, detected)
        checkCategory(textTokens, STORAGE_KEYWORDS, KernelSubsystemType.STORAGE_EMMC, detected)
        checkCategory(textTokens, DISPLAY_KEYWORDS, KernelSubsystemType.DISPLAY_DRM, detected)
        checkCategory(textTokens, GPU_KEYWORDS, KernelSubsystemType.GPU_MALI, detected)
        checkCategory(textTokens, WIFI_KEYWORDS, KernelSubsystemType.WIRELESS_WIFI, detected)
        checkCategory(textTokens, BT_KEYWORDS, KernelSubsystemType.WIRELESS_BT, detected)
        checkCategory(textTokens, AUDIO_KEYWORDS, KernelSubsystemType.AUDIO_ALSA, detected)
        checkCategory(textTokens, POWER_KEYWORDS, KernelSubsystemType.POWER_PMIC, detected)
        checkCategory(textTokens, CAMERA_KEYWORDS, KernelSubsystemType.CAMERA_V4L2, detected)
        checkCategory(textTokens, USB_KEYWORDS, KernelSubsystemType.USB_OTG, detected)
        checkCategory(textTokens, TOUCH_KEYWORDS, KernelSubsystemType.INPUT_TOUCH, detected)
        checkCategory(textTokens, RIL_KEYWORDS, KernelSubsystemType.CELLULAR_RIL, detected)
        checkCategory(textTokens, MEMORY_KEYWORDS, KernelSubsystemType.MEMORY_SUBSYSTEM, detected)
        checkCategory(textTokens, SCHEDULER_KEYWORDS, KernelSubsystemType.SCHEDULER_CORE, detected)
        checkCategory(textTokens, FS_KEYWORDS, KernelSubsystemType.FILESYSTEM_VFS, detected)
        checkCategory(textTokens, SELINUX_KEYWORDS, KernelSubsystemType.SECURITY_SELINUX, detected)

        return detected.sortedByDescending {
            when (it.confidence) {
                AnalysisConfidence.HIGH -> 3
                AnalysisConfidence.MEDIUM -> 2
                AnalysisConfidence.LOW -> 1
            }
        }
    }

    private fun checkCategory(
        tokens: List<String>,
        keywords: List<String>,
        type: KernelSubsystemType,
        destination: MutableList<SuspectedSubsystem>
    ) {
        val matched = mutableListOf<String>()
        for (token in tokens) {
            val lower = token.lowercase()
            for (kw in keywords) {
                if (lower.contains(kw) && !matched.contains(kw)) {
                    matched.add(kw)
                }
            }
        }

        if (matched.isNotEmpty()) {
            val confidence = when {
                matched.size >= 3 -> AnalysisConfidence.HIGH
                matched.size == 2 -> AnalysisConfidence.MEDIUM
                else -> AnalysisConfidence.LOW
            }
            destination.add(SuspectedSubsystem(type, matched, confidence))
        }
    }

    fun analyzeRootCause(
        type: KernelCrashType,
        panicReason: String?,
        faultAddress: String?,
        pcSymbol: String?,
        stackSymbols: List<String>,
        processName: String?,
        contextLines: List<String>
    ): KernelRootCauseAnalysis {
        val evidence = mutableListOf<String>()
        val possibleCauses = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // 1. Facts & Evidence
        val fact = when (type) {
            KernelCrashType.KERNEL_PANIC -> "Kernel Panic: ${panicReason ?: "System halted"}"
            KernelCrashType.OOPS -> "Kernel Oops: Internal kernel fault executing at ${pcSymbol ?: "unknown address"}"
            KernelCrashType.NULL_POINTER_DEREFERENCE -> "NULL pointer dereference at fault address ${faultAddress ?: "0x0"}"
            KernelCrashType.PAGE_FAULT -> "Kernel paging request failed at address ${faultAddress ?: "unknown"}"
            KernelCrashType.WATCHDOG_TIMEOUT -> "Watchdog lockup detected: CPU or thread execution stalled"
            KernelCrashType.SOFT_LOCKUP -> "Soft lockup: Kernel thread occupied CPU without scheduling"
            KernelCrashType.HARD_LOCKUP -> "Hard lockup: CPU unresponsive to interrupts"
            KernelCrashType.HUNG_TASK -> "Hung task: Kernel task blocked in D-state waiting on resource"
            KernelCrashType.RCU_STALL -> "RCU Stall: Read-side critical section or grace period stalled"
            KernelCrashType.USERSPACE_FATAL -> "Android core userspace crash: ${processName ?: "system service"} died"
            else -> "${type.displayName}: $panicReason"
        }

        if (pcSymbol != null) {
            evidence.add("Crashing Instruction/Function (PC): $pcSymbol")
        }
        if (faultAddress != null) {
            evidence.add("Fault Address: $faultAddress")
        }
        if (processName != null) {
            evidence.add("Active Process / Thread: $processName")
        }

        val allTokens = mutableListOf<String>()
        if (pcSymbol != null) allTokens.add(pcSymbol)
        allTokens.addAll(stackSymbols)
        if (processName != null) allTokens.add(processName)
        if (panicReason != null) allTokens.add(panicReason)
        allTokens.addAll(contextLines.take(15))

        val suspectedSubsystems = detectSubsystems(allTokens)
        val topSubsystem = suspectedSubsystems.firstOrNull()

        if (topSubsystem != null) {
            evidence.add("Subsystem evidence keywords: ${topSubsystem.matchedKeywords.joinToString(", ")} (${topSubsystem.type.displayName})")
        }

        // 2. Possible Causes & Confidence
        var confidence = AnalysisConfidence.LOW

        when (type) {
            KernelCrashType.NULL_POINTER_DEREFERENCE -> {
                confidence = if (pcSymbol != null) AnalysisConfidence.HIGH else AnalysisConfidence.MEDIUM
                possibleCauses.add("Unchecked pointer returned NULL from driver initialization or hardware access.")
                possibleCauses.add("Missing device tree (DTS/DTB) node or unmapped memory register base.")
                recommendations.add("Check NULL checks in '$pcSymbol' and verify corresponding DTB bindings.")
            }
            KernelCrashType.PAGE_FAULT, KernelCrashType.DATA_ABORT -> {
                confidence = AnalysisConfidence.MEDIUM
                possibleCauses.add("Accessing unmapped or freed kernel memory (use-after-free or bad pointer arithmetic).")
                possibleCauses.add("Incompatible kernel module ABI or mismatched struct layout in vendor drivers.")
                recommendations.add("Verify kernel memory mapping (ioremap) and struct layout in custom kernel modules.")
            }
            KernelCrashType.WATCHDOG_TIMEOUT, KernelCrashType.SOFT_LOCKUP, KernelCrashType.HARD_LOCKUP -> {
                confidence = AnalysisConfidence.MEDIUM
                possibleCauses.add("Deadlock in spinlock/mutex or infinite loop in interrupt context.")
                possibleCauses.add("Hardware bus lockup (I2C/SPI/eMMC/GPU) waiting indefinitely for hardware ready signal.")
                recommendations.add("Inspect lock acquisition order in '${stackSymbols.take(3).joinToString(", ")}' and check bus timeouts.")
            }
            KernelCrashType.HUNG_TASK -> {
                confidence = AnalysisConfidence.MEDIUM
                possibleCauses.add("Thread blocked on disk I/O, eMMC storage sleep, or unresponsive hardware regulator.")
                recommendations.add("Inspect storage driver and I/O scheduler state.")
            }
            KernelCrashType.KERNEL_PANIC -> {
                if (panicReason?.contains("Fatal exception", ignoreCase = true) == true) {
                    confidence = AnalysisConfidence.HIGH
                    possibleCauses.add("Kernel panic triggered directly by fatal exception in interrupt or core driver.")
                } else if (panicReason?.contains("VFS", ignoreCase = true) == true || panicReason?.contains("mount", ignoreCase = true) == true) {
                    confidence = AnalysisConfidence.HIGH
                    possibleCauses.add("Rootfs/partition mount failure or missing ramdisk init binary.")
                    recommendations.add("Verify fstab mount paths, partition formats (ext4/f2fs/erofs), and init.rc entry point.")
                } else {
                    confidence = if (topSubsystem != null) AnalysisConfidence.MEDIUM else AnalysisConfidence.LOW
                    possibleCauses.add("Kernel assertion failure or deliberate panic invocation from '${topSubsystem?.type?.displayName ?: "driver"}'.")
                }
            }
            KernelCrashType.USERSPACE_FATAL -> {
                confidence = AnalysisConfidence.HIGH
                possibleCauses.add("Android service '${processName ?: "core"}' crashed due to missing HAL service, SELinux denial, or ABI mismatch.")
                recommendations.add("Inspect logcat tombstone, SELinux audit logs, and matching 32-bit/64-bit HAL libraries.")
            }
            else -> {
                possibleCauses.add("Kernel state corrupted or unhandled driver exception.")
            }
        }

        return KernelRootCauseAnalysis(
            fact = fact,
            evidenceList = evidence,
            suspectedSubsystem = topSubsystem,
            possibleCauses = possibleCauses,
            confidence = confidence,
            recommendedActions = recommendations
        )
    }
}
