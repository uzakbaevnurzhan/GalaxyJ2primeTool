package com.example.ui.analyzer.flash

import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.partition.PartitionIssue
import com.example.ui.analyzer.partition.PartitionIssueSeverity

enum class FlashVerdict(val label: String) {
    SAFE_TO_FLASH("SAFE TO FLASH (Pre-Checks Passed)"),
    WARNING_CAUTION("PROCEED WITH CAUTION (Warnings Detected)"),
    UNSAFE_DO_NOT_FLASH("UNSAFE - DO NOT FLASH (Critical Risks)"),
    FATAL_SIZE_MISMATCH("FATAL MISMATCH (Brick Hazard)")
}

data class FlashPrecheckResult(
    val status: AnalyzerStatus = AnalyzerStatus.SUCCESS,
    val verdict: FlashVerdict = FlashVerdict.SAFE_TO_FLASH,
    val plan: FlashPlan = FlashPlan(),
    val issues: List<PartitionIssue> = emptyList(),
    val summary: String = "",
    val detailedReport: String = "",
    val preFlashChecklist: List<String> = emptyList(),
    val processingTimeMs: Long = 0L
) {
    val criticalIssuesCount: Int
        get() = issues.count { it.severity == PartitionIssueSeverity.CRITICAL }

    val warningIssuesCount: Int
        get() = issues.count { it.severity == PartitionIssueSeverity.WARNING }

    val infoIssuesCount: Int
        get() = issues.count { it.severity == PartitionIssueSeverity.INFO }
}
