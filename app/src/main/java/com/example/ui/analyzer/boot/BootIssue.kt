package com.example.ui.analyzer.boot

enum class BootIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

enum class BootIssueType {
    INVALID_HEADER,
    CORRUPTED_PAYLOAD,
    KERNEL_MISSING,
    KERNEL_PANIC_SIGNATURE,
    RAMDISK_MISSING,
    RAMDISK_CORRUPT,
    INIT_PARSER_ERROR,
    MISSING_INIT_SERVICE_BINARY,
    BINARY_ABI_MISMATCH,
    MISSING_ELF_DEPENDENCY,
    INVALID_FSTAB,
    MISSING_MANDATORY_MOUNT,
    MOUNT_PARTITION_MISSING,
    SELINUX_DENIAL_RISK,
    SELINUX_POLICY_MISSING,
    PROPERTY_VERSION_CONFLICT,
    TREBLE_INCOMPATIBILITY,
    AB_SLOT_MISCONFIGURED,
    ARCH_32_64_CONFLICT,
    ANDROID_11_PORTING_BLOCKED,
    VENDOR_INTEGRITY_FAIL,
    VINTF_MISMATCH
}

data class BootIssue(
    val type: BootIssueType,
    val severity: BootIssueSeverity,
    val title: String,
    val description: String,
    val evidence: String,
    val file: String? = null,
    val line: Int? = null,
    val possibleCause: String? = null,
    val recommendedFix: String? = null,
    val confidence: String = "HIGH" // HIGH, MEDIUM, LOW
)
