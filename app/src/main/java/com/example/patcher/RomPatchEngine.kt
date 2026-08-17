package com.example.patcher

import com.example.patcher.operations.*
import java.io.File

object RomPatchEngine {

    suspend fun executePatchPlan(
        workspaceDir: File,
        plan: PatchPlan,
        snapshotId: String?,
        onProgress: (Int, Int, String) -> Unit
    ): PatchExecutionResult {
        val transactionId = java.util.UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<SinglePatchExecutionResult>()
        var appliedCount = 0
        var hasFailure = false
        var errorMessage: String? = null

        val enabledOps = plan.enabledOperations
        val totalCount = enabledOps.size

        for ((index, op) in enabledOps.withIndex()) {
            if (hasFailure) {
                // Skip remaining if one fails in a strict transaction
                results.add(
                    SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Skipped due to previous failure in transaction"
                    )
                )
                continue
            }

            onProgress(index + 1, totalCount, "Applying ${op.name}...")

            val result = try {
                when (op.type) {
                    PatchType.TEXT -> TextPatch.apply(workspaceDir, op)
                    PatchType.PROPERTY -> PropertyPatch.apply(workspaceDir, op)
                    PatchType.XML -> XmlPatch.apply(workspaceDir, op)
                    PatchType.FILE_REPLACE -> FilePatch.apply(workspaceDir, op)
                    PatchType.FILE_ADD -> AddFilePatch.apply(workspaceDir, op)
                    PatchType.FILE_DELETE -> DeleteFilePatch.apply(workspaceDir, op)
                    PatchType.BINARY -> BinaryPatch.apply(workspaceDir, op)
                    PatchType.PERMISSION -> PermissionPatch.apply(workspaceDir, op)
                    PatchType.SYMLINK -> SymlinkPatch.apply(workspaceDir, op)
                    else -> SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Unsupported patch type: ${op.type}"
                    )
                }
            } catch (e: Exception) {
                SinglePatchExecutionResult(
                    operationId = op.id,
                    success = false,
                    message = "Exception during patch execution: ${e.message}"
                )
            }

            results.add(result)
            if (result.success) {
                appliedCount++
            } else {
                hasFailure = true
                errorMessage = result.message
            }
        }

        val endTime = System.currentTimeMillis()

        return PatchExecutionResult(
            transactionId = transactionId,
            snapshotId = snapshotId,
            success = !hasFailure,
            status = if (!hasFailure) "APPLIED" else "FAILED",
            appliedCount = appliedCount,
            totalCount = totalCount,
            results = results,
            errorMessage = errorMessage,
            startTime = startTime,
            endTime = endTime
        )
    }
}
