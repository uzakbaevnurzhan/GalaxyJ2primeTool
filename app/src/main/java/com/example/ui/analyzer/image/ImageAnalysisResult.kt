package com.example.ui.analyzer.image

import com.example.ui.analyzer.core.AnalyzerStatus

data class ExtractedFileInfo(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val permissions: String = "rwxr-xr-x",
    val selinuxContext: String = "u:object_r:system_file:s0"
)

data class ImageAnalysisResult(
    val status: AnalyzerStatus = AnalyzerStatus.SUCCESS,
    val summary: String = "",
    val details: String = "",
    val metadata: ImageMetadata = ImageMetadata(),
    val partitions: List<ImagePartition> = emptyList(),
    val issues: List<ImageIssue> = emptyList(),
    val extractedFiles: List<ExtractedFileInfo> = emptyList(),
    val detectedProperties: Map<String, String> = emptyMap(),
    val elfArchitecturesFound: Set<String> = emptySet(),
    val hasTrebleSupport: Boolean = false,
    val isDynamicPartitions: Boolean = false,
    val isSystemAsRoot: Boolean = false,
    val androidTargetVersion: String = "Unknown",
    val sdkLevel: Int = 0,
    val processingTimeMs: Long = 0L
) {
    val criticalIssuesCount: Int
        get() = issues.count { it.severity == IssueSeverity.CRITICAL }

    val warningIssuesCount: Int
        get() = issues.count { it.severity == IssueSeverity.WARNING }

    val infoIssuesCount: Int
        get() = issues.count { it.severity == IssueSeverity.INFO }
}
