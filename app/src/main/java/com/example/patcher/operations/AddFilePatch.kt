package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File

object AddFilePatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = File(workspaceDir, op.targetPath)

        if (targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Destination file already exists: ${op.targetPath}. Use Replace File instead.",
                oldHash = SnapshotManager.calculateSha256(targetFile),
                oldSize = targetFile.length(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val contentBytes: ByteArray = when {
            op.sourceFilePath != null -> {
                val src = File(op.sourceFilePath)
                if (!src.exists()) {
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Source file to add not found: ${op.sourceFilePath}",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                src.readBytes()
            }
            op.sourceContentBase64 != null -> {
                try {
                    android.util.Base64.decode(op.sourceContentBase64, android.util.Base64.DEFAULT)
                } catch (e: Exception) {
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Failed to decode base64 file data: ${e.message}",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
            op.textDiffPayload != null -> {
                op.textDiffPayload.toByteArray(Charsets.UTF_8)
            }
            else -> ByteArray(0) // Create empty file
        }

        try {
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(contentBytes)

            // Permissions if provided
            val mode = op.permissionMode ?: "0644"
            val isExec = mode.endsWith("5") || mode.endsWith("7") || mode.endsWith("1") || mode.contains("755")
            val isWrite = mode.contains("6") || mode.contains("7")
            targetFile.setReadable(true, false)
            targetFile.setWritable(isWrite, false)
            targetFile.setExecutable(isExec, false)

            val newHash = SnapshotManager.calculateSha256(targetFile)

            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "Added new file ${op.targetPath} (${contentBytes.size} bytes)",
                oldHash = null,
                newHash = newHash,
                oldSize = 0L,
                newSize = targetFile.length(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Failed to create file: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
