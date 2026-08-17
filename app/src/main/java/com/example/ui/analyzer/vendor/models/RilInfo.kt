package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

enum class RilReadinessLevel {
    HIGH_READINESS,
    PARTIAL_READINESS,
    HIGH_RISK,
    MISSING_OR_INCOMPATIBLE,
    UNKNOWN
}

@Serializable
data class RilDaemonInfo(
    val name: String,
    val path: String,
    val exists: Boolean,
    val architecture: String? = null,
    val neededLibraries: List<String> = emptyList(),
    val missingLibraries: List<String> = emptyList()
)

@Serializable
data class RilLibraryInfo(
    val name: String,
    val path: String,
    val exists: Boolean,
    val architecture: String? = null,
    val soname: String? = null,
    val neededLibraries: List<String> = emptyList(),
    val missingLibraries: List<String> = emptyList(),
    val isVendorSpecific: Boolean = false,
    val vendorFlavor: String = "Generic" // e.g. MediaTek, Samsung SecRil, Qualcomm, Reference
) {
    val arch: String get() = architecture ?: "unknown"
}

@Serializable
data class RilPropertyInfo(
    val property: String,
    val value: String,
    val source: String,
    val category: String = "Telephony",
    val description: String = ""
) {
    val key: String get() = property
}

@Serializable
data class RilInitService(
    val serviceName: String,
    val binaryPath: String,
    val isBinaryFound: Boolean,
    val className: String? = null,
    val user: String? = null,
    val group: String? = null,
    val seclabel: String? = null,
    val isDisabled: Boolean = false
)

@Serializable
data class RilSelinuxDenial(
    val scontext: String,
    val tcontext: String,
    val tclass: String,
    val permission: String,
    val rawLog: String,
    val impact: String
)

@Serializable
data class RilDependencyChain(
    val initService: String = "",
    val initStatus: StageStatus = StageStatus.UNKNOWN,
    val daemonBinary: String = "",
    val daemonStatus: StageStatus = StageStatus.UNKNOWN,
    val rilLibrary: String = "",
    val rilLibStatus: StageStatus = StageStatus.UNKNOWN,
    val vendorImplLibrary: String = "",
    val vendorImplStatus: StageStatus = StageStatus.UNKNOWN,
    val halService: String = "",
    val halStatus: StageStatus = StageStatus.UNKNOWN,
    val kernelInterface: String = "",
    val kernelStatus: StageStatus = StageStatus.UNKNOWN,
    val selinuxDenialsCount: Int = 0,
    val logIssuesCount: Int = 0
)

@Serializable
data class RilReadinessScore(
    val structureStatus: StageStatus = StageStatus.UNKNOWN,
    val binaryStatus: StageStatus = StageStatus.UNKNOWN,
    val dependencyStatus: StageStatus = StageStatus.UNKNOWN,
    val selinuxStatus: StageStatus = StageStatus.UNKNOWN,
    val logsStatus: LogStatus = LogStatus.UNKNOWN,
    val overallReadiness: RilReadinessLevel = RilReadinessLevel.UNKNOWN,
    val readinessPercentage: Int = 0,
    val diagnosticSummary: String = "",
    val scoreEvidence: List<String> = emptyList()
)

@Serializable
data class RilInfo(
    val daemons: List<RilDaemonInfo> = emptyList(),
    val libraries: List<RilLibraryInfo> = emptyList(),
    val properties: List<RilPropertyInfo> = emptyList(),
    val initServices: List<RilInitService> = emptyList(),
    val selinuxDenials: List<RilSelinuxDenial> = emptyList(),
    val dependencyChain: RilDependencyChain = RilDependencyChain(),
    val readinessScore: RilReadinessScore = RilReadinessScore(),
    val findings: List<EvidenceFinding> = emptyList(),
    val issues: List<VendorIssue> = emptyList()
)
