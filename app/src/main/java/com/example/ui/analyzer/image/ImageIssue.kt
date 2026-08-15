package com.example.ui.analyzer.image

enum class IssueSeverity {
    CRITICAL,
    WARNING,
    INFO
}

data class ImageIssue(
    val id: String,
    val severity: IssueSeverity,
    val title: String,
    val description: String,
    val recommendation: String,
    val affectedPartition: String = "Global",
    val category: String = "Structure"
)
