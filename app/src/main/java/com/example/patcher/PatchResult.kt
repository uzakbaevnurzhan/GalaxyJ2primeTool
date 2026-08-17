package com.example.patcher

import kotlinx.serialization.Serializable

@Serializable
data class PatchConflict(
    val operationId1: String,
    val operationId2: String,
    val targetPath: String,
    val conflictType: ConflictType,
    val description: String
)

enum class ConflictType {
    SAME_PROPERTY_MODIFIED_TWICE,
    MULTIPLE_PATCHES_SAME_FILE,
    BINARY_OFFSET_OVERLAP,
    DELETED_FILE_MODIFIED_LATER,
    CIRCULAR_DEPENDENCY,
    FILE_MISSING_FOR_OPERATION
}

@Serializable
data class PatchValidationIssue(
    val severity: ValidationSeverity,
    val operationId: String?,
    val targetPath: String,
    val message: String,
    val suggestion: String? = null
)

enum class ValidationSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKER
}

@Serializable
data class PatchValidationSummary(
    val isValid: Boolean,
    val blockersCount: Int,
    val errorsCount: Int,
    val warningsCount: Int,
    val conflicts: List<PatchConflict>,
    val issues: List<PatchValidationIssue>,
    val overallRisk: PatchRisk
) {
    val canApply: Boolean get() = blockersCount == 0 && errorsCount == 0 && conflicts.isEmpty()
}

@Serializable
data class DryRunChange(
    val operationId: String,
    val operationName: String,
    val targetPath: String,
    val type: PatchType,
    val oldValueSummary: String?,
    val newValueSummary: String?,
    val diffText: String?,
    val risk: PatchRisk,
    val isExecutable: Boolean,
    val warnings: List<String> = emptyList()
)

@Serializable
data class DryRunReport(
    val planId: String,
    val planName: String,
    val totalChanges: Int,
    val affectedFiles: List<String>,
    val changes: List<DryRunChange>,
    val validationSummary: PatchValidationSummary,
    val estimatedBytesImpact: Long
)

@Serializable
data class SinglePatchExecutionResult(
    val operationId: String,
    val success: Boolean,
    val message: String,
    val oldHash: String? = null,
    val newHash: String? = null,
    val oldSize: Long = 0L,
    val newSize: Long = 0L,
    val executionTimeMs: Long = 0L
)

@Serializable
data class PatchExecutionResult(
    val transactionId: String,
    val snapshotId: String?,
    val success: Boolean,
    val status: String,
    val appliedCount: Int,
    val totalCount: Int,
    val results: List<SinglePatchExecutionResult>,
    val rolledBack: Boolean = false,
    val rollbackMessage: String? = null,
    val errorMessage: String? = null,
    val startTime: Long,
    val endTime: Long
)
