package com.example.ui.analyzer.kernel.model

data class KernelWarning(
    val title: String,
    val description: String,
    val severity: KernelSeverity = KernelSeverity.WARNING,
    val lineIndex: Long? = null
)

data class BootFailureAnalysis(
    val isBootFailureLikely: Boolean = false,
    val kernelPanicPresent: Boolean = false,
    val watchdogTriggered: Boolean = false,
    val initCrashed: Boolean = false,
    val zygoteCrashed: Boolean = false,
    val systemServerCrashed: Boolean = false,
    val surfaceFlingerCrashed: Boolean = false,
    val criticalDriverFailed: Boolean = false,
    val mountFailureDetected: Boolean = false,
    val selinuxEnforcingDenialsDetected: Boolean = false,
    val detectedBlockers: List<String> = emptyList(),
    val recoveryRecommendations: List<String> = emptyList()
)

data class KernelCrashReport(
    val fileName: String = "unknown",
    val fileSize: Long = 0L,
    val fileSha256: String? = null,
    val totalLinesAnalyzed: Long = 0L,
    val kernelVersion: String? = null,
    val compilerInfo: String? = null,
    val buildDate: String? = null,
    val architecture: KernelArchitecture = KernelArchitecture.UNKNOWN,
    val totalEvents: Int = 0,
    val criticalEvents: Int = 0,
    val errorEvents: Int = 0,
    val warningEvents: Int = 0,
    val crashEvents: List<KernelCrashEvent> = emptyList(),
    val repeatedTraces: List<KernelTraceGroup> = emptyList(),
    val warnings: List<KernelWarning> = emptyList(),
    val bootFailureAnalysis: BootFailureAnalysis = BootFailureAnalysis(),
    val topProcesses: List<Pair<String, Int>> = emptyList(),
    val topSymbols: List<Pair<String, Int>> = emptyList(),
    val suspectedSubsystems: List<SuspectedSubsystem> = emptyList()
)
