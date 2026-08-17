package com.example.patcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val planId: String,
    val planName: String,
    val snapshotId: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // APPLIED, ROLLED_BACK, FAILED, PARTIAL, CANCELLED
    val affectedFilesCount: Int,
    val affectedFiles: List<String>,
    val operationsExecuted: List<SinglePatchExecutionResult>,
    val risk: PatchRisk,
    val details: String = ""
)

object PatchHistoryManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun getHistoryFile(workspaceRoot: File): File {
        val dir = File(workspaceRoot, "metadata/history")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "patch_history.json")
    }

    suspend fun recordHistory(workspaceRoot: File, entry: HistoryEntry) = withContext(Dispatchers.IO) {
        val file = getHistoryFile(workspaceRoot)
        val currentHistory = loadHistory(workspaceRoot).toMutableList()
        currentHistory.add(0, entry) // prepend latest
        file.writeText(json.encodeToString(currentHistory))
    }

    suspend fun updateEntryStatus(workspaceRoot: File, transactionId: String, newStatus: String, details: String = "") = withContext(Dispatchers.IO) {
        val file = getHistoryFile(workspaceRoot)
        val currentHistory = loadHistory(workspaceRoot).map {
            if (it.transactionId == transactionId) {
                it.copy(status = newStatus, details = if (details.isNotBlank()) "${it.details} | $details" else it.details)
            } else it
        }
        file.writeText(json.encodeToString(currentHistory))
    }

    suspend fun loadHistory(workspaceRoot: File): List<HistoryEntry> = withContext(Dispatchers.IO) {
        val file = getHistoryFile(workspaceRoot)
        if (!file.exists()) return@withContext emptyList()
        try {
            json.decodeFromString<List<HistoryEntry>>(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearHistory(workspaceRoot: File) = withContext(Dispatchers.IO) {
        val file = getHistoryFile(workspaceRoot)
        if (file.exists()) file.delete()
    }
}
