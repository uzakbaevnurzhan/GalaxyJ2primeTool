package com.example.ui.analyzer.system.models

import com.example.data.model.ReportFormat
import kotlinx.serialization.Serializable

enum class EvidenceSource(val label: String) {
    LIVE_DEVICE("Live Device"),
    ROOT("Root Privileged"),
    ANDROID_API("Android API"),
    SYSFS("Sysfs (/sys)"),
    PROCFS("Procfs (/proc)"),
    GETPROP("Getprop"),
    LOGCAT("Logcat"),
    DMESG("Dmesg / Kernel"),
    PSTORE("Pstore / Ram-Oops"),
    PARTITION_TABLE("Partition Table"),
    DEVICE_TREE("Device Tree / DTB"),
    HAL("HAL Manifest / VINTF"),
    ELF("ELF Native Inspection"),
    PROJECT("Project Workspace"),
    IMPORTED_DATA("Imported Archive / Dump")
}

enum class ComponentStatus(val label: String) {
    WORKING("WORKING"),
    FAILED("FAILED"),
    PARTIAL("PARTIAL"),
    UNKNOWN("UNKNOWN"),
    NOT_TESTED("NOT TESTED"),
    UNAVAILABLE("UNAVAILABLE")
}

enum class SystemSeverity(val label: String, val weight: Int) {
    INFO("INFO", 1),
    WARNING("WARNING", 2),
    ERROR("ERROR", 3),
    CRITICAL("CRITICAL", 4),
    BLOCKER("BLOCKER", 5)
}

enum class SystemHealthStatus(val label: String) {
    HEALTHY("HEALTHY"),
    HEALTHY_WITH_WARNINGS("HEALTHY WITH WARNINGS"),
    DEGRADED("DEGRADED"),
    CRITICAL("CRITICAL"),
    BLOCKED("BLOCKED"),
    INSUFFICIENT_DATA("INSUFFICIENT DATA")
}

enum class AnalysisMode(val title: String, val description: String) {
    BASIC("Basic", "Core checks, main error logs & basic hardware presence"),
    DEEP("Deep", "Full subsystem audit, HAL, ELF dependencies, dmesg & SELinux"),
    EXPERT("Expert", "Raw evidence, symbol tracing, partition flags, DTB nodes & root cause graphs")
}

enum class ErrorSubsystem(val label: String) {
    BOOT("BOOT"),
    KERNEL("KERNEL"),
    INIT("INIT"),
    STORAGE("STORAGE"),
    SELINUX("SELINUX"),
    SYSTEM("SYSTEM"),
    FRAMEWORK("FRAMEWORK"),
    SYSTEM_SERVER("SYSTEM_SERVER"),
    VENDOR("VENDOR"),
    HAL("HAL"),
    RIL("RIL"),
    CAMERA("CAMERA"),
    AUDIO("AUDIO"),
    WIFI("WIFI"),
    BLUETOOTH("BLUETOOTH"),
    SENSOR("SENSOR"),
    USB("USB"),
    GRAPHICS("GRAPHICS"),
    MEMORY("MEMORY"),
    FILESYSTEM("FILESYSTEM"),
    OTHER("OTHER")
}

@Serializable
data class AuditEvidenceItem(
    val field: String,
    val value: String,
    val status: ComponentStatus = ComponentStatus.UNKNOWN,
    val source: EvidenceSource = EvidenceSource.ANDROID_API,
    val sourcePath: String = "",
    val evidence: String = "",
    val confidence: Int = 100, // 0 - 100%
    val isConflict: Boolean = false,
    val conflictNotes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SystemErrorItem(
    val id: String,
    val subsystem: ErrorSubsystem,
    val severity: SystemSeverity,
    val message: String,
    val component: String,
    val stage: String,
    val sourceFile: String? = null,
    val rawEvidence: String = "",
    val repeatCount: Int = 1,
    val relatedTool: String = "error_center",
    val suggestedAction: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class RootCauseCandidate(
    val id: String,
    val problem: String,
    val evidence: String,
    val component: String,
    val causeChain: List<String>, // Step 1 -> Step 2 -> Step 3
    val relatedErrors: List<String>,
    val severity: SystemSeverity,
    val confidence: Int, // 0 - 100%
    val nextTool: String,
    val nextAction: String
)

@Serializable
data class SystemComponentMatrixItem(
    val componentKey: String,
    val componentName: String,
    val category: String,
    val status: ComponentStatus,
    val primaryError: String? = null,
    val source: EvidenceSource,
    val evidence: String,
    val confidence: Int = 100,
    val lastConfirmedStage: String? = null,
    val relatedToolRoute: String? = null,
    val details: List<AuditEvidenceItem> = emptyList()
)

@Serializable
data class FixSuggestionItem(
    val id: String,
    val problem: String,
    val evidence: String,
    val nextTool: String,
    val nextToolRoute: String,
    val nextAction: String,
    val priority: SystemSeverity = SystemSeverity.WARNING
)

@Serializable
data class AnalysisCapabilities(
    val rootAvailable: Boolean,
    val usbConnected: Boolean,
    val adbEnabled: Boolean,
    val liveDeviceAvailable: Boolean,
    val projectAvailable: Boolean,
    val storageReadAvailable: Boolean,
    val procAvailable: Boolean,
    val sysAvailable: Boolean,
    val partitionsAvailable: Boolean,
    val pstoreAvailable: Boolean,
    val cameraPermission: Boolean,
    val audioPermission: Boolean
)

@Serializable
data class AndroidVersionAudit(
    val liveRelease: String,
    val liveSdk: Int,
    val getpropRelease: String,
    val buildPropRelease: String?,
    val projectRelease: String?,
    val hasConflict: Boolean,
    val conflictSummary: String?,
    val isTreble: Boolean,
    val trebleDetails: String
)

@Serializable
data class SecurityAudit(
    val rootStatus: ComponentStatus,
    val rootEvidence: String,
    val selinuxMode: String,
    val selinuxStatus: ComponentStatus,
    val avbStatus: ComponentStatus,
    val verifiedBootState: String,
    val encryptionState: String,
    val debuggable: Boolean,
    val adbSecurity: String,
    val buildTags: String
)

@Serializable
data class CpuAbiAudit(
    val cpuArchitecture: String,
    val kernelArchitecture: String,
    val systemAbi: String,
    val supportedAbis: List<String>,
    val vendorAbi: String,
    val halAbi: String,
    val elfAbi: String,
    val hasAbiMismatch: Boolean,
    val mismatchDetails: String?
)

@Serializable
data class RamAudit(
    val totalMemKb: Long,
    val availMemKb: Long,
    val freeMemKb: Long,
    val buffersKb: Long,
    val cachedKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val zramSizeKb: Long,
    val ramHealthStatus: ComponentStatus
)

@Serializable
data class StorageAudit(
    val internalTotalBytes: Long,
    val internalFreeBytes: Long,
    val dataTotalBytes: Long,
    val dataFreeBytes: Long,
    val mountsList: List<String>,
    val storageHealthStatus: ComponentStatus
)

@Serializable
data class PartitionAuditItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val filesystem: String,
    val mountPoint: String?,
    val isMounted: Boolean,
    val isReadOnly: Boolean,
    val flags: String,
    val status: ComponentStatus,
    val anomaly: String? = null
)

@Serializable
data class KernelAudit(
    val linuxVersion: String,
    val compiler: String,
    val architecture: String,
    val cmdline: String,
    val hasConfigGz: Boolean,
    val loadedModulesCount: Int,
    val kernelStatus: ComponentStatus,
    val evidence: String
)

@Serializable
data class BootAudit(
    val isBootImgAvailable: Boolean,
    val headerVersion: String?,
    val kernelSize: Long?,
    val ramdiskSize: Long?,
    val dtbSize: Long?,
    val cmdline: String?,
    val architecture: String?,
    val status: ComponentStatus,
    val evidence: String
)

@Serializable
data class DtbAudit(
    val isDtbAvailable: Boolean,
    val compatibleStrings: List<String>,
    val detectedSoC: String?,
    val detectedNodes: List<String>,
    val missingCriticalNodes: List<String>,
    val status: ComponentStatus,
    val evidence: String
)

@Serializable
data class SystemVendorTrebleAudit(
    val hasSystemPartition: Boolean,
    val hasVendorPartition: Boolean,
    val isTreble: Boolean,
    val vintfAvailable: Boolean,
    val halLayout: String,
    val systemBinariesCount: Int,
    val systemLibsCount: Int,
    val vendorBinariesCount: Int,
    val vendorLibsCount: Int,
    val status: ComponentStatus,
    val notes: String
)

@Serializable
data class ElfAudit(
    val scannedBinariesCount: Int,
    val arm32BinariesCount: Int,
    val arm64BinariesCount: Int,
    val missingLibrariesList: List<String>,
    val wrongClassLibrariesList: List<String>,
    val linkageErrorsList: List<String>,
    val status: ComponentStatus,
    val evidence: String
)

@Serializable
data class HardwareSubsystemAudit(
    val audioStatus: ComponentStatus,
    val audioEvidence: String,
    val cameraStatus: ComponentStatus,
    val cameraIds: List<String>,
    val cameraEvidence: String,
    val wifiStatus: ComponentStatus,
    val wifiEvidence: String,
    val bluetoothStatus: ComponentStatus,
    val bluetoothEvidence: String,
    val sensorsStatus: ComponentStatus,
    val sensorsList: List<String>,
    val sensorsEvidence: String,
    val displayStatus: ComponentStatus,
    val displayResolution: String,
    val displayFps: Float,
    val displayEvidence: String,
    val usbStatus: ComponentStatus,
    val usbMode: String,
    val usbEvidence: String,
    val batteryStatus: ComponentStatus,
    val batteryLevel: Int,
    val batteryVoltageMv: Int,
    val batteryTempC: Float,
    val batteryHealth: String,
    val batteryEvidence: String
)

@Serializable
data class SelinuxAudit(
    val currentMode: String,
    val status: ComponentStatus,
    val totalDenialsCount: Int,
    val groupedDenials: Map<String, Int>, // key = "source->target:class(perm)" -> count
    val topDenialsList: List<String>,
    val evidence: String
)

@Serializable
data class LogSubsystemAudit(
    val logcatLinesRead: Int,
    val dmesgLinesRead: Int,
    val pstoreAvailable: Boolean,
    val lastKmsgAvailable: Boolean,
    val fatalSignalsFound: List<String>,
    val kernelPanicsFound: List<String>,
    val crashesFound: List<String>,
    val anrsFound: List<String>,
    val status: ComponentStatus
)

@Serializable
data class FullSystemAnalysisResult(
    val id: String,
    val timestamp: Long,
    val appVersion: String = "Beta 3",
    val toolTitle: String = "Galaxy J2 Prime Tool — Full System Analyzer",
    val analysisMode: AnalysisMode,
    val capabilities: AnalysisCapabilities,
    val healthStatus: SystemHealthStatus,
    val lastConfirmedWorkingStage: String,
    val suspectedFailureStage: String?,
    val deviceSummary: Map<String, AuditEvidenceItem>,
    val androidVersionAudit: AndroidVersionAudit,
    val securityAudit: SecurityAudit,
    val cpuAbiAudit: CpuAbiAudit,
    val ramAudit: RamAudit,
    val storageAudit: StorageAudit,
    val partitionAudit: List<PartitionAuditItem>,
    val kernelAudit: KernelAudit,
    val bootAudit: BootAudit,
    val dtbAudit: DtbAudit,
    val systemVendorTrebleAudit: SystemVendorTrebleAudit,
    val elfAudit: ElfAudit,
    val halComponentMatrix: List<SystemComponentMatrixItem>,
    val hardwareAudit: HardwareSubsystemAudit,
    val selinuxAudit: SelinuxAudit,
    val logAudit: LogSubsystemAudit,
    val deduplicatedErrors: List<SystemErrorItem>,
    val rootCauses: List<RootCauseCandidate>,
    val fixSuggestions: List<FixSuggestionItem>,
    val workingCount: Int,
    val failedCount: Int,
    val partialCount: Int,
    val unknownCount: Int,
    val totalErrorsCount: Int,
    val blockersCount: Int,
    val criticalCount: Int,
    val elapsedMillis: Long,
    val rawEvidenceLog: List<String> = emptyList()
)

@Serializable
data class AnalysisRegressionDiff(
    val oldSessionId: String,
    val newSessionId: String,
    val oldTimestamp: Long,
    val newTimestamp: Long,
    val fixedErrors: List<SystemErrorItem>,
    val newErrors: List<SystemErrorItem>,
    val persistentErrors: List<SystemErrorItem>,
    val regressedComponents: List<String>, // component name
    val improvedComponents: List<String>,
    val healthChangedFrom: SystemHealthStatus,
    val healthChangedTo: SystemHealthStatus
)
