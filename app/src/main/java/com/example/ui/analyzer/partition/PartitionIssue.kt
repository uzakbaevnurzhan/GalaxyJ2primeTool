package com.example.ui.analyzer.partition

enum class PartitionIssueSeverity {
    CRITICAL,
    WARNING,
    INFO
}

data class PartitionIssue(
    val id: String,
    val severity: PartitionIssueSeverity,
    val title: String,
    val description: String,
    val evidence: String = "",
    val recommendation: String = "",
    val affectedPartition: String = "Global",
    val category: String = "Table Structure"
)
