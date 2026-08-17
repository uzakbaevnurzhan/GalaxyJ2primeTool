package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

enum class HalFormat {
    HIDL,
    AIDL,
    PASSTHROUGH,
    LEGACY,
    UNKNOWN
}

enum class HalTransport {
    HWBINDER,
    PASSTHROUGH,
    AIDL_IPC,
    UNKNOWN
}

@Serializable
data class HalInterfaceInstance(
    val interfaceName: String,
    val instances: List<String> = emptyList()
)

@Serializable
data class HalEntry(
    val name: String,
    val format: HalFormat = HalFormat.HIDL,
    val transport: HalTransport = HalTransport.HWBINDER,
    val versions: List<String> = emptyList(),
    val interfaces: List<HalInterfaceInstance> = emptyList(),
    val sourceFile: String = "",
    val category: String = "General"
) {
    val type: String get() = transport.name
    val version: String get() = versions.joinToString()
}

@Serializable
data class HalInfo(
    val manifestsParsed: List<String> = emptyList(),
    val hals: List<HalEntry> = emptyList(),
    val categoryHals: Map<String, List<HalEntry>> = emptyMap(),
    val services: List<HalServiceMapItem> = emptyList(),
    val findings: List<EvidenceFinding> = emptyList(),
    val issues: List<VendorIssue> = emptyList()
)

@Serializable
data class HalServiceMapItem(
    val category: String,
    val halName: String,
    val version: String = "",
    val manifestStatus: StageStatus = StageStatus.UNKNOWN,
    val initServiceName: String? = null,
    val initServiceStatus: StageStatus = StageStatus.UNKNOWN,
    val binaryPath: String? = null,
    val binaryStatus: StageStatus = StageStatus.UNKNOWN,
    val binaryArchitecture: String? = null,
    val requiredLibraries: List<String> = emptyList(),
    val missingLibraries: List<String> = emptyList(),
    val libraryStatus: StageStatus = StageStatus.UNKNOWN,
    val kernelInterface: String? = null,
    val kernelInterfaceStatus: StageStatus = StageStatus.UNKNOWN,
    val status: HardwarePresenceStatus = HardwarePresenceStatus.UNKNOWN,
    val evidence: String = ""
) {
    val serviceName: String get() = initServiceName ?: halName
    val arch: String get() = binaryArchitecture ?: "unknown"
    val isDeclaredInVintf: Boolean get() = manifestStatus == StageStatus.FOUND
}
