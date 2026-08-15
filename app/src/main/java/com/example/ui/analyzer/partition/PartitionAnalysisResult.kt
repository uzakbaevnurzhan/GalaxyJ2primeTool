package com.example.ui.analyzer.partition

import com.example.ui.analyzer.core.AnalyzerStatus

enum class PartitionTableHealth {
    VALID,
    WARNING,
    CORRUPTED
}

data class PartitionAnalysisResult(
    val status: AnalyzerStatus = AnalyzerStatus.SUCCESS,
    val health: PartitionTableHealth = PartitionTableHealth.VALID,
    val summary: String = "",
    val details: String = "",
    val table: PartitionTable = PartitionTable(),
    val issues: List<PartitionIssue> = emptyList(),
    val addressGaps: List<Pair<Long, Long>> = emptyList(),
    val processingTimeMs: Long = 0L
) {
    val criticalIssuesCount: Int
        get() = issues.count { it.severity == PartitionIssueSeverity.CRITICAL }

    val warningIssuesCount: Int
        get() = issues.count { it.severity == PartitionIssueSeverity.WARNING }

    val infoIssuesCount: Int
        get() = issues.count { it.severity == PartitionIssueSeverity.INFO }
}
