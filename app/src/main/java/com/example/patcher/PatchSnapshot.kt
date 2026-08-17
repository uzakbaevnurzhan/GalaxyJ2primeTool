package com.example.patcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

@Serializable
data class SnapshotFileEntry(
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val isDirectory: Boolean = false,
    val unixMode: String? = null,
    val isSymlink: Boolean = false,
    val symlinkTarget: String? = null,
    val backupStoredPath: String? = null // relative inside snapshot directory
)

@Serializable
data class PatchSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalFilesTracked: Int = 0,
    val totalSizeBytes: Long = 0L,
    val files: List<SnapshotFileEntry> = emptyList(),
    val triggerReason: String = "Manual Snapshot"
)

object SnapshotManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun getSnapshotsDir(workspaceRoot: File): File {
        val dir = File(workspaceRoot, "metadata/snapshots")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun createSnapshot(
        workspaceRoot: File,
        projectId: String,
        name: String,
        description: String = "",
        triggerReason: String = "Manual Snapshot",
        filterRelativePaths: Set<String>? = null // If null, creates full snapshot of workspace
    ): PatchSnapshot = withContext(Dispatchers.IO) {
        val snapshotId = UUID.randomUUID().toString()
        val snapshotsDir = getSnapshotsDir(workspaceRoot)
        val snapshotStorageDir = File(snapshotsDir, snapshotId)
        val filesBackupDir = File(snapshotStorageDir, "files")
        filesBackupDir.mkdirs()

        val entries = mutableListOf<SnapshotFileEntry>()
        var totalBytes = 0L

        val workspaceDir = File(workspaceRoot, "workspace")
        if (workspaceDir.exists()) {
            if (filterRelativePaths != null) {
                // Targeted snapshot for specific files
                for (relPath in filterRelativePaths) {
                    val targetFile = File(workspaceDir, relPath)
                    if (targetFile.exists()) {
                        val entry = backupFile(workspaceDir, targetFile, filesBackupDir)
                        if (entry != null) {
                            entries.add(entry)
                            totalBytes += entry.sizeBytes
                        }
                    } else {
                        // File did not exist originally (was newly added or pending)
                        entries.add(
                            SnapshotFileEntry(
                                relativePath = relPath,
                                sha256 = "NOT_EXISTS",
                                sizeBytes = 0L,
                                backupStoredPath = null
                            )
                        )
                    }
                }
            } else {
                // Full workspace snapshot
                workspaceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val entry = backupFile(workspaceDir, file, filesBackupDir)
                        if (entry != null) {
                            entries.add(entry)
                            totalBytes += entry.sizeBytes
                        }
                    }
                }
            }
        }

        val snapshot = PatchSnapshot(
            id = snapshotId,
            name = name,
            description = description,
            projectId = projectId,
            timestamp = System.currentTimeMillis(),
            totalFilesTracked = entries.size,
            totalSizeBytes = totalBytes,
            files = entries,
            triggerReason = triggerReason
        )

        // Save metadata
        val metaFile = File(snapshotStorageDir, "snapshot.json")
        metaFile.writeText(json.encodeToString(snapshot))

        snapshot
    }

    private fun backupFile(workspaceDir: File, file: File, filesBackupDir: File): SnapshotFileEntry? {
        if (!file.exists()) return null
        val relPath = file.relativeTo(workspaceDir).path
        val hash = calculateSha256(file)
        val size = file.length()
        
        val backupDest = File(filesBackupDir, relPath)
        backupDest.parentFile?.mkdirs()
        
        file.copyTo(backupDest, overwrite = true)

        return SnapshotFileEntry(
            relativePath = relPath,
            sha256 = hash,
            sizeBytes = size,
            isDirectory = file.isDirectory,
            backupStoredPath = "files/$relPath"
        )
    }

    suspend fun listSnapshots(workspaceRoot: File): List<PatchSnapshot> = withContext(Dispatchers.IO) {
        val snapshotsDir = getSnapshotsDir(workspaceRoot)
        if (!snapshotsDir.exists()) return@withContext emptyList()

        val list = mutableListOf<PatchSnapshot>()
        snapshotsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val metaFile = File(dir, "snapshot.json")
            if (metaFile.exists()) {
                try {
                    val snap = json.decodeFromString<PatchSnapshot>(metaFile.readText())
                    list.add(snap)
                } catch (_: Exception) {}
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    suspend fun restoreSnapshot(
        workspaceRoot: File,
        snapshotId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val snapshotsDir = getSnapshotsDir(workspaceRoot)
        val snapshotStorageDir = File(snapshotsDir, snapshotId)
        val metaFile = File(snapshotStorageDir, "snapshot.json")
        if (!metaFile.exists()) return@withContext false

        val snapshot = try {
            json.decodeFromString<PatchSnapshot>(metaFile.readText())
        } catch (e: Exception) {
            return@withContext false
        }

        val workspaceDir = File(workspaceRoot, "workspace")
        if (!workspaceDir.exists()) workspaceDir.mkdirs()

        for (entry in snapshot.files) {
            val targetFile = File(workspaceDir, entry.relativePath)
            if (entry.sha256 == "NOT_EXISTS") {
                // Originally did not exist, so remove if created
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            } else if (entry.backupStoredPath != null) {
                val backupFile = File(snapshotStorageDir, entry.backupStoredPath)
                if (backupFile.exists()) {
                    targetFile.parentFile?.mkdirs()
                    backupFile.copyTo(targetFile, overwrite = true)
                }
            }
        }
        true
    }

    suspend fun deleteSnapshot(workspaceRoot: File, snapshotId: String): Boolean = withContext(Dispatchers.IO) {
        val snapshotsDir = getSnapshotsDir(workspaceRoot)
        val snapshotStorageDir = File(snapshotsDir, snapshotId)
        if (snapshotStorageDir.exists()) {
            snapshotStorageDir.deleteRecursively()
        } else {
            false
        }
    }

    fun calculateSha256(file: File): String {
        if (!file.exists() || !file.isFile) return "0000000000000000000000000000000000000000000000000000000000000000"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { fis ->
                var read = fis.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = fis.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "error_sha256_${e.message}"
        }
    }
}
