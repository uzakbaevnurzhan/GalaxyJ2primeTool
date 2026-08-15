package com.example.ui.analyzer.kernel.model

enum class KernelSubsystemType(val displayName: String) {
    MEDIATEK_SOC("MediaTek SoC / Drivers"),
    STORAGE_EMMC("Storage (eMMC / MMC / SD)"),
    DISPLAY_DRM("Display / DRM / Framebuffer"),
    GPU_MALI("GPU (Mali / Graphics)"),
    WIRELESS_WIFI("Wireless LAN / WiFi"),
    WIRELESS_BT("Bluetooth"),
    AUDIO_ALSA("Audio / ALSA / Codec"),
    POWER_PMIC("Power Management / PMIC / Battery"),
    CAMERA_V4L2("Camera / V4L2 / ISP"),
    USB_OTG("USB / OTG / Gadget"),
    INPUT_TOUCH("Touchscreen / Keypad / Sensors"),
    CELLULAR_RIL("Cellular Modem / RIL"),
    MEMORY_SUBSYSTEM("Memory Management / MM / Slub"),
    SCHEDULER_CORE("Kernel Scheduler / Core / RCU"),
    FILESYSTEM_VFS("VFS / Filesystem / Ext4 / F2FS"),
    SECURITY_SELINUX("SELinux / Security Subsystem"),
    UNKNOWN("Generic / Unclassified Kernel Subsystem")
}

data class SuspectedSubsystem(
    val type: KernelSubsystemType,
    val matchedKeywords: List<String>,
    val confidence: AnalysisConfidence
)

data class KernelRootCauseAnalysis(
    val fact: String,
    val evidenceList: List<String>,
    val suspectedSubsystem: SuspectedSubsystem?,
    val possibleCauses: List<String>,
    val confidence: AnalysisConfidence,
    val recommendedActions: List<String>
)
