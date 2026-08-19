package com.example.porting.model

import com.example.data.model.ProjectHealthReport
import kotlinx.serialization.Serializable

@Serializable
enum class PipelineStage(val label: String, val order: Int) {
    PORT_ANALYSIS("1. Port Analysis", 1),
    PORT_PLAN("2. Port Plan Generation", 2),
    SELECT_CANDIDATES("3. Candidate Pre-Merge Verification", 3),
    SNAPSHOT("4. Workspace Snapshot", 4),
    MERGE_PATCH("5. Safe Merge / Patch", 5),
    VALIDATE("6. Post-Merge Subsystem Validation", 6),
    BUILD("7. ROM Studio Build", 7),
    POST_BUILD_ANALYSIS("8. Post-Build Forensics", 8),
    REPORT("9. Final Audit Report", 9)
}

@Serializable
enum class PipelineStatus {
    IDLE,
    QUEUED,
    RUNNING,
    PAUSED_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED,
    ROLLED_BACK
}

@Serializable
data class PreMergeVerificationResult(
    val allPassed: Boolean,
    val conflictCount: Int,
    val abiPassCount: Int,
    val abiFailures: List<String> = emptyList(),
    val dependencyMissingCount: Int,
    val missingDependencies: List<String> = emptyList(),
    val selinuxContextMissingCount: Int,
    val missingSelinuxContexts: List<String> = emptyList(),
    val partitionOverflow: Boolean = false,
    val totalCandidateBytes: Long = 0L,
    val partitionBudgetBytes: Long = 1719664640L,
    val details: List<String> = emptyList()
)

@Serializable
data class SubsystemValidationResult(
    val subsystemName: String,
    val passed: Boolean,
    val status: String,
    val findings: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

@Serializable
data class PostMergeValidationReport(
    val allSubsystemsPassed: Boolean,
    val romAnalyzer: SubsystemValidationResult,
    val bootAnalyzer: SubsystemValidationResult,
    val kernelAnalyzer: SubsystemValidationResult,
    val dtbAnalyzer: SubsystemValidationResult,
    val elfAnalyzer: SubsystemValidationResult,
    val halAnalyzer: SubsystemValidationResult,
    val rilAnalyzer: SubsystemValidationResult,
    val selinuxAnalyzer: SubsystemValidationResult,
    val partitionAnalyzer: SubsystemValidationResult,
    val healthScore: Int = 100,
    val healthReportSummary: String = "All workspace invariants intact."
)

@Serializable
data class OutputArtifactForensic(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val md5: String,
    val magicValid: Boolean,
    val detectedMagic: String,
    val architecture: String,
    val isArm32Valid: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val verificationPassed: Boolean,
    val notes: String = ""
)

@Serializable
data class PostBuildAnalysisReport(
    val allArtifactsValid: Boolean,
    val artifacts: List<OutputArtifactForensic> = emptyList(),
    val warnings: List<String> = emptyList(),
    val totalOutputSizeBytes: Long = 0L
)

@Serializable
data class PipelineExecutionSummary(
    val pipelineId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceRomName: String,
    val targetDeviceName: String,
    val status: PipelineStatus,
    val currentStage: PipelineStage = PipelineStage.PORT_ANALYSIS,
    val progress: Float = 0f,
    val snapshotId: String? = null,
    val preMergeResult: PreMergeVerificationResult? = null,
    val mergedFileCount: Int = 0,
    val postMergeValidation: PostMergeValidationReport? = null,
    val buildArtifactPath: String? = null,
    val postBuildAnalysis: PostBuildAnalysisReport? = null,
    val reportMarkdownPath: String? = null,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val canRollback: Boolean = false,
    val isRolledBack: Boolean = false
)
