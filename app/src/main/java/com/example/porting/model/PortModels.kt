package com.example.porting.model

import kotlinx.serialization.Serializable

/**
 * Port status severity indicators for compatibility and subsystem checks.
 */
@Serializable
enum class PortStatus {
    PASS,
    WARNING,
    ERROR,
    BLOCKER,
    UNKNOWN
}

/**
 * Standardized blocker and root cause severities.
 */
@Serializable
enum class BlockerSeverity {
    BLOCKER,
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO,
    UNKNOWN
}

/**
 * Explicit confidence assessment for diagnostic evidence.
 */
@Serializable
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

/**
 * Deterministic ROM port readiness status.
 * Prevents deceptive percentages and establishes transparent porting viability.
 */
@Serializable
enum class PortReadinessState(val label: String, val description: String) {
    READY("READY", "All architectural subsystems match and port is ready for compilation."),
    READY_WITH_WARNINGS("READY WITH WARNINGS", "Port is structurally viable with non-fatal adaptations required."),
    HIGH_RISK("HIGH RISK", "Critical subsystem discrepancies detected requiring manual engineering."),
    BLOCKED("BLOCKED", "Fatal blocker detected that physically or architecturally prevents execution."),
    INSUFFICIENT_DATA("INSUFFICIENT DATA", "Crucial metadata or partition dumps are missing; audit is inconclusive.")
}

/**
 * Distinct Root Cause categories for ROM porting failures.
 */
@Serializable
enum class RootCauseType {
    MISSING_LIBRARY,
    ABI_MISMATCH,
    KERNEL_MISMATCH,
    DTB_CONFLICT,
    RIL_DEPENDENCY_MISSING,
    HAL_DEPENDENCY_MISSING,
    SELINUX_CONFLICT,
    INVALID_PARTITION,
    INVALID_BOOT,
    MISSING_VENDOR,
    INIT_SERVICE_MISSING,
    UNKNOWN
}

/**
 * Provenance tracking for profile data sources.
 * Strictly separates LIVE DEVICE from imported/project files.
 */
@Serializable
enum class ProfileSourceType {
    LIVE_DEVICE,
    DEVICE_SNAPSHOT,
    IMPORTED_FILE,
    ROM_FOLDER,
    PROJECT,
    SINGLE_IMAGE,
    DAT_ARCHIVE,
    SAMSUNG_TAR,
    REFERENCE_PROFILE
}

/**
 * Concrete audit evidence backing any value or finding.
 */
@Serializable
data class PortEvidence(
    val key: String,
    val rawValue: String,
    val sourceDescription: String,
    val originFileOrCommand: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Evaluated property/feature comparison point.
 * Explicitly carries value, source, evidence, confidence and status.
 */
@Serializable
data class PortEvaluatedProperty(
    val key: String,
    val category: String,
    val label: String,
    val value: String,
    val source: ProfileSourceType,
    val evidence: PortEvidence,
    val confidence: Float, // 0.0f .. 1.0f (e.g. 0.95 = 95%)
    val status: PortStatus,
    val notes: String = ""
)

/**
 * Formal Blocker Model with strict provenance, confidence level, and actionable resolution.
 */
@Serializable
data class PortBlocker(
    val id: String,
    val rootCauseType: RootCauseType,
    val title: String,
    val description: String,
    val component: String,
    val severity: BlockerSeverity = BlockerSeverity.BLOCKER,
    val evidenceList: List<PortEvidence> = emptyList(),
    val primaryEvidence: PortEvidence,
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val source: ProfileSourceType,
    val relatedFiles: List<String> = emptyList(),
    val relatedAnalyzers: List<String> = emptyList(),
    val directBootFailureEvidence: String? = null,
    val recommendation: String,
    val fixStrategy: String? = null,
    val suggestedTool: String = "Port Tools"
)

/**
 * Non-fatal Warning Model for adaptations and subsystem shims.
 */
@Serializable
data class PortWarning(
    val id: String,
    val rootCauseType: RootCauseType,
    val title: String,
    val description: String,
    val component: String,
    val severity: BlockerSeverity = BlockerSeverity.MEDIUM,
    val evidenceList: List<PortEvidence> = emptyList(),
    val primaryEvidence: PortEvidence,
    val confidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val source: ProfileSourceType,
    val relatedFiles: List<String> = emptyList(),
    val relatedAnalyzers: List<String> = emptyList(),
    val recommendation: String,
    val fixStrategy: String? = null,
    val suggestedTool: String = "Port Tools"
)

/**
 * Evaluated Root Cause Candidate with direct causal mechanism and required tool.
 */
@Serializable
data class RootCauseCandidate(
    val id: String,
    val type: RootCauseType,
    val title: String,
    val failureMechanism: String,
    val component: String,
    val severity: BlockerSeverity,
    val confidence: ConfidenceLevel,
    val source: ProfileSourceType,
    val evidence: List<PortEvidence> = emptyList(),
    val relatedFiles: List<String> = emptyList(),
    val relatedAnalyzers: List<String> = emptyList(),
    val canCauseBootFailure: Boolean = false,
    val directBootFailureEvidence: String? = null, // ONLY set if direct proof exists!
    val recommendedFix: String,
    val targetTool: String,
    val nextAction: String
)

/**
 * Actionable Root Cause Triage recommendation: "WHAT SHOULD I FIX FIRST?"
 */
@Serializable
data class FixFirstRecommendation(
    val blockerId: String,
    val rootCauseType: RootCauseType,
    val problem: String,
    val evidence: String,
    val tool: String,
    val nextAction: String,
    val component: String,
    val severity: BlockerSeverity,
    val confidence: ConfidenceLevel
)

/**
 * Specific compatibility issue, blocker, or warning.
 */
@Serializable
data class PortIssue(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val status: PortStatus,
    val isBlocker: Boolean,
    val value: String,
    val source: ProfileSourceType,
    val evidence: PortEvidence,
    val confidence: Float,
    val recommendation: String,
    val fixStrategy: String? = null
)

/**
 * Explicit audited field with provenance, source origin, and confidence.
 */
@Serializable
data class SourceFieldAudit(
    val fieldKey: String,
    val label: String,
    val value: String,
    val sourceOrigin: String, // e.g. "build.prop: ro.build.version.release", "boot.img header", "ELF header"
    val confidence: Float, // 0.0f .. 1.0f
    val isUnknown: Boolean = false,
    val category: String = "General"
)

/**
 * Partition metadata extracted from donor ROM.
 */
@Serializable
data class PartitionInfo(
    val name: String, // "system", "vendor", "boot", "product", "odm", "super", "dtbo", "vbmeta"
    val fileName: String,
    val sizeBytes: Long,
    val format: String, // "ext4", "sparse_ext4", "dat", "dat_br", "raw", "erofs", "f2fs", "tar"
    val isSparse: Boolean = false,
    val isDatOrBr: Boolean = false,
    val mountPoint: String? = null,
    val sha256Digest: String? = null
)

/**
 * Deep boot image header metadata.
 */
@Serializable
data class BootImageDetails(
    val headerVersion: Int = 0, // v0, v1, v2, v3, v4
    val pageSize: Int = 2048,
    val kernelSizeBytes: Long = 0L,
    val kernelLoadAddr: Long = 0L,
    val ramdiskSizeBytes: Long = 0L,
    val ramdiskLoadAddr: Long = 0L,
    val secondSizeBytes: Long = 0L,
    val dtbSizeBytes: Long = 0L,
    val cmdline: String = "",
    val extraCmdline: String = "",
    val osVersion: String = "UNKNOWN", // e.g. "8.1.0" or "UNKNOWN"
    val osPatchLevel: String = "UNKNOWN",
    val boardName: String = "",
    val signatureVerified: Boolean = false
)

/**
 * Device Tree Blob (DTB / DTBO) metadata.
 */
@Serializable
data class DtbInfo(
    val hasDtb: Boolean = false,
    val hasDtbo: Boolean = false,
    val totalDtbSizeBytes: Long = 0L,
    val socCompatibleList: List<String> = emptyList(), // e.g. ["mediatek,mt6737t", "samsung,exynos7570"]
    val boardCompatibleList: List<String> = emptyList(),
    val nodeCount: Int = 0
)

/**
 * HAL services and subsystem implementations.
 */
@Serializable
data class HalSummary(
    val isTreble: Boolean = false,
    val vndkVersion: String = "None (Legacy non-Treble)",
    val hidlServices: List<String> = emptyList(),
    val legacyHals: List<String> = emptyList(),
    val cameraHalVersion: String = "UNKNOWN", // "HAL1 (Legacy)", "HAL3", "HIDL Camera@2.4"
    val audioHalVersion: String = "UNKNOWN", // "MediaTek ALSA", "Audio@4.0"
    val graphicsHalVersion: String = "UNKNOWN" // "Mali-T720 Gralloc 0.3", "Allocator@2.0"
)

/**
 * Telephony and RIL subsystem details.
 */
@Serializable
data class RilSummary(
    val rilImplementation: String = "UNKNOWN", // "Samsung SEC RIL (libsec-ril.so)", "MediaTek RIL (librilmtk.so)", "Qualcomm QMI"
    val telephonyLibraries: List<String> = emptyList(),
    val multiSimConfig: String = "UNKNOWN", // "dsds", "dsda", "ss", "none"
    val defaultNetwork: String = "UNKNOWN"
)

/**
 * SELinux policy, contexts, and domain configuration.
 */
@Serializable
data class SelinuxSummary(
    val defaultMode: String = "Enforcing", // "Permissive", "Enforcing", "Disabled"
    val hasPlatSepolicy: Boolean = false,
    val hasVendorSepolicy: Boolean = false,
    val fileContextsCount: Int = 0,
    val serviceContextsCount: Int = 0,
    val detectedPermissiveFlags: List<String> = emptyList()
)

/**
 * ELF native binary audit summary.
 */
@Serializable
data class ElfSummary(
    val totalBinariesScanned: Int = 0,
    val elf32Count: Int = 0,
    val elf64Count: Int = 0,
    val isPure32Bit: Boolean = true,
    val contains64BitBlobs: Boolean = false,
    val sample64BitBinaries: List<String> = emptyList(),
    val sample32BitBinaries: List<String> = emptyList(),
    val missingLibrariesDetected: List<String> = emptyList()
)

/**
 * Profile of the Source ROM being ported FROM.
 * Strictly distinct from Target Device.
 */
@Serializable
data class SourceRomProfile(
    val id: String,
    val name: String,
    val source: ProfileSourceType,
    // Key Hardware / Identifiers
    val model: String = "UNKNOWN",
    val device: String = "UNKNOWN",
    val brand: String = "UNKNOWN",
    val manufacturer: String = "UNKNOWN",
    // OS & Framework
    val androidVersion: String = "UNKNOWN", // "UNKNOWN" if not found. NEVER default to Android 11.
    val sdkInt: Int = -1, // -1 if UNKNOWN
    val securityPatch: String = "UNKNOWN",
    // Architecture & ABI
    val architecture: String = "UNKNOWN", // e.g. "armeabi-v7a (32-bit)", "arm64-v8a (64-bit)"
    val is64Bit: Boolean = false,
    val isTreble: Boolean = false,
    val isAb: Boolean = false, // A/B seamless vs A-only
    // Partitions & Storage
    val systemFsType: String = "UNKNOWN",
    val systemSizeBytes: Long = 0L,
    val bootImgSize: Long = 0L,
    val kernelCmdline: String = "",
    val targetChipset: String = "UNKNOWN", // e.g. "MT6737", "Exynos7570", "Generic AOSP"
    val buildDisplayId: String = "UNKNOWN",
    val fingerprint: String = "UNKNOWN",
    val selinuxMode: String = "Enforcing",
    // Deep Subsystem Analysis Structures
    val partitions: List<PartitionInfo> = emptyList(),
    val bootDetails: BootImageDetails = BootImageDetails(),
    val dtbDetails: DtbInfo = DtbInfo(),
    val halDetails: HalSummary = HalSummary(),
    val rilDetails: RilSummary = RilSummary(),
    val selinuxDetails: SelinuxSummary = SelinuxSummary(),
    val elfDetails: ElfSummary = ElfSummary(),
    // Audited Key-Value Fields with Confidence & Source Origin
    val auditedFields: List<SourceFieldAudit> = emptyList(),
    val halServices: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
    val evidenceList: List<PortEvidence> = emptyList(),
    val detectedIssues: List<String> = emptyList(),
    // Summary Breakdowns
    val sourceIssues: List<PortIssue> = emptyList(),
    val sourceWarnings: List<PortIssue> = emptyList(),
    val unknownFieldsList: List<SourceFieldAudit> = emptyList()
)

/**
 * Target issue, limitation, or hardware constraint detected during target device analysis.
 */
@Serializable
data class TargetIssue(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val status: PortStatus,
    val isBlocker: Boolean,
    val value: String,
    val source: ProfileSourceType,
    val evidence: PortEvidence,
    val confidence: Float = 0.95f,
    val recommendation: String,
    val fixStrategy: String? = null
)

/**
 * Explicit audited field on the Target Device with value, provenance, and confidence.
 */
@Serializable
data class TargetFieldAudit(
    val fieldKey: String,
    val label: String,
    val value: String,
    val source: ProfileSourceType,
    val sourceOrigin: String,
    val confidence: Float = 0.95f,
    val isUnknown: Boolean = false,
    val category: String = "General"
)

/**
 * Target device filesystem mount inspection details.
 */
@Serializable
data class TargetMountInfo(
    val mountPoint: String,
    val deviceBlock: String,
    val fsType: String,
    val flags: String
)

/**
 * Concrete live device probe evidence item.
 */
@Serializable
data class LiveDeviceEvidence(
    val key: String,
    val value: String,
    val source: ProfileSourceType,
    val originCommandOrFile: String,
    val confidence: Float = 0.95f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Comprehensive high-level summary produced by the Target Device Analyzer.
 */
@Serializable
data class TargetDeviceSummary(
    val headline: String = "",
    val model: String = "",
    val device: String = "",
    val board: String = "",
    val platform: String = "",
    val soc: String = "",
    val cpu: String = "",
    val gpu: String = "",
    val ramDisplay: String = "",
    val storageDisplay: String = "",
    val androidDisplay: String = "",
    val kernelDisplay: String = "",
    val abiDisplay: String = "",
    val trebleDisplay: String = "",
    val abDisplay: String = "",
    val avbDisplay: String = "",
    val selinuxDisplay: String = "",
    val encryptionDisplay: String = "",
    val partitionsCount: Int = 0,
    val mountsCount: Int = 0,
    val halDisplay: String = "",
    val rilDisplay: String = "",
    val limitations: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val sourceProvenance: String = ""
)

/**
 * Profile of the Target Device being ported TO.
 * Contains hardware, partition budget, kernel, and proprietary vendor specs.
 */
@Serializable
data class TargetDeviceProfile(
    val id: String,
    val name: String,
    val source: ProfileSourceType,
    val model: String, // "SM-G532F"
    val board: String, // "grandpplte"
    val platform: String, // "mt6737t"
    val cpuArch: String, // "armv7-a-neon (32-bit)"
    val is64Bit: Boolean = false,
    val maxKernelVersion: String, // "3.18.35+"
    val isTrebleSupported: Boolean = false, // Legacy non-treble
    val maxSystemPartitionBytes: Long, // 1,719,664,640L (~1.6 GB)
    val maxBootPartitionBytes: Long, // 16,777,216L (16 MB)
    val selinuxMode: String, // "Enforcing"
    val rootAvailable: Boolean,
    val supportedAbis: List<String> = listOf("armeabi-v7a", "armeabi"),
    val maliGpu: String = "Mali-T720 MP2",
    val rilInterface: String = "Samsung SEC RIL (IPC) / MediaTek CCK",
    val audioDriver: String = "MTK ALSA MT6737",
    val cameraHal: String = "MediaTek Camera HAL1 (Legacy non-Treble)",
    val mountPoints: Map<String, String> = emptyMap(),
    val properties: Map<String, String> = emptyMap(),
    val evidenceList: List<PortEvidence> = emptyList(),
    // Expanded Target Device Analyzer fields
    val device: String = "UNKNOWN",
    val hardware: String = "UNKNOWN",
    val soc: String = "UNKNOWN",
    val cpuCores: Int = 4,
    val cpuDetails: String = "UNKNOWN",
    val ramTotalBytes: Long = 0L,
    val ramTotalMb: Int = 0,
    val storageTotalBytes: Long = 0L,
    val androidVersion: String = "UNKNOWN",
    val sdkInt: Int = -1,
    val kernelCmdline: String = "",
    val isAbSupported: Boolean = false,
    val isAvbSupported: Boolean = false,
    val encryptionState: String = "Unencrypted",
    val partitionsList: List<PartitionInfo> = emptyList(),
    val mountsList: List<TargetMountInfo> = emptyList(),
    val halServices: List<String> = emptyList(),
    val halSummary: HalSummary = HalSummary(),
    val rilSummary: RilSummary = RilSummary(),
    val auditedFields: List<TargetFieldAudit> = emptyList(),
    val liveEvidence: List<LiveDeviceEvidence> = emptyList(),
    val targetIssues: List<TargetIssue> = emptyList(),
    val targetWarnings: List<TargetIssue> = emptyList(),
    val summary: TargetDeviceSummary = TargetDeviceSummary()
)

/**
 * Port readiness scoring and status breakdown.
 */
@Serializable
data class PortReadiness(
    val state: PortReadinessState = PortReadinessState.READY,
    val score: Int = 0, // 0 .. 100
    val status: PortStatus = PortStatus.PASS,
    val canProceedToBuild: Boolean = true,
    val blockerCount: Int = 0,
    val criticalCount: Int = 0,
    val highCount: Int = 0,
    val mediumCount: Int = 0,
    val lowCount: Int = 0,
    val infoCount: Int = 0,
    val verifiedPassCount: Int = 0,
    val warningCount: Int = 0,
    val passCount: Int = 0,
    val errorCount: Int = 0,
    val summary: String = "",
    val whatToFixFirst: FixFirstRecommendation? = null
)

/**
 * Actionable step in the generated Port Plan.
 */
@Serializable
data class PortPlanStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val category: String,
    val automatedActionType: String? = null,
    val isRequired: Boolean = true,
    val estimatedRisk: String = "Low",
    val commandHint: String? = null
)

/**
 * Complete porting roadmap generated by the assistant.
 */
@Serializable
data class PortPlan(
    val id: String,
    val title: String,
    val targetDeviceName: String,
    val sourceRomName: String,
    val steps: List<PortPlanStep>,
    val preCheckList: List<String>,
    val postInstallChecklist: List<String>,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Complete analysis result output for a porting session.
 */
@Serializable
data class PortAnalysisResult(
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceRom: SourceRomProfile,
    val targetDevice: TargetDeviceProfile,
    val evaluatedProperties: List<PortEvaluatedProperty>,
    val blockers: List<PortIssue>,
    val warnings: List<PortIssue>,
    val errors: List<PortIssue>,
    val passes: List<PortIssue>,
    val readiness: PortReadiness,
    val generatedPortPlan: PortPlan,
    val compatibilityResult: CompatibilityResult? = null,
    // Root Cause / Blocker Engine additions
    val portBlockers: List<PortBlocker> = emptyList(),
    val portWarnings: List<PortWarning> = emptyList(),
    val rootCauses: List<RootCauseCandidate> = emptyList(),
    val whatToFixFirst: FixFirstRecommendation? = null,
    // Beta 3: Migration Candidates & Structured Port Plan
    val migrationCandidates: List<MigrationCandidate> = emptyList(),
    val structuredPortPlan: StructuredPortPlan? = null
)

/**
 * Comparison result status for Source vs Target subsystems.
 */
@Serializable
enum class CompatibilityStatus {
    MATCH,       // Fully matching or compatible
    DIFFERENT,   // Different, but normal/adaptable (not an error)
    MISSING,     // Required component/library/partition is missing
    CONFLICT,    // Direct incompatibility or conflict requiring resolution
    UNKNOWN      // Information missing or inconclusive
}

/**
 * Severity level for compatibility issues.
 */
@Serializable
enum class CompatibilitySeverity {
    BLOCKER,     // Fatal issue preventing boot/execution
    ERROR,       // High severity error
    WARNING,     // Non-fatal warning / adaptation required
    INFO,        // Informational difference
    PASS         // Verified compatible
}

/**
 * Detailed compatibility issue with evidence, reason, and confidence.
 */
@Serializable
data class CompatibilityIssue(
    val id: String,
    val category: String,
    val title: String,
    val severity: CompatibilitySeverity,
    val reason: String,
    val evidence: PortEvidence,
    val confidence: Float,
    val isBlocker: Boolean = (severity == CompatibilitySeverity.BLOCKER),
    val recommendation: String,
    val fixStrategy: String? = null
)

/**
 * Granular evaluation of a single subsystem dimension (one of the 25 dimensions).
 */
@Serializable
data class CompatibilityComparisonItem(
    val key: String,
    val subsystem: String,
    val category: String,
    val label: String,
    val sourceValue: String,
    val targetValue: String,
    val status: CompatibilityStatus,
    val severity: CompatibilitySeverity,
    val reason: String,
    val evidence: PortEvidence,
    val confidence: Float,
    val isBlocker: Boolean = (severity == CompatibilitySeverity.BLOCKER),
    val actionRequired: String? = null
)

/**
 * Complete result of the Source vs Target Compatibility Engine.
 */
@Serializable
data class CompatibilityResult(
    val sessionId: String,
    val sourceName: String,
    val targetName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val overallScore: Int = 0, // 0 - 100
    val canProceedToPort: Boolean = true,
    val items: List<CompatibilityComparisonItem> = emptyList(),
    val issues: List<CompatibilityIssue> = emptyList(),
    val blockerCount: Int = 0,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val matchCount: Int = 0,
    val differentCount: Int = 0,
    val missingCount: Int = 0,
    val conflictCount: Int = 0,
    val unknownCount: Int = 0,
    val summary: String = "",
    val recommendations: List<String> = emptyList()
)

/**
 * Root state for a ROM Port Assistant session.
 */
@Serializable
data class PortSession(
    val id: String,
    val title: String,
    val sourceRom: SourceRomProfile?,
    val targetDevice: TargetDeviceProfile?,
    val analysisResult: PortAnalysisResult?,
    val lastReportMarkdown: String? = null,
    val lastReportJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Migration Candidate status for potential transplantable components.
 */
@Serializable
enum class CandidateStatus(val label: String) {
    CANDIDATE("CANDIDATE"),
    SAFE_TO_INVESTIGATE("SAFE TO INVESTIGATE"),
    HIGH_RISK("HIGH RISK"),
    BLOCKED("BLOCKED"),
    UNKNOWN("UNKNOWN")
}

/**
 * Granular classification of transplantable ROM components.
 */
@Serializable
enum class CandidateCategory(val label: String, val description: String) {
    LIBRARIES("Libraries", "Native dynamic shared libraries (.so) and framework shims"),
    HAL("HAL", "Hardware Abstraction Layer modules and HIDL/AIDL services"),
    CONFIGS("Configs", "Media, audio, GPS, display, and hardware XML/conf configurations"),
    INIT_SERVICES("Init Services", "Init scripts (.rc), daemon triggers, and uevent rules"),
    PROPERTIES("Properties", "System properties, build fingerprint, and runtime flags"),
    DTB_NODES("DTB Nodes", "Device Tree Blob node bindings and hardware registers"),
    DTBO_ENTRIES("DTBO Entries", "Device Tree Overlay dynamic entries"),
    FIRMWARE_REFS("Firmware Refs", "Modem, Wi-Fi, Bluetooth, GPS, and touch firmware blobs"),
    PERMISSIONS("Permissions", "System and privileged app permissions declarations"),
    SELINUX_CONTEXTS("SELinux Contexts", "Plat/vendor file_contexts, property_contexts & sepolicy"),
    SYSTEM_FILES("System Files", "Framework JARs, keylayout, fonts, and system binaries"),
    VENDOR_FILES("Vendor Files", "Proprietary vendor binaries and MTK hardware blobs")
}

/**
 * Evaluated migration risk level.
 */
@Serializable
enum class MigrationRisk(val label: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL")
}

/**
 * Specific Migration Candidate representing a potentially transplantable component.
 * Strict constraint: NEVER copied automatically.
 */
@Serializable
data class MigrationCandidate(
    val id: String,
    val name: String,
    val category: CandidateCategory,
    val path: String,
    val source: String,
    val target: String,
    val architecture: String, // e.g. "arm32", "armeabi-v7a", "device-tree", "agnostic"
    val dependencies: List<String> = emptyList(),
    val risk: MigrationRisk,
    val reason: String,
    val confidence: Float, // 0.0f .. 1.0f
    val status: CandidateStatus,
    val isIgnored: Boolean = false,
    val addedToPatchPlan: Boolean = false,
    val details: String = ""
)

/**
 * Port Plan core section types.
 */
@Serializable
enum class PortPlanSectionType(val label: String, val description: String) {
    KERNEL("KERNEL", "Linux 3.18.35+ defconfig, 32-bit binder IPC ioctl & cmdline tuning"),
    BOOT("BOOT", "boot.img header, 2048 page size, zImage replacement & ramdisk pack"),
    DTB("DTB", "Device Tree Blob extraction, MT6737T nodes, panel NT35521 & touch drivers"),
    SYSTEM("SYSTEM", "Framework JARs, 1.6GB eMMC storage budget, system/lib & debloating"),
    VENDOR("VENDOR", "MT6737T proprietary vendor blobs, Mali-T720 EGL & NVRAM calibration"),
    HAL("HAL", "Camera HAL1 legacy adaptation, ALSA audio, Gralloc 0.3 & sensors"),
    RIL("RIL", "Samsung SEC RIL / MTK CCK telephony shims & baseband configuration"),
    SELINUX("SELINUX", "file_contexts, service_contexts, permissive audit & domain shims"),
    PROPERTIES("PROPERTIES", "build.prop merging, removing 64-bit overrides, ro.product.model"),
    INIT("INIT", "init.mt6737t.rc service declarations, ueventd nodes & daemon sockets"),
    PARTITIONS("PARTITIONS", "Sparse ext4 packaging, 1.6GB system partition fit & flashing script")
}

/**
 * Port Plan task lifecycle status.
 */
@Serializable
enum class PortTaskStatus(val label: String) {
    PENDING("PENDING"),
    IN_PROGRESS("IN PROGRESS"),
    READY_TO_APPLY("READY TO APPLY"),
    COMPLETED("COMPLETED"),
    BLOCKED("BLOCKED"),
    IGNORED("IGNORED")
}

/**
 * Discrete actionable task within a Port Plan section.
 */
@Serializable
data class PortPlanTask(
    val id: String,
    val section: PortPlanSectionType,
    val title: String,
    val description: String,
    val dependencies: List<String> = emptyList(),
    val risk: MigrationRisk,
    val status: PortTaskStatus = PortTaskStatus.PENDING,
    val sourcePath: String? = null,
    val targetPath: String? = null,
    val actionCommandHint: String? = null,
    val relatedCandidateId: String? = null,
    val addedToPatchPlan: Boolean = false
)

/**
 * Cohesive category section in the structured Port Plan.
 */
@Serializable
data class PortPlanSection(
    val sectionType: PortPlanSectionType,
    val title: String,
    val description: String,
    val tasks: List<PortPlanTask> = emptyList()
)

/**
 * Complete Structured Port Plan spanning all 11 architectural sections.
 */
@Serializable
data class StructuredPortPlan(
    val id: String,
    val title: String,
    val sourceRomName: String,
    val targetDeviceName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sections: List<PortPlanSection> = emptyList()
) {
    val totalTasks: Int get() = sections.sumOf { it.tasks.size }
    val readyTasks: Int get() = sections.sumOf { it.tasks.count { t -> t.status == PortTaskStatus.READY_TO_APPLY } }
    val inProgressTasks: Int get() = sections.sumOf { it.tasks.count { t -> t.status == PortTaskStatus.IN_PROGRESS } }
    val completedTasks: Int get() = sections.sumOf { it.tasks.count { t -> t.status == PortTaskStatus.COMPLETED } }
    val blockedTasks: Int get() = sections.sumOf { it.tasks.count { t -> t.status == PortTaskStatus.BLOCKED } }
    val ignoredTasks: Int get() = sections.sumOf { it.tasks.count { t -> t.status == PortTaskStatus.IGNORED } }
}
