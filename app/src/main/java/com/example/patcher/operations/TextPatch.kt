package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import java.io.File

object TextPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = File(workspaceDir, op.targetPath)
        
        if (!targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target file does not exist: ${op.targetPath}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val originalText = targetFile.readText(Charsets.UTF_8)
        val oldHash = com.example.patcher.SnapshotManager.calculateSha256(targetFile)
        val oldSize = targetFile.length()

        val newText: String = when {
            op.propertyOldValue != null && op.propertyNewValue != null -> {
                if (!originalText.contains(op.propertyOldValue)) {
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Old text block not found in file: ${op.targetPath}",
                        oldHash = oldHash,
                        oldSize = oldSize,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                originalText.replaceFirst(op.propertyOldValue, op.propertyNewValue)
            }
            op.textDiffPayload != null -> {
                // If entire new content or patch payload is provided
                op.textDiffPayload
            }
            else -> {
                return SinglePatchExecutionResult(
                    operationId = op.id,
                    success = false,
                    message = "No text change payload provided",
                    oldHash = oldHash,
                    oldSize = oldSize,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        targetFile.writeText(newText, Charsets.UTF_8)
        val newHash = com.example.patcher.SnapshotManager.calculateSha256(targetFile)

        return SinglePatchExecutionResult(
            operationId = op.id,
            success = true,
            message = "Text patch applied to ${op.targetPath}",
            oldHash = oldHash,
            newHash = newHash,
            oldSize = oldSize,
            newSize = targetFile.length(),
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    fun generateUnifiedDiff(original: String, modified: String, fileName: String): String {
        val origLines = original.lines()
        val modLines = modified.lines()
        val sb = StringBuilder()
        sb.append("--- a/$fileName\n")
        sb.append("+++ b/$fileName\n")
        
        var i = 0
        var j = 0
        while (i < origLines.size || j < modLines.size) {
            val oLine = origLines.getOrNull(i)
            val mLine = modLines.getOrNull(j)
            
            if (oLine == mLine) {
                sb.append(" $oLine\n")
                i++
                j++
            } else {
                if (oLine != null && !modLines.contains(oLine)) {
                    sb.append("-$oLine\n")
                    i++
                } else if (mLine != null && !origLines.contains(mLine)) {
                    sb.append("+$mLine\n")
                    j++
                } else {
                    if (oLine != null) {
                        sb.append("-$oLine\n")
                        i++
                    }
                    if (mLine != null) {
                        sb.append("+$mLine\n")
                        j++
                    }
                }
            }
        }
        return sb.toString()
    }
}
