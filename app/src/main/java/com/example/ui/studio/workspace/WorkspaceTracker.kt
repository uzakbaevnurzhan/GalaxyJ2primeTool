package com.example.ui.studio.workspace

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object WorkspaceTracker {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun createSnapshot(project: RomProject) {
        val files = RomFileManager.scanWorkspace(project)
        val snapshot = files.associate { it.path to it.modifiedTime } // Simplified tracking, use hashes for deep
        val snapshotFile = File(project.rootPath, "metadata/snapshot.json")
        snapshotFile.writeText(json.encodeToString(snapshot))
    }

    fun getChanges(project: RomProject): Map<String, FileState> {
        val snapshotFile = File(project.rootPath, "metadata/snapshot.json")
        if (!snapshotFile.exists()) return emptyMap()

        val snapshot: Map<String, Long> = try {
            json.decodeFromString(snapshotFile.readText())
        } catch (e: Exception) {
            emptyMap()
        }

        val currentFiles = RomFileManager.scanWorkspace(project)
        val changes = mutableMapOf<String, FileState>()

        val currentMap = currentFiles.associate { it.path to it.modifiedTime }

        for ((path, modifiedTime) in currentMap) {
            val originalTime = snapshot[path]
            if (originalTime == null) {
                changes[path] = FileState.ADDED
            } else if (originalTime != modifiedTime) {
                changes[path] = FileState.MODIFIED
            }
        }

        for (path in snapshot.keys) {
            if (!currentMap.containsKey(path)) {
                changes[path] = FileState.DELETED
            }
        }

        return changes
    }
}
