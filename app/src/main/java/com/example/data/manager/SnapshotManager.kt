package com.example.data.manager

import android.content.Context
import android.os.Build
import com.example.data.model.DeviceSnapshot
import com.example.data.model.DiffItem
import com.example.data.model.DiffStatus
import com.example.data.model.PartitionSnapshotItem
import com.example.data.model.SnapshotDiff
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object SnapshotManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun createLiveDeviceSnapshot(
        projectId: String,
        name: String,
        notes: String = "",
        context: Context
    ): DeviceSnapshot = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val isRooted = RootShell.isRootAvailable()

        val propsMap = mutableMapOf<String, String>()
        val propStr = RootShell.executeCommand("getprop").getOrNull() ?: ""
        propStr.lines().forEach { line ->
            val match = Regex("""\[(.*?)\]:\s*\[(.*?)\]""").find(line)
            if (match != null) {
                propsMap[match.groupValues[1]] = match.groupValues[2]
            }
        }

        val kernelVersion = RootShell.executeCommand("cat /proc/version").getOrNull()
            ?: System.getProperty("os.version") ?: "Linux Unknown"
        val kernelCmdline = RootShell.executeCommand("cat /proc/cmdline").getOrNull() ?: "Unavailable"
        val selinuxMode = RootShell.executeCommand("getenforce").getOrNull() ?: "Enforcing"

        val partitions = mutableListOf<PartitionSnapshotItem>()
        val procPartitions = RootShell.executeCommand("cat /proc/partitions").getOrNull() ?: ""
        procPartitions.lines().drop(2).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                val blocks = parts[2].toLongOrNull() ?: 0L
                val pName = parts[3]
                partitions.add(PartitionSnapshotItem(name = pName, sizeBytes = blocks * 1024))
            }
        }

        val snapshot = DeviceSnapshot(
            id = "snap_${timestamp}",
            projectId = projectId,
            name = name.ifBlank { "Snapshot ${System.currentTimeMillis()}" },
            timestamp = timestamp,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            buildDisplayId = Build.DISPLAY,
            fingerprint = Build.FINGERPRINT,
            kernelVersion = kernelVersion,
            kernelCmdline = kernelCmdline,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm32",
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            selinuxMode = selinuxMode,
            rootAvailable = isRooted,
            partitions = partitions,
            systemProperties = propsMap,
            notes = notes
        )

        saveSnapshot(projectId, snapshot, context)
        snapshot
    }

    suspend fun saveSnapshot(projectId: String, snapshot: DeviceSnapshot, context: Context) = withContext(Dispatchers.IO) {
        val snapshotsDir = File(context.filesDir, "rom_studio/$projectId/snapshots")
        snapshotsDir.mkdirs()
        val file = File(snapshotsDir, "${snapshot.id}.json")
        file.writeText(json.encodeToString(snapshot))
    }

    suspend fun getSnapshotsForProject(projectId: String, context: Context): List<DeviceSnapshot> = withContext(Dispatchers.IO) {
        val snapshotsDir = File(context.filesDir, "rom_studio/$projectId/snapshots")
        if (!snapshotsDir.exists()) return@withContext emptyList()
        snapshotsDir.listFiles { f -> f.extension == "json" }?.mapNotNull { file ->
            try {
                json.decodeFromString<DeviceSnapshot>(file.readText())
            } catch (e: Exception) {
                null
            }
        }?.sortedByDescending { it.timestamp } ?: emptyList()
    }

    fun compareSnapshots(before: DeviceSnapshot, after: DeviceSnapshot): SnapshotDiff {
        // Properties diff
        val allPropKeys = before.systemProperties.keys + after.systemProperties.keys
        val propDiffs = mutableListOf<DiffItem>()
        for (key in allPropKeys) {
            val valA = before.systemProperties[key]
            val valB = after.systemProperties[key]
            when {
                valA == null && valB != null -> propDiffs.add(DiffItem(key, null, valB, DiffStatus.ADDED))
                valA != null && valB == null -> propDiffs.add(DiffItem(key, valA, null, DiffStatus.REMOVED))
                valA != valB -> propDiffs.add(DiffItem(key, valA, valB, DiffStatus.MODIFIED))
            }
        }

        // Partitions diff
        val partA = before.partitions.associateBy { it.name }
        val partB = after.partitions.associateBy { it.name }
        val allPartNames = partA.keys + partB.keys
        val partDiffs = mutableListOf<DiffItem>()
        for (name in allPartNames) {
            val a = partA[name]
            val b = partB[name]
            when {
                a == null && b != null -> partDiffs.add(DiffItem(name, null, "${b.sizeBytes} B", DiffStatus.ADDED))
                a != null && b == null -> partDiffs.add(DiffItem(name, "${a.sizeBytes} B", null, DiffStatus.REMOVED))
                a != null && b != null && a.sizeBytes != b.sizeBytes ->
                    partDiffs.add(DiffItem(name, "${a.sizeBytes} B", "${b.sizeBytes} B", DiffStatus.MODIFIED))
            }
        }

        val selinuxDiff = if (before.selinuxMode != after.selinuxMode) {
            DiffItem("SELinux Mode", before.selinuxMode, after.selinuxMode, DiffStatus.MODIFIED)
        } else null

        val kernelDiff = if (before.kernelVersion != after.kernelVersion) {
            DiffItem("Kernel Version", before.kernelVersion, after.kernelVersion, DiffStatus.MODIFIED)
        } else null

        val summary = "Diff between '${before.name}' and '${after.name}': " +
                "${propDiffs.size} property changes, ${partDiffs.size} partition changes."

        return SnapshotDiff(
            timestampA = before.timestamp,
            timestampB = after.timestamp,
            propertyDiffs = propDiffs,
            partitionDiffs = partDiffs,
            selinuxDiff = selinuxDiff,
            kernelDiff = kernelDiff,
            summary = summary
        )
    }
}
