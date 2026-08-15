package com.example.ui.analyzer.selinux.model

enum class SelinuxMode {
    ENFORCING,
    PERMISSIVE,
    DISABLED,
    UNKNOWN
}

data class SelinuxStatusDetection(
    val mode: SelinuxMode,
    val sourceEvidence: List<String>,
    val warnings: List<String> = emptyList(),
    val hasConflict: Boolean = false
)

data class AvcStatistics(
    val totalDenials: Int,
    val uniqueDenials: Int,
    val permissiveCount: Int,
    val enforcingCount: Int,
    val mostFrequentSource: Pair<String, Int>?,
    val mostFrequentTarget: Pair<String, Int>?,
    val mostFrequentPermission: Pair<String, Int>?,
    val mostFrequentClass: Pair<String, Int>?,
    val topSources: List<Pair<String, Int>>,
    val topTargets: List<Pair<String, Int>>,
    val topPermissions: List<Pair<String, Int>>,
    val topClasses: List<Pair<String, Int>>
)

enum class SelinuxFileType {
    AVC_LOG,
    FILE_CONTEXTS,
    PROPERTY_CONTEXTS,
    SERVICE_CONTEXTS,
    SEAPP_CONTEXTS,
    GENFS_CONTEXTS,
    BINARY_SEPOLICY,
    UNKNOWN
}

data class SelinuxAnalysisResult(
    val detectedType: SelinuxFileType,
    val detectedStatus: SelinuxStatusDetection? = null,
    val totalLinesParsed: Long = 0,
    val skippedLinesCount: Long = 0,
    val warnings: List<String> = emptyList(),
    
    // AVC results
    val avcDenials: List<AvcDenial> = emptyList(),
    val avcGroups: List<AvcGroup> = emptyList(),
    val avcStatistics: AvcStatistics? = null,

    // Contexts results
    val fileContexts: List<FileContextEntry> = emptyList(),
    val propertyContexts: List<PropertyContextEntry> = emptyList(),
    val serviceContexts: List<ServiceContextEntry> = emptyList(),
    val seappContexts: List<SeappContextEntry> = emptyList(),
    val genfsContexts: List<GenfsContextEntry> = emptyList(),

    // Cross-analysis / Boot failure diagnosis
    val bootDiagnosis: List<String> = emptyList()
)
