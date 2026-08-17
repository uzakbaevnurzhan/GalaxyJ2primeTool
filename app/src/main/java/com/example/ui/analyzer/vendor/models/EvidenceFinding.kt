package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

enum class Severity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class TrebleStatus {
    TREBLE,
    NON_TREBLE,
    UNKNOWN
}

enum class StageStatus {
    FOUND,
    MISSING,
    UNKNOWN,
    CONFLICT
}

enum class LogStatus {
    NO_ERRORS,
    WARNINGS_FOUND,
    ERRORS_FOUND,
    CRITICAL_FOUND,
    NO_LOGS,
    UNKNOWN
}

enum class HardwarePresenceStatus {
    LIKELY_PRESENT,
    PARTIALLY_PRESENT,
    MISSING,
    CONFLICT,
    UNKNOWN
}

@Serializable
data class EvidenceFinding(
    val fact: String,
    val evidence: String,
    val severity: Severity,
    val confidence: Confidence,
    val source: String = "Analysis"
)
