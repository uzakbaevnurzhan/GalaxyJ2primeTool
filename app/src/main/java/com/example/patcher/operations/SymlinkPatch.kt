package com.example.patcher.operations

import android.system.Os
import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import com.example.utils.SecurityUtil
import java.io.File

object SymlinkPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        var oldHash: String? = null
        var oldSize = 0L

        try {
            val linkFile = SecurityUtil.safeResolve(workspaceDir, op.targetPath)
            val action = op.symlinkAction ?: "CREATE"
            val target = op.symlinkTarget ?: ""

            oldHash = if (linkFile.exists()) SnapshotManager.calculateSha256(linkFile) else null
            oldSize = if (linkFile.exists()) linkFile.length() else 0L

            when (action) {
                "CREATE", "MODIFY" -> {
                    if (target.isBlank()) {
                        return SinglePatchExecutionResult(
                            operationId = op.id,
                            success = false,
                            message = "Symlink target cannot be empty",
                            oldHash = oldHash,
                            oldSize = oldSize,
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }

                    linkFile.parentFile?.mkdirs()
                    if (linkFile.exists()) {
                        linkFile.delete()
                    }

                    try {
                        Os.symlink(target, linkFile.absolutePath)
                    } catch (e: Exception) {
                        return SinglePatchExecutionResult(
                            operationId = op.id,
                            success = false,
                            message = "Failed to create authentic symlink using Os.symlink: ${e.message}",
                            oldHash = oldHash,
                            oldSize = oldSize,
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }

                    // Record symlink metadata in workspace metadata/symlinks.txt
                    val metaFile = File(workspaceDir, "metadata/symlinks.txt")
                    metaFile.parentFile?.mkdirs()
                    
                    // Filter old entry if exists
                    val existing = if (metaFile.exists()) metaFile.readLines().filterNot { it.startsWith("${op.targetPath} ") } else emptyList()
                    val newLines = existing + "${op.targetPath} $target"
                    metaFile.writeText(newLines.joinToString("\n") + "\n")

                    val newHash = SnapshotManager.calculateSha256(linkFile)

                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = true,
                        message = "Symlink created/updated: ${op.targetPath} -> $target",
                        oldHash = oldHash,
                        newHash = newHash,
                        oldSize = oldSize,
                        newSize = linkFile.length(),
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                "DELETE" -> {
                    if (linkFile.exists()) {
                        linkFile.delete()
                    }
                    val metaFile = File(workspaceDir, "metadata/symlinks.txt")
                    if (metaFile.exists()) {
                        val remaining = metaFile.readLines().filterNot { it.startsWith("${op.targetPath} ") }
                        metaFile.writeText(remaining.joinToString("\n") + "\n")
                    }
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = true,
                        message = "Symlink removed: ${op.targetPath}",
                        oldHash = oldHash,
                        newHash = null,
                        oldSize = oldSize,
                        newSize = 0L,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                else -> {
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Unknown symlink action: $action",
                        oldHash = oldHash,
                        oldSize = oldSize,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Failed to handle symlink: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
