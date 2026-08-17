package com.example.ui.device

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

enum class DeviceSection(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Filled.Dashboard),
    OVERVIEW("Overview", Icons.Filled.PhoneAndroid),
    HARDWARE("Hardware", Icons.Filled.Memory),
    SOFTWARE("Software", Icons.Filled.Android),
    KERNEL("Kernel", Icons.Filled.DeveloperBoard),
    SECURITY("Security", Icons.Filled.Security),
    STORAGE("Storage", Icons.Filled.Storage),
    PARTITIONS("Partitions", Icons.Filled.Folder),
    DISPLAY("Display", Icons.Filled.Smartphone),
    CAMERA("Camera", Icons.Filled.CameraAlt),
    AUDIO("Audio", Icons.Filled.VolumeUp),
    SENSORS("Sensors", Icons.Filled.Sensors),
    NETWORK("Network", Icons.Filled.Wifi),
    USB("USB", Icons.Filled.Usb),
    BATTERY("Battery", Icons.Filled.BatteryChargingFull),
    THERMAL("Thermal", Icons.Filled.Thermostat),
    TELEPHONY("Telephony", Icons.Filled.SimCard)
}

data class DeviceSpecItem(
    val section: DeviceSection,
    val label: String,
    val value: String,
    val source: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var selectedSection by remember { mutableStateOf(DeviceSection.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var specItems by remember { mutableStateOf<List<DeviceSpecItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    fun loadAllSpecs() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            val items = collectDeviceSpecs(context)
            withContext(Dispatchers.Main) {
                specItems = items
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllSpecs()
    }

    val filteredItems = remember(specItems, selectedSection, searchQuery) {
        specItems.filter { item ->
            val matchSection = selectedSection == DeviceSection.ALL || item.section == selectedSection
            val matchQuery = searchQuery.isBlank() ||
                    item.label.contains(searchQuery, ignoreCase = true) ||
                    item.value.contains(searchQuery, ignoreCase = true) ||
                    item.source.contains(searchQuery, ignoreCase = true)
            matchSection && matchQuery
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val sb = java.lang.StringBuilder()
                    sb.append("=== DEVICE AUDIT REPORT ===\n")
                    sb.append("Generated on: ${java.util.Date()}\n")
                    sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n\n")
                    
                    specItems.groupBy { it.section }.forEach { (section, items) ->
                        sb.append("--- [${section.label.uppercase()}] ---\n")
                        items.forEach { item ->
                            sb.append("${item.label}: ${item.value} [Source: ${item.source}]\n")
                        }
                        sb.append("\n")
                    }
                    
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(sb.toString().toByteArray())
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Exported to TXT", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val rootJson = JSONObject()
                    rootJson.put("device_model", Build.MODEL)
                    rootJson.put("device_manufacturer", Build.MANUFACTURER)
                    rootJson.put("timestamp", System.currentTimeMillis())
                    
                    val sectionsObj = JSONObject()
                    specItems.groupBy { it.section }.forEach { (section, items) ->
                        val sectionArray = JSONArray()
                        items.forEach { item ->
                            val itemObj = JSONObject()
                            itemObj.put("label", item.label)
                            itemObj.put("value", item.value)
                            itemObj.put("source", item.source)
                            sectionArray.put(itemObj)
                        }
                        sectionsObj.put(section.name.lowercase(), sectionArray)
                    }
                    rootJson.put("specs", sectionsObj)
                    
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(rootJson.toString(2).toByteArray())
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Exported to JSON", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isScanning = true
                        loadAllSpecs()
                        isScanning = false
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { exportTxtLauncher.launch("device_specs_${Build.MODEL}.txt") }) {
                        Icon(Icons.Filled.Description, contentDescription = "Export TXT")
                    }
                    IconButton(onClick = { exportJsonLauncher.launch("device_specs_${Build.MODEL}.json") }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Export JSON")
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
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search specifications...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Section Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceSection.values().forEach { section ->
                    FilterChip(
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                        label = { Text(section.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Auditing device hardware & parameters...")
                    }
                }
            } else if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No parameters found in this section" else "No matching parameters found for \"$searchQuery\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Summary count item
                    item {
                        Text(
                            text = "Found ${filteredItems.size} parameters (${if (selectedSection == DeviceSection.ALL) "All Categories" else selectedSection.label})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(filteredItems) { item ->
                        SpecItemCard(item = item, onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(item.label, "${item.label}: ${item.value}"))
                            Toast.makeText(context, "Copied ${item.label}", Toast.LENGTH_SHORT).show()
                        })
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SpecItemCard(item: DeviceSpecItem, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.value.ifEmpty { "UNKNOWN" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun collectDeviceSpecs(context: Context): List<DeviceSpecItem> {
    val list = mutableListOf<DeviceSpecItem>()

    fun add(section: DeviceSection, label: String, value: String?, source: String) {
        val safeVal = if (value.isNullOrBlank()) "UNKNOWN" else value.trim()
        list.add(DeviceSpecItem(section, label, safeVal, source))
    }

    // 1. OVERVIEW
    add(DeviceSection.OVERVIEW, "Device Name", "${Build.MANUFACTURER} ${Build.MODEL}", "BUILD_API")
    add(DeviceSection.OVERVIEW, "Model", Build.MODEL, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Manufacturer", Build.MANUFACTURER, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Brand", Build.BRAND, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Android OS", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "BUILD_API")
    add(DeviceSection.OVERVIEW, "Board / Hardware", "${Build.BOARD} / ${Build.HARDWARE}", "BUILD_API")

    // 2. HARDWARE
    add(DeviceSection.HARDWARE, "SoC Model", getSystemProperty("ro.soc.model").ifEmpty { getSystemProperty("ro.board.platform") }, "GETPROP")
    add(DeviceSection.HARDWARE, "Platform", getSystemProperty("ro.board.platform"), "GETPROP")
    add(DeviceSection.HARDWARE, "CPU Architecture", System.getProperty("os.arch"), "JAVA_ENV")
    add(DeviceSection.HARDWARE, "CPU Processors / Cores", Runtime.getRuntime().availableProcessors().toString(), "RUNTIME_API")
    add(DeviceSection.HARDWARE, "Primary ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown", "BUILD_API")
    add(DeviceSection.HARDWARE, "32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", "), "BUILD_API")
    add(DeviceSection.HARDWARE, "64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None (32-bit only)" }, "BUILD_API")
    add(DeviceSection.HARDWARE, "Instruction Sets", Build.SUPPORTED_ABIS.joinToString(", "), "BUILD_API")
    
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    if (actManager != null) {
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        add(DeviceSection.HARDWARE, "Total RAM", formatBytes(memInfo.totalMem), "ACTIVITY_MANAGER")
        add(DeviceSection.HARDWARE, "Available RAM", formatBytes(memInfo.availMem), "ACTIVITY_MANAGER")
        add(DeviceSection.HARDWARE, "Low RAM Device Flag", if (actManager.isLowRamDevice) "YES (Android Go / Low-RAM)" else "NO", "ACTIVITY_MANAGER")
        add(DeviceSection.HARDWARE, "Memory Threshold", formatBytes(memInfo.threshold), "ACTIVITY_MANAGER")
    }

    // 3. SOFTWARE
    add(DeviceSection.SOFTWARE, "Android Release", Build.VERSION.RELEASE, "BUILD_API")
    add(DeviceSection.SOFTWARE, "SDK Version (API)", Build.VERSION.SDK_INT.toString(), "BUILD_API")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        add(DeviceSection.SOFTWARE, "Security Patch Level", Build.VERSION.SECURITY_PATCH, "BUILD_API")
    }
    add(DeviceSection.SOFTWARE, "Build ID", Build.DISPLAY, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Incremental", Build.VERSION.INCREMENTAL, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Type", Build.TYPE, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Tags", Build.TAGS, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Fingerprint", Build.FINGERPRINT, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Baseband Version", Build.getRadioVersion(), "TELEPHONY_RADIO")
    add(DeviceSection.SOFTWARE, "Bootloader", Build.BOOTLOADER, "BUILD_API")

    // 4. KERNEL
    add(DeviceSection.KERNEL, "Kernel OS Version", System.getProperty("os.version"), "JAVA_ENV")
    val procVersion = readFileFirstLine("/proc/version")
    add(DeviceSection.KERNEL, "/proc/version", procVersion, "PROC_FS")
    val procCmdline = readFileFirstLine("/proc/cmdline")
    add(DeviceSection.KERNEL, "Kernel Cmdline", procCmdline, "PROC_FS")

    // 5. SECURITY
    val isRoot = RootShell.isRootAvailable()
    add(DeviceSection.SECURITY, "Root Privileges", if (isRoot) "ACTIVE (UID 0 root accessible)" else "NO ROOT (Standard User Sandbox)", "ROOT_PROBE")
    val selinuxEnforce = RootShell.executeCommand("getenforce").getOrNull() ?: getSystemProperty("ro.boot.selinux")
    add(DeviceSection.SECURITY, "SELinux Mode", selinuxEnforce, "SELINUX_GETENFORCE")
    add(DeviceSection.SECURITY, "Treble Enabled", getSystemProperty("ro.treble.enabled"), "GETPROP")
    add(DeviceSection.SECURITY, "A/B Seamless Update", getSystemProperty("ro.build.ab_update"), "GETPROP")
    add(DeviceSection.SECURITY, "AVB / Verified Boot", getSystemProperty("ro.boot.verifiedbootstate"), "GETPROP")
    add(DeviceSection.SECURITY, "Device Encryption State", getSystemProperty("ro.crypto.state"), "GETPROP")
    add(DeviceSection.SECURITY, "Crypto Type", getSystemProperty("ro.crypto.type"), "GETPROP")
    add(DeviceSection.SECURITY, "ADB Daemon Status", getSystemProperty("init.svc.adbd"), "GETPROP")

    // 6. STORAGE
    try {
        val dataStat = StatFs(Environment.getDataDirectory().path)
        val dataTotal = dataStat.blockCountLong * dataStat.blockSizeLong
        val dataFree = dataStat.availableBlocksLong * dataStat.blockSizeLong
        add(DeviceSection.STORAGE, "Internal Data (/data) Total", formatBytes(dataTotal), "STATFS")
        add(DeviceSection.STORAGE, "Internal Data (/data) Free", formatBytes(dataFree), "STATFS")
    } catch (e: Exception) {}

    try {
        val rootStat = StatFs(Environment.getRootDirectory().path)
        val rootTotal = rootStat.blockCountLong * rootStat.blockSizeLong
        val rootFree = rootStat.availableBlocksLong * rootStat.blockSizeLong
        add(DeviceSection.STORAGE, "System Partition (/system) Total", formatBytes(rootTotal), "STATFS")
        add(DeviceSection.STORAGE, "System Partition (/system) Free", formatBytes(rootFree), "STATFS")
    } catch (e: Exception) {}

    // 7. PARTITIONS & MOUNTS
    val procMounts = readFileLines("/proc/mounts")
    if (procMounts.isNotEmpty()) {
        val relevantMounts = procMounts.filter { it.contains(" /system ") || it.contains(" /vendor ") || it.contains(" /data ") || it.contains(" /cache ") }
        relevantMounts.forEach { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 4) {
                add(DeviceSection.PARTITIONS, "Mount: ${parts[1]}", "${parts[0]} (${parts[2]}, ${parts[3]})", "PROC_MOUNTS")
            }
        }
    }

    // 8. DISPLAY
    try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getMetrics(metrics)
        add(DeviceSection.DISPLAY, "Resolution (Pixels)", "${metrics.widthPixels} x ${metrics.heightPixels}", "DISPLAY_METRICS")
        add(DeviceSection.DISPLAY, "Density (DPI)", "${metrics.densityDpi} dpi", "DISPLAY_METRICS")
        add(DeviceSection.DISPLAY, "Density Scale Factor", "${metrics.density}x", "DISPLAY_METRICS")
        add(DeviceSection.DISPLAY, "Scaled Density", "${metrics.scaledDensity}x", "DISPLAY_METRICS")
        @Suppress("DEPRECATION")
        val refresh = wm?.defaultDisplay?.refreshRate
        if (refresh != null) {
            add(DeviceSection.DISPLAY, "Refresh Rate", "${DecimalFormat("#.#").format(refresh)} Hz", "DISPLAY_METRICS")
        }
    } catch (e: Exception) {}

    // 9. CAMERA
    try {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (camManager != null) {
            val camIds = camManager.cameraIdList
            add(DeviceSection.CAMERA, "Total Available Cameras", camIds.size.toString(), "CAMERA_MANAGER")
            camIds.forEach { id ->
                val chars = camManager.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                    else -> "Unknown"
                }
                val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                    else -> "Unknown"
                }
                add(DeviceSection.CAMERA, "Camera ID $id", "Facing: $facing | Hardware Level: $hwLevel | Flash: ${if (flash) "Yes" else "No"}", "CAMERA_MANAGER")
            }
        }
    } catch (e: Exception) {}

    // 10. AUDIO
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            val framesPerBuffer = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            add(DeviceSection.AUDIO, "Output Sample Rate", "${sampleRate ?: "Unknown"} Hz", "AUDIO_MANAGER")
            add(DeviceSection.AUDIO, "Frames Per Buffer", "${framesPerBuffer ?: "Unknown"} frames", "AUDIO_MANAGER")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val deviceNames = devices.joinToString(", ") { dev ->
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
                        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                        else -> "Device (${dev.type})"
                    }
                }
                add(DeviceSection.AUDIO, "Output Audio Devices", deviceNames.ifEmpty { "None connected" }, "AUDIO_MANAGER")
            }
        }
    } catch (e: Exception) {}

    // 11. SENSORS
    try {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager != null) {
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            add(DeviceSection.SENSORS, "Total Hardware Sensors", sensors.size.toString(), "SENSOR_MANAGER")
            sensors.forEach { sensor ->
                add(DeviceSection.SENSORS, sensor.name, "Vendor: ${sensor.vendor} | Power: ${sensor.power} mA | Max: ${sensor.maximumRange}", "SENSOR_MANAGER")
            }
        }
    } catch (e: Exception) {}

    // 12. NETWORK
    try {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val activeTransport = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Cellular Mobile Data"
            isEthernet -> "Ethernet"
            isVpn -> "VPN Connected"
            else -> "Disconnected / Airplane"
        }
        add(DeviceSection.NETWORK, "Active Connection", activeTransport, "CONNECTIVITY_MANAGER")
    } catch (e: Exception) {}

    // 13. USB
    add(DeviceSection.USB, "USB Host (OTG) Supported", if (context.packageManager.hasSystemFeature("android.hardware.usb.host")) "YES (OTG Capable)" else "NO", "PACKAGE_MANAGER")
    add(DeviceSection.USB, "USB Accessory Supported", if (context.packageManager.hasSystemFeature("android.hardware.usb.accessory")) "YES" else "NO", "PACKAGE_MANAGER")

    // 14. BATTERY
    try {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
            val health = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }
            val status = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                else -> "Unknown"
            }

            if (level != -1 && scale != -1) {
                add(DeviceSection.BATTERY, "Battery Level", "${(level * 100) / scale}%", "BATTERY_BROADCAST")
            }
            add(DeviceSection.BATTERY, "Battery Status", status, "BATTERY_BROADCAST")
            add(DeviceSection.BATTERY, "Battery Health", health, "BATTERY_BROADCAST")
            if (temp != -1) {
                add(DeviceSection.BATTERY, "Battery Temperature", "${temp / 10.0} °C", "BATTERY_BROADCAST")
            }
            if (voltage != -1) {
                add(DeviceSection.BATTERY, "Battery Voltage", "$voltage mV", "BATTERY_BROADCAST")
            }
            add(DeviceSection.BATTERY, "Battery Technology", technology, "BATTERY_BROADCAST")
        }
    } catch (e: Exception) {}

    // 15. THERMAL
    try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val thermalStatus = when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "None (Normal Temperature)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical (Near Shutdown)"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency (Thermal Shutdown Imminent)"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                else -> "Unknown"
            }
            add(DeviceSection.THERMAL, "Thermal Headroom Status", thermalStatus, "POWER_MANAGER")
        }
    } catch (e: Exception) {}

    // 16. TELEPHONY
    try {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephonyManager != null) {
            val simState = when (telephonyManager.simState) {
                TelephonyManager.SIM_STATE_READY -> "Ready / Inserted"
                TelephonyManager.SIM_STATE_ABSENT -> "No SIM Card Inserted"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
                else -> "Unknown"
            }
            add(DeviceSection.TELEPHONY, "SIM State", simState, "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "Network Operator Name", telephonyManager.networkOperatorName, "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "Network Country ISO", telephonyManager.networkCountryIso.uppercase(), "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "Phone Type", when (telephonyManager.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP"
                else -> "None"
            }, "TELEPHONY_MANAGER")
        }
    } catch (e: Exception) {}

    return list
}

private fun getSystemProperty(key: String): String {
    return try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        val res = get.invoke(null, key) as? String
        res ?: ""
    } catch (e: Exception) {
        ""
    }
}

private fun readFileFirstLine(path: String): String {
    return try {
        val file = File(path)
        if (file.exists() && file.canRead()) {
            file.bufferedReader().use { it.readLine() ?: "" }
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
}

private fun readFileLines(path: String): List<String> {
    return try {
        val file = File(path)
        if (file.exists() && file.canRead()) {
            file.bufferedReader().use { it.readLines() }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
