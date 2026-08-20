package com.example.ui.dashboard

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.ui.common.AppTopBar
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

data class TelemetryField(
    val label: String,
    val value: String,
    val source: String,
    val icon: ImageVector? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    var isRooted by remember { mutableStateOf(false) }
    var rootSource by remember { mutableStateOf("su binary check") }
    var selinuxStatus by remember { mutableStateOf("UNKNOWN") }
    var selinuxSource by remember { mutableStateOf("/sys/fs/selinux/enforce") }
    var uptime by remember { mutableStateOf("UNKNOWN") }

    var kernelStr by remember { mutableStateOf(System.getProperty("os.version") ?: "UNKNOWN") }
    var getpropMap by remember { mutableStateOf(emptyMap<String, String>()) }

    var batteryTemp by remember { mutableStateOf("UNKNOWN") }
    var batteryVolt by remember { mutableStateOf("UNKNOWN") }
    var batteryLevel by remember { mutableStateOf("UNKNOWN") }
    var batteryHealth by remember { mutableStateOf("UNKNOWN") }
    var batterySource by remember { mutableStateOf("BatteryManager") }

    var ramFree by remember { mutableStateOf("UNKNOWN") }
    var ramTotal by remember { mutableStateOf("UNKNOWN") }
    var storageFree by remember { mutableStateOf("UNKNOWN") }
    var storageTotal by remember { mutableStateOf("UNKNOWN") }
    var cpuInfoHardware by remember { mutableStateOf("UNKNOWN") }
    var adbStatus by remember { mutableStateOf("UNKNOWN") }
    var usbDevicesCount by remember { mutableStateOf(0) }

    fun refreshStats(showToast: Boolean = false) {
        coroutineScope.launch(Dispatchers.IO) {
            isRefreshing = true
            val rootAvailable = RootShell.isRootAvailable()
            val seLinux = RootShell.executeCommand("getenforce").getOrNull()?.trim() 
                ?: RootShell.executeCommand("cat /sys/fs/selinux/enforce").getOrNull()?.let { if (it.trim() == "1") "Enforcing" else "Permissive" }
                ?: "UNKNOWN"
            val upTimeVal = RootShell.executeCommand("uptime -p").getOrNull()?.trim() ?: "UNKNOWN"

            // Prop reader
            val props = mutableMapOf<String, String>()
            val propStr = RootShell.executeCommand("getprop").getOrNull() ?: ""
            propStr.lines().forEach { line ->
                val match = Regex("""\[(.*?)\]:\s*\[(.*?)\]""").find(line)
                if (match != null) {
                    props[match.groupValues[1]] = match.groupValues[2]
                }
            }

            // CPU info
            var cpuHw = "UNKNOWN"
            try {
                val cpuFile = File("/proc/cpuinfo")
                if (cpuFile.exists()) {
                    cpuFile.readLines().forEach { l ->
                        if (l.startsWith("Hardware", ignoreCase = true)) {
                            cpuHw = l.substringAfter(":").trim()
                        }
                    }
                }
            } catch (_: Exception) {}

            // Battery
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val volt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

            val bTemp = if (temp != -1) "${temp / 10.0} °C" else "UNKNOWN"
            val bVolt = if (volt != -1) "${volt} mV" else "UNKNOWN"
            val bLevel = if (level != -1 && scale > 0) "${(level * 100) / scale}%" else "UNKNOWN"
            val bHealth = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                else -> "Normal"
            }

            // RAM
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val rFree = formatSize(memInfo.availMem)
            val rTotal = formatSize(memInfo.totalMem)

            // Storage
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val totalData = stat.blockCountLong * stat.blockSizeLong
            val availData = stat.availableBlocksLong * stat.blockSizeLong
            val sFree = formatSize(availData)
            val sTotal = formatSize(totalData)

            // USB & ADB
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            val uCount = usbManager?.deviceList?.size ?: 0
            val adb = props["init.svc.adbd"] ?: if (props["sys.usb.config"]?.contains("adb") == true) "Active" else "Disabled"

            withContext(Dispatchers.Main) {
                isRooted = rootAvailable
                rootSource = if (rootAvailable) "su executable" else "none found"
                selinuxStatus = seLinux
                selinuxSource = "SELinux subsystem"
                uptime = upTimeVal
                getpropMap = props
                cpuInfoHardware = cpuHw
                batteryTemp = bTemp
                batteryVolt = bVolt
                batteryLevel = bLevel
                batteryHealth = bHealth
                batterySource = "BatteryManager"
                ramFree = rFree
                ramTotal = rTotal
                storageFree = sFree
                storageTotal = sTotal
                adbStatus = adb
                usbDevicesCount = uCount
                isRefreshing = false
                if (showToast) {
                    Toast.makeText(context, "Hardware telemetry updated in real time.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStats()
    }

    var showScanModeDialog by remember { mutableStateOf(false) }

    if (showScanModeDialog) {
        AlertDialog(
            onDismissRequest = { showScanModeDialog = false },
            icon = { Icon(Icons.Filled.Troubleshoot, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Universal System Scanner", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select audit depth for comprehensive device & subsystem analysis:")
                    
                    Surface(
                        onClick = {
                            showScanModeDialog = false
                            navController.navigate("full_system_analyzer")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("QUICK SCAN", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Core specs, critical blocker errors, battery, root & SELinux status", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Surface(
                        onClick = {
                            showScanModeDialog = false
                            navController.navigate("full_system_analyzer")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DEEP SCAN (Recommended)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Full analysis: Boot, Kernel, DTB, Partitions, HAL, RIL, SELinux, ELF & Logs", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    Surface(
                        onClick = {
                            showScanModeDialog = false
                            navController.navigate("full_system_analyzer")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("EXPERT AUDIT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Raw evidence, symbol tables, dmesg panic traces, AVC denials & correlation", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScanModeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Galaxy J2 Prime Tool",
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL} • Universal Scanner (Beta 3)",
                actions = {
                    IconButton(onClick = { navController.navigate("global_search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Global Search")
                    }
                    IconButton(onClick = { refreshStats(showToast = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Telemetry")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedVisibility(visible = isRefreshing, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // PRIMARY CALL TO ACTION: SCAN SYSTEM
            Card(
                onClick = { showScanModeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = { showScanModeDialog = true },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Troubleshoot, contentDescription = "Scan System", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SCAN SYSTEM",
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Universal Full-Device Diagnostic Audit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Orchestrates: Device → Android → Kernel → Boot → DTB → Partitions → System → Vendor → HAL → RIL → SELinux → ELF → Logs → Hardware → Security → Correlation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { navController.navigate("full_system_analyzer") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("RUN SCAN", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showScanModeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("MODES", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Quick Actions Hub (4 Dedicated Core Navigation Actions)
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { navController.navigate("device_info") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("DEVICE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = { navController.navigate("tools") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("TOOLS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = { navController.navigate("log_analyzer") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("LOGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(2.dp))
                        Text("SETTINGS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Top Status Metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusCard(
                    title = "ROOT",
                    value = if (isRooted) "GRANTED" else "NO ROOT",
                    source = rootSource,
                    icon = Icons.Filled.Security,
                    color = if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "SELINUX",
                    value = selinuxStatus.uppercase(),
                    source = selinuxSource,
                    icon = Icons.Filled.Shield,
                    color = if (selinuxStatus.contains("Enforcing", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "BATTERY",
                    value = batteryLevel,
                    source = "$batteryTemp • $batteryVolt",
                    icon = Icons.Filled.BatteryChargingFull,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Master Orchestrator Card
            Card(
                onClick = { navController.navigate("full_system_analyzer") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Troubleshoot, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Full System Analyzer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Master diagnostic auditor: Hardware, Kernel 3.18, HALs, RIL, SELinux, Partitions & Boot verification.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            // 1. Device Hardware Identification Section
            TelemetrySection(title = "Device Identification", icon = Icons.Filled.PhoneAndroid) {
                TelemetryRow("Manufacturer", Build.MANUFACTURER, "Build.MANUFACTURER")
                TelemetryRow("Brand", Build.BRAND, "Build.BRAND")
                TelemetryRow("Model", Build.MODEL, "Build.MODEL")
                TelemetryRow("Device Codename", Build.DEVICE, "Build.DEVICE")
                TelemetryRow("Product Name", Build.PRODUCT, "Build.PRODUCT")
                TelemetryRow("Board", Build.BOARD, "Build.BOARD")
                TelemetryRow("Hardware String", Build.HARDWARE, "Build.HARDWARE")
                TelemetryRow(
                    "SoC / Platform",
                    getpropMap["ro.soc.model"] ?: getpropMap["ro.board.platform"] ?: cpuInfoHardware.ifEmpty { "UNKNOWN" },
                    "getprop / /proc/cpuinfo"
                )
            }

            // 2. CPU, GPU & Architecture Section
            TelemetrySection(title = "Processor & Architecture", icon = Icons.Filled.Memory) {
                val arch = System.getProperty("os.arch") ?: "UNKNOWN"
                val cores = Runtime.getRuntime().availableProcessors().toString()
                TelemetryRow("Architecture", arch, "System.getProperty(os.arch)")
                TelemetryRow("CPU Cores", cores, "Runtime.availableProcessors()")
                TelemetryRow("Primary ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "UNKNOWN", "Build.SUPPORTED_ABIS")
                TelemetryRow("32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "UNKNOWN" }, "Build.SUPPORTED_32_BIT_ABIS")
                TelemetryRow("64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None (32-bit only SoC)" }, "Build.SUPPORTED_64_BIT_ABIS")
                TelemetryRow("Kernel Version", kernelStr, "/proc/version / System.getProperty")
            }

            // 3. Memory & Storage Section
            TelemetrySection(title = "Memory & Storage", icon = Icons.Filled.Storage) {
                TelemetryRow("RAM Available", "$ramFree free / $ramTotal total", "ActivityManager.MemoryInfo")
                TelemetryRow("Data Partition (/data)", "$storageFree free / $storageTotal total", "StatFs(Environment.getDataDirectory)")
            }

            // 4. Android Software & OS
            TelemetrySection(title = "Operating System & Environment", icon = Icons.Filled.Android) {
                TelemetryRow("Android OS Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Build.VERSION [Live Device]")
                TelemetryRow("Security Patch", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "UNKNOWN", "Build.VERSION.SECURITY_PATCH")
                TelemetryRow("Build Display ID", Build.DISPLAY, "Build.DISPLAY")
                TelemetryRow("Build Fingerprint", Build.FINGERPRINT, "Build.FINGERPRINT")
                TelemetryRow("Treble Enabled", getpropMap["ro.treble.enabled"] ?: "false (Legacy Non-Treble)", "getprop ro.treble.enabled")
                TelemetryRow("A/B Update Slots", getpropMap["ro.build.ab_update"] ?: "false (A-only)", "getprop ro.build.ab_update")
            }

            // 5. Security & Bridges
            TelemetrySection(title = "Security, ADB & USB", icon = Icons.Filled.Lock) {
                TelemetryRow("SELinux State", selinuxStatus, selinuxSource)
                TelemetryRow("Root Privilege", if (isRooted) "Granted (UID 0)" else "Not Rooted", rootSource)
                TelemetryRow("ADB Daemon", adbStatus, "getprop init.svc.adbd / sys.usb.config")
                TelemetryRow("USB Devices Attached", "$usbDevicesCount detected", "UsbManager.getDeviceList")
                TelemetryRow("Verified Boot State", getpropMap["ro.boot.verifiedbootstate"] ?: "UNKNOWN", "getprop ro.boot.verifiedbootstate")
                TelemetryRow("Device Encryption", getpropMap["ro.crypto.state"] ?: "UNKNOWN", "getprop ro.crypto.state")
            }

            // 6. Battery & Power Telemetry
            TelemetrySection(title = "Power & Telemetry", icon = Icons.Filled.BatteryFull) {
                TelemetryRow("Battery Level", batteryLevel, batterySource)
                TelemetryRow("Temperature", batteryTemp, batterySource)
                TelemetryRow("Voltage", batteryVolt, batterySource)
                TelemetryRow("Battery Health", batteryHealth, batterySource)
                TelemetryRow("System Uptime", uptime, "uptime / proc")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TelemetrySection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun TelemetryRow(label: String, value: String, source: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                fontWeight = FontWeight.SemiBold,
                color = if (value.equals("UNKNOWN", ignoreCase = true)) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "Source: $source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontFamily = FontFamily.Monospace
        )
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    source: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(source, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
