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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.utils.RootShell
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
    val mode: String, // ADB or FASTBOOT or SHELL
    val risk: CommandRisk,
    val description: String
)

data class TerminalLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbFastbootScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedTab by remember { mutableStateOf(0) }
    var currentCommand by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var terminalLogs by remember { mutableStateOf<List<TerminalLogEntry>>(emptyList()) }
    var pendingConfirmationCommand by remember { mutableStateOf<AdbCommandPreset?>(null) }

    val presets = listOf(
        AdbCommandPreset("ADB Devices", "adb devices -l", "ADB", CommandRisk.SAFE, "Enumerate attached ADB devices"),
        AdbCommandPreset("ADB Get State", "adb get-state", "ADB", CommandRisk.SAFE, "Get device state (device, recovery, sideload)"),
        AdbCommandPreset("ADB Serial Number", "adb get-serialno", "ADB", CommandRisk.SAFE, "Get hardware serial number"),
        AdbCommandPreset("ADB Logcat Snapshot", "logcat -d -t 100", "ADB", CommandRisk.SAFE, "Read last 100 logcat lines"),
        AdbCommandPreset("ADB Dmesg Dump", "dmesg", "ADB", CommandRisk.SAFE, "Read kernel ring buffer messages"),
        AdbCommandPreset("Reboot System", "reboot", "ADB", CommandRisk.WARNING, "Reboot device immediately"),
        AdbCommandPreset("Reboot to Recovery", "reboot recovery", "ADB", CommandRisk.WARNING, "Reboot into recovery / TWRP"),
        AdbCommandPreset("Reboot to Bootloader/Download", "reboot bootloader || reboot download", "ADB", CommandRisk.WARNING, "Reboot to Odin / Download mode"),
        AdbCommandPreset("Fastboot Devices", "fastboot devices", "FASTBOOT", CommandRisk.SAFE, "Enumerate Fastboot devices"),
        AdbCommandPreset("Fastboot Getvar All", "fastboot getvar all", "FASTBOOT", CommandRisk.SAFE, "Query all Fastboot bootloader variables"),
        AdbCommandPreset("Fastboot Reboot", "fastboot reboot", "FASTBOOT", CommandRisk.WARNING, "Reboot from bootloader to system"),
        AdbCommandPreset("Erase Cache Partition", "fastboot erase cache", "FASTBOOT", CommandRisk.DANGEROUS, "Erase cache partition block")
    )

    fun executeRawCommand(cmd: String) {
        if (cmd.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            isExecuting = true
            val startTime = System.currentTimeMillis()
            
            var stdout = ""
            var stderr = ""
            var exitCode = 0

            try {
                // If device is rooted, run via root shell or runtime exec
                val isRoot = RootShell.isRootAvailable()
                val process = if (isRoot) {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                } else {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                }

                val outReader = process.inputStream.bufferedReader()
                val errReader = process.errorStream.bufferedReader()
                
                stdout = outReader.readText()
                stderr = errReader.readText()
                
                process.waitFor()
                exitCode = process.exitValue()
            } catch (e: Exception) {
                stderr = "Execution failed: ${e.message}"
                exitCode = -1
            }

            val duration = System.currentTimeMillis() - startTime
            val entry = TerminalLogEntry(
                command = cmd,
                stdout = stdout.trim(),
                stderr = stderr.trim(),
                exitCode = exitCode,
                durationMs = duration
            )

            withContext(Dispatchers.Main) {
                terminalLogs = terminalLogs + entry
                isExecuting = false
                currentCommand = ""
            }
        }
    }

    if (pendingConfirmationCommand != null) {
        val cmd = pendingConfirmationCommand!!
        AlertDialog(
            onDismissRequest = { pendingConfirmationCommand = null },
            title = { Text("Confirm ${cmd.risk.name} Command") },
            text = {
                Column {
                    Text("Command: ${cmd.command}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(cmd.description)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Risk Level: ${cmd.risk.name}", color = if (cmd.risk == CommandRisk.DANGEROUS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRun = cmd.command
                        pendingConfirmationCommand = null
                        executeRawCommand(toRun)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (cmd.risk == CommandRisk.DANGEROUS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
            TopAppBar(
                title = { Text("ADB & Fastboot Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { terminalLogs = emptyList() }) {
                        Icon(Icons.Filled.ClearAll, contentDescription = "Clear Logs")
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
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Terminal") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Presets") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("History (${terminalLogs.size})") })
            }

            when (selectedTab) {
                0 -> {
                    // Terminal Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Terminal Display
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                        ) {
                            if (terminalLogs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Terminal Ready. Type a command or run a preset below.",
                                        color = Color.LightGray,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(terminalLogs) { log ->
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("$ ", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                Text(log.command, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text("${log.durationMs}ms [code: ${log.exitCode}]", color = if (log.exitCode == 0) Color(0xFF81C784) else Color(0xFFE57373), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            }
                                            if (log.stdout.isNotBlank()) {
                                                Text(log.stdout, color = Color(0xFFE0E0E0), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                            }
                                            if (log.stderr.isNotBlank()) {
                                                Text(log.stderr, color = Color(0xFFFF8A80), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                            }
                                            Divider(color = Color(0xFF333333), modifier = Modifier.padding(top = 8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Command input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = currentCommand,
                                onValueChange = { currentCommand = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Command (e.g. getprop, logcat, ls)...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { executeRawCommand(currentCommand) },
                                enabled = currentCommand.isNotBlank() && !isExecuting,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isExecuting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Send, contentDescription = "Run")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Presets Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets) { preset ->
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(preset.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
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
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(preset.command, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                        Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            if (preset.risk == CommandRisk.DANGEROUS || preset.risk == CommandRisk.WARNING) {
                                                pendingConfirmationCommand = preset
                                            } else {
                                                executeRawCommand(preset.command)
                                                selectedTab = 0
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
                    // History Tab
                    if (terminalLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No executed commands in history yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(log.command, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            Text("Exit: ${log.exitCode} (${log.durationMs}ms)", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(log.stdout.take(200) + if (log.stdout.length > 200) "..." else "", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
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
