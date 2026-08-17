package com.example.ui.root

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.common.AppTopBar
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BootModeItem(
    val id: String,
    val title: String,
    val command: String,
    val description: String,
    val isSupported: Boolean,
    val supportNote: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootModesScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRooted by remember { mutableStateOf(false) }
    var pendingRebootMode by remember { mutableStateOf<BootModeItem?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var executionMessage by remember { mutableStateOf<String?>(null) }

    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    val isSamsung = manufacturer.contains("samsung", ignoreCase = true)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isRooted = RootShell.isRootAvailable()
        }
    }

    val bootModes = listOf(
        BootModeItem(
            id = "system",
            title = "Reboot System",
            command = "reboot",
            description = "Standard reboot into Android operating system",
            isSupported = true,
            supportNote = "Universal Android reboot",
            icon = Icons.Filled.RestartAlt
        ),
        BootModeItem(
            id = "recovery",
            title = "Reboot Recovery",
            command = "reboot recovery",
            description = "Reboot into TWRP or stock recovery environment",
            isSupported = true,
            supportNote = "Supported via recovery partition flag",
            icon = Icons.Filled.Build
        ),
        BootModeItem(
            id = "download",
            title = "Reboot Download Mode (Odin)",
            command = "reboot download",
            description = "Reboot into Samsung Odin / Loke flashing mode",
            isSupported = isSamsung || model.contains("G532", ignoreCase = true),
            supportNote = if (isSamsung) "Supported on Samsung hardware" else "Unsupported (Non-Samsung Device)",
            icon = Icons.Filled.CloudDownload
        ),
        BootModeItem(
            id = "bootloader",
            title = "Reboot Bootloader",
            command = "reboot bootloader",
            description = "Reboot into device bootloader / LK stage",
            isSupported = true,
            supportNote = "Standard bootloader trigger",
            icon = Icons.Filled.PowerSettingsNew
        ),
        BootModeItem(
            id = "fastbootd",
            title = "Reboot Fastbootd (Dynamic)",
            command = "reboot fastboot",
            description = "Reboot into userspace fastbootd for dynamic partitions",
            isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            supportNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "Supported (Android 10+)" else "Unsupported (Requires Android 10+ / Treble)",
            icon = Icons.Filled.SettingsSystemDaydream
        ),
        BootModeItem(
            id = "soft_reboot",
            title = "Soft Reboot (Userspace)",
            command = "setprop ctl.restart zygote",
            description = "Restart Android Zygote and System Server without kernel power cycle",
            isSupported = true,
            supportNote = "Supported with root privileges",
            icon = Icons.Filled.Speed
        )
    )

    fun executeReboot(mode: BootModeItem) {
        coroutineScope.launch {
            isExecuting = true
            executionMessage = "Executing ${mode.command}..."
            val res = withContext(Dispatchers.IO) {
                RootShell.executeCommand(mode.command)
            }
            if (res.isSuccess) {
                Toast.makeText(context, "Reboot triggered successfully.", Toast.LENGTH_SHORT).show()
            } else {
                executionMessage = "Reboot failed: ${res.exceptionOrNull()?.message ?: "Root permission denied or command unavailable"}"
            }
            isExecuting = false
        }
    }

    if (pendingRebootMode != null) {
        val mode = pendingRebootMode!!
        AlertDialog(
            onDismissRequest = { pendingRebootMode = null },
            title = { Text("Confirm Reboot", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Device: $manufacturer $model", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Target Mode: ${mode.title}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Command: ${mode.command}")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The device will restart immediately. Ensure all unsaved work in ROM Studio is saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRun = mode
                        pendingRebootMode = null
                        executeReboot(toRun)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reboot Now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRebootMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Boot Modes & Reboot Tool",
                subtitle = "$manufacturer $model",
                onNavigateBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Privilege Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isRooted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    if (isRooted) "ROOT GRANTED" else "NO ROOT",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRooted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isRooted) "Device has active root access. Privileged boot triggers are functional." else "Root access was not detected. Reboot commands require root privileges or ADB connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (executionMessage != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            executionMessage!!,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text("Select Target Boot Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            items(bootModes) { mode ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mode.isSupported) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(mode.icon, contentDescription = null, tint = if (mode.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(mode.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    mode.supportNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (mode.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Button(
                            onClick = { pendingRebootMode = mode },
                            enabled = mode.isSupported && isRooted && !isExecuting,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (mode.isSupported) "Reboot" else "Unsupported")
                        }
                    }
                }
            }
        }
    }
}
