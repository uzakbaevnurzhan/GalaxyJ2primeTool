package com.example.ui.analyzer.boot

enum class BootStageStatus {
    PASS,
    WARNING,
    ERROR,
    UNKNOWN
}

enum class BootStage {
    BOOTLOADER,
    KERNEL,
    RAMDISK,
    INIT,
    MOUNT,
    SELINUX,
    VENDOR,
    HAL,
    ZYGOTE,
    SYSTEM_SERVER,
    ANDROID_FRAMEWORK
}

data class BootStageResult(
    val stage: BootStage,
    val status: BootStageStatus,
    val summary: String,
    val details: List<String> = emptyList(),
    val issues: List<BootIssue> = emptyList()
)

data class BootPartitionInfo(
    val partitionName: String,
    val fileName: String,
    val fileSize: Long,
    val format: String, // raw, sparse, new.dat, compressed
    val mountPoint: String? = null,
    val detected: Boolean = true,
    val isValid: Boolean = true,
    val notes: String = ""
)

data class PortingCheckRuleResult(
    val ruleName: String,
    val status: BootStageStatus,
    val description: String,
    val evidence: String,
    val recommendation: String? = null
)

data class GalaxyJ2PrimeProfileCheck(
    val expectedChipset: String = "MediaTek MT6737T",
    val expectedArch: String = "ARM32 (armv7-a)",
    val actualChipset: String? = null,
    val actualArch: String? = null,
    val isMatch: Boolean = true,
    val notes: List<String> = emptyList()
)

data class BootAnalysisResult(
    val bootHeader: BootHeaderInfo? = null,
    val kernelInfo: KernelDetailsInfo? = null,
    val ramdiskInfo: RamdiskDetailsInfo? = null,
    val initAnalysis: InitAnalysisInfo? = null,
    val fstabAnalysis: FstabAnalysisInfo? = null,
    val partitionMap: List<BootPartitionInfo> = emptyList(),
    val trebleInfo: TrebleStatusInfo? = null,
    val abSlotInfo: AbSlotStatusInfo? = null,
    val architectureInfo: ArchitectureCheckInfo? = null,
    val versionAnalysis: AndroidVersionAnalysisInfo? = null,
    val vendorAnalysis: VendorDetailsInfo? = null,
    val vintfAnalysis: VintfDetailsInfo? = null,
    val stageResults: Map<BootStage, BootStageResult> = emptyMap(),
    val lastConfirmedStage: BootStage = BootStage.BOOTLOADER,
    val suspectedFailureStage: BootStage? = null,
    val failureConfidence: String = "UNKNOWN", // HIGH, MEDIUM, LOW
    val allIssues: List<BootIssue> = emptyList(),
    val android11PortingChecks: List<PortingCheckRuleResult> = emptyList(),
    val j2PrimeProfile: GalaxyJ2PrimeProfileCheck? = null,
    val rawLogAnalysisSummary: String? = null
)
