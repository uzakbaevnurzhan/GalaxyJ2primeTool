package com.example.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    var isRooted by remember { mutableStateOf(false) }
    var selinuxStatus by remember { mutableStateOf("Unknown") }
    var uptime by remember { mutableStateOf("Unknown") }

    fun refreshStats() {
        coroutineScope.launch(Dispatchers.IO) {
            isRefreshing = true
            isRooted = RootShell.isRootAvailable()
            selinuxStatus = RootShell.executeCommand("getenforce").getOrNull() ?: "Enforcing (Default)"
            uptime = RootShell.executeCommand("uptime -p").getOrNull() ?: "Unknown"
            withContext(Dispatchers.Main) {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { refreshStats() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Status Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard("ROOT", if (isRooted) "GRANTED" else "NO ROOT", if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                StatusCard("SELINUX", selinuxStatus, MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Hardware Info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Hardware Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Model", Build.MODEL)
                    InfoRow("Manufacturer", Build.MANUFACTURER)
                    InfoRow("Device", Build.DEVICE)
                    InfoRow("Board", Build.BOARD)
                    InfoRow("Hardware", Build.HARDWARE)
                    InfoRow("CPU ABI", Build.SUPPORTED_ABIS.joinToString(", "))
                    InfoRow("CPU Cores", Runtime.getRuntime().availableProcessors().toString())
                    
                    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val memInfo = ActivityManager.MemoryInfo()
                    actManager.getMemoryInfo(memInfo)
                    InfoRow("RAM", "${formatSize(memInfo.availMem)} free / ${formatSize(memInfo.totalMem)}")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Software Info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Software Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Android Version", Build.VERSION.RELEASE)
                    InfoRow("SDK Int", Build.VERSION.SDK_INT.toString())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        InfoRow("Security Patch", Build.VERSION.SECURITY_PATCH)
                    }
                    InfoRow("Build ID", Build.DISPLAY)
                    InfoRow("Fingerprint", Build.FINGERPRINT)
                    InfoRow("Uptime", uptime)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Storage Info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Storage Information", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    val path = Environment.getDataDirectory()
                    val stat = StatFs(path.path)
                    val blockSize = stat.blockSizeLong
                    val totalBlocks = stat.blockCountLong
                    val availableBlocks = stat.availableBlocksLong
                    InfoRow("/data Total", formatSize(totalBlocks * blockSize))
                    InfoRow("/data Available", formatSize(availableBlocks * blockSize))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { navController.navigate("report_generator") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Device Report")
                }
                if (isRooted) {
                    val exportSnapshotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
                        uri?.let {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val getprop = RootShell.executeCommand("getprop").getOrNull() ?: ""
                                    val mount = RootShell.executeCommand("mount").getOrNull() ?: ""
                                    val df = RootShell.executeCommand("df").getOrNull() ?: ""
                                    val snapshot = "=== GETPROP ===\n$getprop\n\n=== MOUNT ===\n$mount\n\n=== DF ===\n$df"
                                    context.contentResolver.openOutputStream(it)?.use { out ->
                                        out.write(snapshot.toByteArray())
                                    }
                                    withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "Snapshot Saved", android.widget.Toast.LENGTH_SHORT).show() }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                    }
                    Button(onClick = { exportSnapshotLauncher.launch("SystemSnapshot.txt") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Snapshot")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        Text(text = value.ifEmpty { "UNKNOWN" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
