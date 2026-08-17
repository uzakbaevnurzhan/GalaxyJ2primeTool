package com.example.ui.analyzer.kernel.studio.models

import kotlinx.serialization.Serializable

@Serializable
enum class KernelIssueType {
    ARCHITECTURE_MISMATCH,
    INVALID_KERNEL,
    INVALID_DTB,
    INVALID_CMDLINE,
    MISSING_CONFIG,
    DTB_CONFLICT,
    MODULE_MISMATCH,
    VERSION_CONFLICT,
    TRUNCATED_DATA,
    UNSUPPORTED_FORMAT
}

@Serializable
enum class KernelIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

@Serializable
data class KernelIssue(
    val type: KernelIssueType,
    val severity: KernelIssueSeverity,
    val message: String,
    val evidence: String,
    val source: String,
    val confidence: String = "HIGH" // HIGH, MEDIUM, LOW
)

@Serializable
enum class CmdlineCategory {
    CONSOLE,
    ROOT,
    ANDROIDBOOT,
    SELINUX,
    DEBUG,
    MEMORY,
    STORAGE,
    DISPLAY,
    BOOT,
    SECURITY,
    OTHER
}

@Serializable
data class KernelCmdlineEntry(
    val key: String,
    val value: String? = null,
    val raw: String,
    val category: CmdlineCategory = CmdlineCategory.OTHER,
    val source: String = "boot.img", // boot.img, /proc/cmdline, saved
    val description: String = ""
)

@Serializable
data class CmdlineComparisonItem(
    val key: String,
    val bootValue: String?,
    val liveValue: String?,
    val status: String // MATCH, DIFFERENCE, BOOT_ONLY, LIVE_ONLY
)

@Serializable
data class DtboEntry(
    val index: Int,
    val dtSize: Long,
    val dtOffset: Long,
    val id: Long,
    val rev: Long,
    val custom: List<Long> = emptyList(),
    val rootNode: KernelNode? = null
)

@Serializable
data class DtboInfo(
    val magic: String,
    val totalSize: Long,
    val headerSize: Long,
    val dtEntrySize: Long,
    val dtEntryCount: Int,
    val dtEntriesOffset: Long,
    val pageSize: Long,
    val version: Long,
    val entries: List<DtboEntry> = emptyList()
)

@Serializable
data class DtbHardwareNode(
    val category: String, // CPU, Memory, Storage, Display, Camera, Audio, USB, Wi-Fi, Bluetooth, Sensors, Power, Bus
    val name: String,
    val path: String,
    val compatible: List<String>,
    val status: String = "DETECTED" // DETECTED, NOT FOUND, UNKNOWN
)

@Serializable
data class PortingCheckSignal(
    val title: String,
    val category: String, // READY_SIGNAL, WARNING, UNKNOWN, BLOCKER
    val description: String,
    val evidence: String
)

@Serializable
data class KernelAnalysisResult(
    val kernelInfo: KernelInfo? = null,
    val configs: List<KernelConfig> = emptyList(),
    val cmdlineEntries: List<KernelCmdlineEntry> = emptyList(),
    val cmdlineComparisons: List<CmdlineComparisonItem> = emptyList(),
    val rootDtbNode: KernelNode? = null,
    val dtbCompatibleStrings: List<String> = emptyList(),
    val dtbHardwareNodes: List<DtbHardwareNode> = emptyList(),
    val dtboInfo: DtboInfo? = null,
    val issues: List<KernelIssue> = emptyList(),
    val portingSignals: List<PortingCheckSignal> = emptyList(),
    val isGalaxyJ2PrimeMatch: Boolean? = null,
    val j2PrimeNotes: List<String> = emptyList(),
    val liveDeviceImported: Boolean = false,
    val logMessages: List<String> = emptyList()
)
