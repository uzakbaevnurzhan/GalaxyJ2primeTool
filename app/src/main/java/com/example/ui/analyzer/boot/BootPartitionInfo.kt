package com.example.ui.analyzer.boot

data class BootHeaderInfo(
    val isValid: Boolean,
    val headerVersion: Int, // 0, 1, 2, 3, 4
    val magic: String,
    val kernelSize: Long,
    val kernelLoadAddr: Long,
    val ramdiskSize: Long,
    val ramdiskLoadAddr: Long,
    val secondStageSize: Long,
    val secondStageLoadAddr: Long,
    val tagsLoadAddr: Long,
    val pageSize: Int,
    val headerSize: Int,
    val osVersionRaw: Int,
    val osVersionString: String,
    val osPatchLevelString: String,
    val boardName: String,
    val cmdline: String,
    val extraCmdline: String,
    val recoveryDtboSize: Long,
    val recoveryDtboOffset: Long,
    val dtbSize: Long,
    val dtbLoadAddr: Long,
    val signatureSha: String,
    val kernelOffset: Long,
    val ramdiskOffset: Long,
    val secondOffset: Long,
    val tagsOffset: Long,
    val isPartialSupport: Boolean = false,
    val notes: String = ""
)

data class KernelDetailsInfo(
    val detectedFormat: String, // raw, gzip, lz4, lzma, xz, zstd, unknown
    val detectedArch: String, // ARM, ARM64, x86, x86_64, unknown
    val kernelVersionString: String? = null,
    val compilerString: String? = null,
    val isSmp: Boolean = false,
    val kernelConfigCount: Int = 0,
    val sampleConfigs: List<String> = emptyList(),
    val kernelSize: Long = 0,
    val rawStringsFound: List<String> = emptyList()
)

data class RamdiskEntryNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val children: MutableList<RamdiskEntryNode> = mutableListOf()
)

data class RamdiskDetailsInfo(
    val present: Boolean,
    val size: Long,
    val compression: String, // gzip, lz4, lzma, xz, bzip2, zstd, cpio_raw, unknown
    val cpioEntriesCount: Int,
    val fileTree: List<RamdiskEntryNode>,
    val foundKeyFiles: List<String>, // init, init.rc, fstab.*, ueventd.rc, default.prop, etc.
    val isCorrupt: Boolean = false,
    val notes: String = ""
)

data class InitActionBlock(
    val trigger: String,
    val stage: String, // early-init, init, fs, post-fs, post-fs-data, boot, late-init, property, custom
    val commands: List<String>
)

data class InitServiceBlock(
    val name: String,
    val binaryPath: String,
    val arguments: List<String>,
    val className: String,
    val user: String,
    val group: String,
    val seclabel: String?,
    val isDisabled: Boolean,
    val isOneshot: Boolean,
    val isCritical: Boolean,
    val restartBehavior: String,
    val binaryExistsInWorkspace: Boolean? = null,
    val binaryArch: String? = null,
    val missingLibraries: List<String> = emptyList()
)

data class InitAnalysisInfo(
    val totalFilesParsed: Int,
    val stagesFound: Map<String, List<InitActionBlock>>,
    val services: List<InitServiceBlock>,
    val imports: List<String>,
    val setProps: Map<String, String>,
    val mountCommands: List<String>,
    val issuesFound: List<BootIssue>
)

data class FstabEntryInfo(
    val mountTarget: String,
    val deviceSource: String,
    val filesystem: String,
    val flags: String,
    val fsMgrFlags: String,
    val isMandatory: Boolean,
    val partitionFileFound: Boolean = true,
    val warningNote: String? = null
)

data class FstabAnalysisInfo(
    val fileName: String,
    val entries: List<FstabEntryInfo>,
    val missingMandatoryPartitions: List<String>,
    val issuesFound: List<BootIssue>
)

data class TrebleStatusInfo(
    val isTreble: Boolean,
    val hasVendorPartition: Boolean,
    val roTrebleProperty: String?,
    val hasVndkProps: Boolean,
    val frameworkVendorSeparation: Boolean,
    val confidence: String, // HIGH, MEDIUM, LOW
    val indicators: List<String>
)

data class AbSlotStatusInfo(
    val isAb: Boolean, // true = A/B, false = A-only, null = Unknown
    val slotSuffixDetected: String?,
    val updateEngineFound: Boolean,
    val bootSlotsFound: Boolean,
    val indicators: List<String>
)

data class ArchitectureCheckInfo(
    val kernelArch: String?,
    val initArch: String?,
    val systemArch: String?,
    val vendorArch: String?,
    val overallArch: String, // ARM32, ARM64, MIXED, UNKNOWN
    val isConsistent: Boolean,
    val notes: List<String>
)

data class AndroidVersionAnalysisInfo(
    val bootHeaderVersion: String?,
    val buildPropVersion: String?,
    val defaultPropVersion: String?,
    val resolvedVersion: String,
    val hasConflict: Boolean,
    val conflictDetails: String? = null
)

data class VendorDetailsInfo(
    val vendorPresent: Boolean,
    val architecture: String?,
    val propertyCount: Int,
    val servicesCount: Int,
    val halList: List<String>,
    val manifestPresent: Boolean,
    val issues: List<BootIssue>
)

data class VintfHalInfo(
    val name: String,
    val transport: String,
    val format: String,
    val versions: List<String>,
    val interfaces: List<String>
)

data class VintfDetailsInfo(
    val hasManifest: Boolean,
    val hasMatrix: Boolean,
    val hals: List<VintfHalInfo>,
    val parsedSummary: String,
    val isPartiallyValidated: Boolean
)
