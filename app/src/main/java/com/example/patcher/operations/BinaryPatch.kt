package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File
import java.io.RandomAccessFile

object BinaryPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = com.example.utils.SecurityUtil.safeResolve(workspaceDir, op.targetPath)

        if (!targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target binary file does not exist: ${op.targetPath}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val oldHash = SnapshotManager.calculateSha256(targetFile)
        val oldSize = targetFile.length()

        val offset = op.binaryOffset
        if (offset == null || offset < 0) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Invalid binary patch offset: $offset",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val expectedOldBytes = hexToByteArray(op.binaryExpectedOldHex ?: "")
        val newBytes = hexToByteArray(op.binaryNewHex ?: "")

        if (newBytes.isEmpty()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "New bytes payload is empty",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (offset + expectedOldBytes.size > oldSize) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Patch offset + length ($offset + ${expectedOldBytes.size}) exceeds file size ($oldSize)",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            RandomAccessFile(targetFile, "rw").use { raf ->
                // Verify old bytes if provided
                if (expectedOldBytes.isNotEmpty()) {
                    raf.seek(offset)
                    val readBuffer = ByteArray(expectedOldBytes.size)
                    raf.readFully(readBuffer)
                    if (!readBuffer.contentEquals(expectedOldBytes)) {
                        val actualHex = byteArrayToHex(readBuffer)
                        val expectedHex = byteArrayToHex(expectedOldBytes)
                        return SinglePatchExecutionResult(
                            operationId = op.id,
                            success = false,
                            message = "Binary verification failed at offset 0x${offset.toString(16)}. Expected: $expectedHex, Actual: $actualHex",
                            oldHash = oldHash,
                            oldSize = oldSize,
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                }

                // Write new bytes
                raf.seek(offset)
                raf.write(newBytes)
            }

            val newHash = SnapshotManager.calculateSha256(targetFile)

            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "Binary patch applied at offset 0x${offset.toString(16)} (${newBytes.size} bytes modified)",
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
                message = "Binary write failed: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "").replace("0x", "").trim()
        if (cleanHex.isEmpty()) return ByteArray(0)
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}
