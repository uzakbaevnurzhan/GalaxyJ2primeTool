package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

enum class VendorIssueType {
    MISSING_VENDOR,
    MISSING_HAL,
    MISSING_BINARY,
    MISSING_LIBRARY,
    ABI_MISMATCH,
    DEPENDENCY_MISSING,
    MANIFEST_MISMATCH,
    INIT_SERVICE_MISSING,
    SELINUX_DENIAL,
    PROPERTY_CONFLICT,
    VERSION_CONFLICT,
    UNKNOWN_IMPLEMENTATION,
    HARDWARE_INTERFACE_MISSING
}

@Serializable
data class VendorIssue(
    val type: VendorIssueType,
    val severity: Severity,
    val message: String,
    val evidence: String,
    val source: String,
    val confidence: Confidence = Confidence.HIGH,
    val recommendation: String? = null
)

@Serializable
data class HalIssue(
    val halName: String,
    val severity: Severity,
    val message: String,
    val evidence: String,
    val source: String,
    val confidence: Confidence = Confidence.HIGH
)
