package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FilePatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = File(workspaceDir, op.targetPath)

        if (!targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target file does not exist to replace: ${op.targetPath}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val oldHash = SnapshotManager.calculateSha256(targetFile)
        val oldSize = targetFile.length()

        // 1. Verify expected target hash if specified
        if (op.expectedTargetHash != null && op.expectedTargetHash != oldHash) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target file hash mismatch. Expected: ${op.expectedTargetHash}, Actual: $oldHash",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // 2. Prepare replacement bytes/file
        val replacementBytes: ByteArray = when {
            op.sourceFilePath != null -> {
                val srcFile = File(op.sourceFilePath)
                if (!srcFile.exists()) {
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Source replacement file not found: ${op.sourceFilePath}",
                        oldHash = oldHash,
                        oldSize = oldSize,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                srcFile.readBytes()
            }
            op.sourceContentBase64 != null -> {
                try {
                    android.util.Base64.decode(op.sourceContentBase64, android.util.Base64.DEFAULT)
                } catch (e: Exception) {
                    return SinglePatchExecutionResult(
                        operationId = op.id,
                        success = false,
                        message = "Failed to decode base64 replacement data: ${e.message}",
                        oldHash = oldHash,
                        oldSize = oldSize,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }
            else -> {
                return SinglePatchExecutionResult(
                    operationId = op.id,
                    success = false,
                    message = "No replacement source file or base64 data provided",
                    oldHash = oldHash,
                    oldSize = oldSize,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // 3. ELF ABI validation if target or source is ELF
        val isTargetElf = isElfBinary(targetFile)
        val isSourceElf = isElfBinary(replacementBytes)
        if (isTargetElf && isSourceElf) {
            val targetArch = getElfMachine(targetFile.readBytes())
            val sourceArch = getElfMachine(replacementBytes)
            if (targetArch != null && sourceArch != null && targetArch != sourceArch) {
                return SinglePatchExecutionResult(
                    operationId = op.id,
                    success = false,
                    message = "ELF ABI Incompatibility: Target is $targetArch (machine $targetArch) but replacement is $sourceArch. Replacement BLOCKED.",
                    oldHash = oldHash,
                    oldSize = oldSize,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // 4. Write replacement file
        try {
            targetFile.writeBytes(replacementBytes)
            val newHash = SnapshotManager.calculateSha256(targetFile)

            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "Replaced ${op.targetPath} (${replacementBytes.size} bytes written)",
                oldHash = oldHash,
                newHash = newHash,
                oldSize = oldSize,
                newSize = targetFile.length(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Failed to write replacement file: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    fun isElfBinary(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            val header = ByteArray(4)
            FileInputStream(file).use { it.read(header) }
            header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
        } catch (_: Exception) {
            false
        }
    }

    fun isElfBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        return bytes[0] == 0x7F.toByte() && bytes[1] == 'E'.code.toByte() && bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
    }

    fun getElfMachine(bytes: ByteArray): Int? {
        if (bytes.size < 20) return null
        if (!isElfBinary(bytes)) return null
        val endian = bytes[5].toInt()
        val order = if (endian == 2) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val buffer = ByteBuffer.wrap(bytes, 16, 4).order(order)
        buffer.short // e_type
        return buffer.short.toInt() and 0xFFFF
    }
}
