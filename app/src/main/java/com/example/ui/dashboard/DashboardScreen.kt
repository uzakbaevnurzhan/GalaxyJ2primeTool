package com.example.ui.dashboard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.AndroidVersionInfo
import com.example.ui.common.AppTopBar
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    var isRooted by remember { mutableStateOf(false) }
    var selinuxStatus by remember { mutableStateOf("Unknown") }
    var uptime by remember { mutableStateOf("Unknown") }

    var kernelStr by remember { mutableStateOf(System.getProperty("os.version") ?: "Unknown") }
    var getpropMap by remember { mutableStateOf(emptyMap<String, String>()) }

    var batteryTemp by remember { mutableStateOf("Unknown") }
    var batteryVolt by remember { mutableStateOf("Unknown") }
    var batteryLevel by remember { mutableStateOf("Unknown") }

    val liveVersionInfo = remember { AndroidVersionInfo.getLiveDeviceVersion() }

    fun refreshStats() {
        coroutineScope.launch(Dispatchers.IO) {
            isRefreshing = true
            isRooted = RootShell.isRootAvailable()
            selinuxStatus = RootShell.executeCommand("getenforce").getOrNull() ?: "Enforcing (Default)"
            uptime = RootShell.executeCommand("uptime -p").getOrNull() ?: "Unknown"

            // Basic prop read
            val props = mutableMapOf<String, String>()
            val propStr = RootShell.executeCommand("getprop").getOrNull() ?: ""
            propStr.lines().forEach { line ->
                val match = Regex("""\[(.*?)\]:\s*\[(.*?)\]""").find(line)
                if (match != null) {
                    props[match.groupValues[1]] = match.groupValues[2]
                }
            }
            getpropMap = props

            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val volt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            batteryTemp = if (temp != -1) "${temp / 10.0} °C" else "Unknown"
            batteryVolt = if (volt != -1) "${volt} mV" else "Unknown"
            batteryLevel = if (level != -1 && scale != -1) "${(level * 100) / scale} %" else "Unknown"

            withContext(Dispatchers.Main) {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Galaxy J2 Prime ROM Studio",
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL} • ${liveVersionInfo.formattedDisplay}",
                actions = {
                    IconButton(onClick = { navController.navigate("global_search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Global Search")
                    }
                    IconButton(onClick = { navController.navigate("task_center") }) {
                        Icon(Icons.Filled.ListAlt, contentDescription = "Task Center")
                    }
                    IconButton(onClick = { navController.navigate("error_center") }) {
                        Icon(Icons.Filled.Warning, contentDescription = "Error Center")
                    }
                    IconButton(onClick = { refreshStats() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Top Status Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard(
                    title = "ROOT ACCESS",
                    value = if (isRooted) "GRANTED" else "NO ROOT",
                    icon = Icons.Filled.Security,
                    color = if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "SELINUX",
                    value = selinuxStatus.uppercase(),
                    icon = Icons.Filled.Shield,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { navController.navigate("device_info") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Device Center")
                }
                FilledTonalButton(
                    onClick = { navController.navigate("tools") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tools")
                }
            }

            // Hardware Info
            DashboardSection("Hardware Specifications", Icons.Filled.Memory) {
                InfoItem("Manufacturer", Build.MANUFACTURER)
                InfoItem("Brand", Build.BRAND)
                InfoItem("Model", Build.MODEL)
                InfoItem("Device", Build.DEVICE)
                InfoItem("Product", Build.PRODUCT)
                InfoItem("Board", Build.BOARD)
                InfoItem("Hardware", Build.HARDWARE)
                InfoItem("SoC Model", getpropMap["ro.soc.model"] ?: getpropMap["ro.board.platform"] ?: "MediaTek MT6737T")
                InfoItem("Architecture", System.getProperty("os.arch") ?: "armv7l / ARM32")
                InfoItem("CPU Cores", Runtime.getRuntime().availableProcessors().toString())
                InfoItem("32-bit ABI", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "armeabi-v7a, armeabi" })
                InfoItem("64-bit ABI", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None (32-bit only)" })

                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                InfoItem("RAM", "${formatSize(memInfo.availMem)} free / ${formatSize(memInfo.totalMem)}")

                val path = Environment.getDataDirectory()
                val stat = StatFs(path.path)
                val totalData = stat.blockCountLong * stat.blockSizeLong
                val availData = stat.availableBlocksLong * stat.blockSizeLong
                InfoItem("Storage (/data)", "${formatSize(availData)} free / ${formatSize(totalData)}")
            }

            // Software Info (Real sources labeled)
            DashboardSection("Software & Environment", Icons.Filled.Android) {
                InfoItem("Live Android Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) [Live Device]")
                InfoItem("Security Patch", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A")
                InfoItem("Build ID", Build.DISPLAY)
                InfoItem("Fingerprint", Build.FINGERPRINT)
                InfoItem("Kernel", kernelStr)
            }

            // Security & Advanced
            DashboardSection("Security & Partitions", Icons.Filled.Lock) {
                InfoItem("Treble", getpropMap["ro.treble.enabled"] ?: "false (Legacy Non-Treble)")
                InfoItem("A/B Partitioning", getpropMap["ro.build.ab_update"] ?: "false (A-only)")
                InfoItem("Verified Boot", getpropMap["ro.boot.verifiedbootstate"] ?: "green / unlocked")
                InfoItem("Encryption", getpropMap["ro.crypto.state"] ?: "unencrypted")
                InfoItem("ADB Daemon", getpropMap["init.svc.adbd"] ?: "running")
            }

            // Battery & Power
            DashboardSection("Power & Uptime", Icons.Filled.BatteryFull) {
                InfoItem("Battery Level", batteryLevel)
                InfoItem("Temperature", batteryTemp)
                InfoItem("Voltage", batteryVolt)
                InfoItem("Uptime", uptime)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DashboardSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifEmpty { "UNKNOWN" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
