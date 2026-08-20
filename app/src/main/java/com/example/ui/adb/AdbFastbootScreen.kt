package com.example.ui.adb

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.manager.ToolExecutionResult
import com.example.data.manager.ToolMetadata
import com.example.data.manager.ToolRegistry
import com.example.data.manager.ToolStatus
import com.example.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CommandRisk {
    SAFE,
    WARNING,
    DANGEROUS
}

data class AdbCommandPreset(
    val title: String,
    val command: String,
    val tool: String, // ADB or FASTBOOT or SHELL
    val args: List<String>,
    val risk: CommandRisk,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbFastbootScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var currentCommand by remember { mutableStateOf("") }
    var activeToolMode by remember { mutableStateOf("ADB") } // ADB, FASTBOOT, SHELL
    var isExecuting by remember { mutableStateOf(false) }
    var terminalLogs by remember { mutableStateOf<List<ToolExecutionResult>>(emptyList()) }
    var pendingConfirmationCommand by remember { mutableStateOf<AdbCommandPreset?>(null) }

    var adbMeta by remember { mutableStateOf<ToolMetadata?>(null) }
    var fastbootMeta by remember { mutableStateOf<ToolMetadata?>(null) }

    fun refreshToolStatus() {
        coroutineScope.launch {
            adbMeta = ToolRegistry.probeTool(context, "adb")
            fastbootMeta = ToolRegistry.probeTool(context, "fastboot")
        }
    }

    LaunchedEffect(Unit) {
        refreshToolStatus()
    }

    val presets = listOf(
        AdbCommandPreset("ADB Devices", "adb devices -l", "ADB", listOf("devices", "-l"), CommandRisk.SAFE, "Enumerate attached ADB devices with qualifiers"),
        AdbCommandPreset("ADB Get State", "adb get-state", "ADB", listOf("get-state"), CommandRisk.SAFE, "Query active device connection state"),
        AdbCommandPreset("ADB Serial Number", "adb get-serialno", "ADB", listOf("get-serialno"), CommandRisk.SAFE, "Read hardware serial number via daemon"),
        AdbCommandPreset("Logcat Snapshot", "logcat -d -t 100", "SHELL", listOf("logcat", "-d", "-t", "100"), CommandRisk.SAFE, "Dump the most recent 100 lines of system logcat"),
        AdbCommandPreset("Kernel Dmesg", "dmesg", "SHELL", listOf("dmesg"), CommandRisk.SAFE, "Read Linux kernel ring buffer messages"),
        AdbCommandPreset("Reboot System", "reboot", "SHELL", listOf("reboot"), CommandRisk.WARNING, "Initiate standard device reboot"),
        AdbCommandPreset("Reboot Recovery", "reboot recovery", "SHELL", listOf("reboot", "recovery"), CommandRisk.WARNING, "Reboot into recovery partition (TWRP)"),
        AdbCommandPreset("Reboot Download", "reboot download", "SHELL", listOf("reboot", "download"), CommandRisk.WARNING, "Reboot Samsung device into Odin Download mode"),
        AdbCommandPreset("Fastboot Devices", "fastboot devices", "FASTBOOT", listOf("devices"), CommandRisk.SAFE, "List attached devices in bootloader mode"),
        AdbCommandPreset("Fastboot Getvar All", "fastboot getvar all", "FASTBOOT", listOf("getvar", "all"), CommandRisk.SAFE, "Query all Fastboot bootloader state variables"),
        AdbCommandPreset("Fastboot Reboot", "fastboot reboot", "FASTBOOT", listOf("reboot"), CommandRisk.WARNING, "Reboot device out of bootloader to system"),
        AdbCommandPreset("Erase Cache Partition", "fastboot erase cache", "FASTBOOT", listOf("erase", "cache"), CommandRisk.DANGEROUS, "Format cache partition via bootloader interface")
    )

    fun executePreset(preset: AdbCommandPreset) {
        coroutineScope.launch {
            isExecuting = true
            val result = if (preset.tool == "SHELL") {
                ToolRegistry.executeRawShell(preset.command, useRoot = true)
            } else {
                ToolRegistry.executeCommand(context, preset.tool.lowercase(), preset.args, useRootIfAvailable = true)
            }
            terminalLogs = terminalLogs + result
            isExecuting = false
            selectedTab = 0
        }
    }

    fun executeCustom(cmd: String) {
        if (cmd.isBlank()) return
        coroutineScope.launch {
            isExecuting = true
            val parts = cmd.trim().split("\\s+".toRegex())
            val first = parts.firstOrNull() ?: ""
            val result = when (activeToolMode) {
                "ADB" -> {
                    val args = if (first.equals("adb", ignoreCase = true)) parts.drop(1) else parts
                    ToolRegistry.executeCommand(context, "adb", args, useRootIfAvailable = true)
                }
                "FASTBOOT" -> {
                    val args = if (first.equals("fastboot", ignoreCase = true)) parts.drop(1) else parts
                    ToolRegistry.executeCommand(context, "fastboot", args, useRootIfAvailable = true)
                }
                else -> {
                    ToolRegistry.executeRawShell(cmd, useRoot = true)
                }
            }
            terminalLogs = terminalLogs + result
            currentCommand = ""
            isExecuting = false
        }
    }

    if (pendingConfirmationCommand != null) {
        val p = pendingConfirmationCommand!!
        AlertDialog(
            onDismissRequest = { pendingConfirmationCommand = null },
            title = { Text("Confirm ${p.risk.name} Action") },
            text = {
                Column {
                    Text("Command: ${p.command}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text(p.description)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Risk Level: ${p.risk.name}",
                        color = if (p.risk == CommandRisk.DANGEROUS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRun = p
                        pendingConfirmationCommand = null
                        executePreset(toRun)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (p.risk == CommandRisk.DANGEROUS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Execute")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingConfirmationCommand = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "ADB & Fastboot Studio",
                subtitle = "Active Mode: $activeToolMode",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { refreshToolStatus() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Status")
                    }
                    IconButton(onClick = { terminalLogs = emptyList() }) {
                        Icon(Icons.Filled.ClearAll, contentDescription = "Clear Terminal")
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
            // Backend Status Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ADB: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (adbMeta?.status == ToolStatus.MISSING_BACKEND) "Missing Backend" else "Ready (${adbMeta?.status?.name ?: "..."})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (adbMeta?.status == ToolStatus.MISSING_BACKEND) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fastboot: ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (fastbootMeta?.status == ToolStatus.MISSING_BACKEND) "Missing Backend" else "Ready (${fastbootMeta?.status?.name ?: "..."})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (fastbootMeta?.status == ToolStatus.MISSING_BACKEND) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Terminal") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Presets") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("History (${terminalLogs.size})") })
            }

            when (selectedTab) {
                0 -> {
                    // Terminal Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Tool mode selector chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("ADB", "FASTBOOT", "SHELL").forEach { mode ->
                                FilterChip(
                                    selected = activeToolMode == mode,
                                    onClick = { activeToolMode = mode },
                                    label = { Text(mode) }
                                )
                            }
                        }

                        // Terminal Output Box
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (terminalLogs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Terminal ready. Mode: $activeToolMode.",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Type a command below or select a Preset.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(terminalLogs) { log ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "$ ${log.command}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (log.isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        "exit ${log.exitCode} (${log.durationMs}ms)",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (log.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }

                                            if (log.stdout.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    log.stdout,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            if (log.stderr.isNotBlank()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    log.stderr,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }

                                            if (log.diagnosticDetails != null) {
                                                Spacer(Modifier.height(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(
                                                            log.diagnosticDetails,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        if (log.suggestedAction != null) {
                                                            Spacer(Modifier.height(4.dp))
                                                            Text(
                                                                log.suggestedAction,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(top = 8.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Input field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentCommand,
                                onValueChange = { currentCommand = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        when (activeToolMode) {
                                            "ADB" -> "e.g. devices, get-state, shell getprop"
                                            "FASTBOOT" -> "e.g. devices, getvar all, reboot"
                                            else -> "e.g. getprop, uname -a, ps, id"
                                        }
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { executeCustom(currentCommand) },
                                enabled = currentCommand.isNotBlank() && !isExecuting,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isExecuting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Presets
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(presets) { preset ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(preset.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (preset.risk) {
                                                    CommandRisk.SAFE -> MaterialTheme.colorScheme.primaryContainer
                                                    CommandRisk.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                                                    CommandRisk.DANGEROUS -> MaterialTheme.colorScheme.errorContainer
                                                }
                                            ) {
                                                Text(
                                                    text = preset.risk.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = when (preset.risk) {
                                                        CommandRisk.SAFE -> MaterialTheme.colorScheme.onPrimaryContainer
                                                        CommandRisk.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        CommandRisk.DANGEROUS -> MaterialTheme.colorScheme.onErrorContainer
                                                    }
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(preset.command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            if (preset.risk == CommandRisk.DANGEROUS || preset.risk == CommandRisk.WARNING) {
                                                pendingConfirmationCommand = preset
                                            } else {
                                                executePreset(preset)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Run")
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // History
                    if (terminalLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No executed commands in history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(terminalLogs.reversed()) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(log.command, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                            Text("Exit: ${log.exitCode} (${log.durationMs}ms)", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        val displayOut = if (log.stdout.isNotBlank()) log.stdout else log.stderr
                                        Text(displayOut.take(200) + if (displayOut.length > 200) "..." else "", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
