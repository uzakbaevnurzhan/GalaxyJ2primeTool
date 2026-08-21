package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File

object PermissionPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = com.example.utils.SecurityUtil.safeResolve(workspaceDir, op.targetPath)

        if (!targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target file does not exist: ${op.targetPath}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val oldHash = SnapshotManager.calculateSha256(targetFile)
        val oldSize = targetFile.length()

        val mode = op.permissionMode ?: "0644"
        try {
            // Apply readable / writable / executable flags on File object
            val isExec = mode.endsWith("5") || mode.endsWith("7") || mode.endsWith("1") || mode.contains("755") || mode.contains("777")
            val isWrite = mode.contains("6") || mode.contains("7") || mode.contains("2")
            
            targetFile.setReadable(true, false)
            targetFile.setWritable(isWrite, false)
            targetFile.setExecutable(isExec, false)

            // Save unix metadata file for repacker fstab/selinux/fs_config generator
            val metaFile = File(workspaceDir, "metadata/file_permissions.txt")
            metaFile.parentFile?.mkdirs()
            val entry = "${op.targetPath} ${op.permissionUid ?: "0"} ${op.permissionGid ?: "0"} $mode ${op.permissionContext ?: "u:object_r:system_file:s0"}\n"
            metaFile.appendText(entry)

            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "Permissions updated for ${op.targetPath} (mode: $mode, uid: ${op.permissionUid ?: "0"}, gid: ${op.permissionGid ?: "0"})",
                oldHash = oldHash,
                newHash = oldHash,
                oldSize = oldSize,
                newSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Failed to update permissions: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
