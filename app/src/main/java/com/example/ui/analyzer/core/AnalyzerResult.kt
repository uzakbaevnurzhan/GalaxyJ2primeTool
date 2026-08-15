package com.example.ui.analyzer.core

enum class AnalyzerStatus {
    SUCCESS,
    WARNING,
    ERROR,
    PARTIAL,
    UNSUPPORTED
}

data class AnalyzerResult(
    val status: AnalyzerStatus,
    val summary: String,
    val details: String,
    val metadata: Map<String, String> = emptyMap()
)
