package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    QUEUED,
    PREPARING,
    RUNNING,
    VALIDATING,
    SUCCESS,
    WARNING,
    FAILED,
    CANCELLED
}

@Serializable
enum class ProjectHealthStatus {
    READY,
    READY_WITH_WARNINGS,
    NOT_READY,
    BLOCKED
}

@Serializable
data class HealthCheckItem(
    val category: String,
    val checkName: String,
    val passed: Boolean,
    val isCritical: Boolean,
    val message: String,
    val recommendation: String? = null
)

@Serializable
data class ProjectHealthReport(
    val status: ProjectHealthStatus,
    val score: Int, // 0 - 100
    val checks: List<HealthCheckItem>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class DeviceSnapshot(
    val id: String,
    val projectId: String,
    val name: String,
    val timestamp: Long,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildDisplayId: String,
    val fingerprint: String,
    val kernelVersion: String,
    val kernelCmdline: String,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val selinuxMode: String,
    val rootAvailable: Boolean,
    val partitions: List<PartitionSnapshotItem>,
    val systemProperties: Map<String, String>,
    val halServices: List<String> = emptyList(),
    val rilDetails: Map<String, String> = emptyMap(),
    val notes: String = ""
)

@Serializable
data class PartitionSnapshotItem(
    val name: String,
    val sizeBytes: Long,
    val mountPoint: String? = null,
    val fsType: String? = null,
    val sha256: String? = null
)

@Serializable
data class SnapshotDiff(
    val timestampA: Long,
    val timestampB: Long,
    val propertyDiffs: List<DiffItem>,
    val partitionDiffs: List<DiffItem>,
    val selinuxDiff: DiffItem?,
    val kernelDiff: DiffItem?,
    val summary: String
)

@Serializable
data class DiffItem(
    val key: String,
    val valueBefore: String?,
    val valueAfter: String?,
    val status: DiffStatus
)

@Serializable
enum class DiffStatus {
    SAME,
    ADDED,
    REMOVED,
    MODIFIED,
    UNKNOWN
}

@Serializable
data class RomDeepCompareResult(
    val romAName: String,
    val romBName: String,
    val timestamp: Long,
    val files: List<DiffItem>,
    val properties: List<DiffItem>,
    val halServices: List<DiffItem>,
    val rilFeatures: List<DiffItem>,
    val selinuxPolicies: List<DiffItem>,
    val initScripts: List<DiffItem>,
    val partitions: List<DiffItem>,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int,
    val sameCount: Int
)

@Serializable
data class MergeConflict(
    val relativePath: String,
    val baseSize: Long,
    val targetSize: Long,
    val conflictReason: String,
    val isResolvable: Boolean = true
)

@Serializable
data class MergePlan(
    val id: String,
    val projectId: String,
    val baseRomName: String,
    val targetRomName: String,
    val selectedFiles: List<String>,
    val conflicts: List<MergeConflict>,
    val abiCompatible: Boolean,
    val dependenciesMet: Boolean,
    val dependencyWarnings: List<String>,
    val snapshotBeforeMergeId: String? = null
)

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val description: String,
    val type: String,
    val status: TaskStatus,
    val progress: Float, // 0.0f .. 1.0f
    val currentStage: String,
    val startTime: Long,
    val endTime: Long? = null,
    val logs: List<String> = emptyList(),
    val errorDetails: String? = null,
    val resultSummary: String? = null,
    val canCancel: Boolean = true
)

@Serializable
data class RecentActivity(
    val id: String,
    val title: String,
    val description: String,
    val actionType: String,
    val timestamp: Long,
    val relatedId: String? = null
)

@Serializable
data class AppErrorLog(
    val id: String,
    val module: String,
    val operation: String,
    val stage: String,
    val message: String,
    val cause: String?,
    val evidence: String?,
    val stackTrace: String?,
    val suggestedAction: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class ReportType {
    ROM_REPORT,
    DEVICE_REPORT,
    BUILD_REPORT,
    PATCH_REPORT,
    DIAGNOSTIC_REPORT,
    ROOT_REPORT,
    USB_REPORT,
    ADB_REPORT,
    SAMSUNG_REPORT,
    ROM_PORT_REPORT
}

@Serializable
enum class ReportFormat {
    MARKDOWN,
    TXT,
    JSON,
    CSV
}
