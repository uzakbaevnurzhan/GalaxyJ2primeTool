package com.example.ui.root

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class RootStatus {
    ROOT_AVAILABLE,
    ROOT_DENIED,
    NO_ROOT,
    UNKNOWN
}

enum class CapabilityState {
    GRANTED,
    DENIED,
    UNKNOWN
}

data class RootCapability(
    val id: String,
    val name: String,
    val description: String,
    val state: CapabilityState,
    val testCommand: String
)

data class PartitionItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val filesystem: String,
    val mountPoint: String?,
    val isReadOnly: Boolean
)

data class RootModule(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val enabled: Boolean,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootCenterScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rootStatus by remember { mutableStateOf(RootStatus.UNKNOWN) }
    var capabilities by remember { mutableStateOf<List<RootCapability>>(emptyList()) }
    var partitions by remember { mutableStateOf<List<PartitionItem>>(emptyList()) }
    var modules by remember { mutableStateOf<List<RootModule>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) }
    var isChecking by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }
    
    // Backup state
    var backingUpPartition by remember { mutableStateOf<String?>(null) }
    var backupProgressMessage by remember { mutableStateOf("") }

    fun refreshRootState() {
        coroutineScope.launch(Dispatchers.IO) {
            isChecking = true
            statusMessage = "Probing root privileges..."
            
            // 1. Check real privileged execution
            val rootRes = RootShell.executeCommand("id")
            val isAvail = rootRes.isSuccess && rootRes.getOrNull()?.contains("uid=0") == true
            rootStatus = if (isAvail) {
                RootStatus.ROOT_AVAILABLE
            } else if (File("/system/bin/su").exists() || File("/system/xbin/su").exists()) {
                RootStatus.ROOT_DENIED
            } else {
                RootStatus.NO_ROOT
            }

            // 2. Check capabilities
            val caps = mutableListOf<RootCapability>()
            val capDefs = listOf(
                Triple("READ_SYSTEM", "Read /system & Props", "cat /system/build.prop || ls /system"),
                Triple("READ_VENDOR", "Read /vendor Partition", "ls /vendor"),
                Triple("READ_PARTITIONS", "Read /proc/partitions", "cat /proc/partitions"),
                Triple("READ_BLOCK_DEVICES", "Read Block Devices", "ls -l /dev/block/by-name/ || ls -l /dev/block/bootdevice/by-name/"),
                Triple("READ_PROC", "Read /proc Filesystem", "cat /proc/cmdline"),
                Triple("READ_SYS", "Read /sys Hardware Tree", "ls /sys/class"),
                Triple("READ_LOGS", "Read Kernel & Radio Logs", "dmesg | head -n 5"),
                Triple("READ_PROPERTIES", "Read System Properties", "getprop ro.build.version.release"),
                Triple("READ_SERVICES", "Inspect Binder Services", "service list"),
                Triple("READ_PROCESSES", "Inspect Process Table", "ps || ps -A"),
                Triple("READ_DEVICE_TREE", "Read Device Tree (DTB)", "ls /sys/firmware/devicetree/base || ls /proc/device-tree"),
                Triple("BACKUP_PARTITIONS", "Raw Block Device Stream", "ls /dev/block"),
                Triple("EXECUTE_ROOT_COMMAND", "Privileged Shell Execution", "echo 'root_ok'")
            )

            for ((id, name, cmd) in capDefs) {
                val state = if (!isAvail) {
                    CapabilityState.DENIED
                } else {
                    val res = RootShell.executeCommand(cmd)
                    if (res.isSuccess && res.getOrNull()?.isNotBlank() == true) {
                        CapabilityState.GRANTED
                    } else {
                        CapabilityState.DENIED
                    }
                }
                caps.add(RootCapability(id, name, "Probe test: $cmd", state, cmd))
            }
            capabilities = caps

            // 3. Scan Partitions
            val partList = mutableListOf<PartitionItem>()
            if (isAvail) {
                // Try finding by-name
                val byNameRes = RootShell.executeCommand("ls -l /dev/block/by-name/ 2>/dev/null || ls -l /dev/block/bootdevice/by-name/ 2>/dev/null || ls -l /dev/block/platform/*/by-name/ 2>/dev/null")
                val mountsRes = RootShell.executeCommand("mount").getOrNull() ?: ""
                
                if (byNameRes.isSuccess && byNameRes.getOrNull()?.isNotBlank() == true) {
                    val lines = byNameRes.getOrNull()!!.lines()
                    lines.forEach { line ->
                        if (line.contains("->")) {
                            val parts = line.split("->")
                            val namePart = parts[0].trim().substringAfterLast(" ")
                            val targetPart = parts[1].trim()
                            
                            val mountLine = mountsRes.lines().firstOrNull { it.contains(namePart) || it.contains(targetPart) }
                            val fs = mountLine?.split("\\s+".toRegex())?.getOrNull(2) ?: "raw"
                            val mp = mountLine?.split("\\s+".toRegex())?.getOrNull(1)
                            val isRo = mountLine?.contains("ro,") == true || mountLine?.contains("(ro") == true

                            partList.add(
                                PartitionItem(
                                    name = namePart,
                                    path = targetPart,
                                    sizeBytes = 0L,
                                    filesystem = fs,
                                    mountPoint = mp,
                                    isReadOnly = isRo
                                )
                            )
                        }
                    }
                }
            }
            partitions = partList.distinctBy { it.name }

            // 4. Scan Magisk / KSU Modules
            val modTmp = mutableListOf<RootModule>()
            if (isAvail) {
                val modDirs = listOf("/data/adb/modules", "/data/adb/ksu/modules", "/data/adb/ap/modules")
                modDirs.forEach { dir ->
                    val lsRes = RootShell.executeCommand("ls $dir").getOrNull() ?: ""
                    lsRes.lines().filter { it.isNotBlank() }.forEach { modName ->
                        val propContent = RootShell.executeCommand("cat $dir/$modName/module.prop").getOrNull() ?: ""
                        val disableExists = RootShell.executeCommand("test -f $dir/$modName/disable && echo 'disabled'").getOrNull()?.contains("disabled") == true
                        
                        var id = modName
                        var name = modName
                        var version = "1.0"
                        var author = "Unknown"
                        var desc = ""

                        propContent.lines().forEach { pLine ->
                            val p = pLine.split("=", limit = 2)
                            if (p.size == 2) {
                                when (p[0].trim()) {
                                    "id" -> id = p[1].trim()
                                    "name" -> name = p[1].trim()
                                    "version" -> version = p[1].trim()
                                    "author" -> author = p[1].trim()
                                    "description" -> desc = p[1].trim()
                                }
                            }
                        }
                        modTmp.add(
                            RootModule(
                                id = id,
                                name = name,
                                version = version,
                                author = author,
                                description = desc,
                                enabled = !disableExists,
                                path = "$dir/$modName"
                            )
                        )
                    }
                }
            }
            modules = modTmp

            withContext(Dispatchers.Main) {
                isChecking = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshRootState()
    }

    fun performPartitionBackup(partition: PartitionItem) {
        coroutineScope.launch(Dispatchers.IO) {
            backingUpPartition = partition.name
            backupProgressMessage = "Dumping ${partition.name} safely..."

            val backupDir = File(context.filesDir, "backups/partitions")
            backupDir.mkdirs()
            val outFile = File(backupDir, "${partition.name}.img")

            val dumpCmd = "dd if=${partition.path} of=${outFile.absolutePath} bs=4096"
            val res = RootShell.executeCommand(dumpCmd)

            if (res.isSuccess && outFile.exists() && outFile.length() > 0) {
                // Calculate SHA-256
                backupProgressMessage = "Calculating SHA-256 integrity hash..."
                val digest = MessageDigest.getInstance("SHA-256")
                outFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                val shaFile = File(backupDir, "${partition.name}.img.sha256")
                shaFile.writeText("$sha256  ${partition.name}.img\n")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup complete: ${outFile.name} (${outFile.length() / 1024} KB)", Toast.LENGTH_LONG).show()
                    backingUpPartition = null
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    backingUpPartition = null
                }
            }
        }
    }

    val tabs = listOf("Capabilities", "Partitions (${partitions.size})", "Modules (${modules.size})", "Root Audit")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Root Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshRootState() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header Status Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (rootStatus) {
                        RootStatus.ROOT_AVAILABLE -> MaterialTheme.colorScheme.primaryContainer
                        RootStatus.ROOT_DENIED -> MaterialTheme.colorScheme.errorContainer
                        RootStatus.NO_ROOT -> MaterialTheme.colorScheme.surfaceVariant
                        RootStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (rootStatus) {
                            RootStatus.ROOT_AVAILABLE -> Icons.Filled.CheckCircle
                            RootStatus.ROOT_DENIED -> Icons.Filled.Cancel
                            RootStatus.NO_ROOT -> Icons.Filled.Info
                            RootStatus.UNKNOWN -> Icons.AutoMirrored.Filled.Help
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = when (rootStatus) {
                            RootStatus.ROOT_AVAILABLE -> MaterialTheme.colorScheme.primary
                            RootStatus.ROOT_DENIED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = when (rootStatus) {
                                RootStatus.ROOT_AVAILABLE -> "ROOT PRIVILEGES ACTIVE"
                                RootStatus.ROOT_DENIED -> "ROOT BINARY FOUND BUT ACCESS DENIED"
                                RootStatus.NO_ROOT -> "NO ROOT ACCESS"
                                RootStatus.UNKNOWN -> "CHECKING ROOT..."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (rootStatus == RootStatus.ROOT_AVAILABLE)
                                "Verified via su -c id (uid=0). All kernel & block operations unlocked."
                            else
                                "App is running in standard unprivileged sandbox. Some low-level tools are restricted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tabs
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1) }
                    )
                }
            }

            if (isChecking) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(statusMessage)
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        // Capabilities Tab
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(capabilities) { cap ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(cap.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(cap.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when (cap.state) {
                                                CapabilityState.GRANTED -> MaterialTheme.colorScheme.primaryContainer
                                                CapabilityState.DENIED -> MaterialTheme.colorScheme.errorContainer
                                                CapabilityState.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = cap.state.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = when (cap.state) {
                                                    CapabilityState.GRANTED -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    CapabilityState.DENIED -> MaterialTheme.colorScheme.onErrorContainer
                                                    CapabilityState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Partitions Tab
                        if (partitions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (rootStatus == RootStatus.ROOT_AVAILABLE) "No by-name block partitions discovered" else "Root access required to scan block devices",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(partitions) { part ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(part.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                FilledTonalButton(
                                                    onClick = { performPartitionBackup(part) },
                                                    enabled = backingUpPartition == null,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    if (backingUpPartition == part.name) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Backup")
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Target Block: ${part.path}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                            Text("FS: ${part.filesystem} | Mount: ${part.mountPoint ?: "Unmounted"} | ${if (part.isReadOnly) "Read-Only" else "Read-Write"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Modules Tab
                        if (modules.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (rootStatus == RootStatus.ROOT_AVAILABLE) "No Magisk / KernelSU modules installed in /data/adb/modules" else "Root access required to manage root modules",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(modules) { mod ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(mod.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    Text("v${mod.version} by ${mod.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (mod.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = if (mod.enabled) "ACTIVE" else "DISABLED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (mod.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            if (mod.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(mod.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Audit Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text("Full Root Device Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Run a deep security, kernel & filesystem audit across all mounted blocks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    navController.navigate("report_generator")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Assessment, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Full System Audit Report")
                            }
                        }
                    }
                }
            }
        }
    }
}
