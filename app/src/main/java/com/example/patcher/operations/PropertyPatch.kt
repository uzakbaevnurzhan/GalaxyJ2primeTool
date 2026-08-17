package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import java.io.File

object PropertyPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = File(workspaceDir, op.targetPath)
        val oldHash = if (targetFile.exists()) SnapshotManager.calculateSha256(targetFile) else null
        val oldSize = if (targetFile.exists()) targetFile.length() else 0L

        val key = op.propertyKey
        if (key.isNullOrBlank()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Property key is missing",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        if (!targetFile.exists()) {
            // If setting or adding a property to a new file, create it
            if (op.propertyAction == "REMOVE") {
                return SinglePatchExecutionResult(
                    operationId = op.id,
                    success = false,
                    message = "Target file does not exist to remove property: ${op.targetPath}",
                    oldHash = oldHash,
                    oldSize = oldSize,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
            targetFile.parentFile?.mkdirs()
            targetFile.writeText("# Auto-generated property file\n$key=${op.propertyNewValue ?: ""}\n", Charsets.UTF_8)
            val newHash = SnapshotManager.calculateSha256(targetFile)
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "Created ${op.targetPath} with property $key=${op.propertyNewValue ?: ""}",
                oldHash = oldHash,
                newHash = newHash,
                oldSize = oldSize,
                newSize = targetFile.length(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val lines = targetFile.readLines(Charsets.UTF_8).toMutableList()
        val action = op.propertyAction ?: "SET"
        var keyFound = false
        val newLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#") && trimmed.contains("=")) {
                val pKey = trimmed.substringBefore("=").trim()
                if (pKey == key) {
                    keyFound = true
                    when (action) {
                        "REMOVE" -> {
                            // Skip line to remove
                            continue
                        }
                        "SET", "ADD" -> {
                            newLines.add("$key=${op.propertyNewValue ?: ""}")
                            continue
                        }
                    }
                }
            }
            newLines.add(line)
        }

        if (!keyFound && (action == "SET" || action == "ADD")) {
            // Append property
            newLines.add("$key=${op.propertyNewValue ?: ""}")
        }

        targetFile.writeText(newLines.joinToString("\n") + "\n", Charsets.UTF_8)
        val newHash = SnapshotManager.calculateSha256(targetFile)

        return SinglePatchExecutionResult(
            operationId = op.id,
            success = true,
            message = "Property $key ${if (action == "REMOVE") "removed from" else "set in"} ${op.targetPath}",
            oldHash = oldHash,
            newHash = newHash,
            oldSize = oldSize,
            newSize = targetFile.length(),
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    fun parseProperties(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val map = linkedMapOf<String, String>()
        file.readLines(Charsets.UTF_8).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                val k = trimmed.substringBefore("=").trim()
                val v = trimmed.substringAfter("=").trim()
                map[k] = v
            }
        }
        return map
    }
}
