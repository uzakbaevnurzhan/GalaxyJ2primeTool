package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File

object DeleteFilePatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = com.example.utils.SecurityUtil.safeResolve(workspaceDir, op.targetPath)

        if (!targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target file to delete does not exist: ${op.targetPath}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val oldHash = SnapshotManager.calculateSha256(targetFile)
        val oldSize = targetFile.length()

        try {
            val deleted = targetFile.deleteRecursively()
            if (!deleted && targetFile.exists()) {
                return SinglePatchExecutionResult(
                    operationId = op.id,
                    success = false,
                    message = "Failed to delete file on disk: ${op.targetPath}",
                    oldHash = oldHash,
                    oldSize = oldSize,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "Deleted ${op.targetPath} (was $oldSize bytes)",
                oldHash = oldHash,
                newHash = null,
                oldSize = oldSize,
                newSize = 0L,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Exception deleting file: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
