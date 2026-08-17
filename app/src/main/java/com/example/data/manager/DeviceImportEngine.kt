package com.example.data.manager

import android.content.Context
import android.os.Build
import com.example.data.model.DeviceSnapshot
import com.example.data.model.PartitionSnapshotItem
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceImportEngine {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun importDeviceAsProject(
        context: Context,
        projectName: String,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): RomProject = withContext(Dispatchers.IO) {
        onProgress("Initializing Project Workspace...", 0.05f)
        val isRooted = RootShell.isRootAvailable()
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val finalName = if (projectName.isNotBlank()) projectName else "Device_${Build.MODEL}_$dateStr"

        val project = WorkspaceManager.createProject(context, finalName)
        val projectDir = File(project.rootPath)
        val metadataDir = File(projectDir, "metadata")
        val snapshotsDir = File(projectDir, "snapshots")
        val logsDir = File(projectDir, "logs")
        val partitionsDir = File(projectDir, "partitions")
        snapshotsDir.mkdirs()
        logsDir.mkdirs()
        partitionsDir.mkdirs()

        // 1. Extract System Properties
        onProgress("Extracting System Properties (getprop)...", 0.15f)
        val propsMap = mutableMapOf<String, String>()
        val getpropOut = RootShell.executeCommand("getprop").getOrNull() ?: ""
        val propLines = getpropOut.lines()
        for (line in propLines) {
            val match = Regex("""\[(.*?)\]:\s*\[(.*?)\]""").find(line)
            if (match != null) {
                propsMap[match.groupValues[1]] = match.groupValues[2]
            }
        }
        File(metadataDir, "build.prop.dump").writeText(getpropOut)

        // 2. Extract Kernel & Cmdline
        onProgress("Extracting Kernel & Boot Parameters...", 0.30f)
        val kernelVersion = RootShell.executeCommand("cat /proc/version").getOrNull() 
            ?: System.getProperty("os.version") ?: "Linux Unknown"
        val kernelCmdline = RootShell.executeCommand("cat /proc/cmdline").getOrNull() ?: "Unavailable"
        File(metadataDir, "proc_version.txt").writeText(kernelVersion)
        File(metadataDir, "proc_cmdline.txt").writeText(kernelCmdline)

        // 3. Extract Partitions Evidence (Non-destructive metadata)
        onProgress("Scanning Partition Tables & Block Devices...", 0.45f)
        val partitions = mutableListOf<PartitionSnapshotItem>()
        val procPartitions = RootShell.executeCommand("cat /proc/partitions").getOrNull() ?: ""
        File(metadataDir, "proc_partitions.txt").writeText(procPartitions)
        
        procPartitions.lines().drop(2).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                val blocks = parts[2].toLongOrNull() ?: 0L
                val name = parts[3]
                partitions.add(
                    PartitionSnapshotItem(
                        name = name,
                        sizeBytes = blocks * 1024,
                        mountPoint = null,
                        fsType = null
                    )
                )
            }
        }

        // By-name link scan if root available
        val byNameListing = RootShell.executeCommand("ls -la /dev/block/by-name/ /dev/block/platform/*/by-name/").getOrNull() ?: ""
        if (byNameListing.isNotBlank()) {
            File(metadataDir, "by_name_blocks.txt").writeText(byNameListing)
        }

        // 4. Extract SELinux & Security State
        onProgress("Auditing SELinux & Security Modules...", 0.60f)
        val selinuxMode = RootShell.executeCommand("getenforce").getOrNull() ?: "Enforcing (Default)"
        File(metadataDir, "selinux_mode.txt").writeText(selinuxMode)

        // 5. Extract HAL & Service Manager Evidence
        onProgress("Querying HAL Services & Service Matrix...", 0.75f)
        val lshalOut = RootShell.executeCommand("lshal").getOrNull() ?: ""
        val serviceListOut = RootShell.executeCommand("service list").getOrNull() ?: ""
        File(metadataDir, "lshal.txt").writeText(lshalOut)
        File(metadataDir, "service_list.txt").writeText(serviceListOut)

        val halServices = mutableListOf<String>()
        if (lshalOut.isNotBlank()) {
            lshalOut.lines().filter { it.contains("android.hardware.") }.forEach {
                val serviceName = it.trim().split(Regex("\\s+")).firstOrNull() ?: ""
                if (serviceName.isNotEmpty() && !halServices.contains(serviceName)) {
                    halServices.add(serviceName)
                }
            }
        }

        // 6. Extract RIL Telephony Metadata
        onProgress("Checking RIL & Telephony Stack...", 0.85f)
        val rilProps = propsMap.filter { it.key.startsWith("gsm.") || it.key.startsWith("ril.") || it.key.startsWith("telephony.") }
        File(metadataDir, "ril_metadata.json").writeText(json.encodeToString(rilProps))

        // 7. Extract System Logs snapshot (logcat last 500 lines, dmesg)
        onProgress("Capturing Initial Diagnostic Logs...", 0.90f)
        val logcatDump = RootShell.executeCommand("logcat -d -t 500").getOrNull() ?: ""
        val dmesgDump = RootShell.executeCommand("dmesg").getOrNull() ?: ""
        File(logsDir, "initial_logcat.txt").writeText(logcatDump)
        File(logsDir, "initial_dmesg.txt").writeText(dmesgDump)

        // 8. Create Initial Device Snapshot
        onProgress("Generating Baseline Device Snapshot...", 0.95f)
        val snapshot = DeviceSnapshot(
            id = "snapshot_initial_$timestamp",
            projectId = project.id,
            name = "Baseline Device Import Snapshot",
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
            halServices = halServices,
            rilDetails = rilProps,
            notes = "Auto-generated upon importing physical device into project."
        )

        SnapshotManager.saveSnapshot(project.id, snapshot, context)

        onProgress("Import complete!", 1.0f)
        project
    }
}
