package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File

object SymlinkPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val linkFile = File(workspaceDir, op.targetPath)
        val action = op.symlinkAction ?: "CREATE"
        val target = op.symlinkTarget ?: ""

        val oldHash = if (linkFile.exists()) SnapshotManager.calculateSha256(linkFile) else null
        val oldSize = if (linkFile.exists()) linkFile.length() else 0L

        try {
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

                    // Record symlink metadata in workspace metadata/symlinks.txt
                    val metaFile = File(workspaceDir, "metadata/symlinks.txt")
                    metaFile.parentFile?.mkdirs()
                    
                    // Filter old entry if exists
                    val existing = if (metaFile.exists()) metaFile.readLines().filterNot { it.startsWith("${op.targetPath} ") } else emptyList()
                    val newLines = existing + "${op.targetPath} $target"
                    metaFile.writeText(newLines.joinToString("\n") + "\n")

                    // Create placeholder marker file for workspace inspection
                    linkFile.writeText("SYMLINK -> $target\n", Charsets.UTF_8)
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
