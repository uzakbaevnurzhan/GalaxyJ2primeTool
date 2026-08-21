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
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.example.ui.common.AppTopBar
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
    CPU("CPU & SoC", Icons.Filled.Memory),
    GPU("GPU & Display", Icons.Filled.Smartphone),
    RAM("RAM & Memory", Icons.Filled.Storage),
    STORAGE("Storage & Disks", Icons.Filled.Folder),
    PARTITIONS("Partitions", Icons.Filled.FolderOpen),
    BATTERY("Battery & Power", Icons.Filled.BatteryChargingFull),
    THERMAL("Thermal", Icons.Filled.Thermostat),
    CAMERA("Cameras", Icons.Filled.CameraAlt),
    AUDIO("Audio & Sound", Icons.AutoMirrored.Filled.VolumeUp),
    SENSORS("Sensors", Icons.Filled.Sensors),
    NETWORK("Network & Wi-Fi", Icons.Filled.Wifi),
    TELEPHONY("Telephony / SIM", Icons.Filled.SimCard),
    USB("USB & OTG", Icons.Filled.Usb),
    SECURITY("Security & Root", Icons.Filled.Security),
    KERNEL("Kernel & OS", Icons.Filled.DeveloperBoard),
    SOFTWARE("Android & Build", Icons.Filled.Android)
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

    fun loadAllSpecs() {
        coroutineScope.launch(Dispatchers.IO) {
            isLoading = true
            val items = collectMaximumDeviceSpecs(context)
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
                    val sb = StringBuilder()
                    sb.append("=== GALAXY J2 PRIME TOOL - MAXIMUM DEVICE AUDIT ===\n")
                    sb.append("Generated: ${java.util.Date()}\n")
                    sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
                    sb.append("Total Parameters Audited: ${specItems.size}\n\n")
                    
                    specItems.groupBy { it.section }.forEach { (section, items) ->
                        sb.append("====================================================\n")
                        sb.append("SECTION: [${section.label.uppercase()}] (${items.size} parameters)\n")
                        sb.append("====================================================\n")
                        items.forEach { item ->
                            sb.append("${item.label.padEnd(35)}: ${item.value} [${item.source}]\n")
                        }
                        sb.append("\n")
                    }
                    
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(sb.toString().toByteArray())
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Full specifications exported to TXT", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val root = JSONObject()
                    root.put("app", "Galaxy J2 Prime Tool")
                    root.put("version", com.example.config.AppVersionConfig.VERSION_NAME)
                    root.put("timestamp", System.currentTimeMillis())
                    root.put("total_specs", specItems.size)
                    
                    val deviceObj = JSONObject()
                    deviceObj.put("manufacturer", Build.MANUFACTURER)
                    deviceObj.put("model", Build.MODEL)
                    deviceObj.put("device", Build.DEVICE)
                    deviceObj.put("hardware", Build.HARDWARE)
                    deviceObj.put("board", Build.BOARD)
                    deviceObj.put("fingerprint", Build.FINGERPRINT)
                    root.put("device_identity", deviceObj)

                    val sectionsObj = JSONObject()
                    specItems.groupBy { it.section }.forEach { (section, items) ->
                        val arr = JSONArray()
                        items.forEach { item ->
                            val itemObj = JSONObject()
                            itemObj.put("label", item.label)
                            itemObj.put("value", item.value)
                            itemObj.put("source", item.source)
                            arr.put(itemObj)
                        }
                        sectionsObj.put(section.name.lowercase(), arr)
                    }
                    root.put("specifications", sectionsObj)

                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(root.toString(2).toByteArray())
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Full specifications exported to JSON", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Device Center",
                subtitle = "${Build.MANUFACTURER} ${Build.MODEL} • ${specItems.size} Parameters",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { loadAllSpecs() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Specifications")
                    }
                    IconButton(onClick = { exportTxtLauncher.launch("device_specs_${Build.MODEL}.txt") }) {
                        Icon(Icons.Filled.Description, contentDescription = "Export TXT")
                    }
                    IconButton(onClick = { exportJsonLauncher.launch("device_specs_${Build.MODEL}.json") }) {
                        Icon(Icons.Filled.Code, contentDescription = "Export JSON")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search hardware, CPU, sensors, memory...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Horizontal Category Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceSection.values().forEach { section ->
                    val isSelected = selectedSection == section
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSection = section },
                        label = { Text(section.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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
                        Text("Auditing maximum device hardware & telemetry...")
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
                            text = "Showing ${filteredItems.size} parameters (${if (selectedSection == DeviceSection.ALL) "All Categories" else selectedSection.label})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(filteredItems) { item ->
                        SpecItemCard(item = item, onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(item.label, "${item.label}: ${item.value}"))
                            Toast.makeText(context, "Copied: ${item.label}", Toast.LENGTH_SHORT).show()
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

private fun collectMaximumDeviceSpecs(context: Context): List<DeviceSpecItem> {
    val list = mutableListOf<DeviceSpecItem>()

    fun add(section: DeviceSection, label: String, value: String?, source: String) {
        val safeVal = if (value.isNullOrBlank()) "UNKNOWN" else value.trim()
        list.add(DeviceSpecItem(section, label, safeVal, source))
    }

    // 1. OVERVIEW & IDENTITY
    add(DeviceSection.OVERVIEW, "Device Name", "${Build.MANUFACTURER} ${Build.MODEL}", "BUILD_API")
    add(DeviceSection.OVERVIEW, "Manufacturer", Build.MANUFACTURER, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Brand", Build.BRAND, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Model", Build.MODEL, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Device Codename", Build.DEVICE, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Product", Build.PRODUCT, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Board Name", Build.BOARD, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Hardware String", Build.HARDWARE, "BUILD_API")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(DeviceSection.OVERVIEW, "Hardware SKU", Build.SKU, "BUILD_API")
        add(DeviceSection.OVERVIEW, "ODM Hardware SKU", Build.ODM_SKU, "BUILD_API")
    }
    add(DeviceSection.OVERVIEW, "Android OS Version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "BUILD_API")
    add(DeviceSection.OVERVIEW, "Build ID / Display", Build.DISPLAY, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Build Fingerprint", Build.FINGERPRINT, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Bootloader Version", Build.BOOTLOADER, "BUILD_API")
    add(DeviceSection.OVERVIEW, "Baseband / Radio", Build.getRadioVersion(), "TELEPHONY_RADIO")
    add(DeviceSection.OVERVIEW, "Build Type / Tags", "${Build.TYPE} / ${Build.TAGS}", "BUILD_API")
    add(DeviceSection.OVERVIEW, "Build User & Host", "${Build.USER}@${Build.HOST}", "BUILD_API")
    add(DeviceSection.OVERVIEW, "Build Timestamp", java.util.Date(Build.TIME).toString(), "BUILD_API")

    // 2. CPU & SOC
    val cpuinfoLines = readFileLines("/proc/cpuinfo")
    val cpuinfoMap = mutableMapOf<String, String>()
    cpuinfoLines.forEach { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2) {
            val k = parts[0].trim()
            val v = parts[1].trim()
            if (!cpuinfoMap.containsKey(k)) cpuinfoMap[k] = v
        }
    }

    add(DeviceSection.CPU, "SoC Model", getSystemProperty("ro.soc.model").ifEmpty { getSystemProperty("ro.board.platform") }.ifEmpty { cpuinfoMap["Hardware"] }, "GETPROP / CPUINFO")
    add(DeviceSection.CPU, "SoC Platform", getSystemProperty("ro.board.platform").ifEmpty { Build.HARDWARE }, "GETPROP")
    add(DeviceSection.CPU, "SoC Chipset ID", getSystemProperty("ro.chipname").ifEmpty { getSystemProperty("ro.mediatek.platform") }, "GETPROP")
    add(DeviceSection.CPU, "CPU Architecture", System.getProperty("os.arch"), "JAVA_ENV")
    val coreCount = Runtime.getRuntime().availableProcessors()
    add(DeviceSection.CPU, "Total CPU Cores (Online)", coreCount.toString(), "RUNTIME_API")
    add(DeviceSection.CPU, "Primary ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown", "BUILD_API")
    add(DeviceSection.CPU, "Supported 32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ").ifEmpty { "None" }, "BUILD_API")
    add(DeviceSection.CPU, "Supported 64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ").ifEmpty { "None (32-bit only SoC)" }, "BUILD_API")
    add(DeviceSection.CPU, "CPU Implementer", cpuinfoMap["CPU implementer"], "PROC_CPUINFO")
    add(DeviceSection.CPU, "CPU Architecture Part", cpuinfoMap["CPU architecture"], "PROC_CPUINFO")
    add(DeviceSection.CPU, "CPU Variant", cpuinfoMap["CPU variant"], "PROC_CPUINFO")
    add(DeviceSection.CPU, "CPU Part", cpuinfoMap["CPU part"], "PROC_CPUINFO")
    add(DeviceSection.CPU, "CPU Revision", cpuinfoMap["CPU revision"], "PROC_CPUINFO")
    add(DeviceSection.CPU, "Hardware String", cpuinfoMap["Hardware"] ?: Build.HARDWARE, "PROC_CPUINFO")
    add(DeviceSection.CPU, "Features / Instructions", cpuinfoMap["Features"], "PROC_CPUINFO")
    add(DeviceSection.CPU, "BogoMIPS", cpuinfoMap["BogoMIPS"], "PROC_CPUINFO")
    
    // Read per-core frequency and governors
    for (i in 0 until 16) {
        val cpuDir = "/sys/devices/system/cpu/cpu$i"
        if (File(cpuDir).exists()) {
            val minFreq = readFileFirstLine("$cpuDir/cpufreq/scaling_min_freq")
            val maxFreq = readFileFirstLine("$cpuDir/cpufreq/scaling_max_freq")
            val curFreq = readFileFirstLine("$cpuDir/cpufreq/scaling_cur_freq")
            val gov = readFileFirstLine("$cpuDir/cpufreq/scaling_governor")
            val minMhz = minFreq.toLongOrNull()?.let { "${it / 1000} MHz" } ?: minFreq
            val maxMhz = maxFreq.toLongOrNull()?.let { "${it / 1000} MHz" } ?: maxFreq
            val curMhz = curFreq.toLongOrNull()?.let { "${it / 1000} MHz" } ?: curFreq
            add(DeviceSection.CPU, "Core #$i Scaling", "Cur: $curMhz | Min: $minMhz | Max: $maxMhz | Gov: ${gov.ifEmpty { "N/A" }}", "SYSFS_CPUFREQ")
        }
    }

    // 3. GPU & DISPLAY
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val reqGlEs = actManager?.deviceConfigurationInfo?.reqGlEsVersion
    add(DeviceSection.GPU, "OpenGL ES Version", reqGlEs?.let { "v${it shr 16}.${it and 0xFFFF}" } ?: "OpenGL ES Supported", "CONFIG_INFO")
    add(DeviceSection.GPU, "GLES Driver Version", getSystemProperty("ro.opengles.version"), "GETPROP")
    add(DeviceSection.GPU, "Vulkan API Supported", if (context.packageManager.hasSystemFeature("android.hardware.vulkan.version")) "YES (Hardware Accelerated)" else "NO / Software Only", "PACKAGE_MANAGER")
    add(DeviceSection.GPU, "GPU Hardware Renderer", getSystemProperty("ro.hardware.egl").ifEmpty { "Mali / Adreno System EGL" }, "GETPROP")

    try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getMetrics(metrics)
        add(DeviceSection.GPU, "Screen Resolution", "${metrics.widthPixels} x ${metrics.heightPixels} px", "DISPLAY_METRICS")
        add(DeviceSection.GPU, "Density DPI", "${metrics.densityDpi} dpi", "DISPLAY_METRICS")
        add(DeviceSection.GPU, "Exact X / Y DPI", "${DecimalFormat("#.##").format(metrics.xdpi)} x ${DecimalFormat("#.##").format(metrics.ydpi)} dpi", "DISPLAY_METRICS")
        add(DeviceSection.GPU, "Density Scale Factor", "${metrics.density}x", "DISPLAY_METRICS")
        @Suppress("DEPRECATION")
        val refresh = wm?.defaultDisplay?.refreshRate
        if (refresh != null) {
            add(DeviceSection.GPU, "Display Refresh Rate", "${DecimalFormat("#.#").format(refresh)} Hz", "DISPLAY_METRICS")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            add(DeviceSection.GPU, "Wide Color Gamut", if (wm?.defaultDisplay?.isWideColorGamut == true) "Supported" else "Standard sRGB", "DISPLAY_METRICS")
            @Suppress("DEPRECATION")
            add(DeviceSection.GPU, "HDR Output Capable", if (wm?.defaultDisplay?.isHdr == true) "Supported" else "Standard SDR", "DISPLAY_METRICS")
        }
    } catch (e: Exception) {}

    // 4. RAM & MEMORY DETAILS
    if (actManager != null) {
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        add(DeviceSection.RAM, "Total Physical RAM", formatBytes(memInfo.totalMem), "ACTIVITY_MANAGER")
        add(DeviceSection.RAM, "Available RAM", formatBytes(memInfo.availMem), "ACTIVITY_MANAGER")
        add(DeviceSection.RAM, "Used RAM", formatBytes(memInfo.totalMem - memInfo.availMem), "ACTIVITY_MANAGER")
        add(DeviceSection.RAM, "Low-Memory Threshold", formatBytes(memInfo.threshold), "ACTIVITY_MANAGER")
        add(DeviceSection.RAM, "Low-Memory State Active", if (memInfo.lowMemory) "YES (CRITICAL MEMORY)" else "NO (Normal)", "ACTIVITY_MANAGER")
        add(DeviceSection.RAM, "Low RAM Go Device Flag", if (actManager.isLowRamDevice) "YES (Android Go Optimized)" else "NO", "ACTIVITY_MANAGER")
        add(DeviceSection.RAM, "App Memory Class", "${actManager.memoryClass} MB (Large: ${actManager.largeMemoryClass} MB)", "ACTIVITY_MANAGER")
    }

    // Read detailed /proc/meminfo
    val meminfoLines = readFileLines("/proc/meminfo")
    val memMap = mutableMapOf<String, String>()
    meminfoLines.forEach { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2) memMap[parts[0].trim()] = parts[1].trim()
    }
    memMap["MemTotal"]?.let { add(DeviceSection.RAM, "/proc/meminfo MemTotal", it, "PROC_MEMINFO") }
    memMap["MemFree"]?.let { add(DeviceSection.RAM, "/proc/meminfo MemFree", it, "PROC_MEMINFO") }
    memMap["MemAvailable"]?.let { add(DeviceSection.RAM, "/proc/meminfo MemAvailable", it, "PROC_MEMINFO") }
    memMap["Buffers"]?.let { add(DeviceSection.RAM, "Buffer Cache", it, "PROC_MEMINFO") }
    memMap["Cached"]?.let { add(DeviceSection.RAM, "Page Cache (Cached)", it, "PROC_MEMINFO") }
    memMap["SwapTotal"]?.let { add(DeviceSection.RAM, "ZRAM / Swap Total", it, "PROC_MEMINFO") }
    memMap["SwapFree"]?.let { add(DeviceSection.RAM, "ZRAM / Swap Free", it, "PROC_MEMINFO") }
    memMap["Dirty"]?.let { add(DeviceSection.RAM, "Dirty Memory", it, "PROC_MEMINFO") }
    memMap["Shmem"]?.let { add(DeviceSection.RAM, "Shared Memory (Shmem)", it, "PROC_MEMINFO") }
    memMap["Slab"]?.let { add(DeviceSection.RAM, "Kernel Slab Allocation", it, "PROC_MEMINFO") }

    // 5. STORAGE & DISKS
    try {
        val dataStat = StatFs(Environment.getDataDirectory().path)
        val dataTotal = dataStat.blockCountLong * dataStat.blockSizeLong
        val dataFree = dataStat.availableBlocksLong * dataStat.blockSizeLong
        add(DeviceSection.STORAGE, "Internal Storage (/data) Total", formatBytes(dataTotal), "STATFS")
        add(DeviceSection.STORAGE, "Internal Storage (/data) Free", formatBytes(dataFree), "STATFS")
        add(DeviceSection.STORAGE, "Internal Storage (/data) Used", formatBytes(dataTotal - dataFree), "STATFS")
    } catch (e: Exception) {}

    try {
        val rootStat = StatFs(Environment.getRootDirectory().path)
        val rootTotal = rootStat.blockCountLong * rootStat.blockSizeLong
        val rootFree = rootStat.availableBlocksLong * rootStat.blockSizeLong
        add(DeviceSection.STORAGE, "System Partition (/system) Total", formatBytes(rootTotal), "STATFS")
        add(DeviceSection.STORAGE, "System Partition (/system) Free", formatBytes(rootFree), "STATFS")
    } catch (e: Exception) {}

    try {
        val cacheStat = StatFs(Environment.getDownloadCacheDirectory().path)
        val cacheTotal = cacheStat.blockCountLong * cacheStat.blockSizeLong
        val cacheFree = cacheStat.availableBlocksLong * cacheStat.blockSizeLong
        add(DeviceSection.STORAGE, "Cache Partition (/cache) Total", formatBytes(cacheTotal), "STATFS")
        add(DeviceSection.STORAGE, "Cache Partition (/cache) Free", formatBytes(cacheFree), "STATFS")
    } catch (e: Exception) {}

    val extDirs = context.getExternalFilesDirs(null)
    if (extDirs.size > 1 && extDirs[1] != null) {
        try {
            val sdStat = StatFs(extDirs[1].path)
            val sdTotal = sdStat.blockCountLong * sdStat.blockSizeLong
            val sdFree = sdStat.availableBlocksLong * sdStat.blockSizeLong
            add(DeviceSection.STORAGE, "External MicroSD Card Total", formatBytes(sdTotal), "STATFS_SDCARD")
            add(DeviceSection.STORAGE, "External MicroSD Card Free", formatBytes(sdFree), "STATFS_SDCARD")
            add(DeviceSection.STORAGE, "MicroSD Mount Path", extDirs[1].absolutePath, "EXTERNAL_FILES")
        } catch (e: Exception) {}
    } else {
        add(DeviceSection.STORAGE, "External MicroSD Card", "No Secondary SD Card Inserted", "SYSTEM_STORAGE")
    }

    // 6. PARTITIONS & MOUNTS
    val procMounts = readFileLines("/proc/mounts")
    if (procMounts.isNotEmpty()) {
        val targetMounts = listOf("/system", "/vendor", "/data", "/cache", "/boot", "/recovery", "/efs", "/persist", "/metadata")
        procMounts.forEach { line ->
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 4) {
                val mountPoint = parts[1]
                if (targetMounts.any { mountPoint == it || mountPoint.startsWith("$it/") }) {
                    add(DeviceSection.PARTITIONS, "Mount: $mountPoint", "Device: ${parts[0]} | FS: ${parts[2]} | Flags: ${parts[3]}", "PROC_MOUNTS")
                }
            }
        }
    }

    val procPartitions = readFileLines("/proc/partitions")
    if (procPartitions.size > 2) {
        val blkCount = procPartitions.drop(2).count { it.isNotBlank() }
        add(DeviceSection.PARTITIONS, "Total Block Devices / Partitions", "$blkCount partitions indexed in /proc/partitions", "PROC_PARTITIONS")
    }

    // 7. BATTERY & POWER
    try {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val chargeSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable / PC"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Induction"
                0 -> "On Battery (Discharging)"
                else -> "Unknown Source"
            }
            val health = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good (Healthy)"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat Alert"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead / Damaged"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold Temperature"
                else -> "Unknown"
            }
            val status = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full (100%)"
                else -> "Unknown"
            }

            if (level != -1 && scale != -1) {
                add(DeviceSection.BATTERY, "Battery Level", "${(level * 100) / scale}%", "BATTERY_BROADCAST")
            }
            add(DeviceSection.BATTERY, "Battery Status", status, "BATTERY_BROADCAST")
            add(DeviceSection.BATTERY, "Power Supply Source", chargeSource, "BATTERY_BROADCAST")
            add(DeviceSection.BATTERY, "Battery Health", health, "BATTERY_BROADCAST")
            if (temp != -1) {
                val c = temp / 10.0
                val f = (c * 9 / 5) + 32
                add(DeviceSection.BATTERY, "Battery Temperature", "${DecimalFormat("#.#").format(c)} °C (${DecimalFormat("#.#").format(f)} °F)", "BATTERY_BROADCAST")
            }
            if (voltage != -1) {
                add(DeviceSection.BATTERY, "Battery Voltage", "$voltage mV (${DecimalFormat("#.##").format(voltage / 1000.0)} V)", "BATTERY_BROADCAST")
            }
            add(DeviceSection.BATTERY, "Battery Chemistry / Tech", technology, "BATTERY_BROADCAST")
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null) {
            add(DeviceSection.BATTERY, "Power Save Mode Active", if (powerManager.isPowerSaveMode) "YES (Battery Saver On)" else "NO", "POWER_MANAGER")
            add(DeviceSection.BATTERY, "Interactive Display State", if (powerManager.isInteractive) "Screen On (Interactive)" else "Screen Off", "POWER_MANAGER")
        }
    } catch (e: Exception) {}

    // 8. THERMAL
    for (i in 0..15) {
        val tZone = "/sys/class/thermal/thermal_zone$i"
        if (File(tZone).exists()) {
            val type = readFileFirstLine("$tZone/type")
            val tempRaw = readFileFirstLine("$tZone/temp")
            val tempC = tempRaw.toDoubleOrNull()?.let { if (it > 1000) it / 1000.0 else it }
            if (type.isNotEmpty() && tempC != null) {
                add(DeviceSection.THERMAL, "Zone #$i: $type", "${DecimalFormat("#.#").format(tempC)} °C", "SYSFS_THERMAL")
            }
        }
    }
    try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val thermalStatus = when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "None (Safe / Normal Temp)"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical (Cooldown Required)"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency (Shutdown Imminent)"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
                else -> "Normal"
            }
            add(DeviceSection.THERMAL, "System Thermal Headroom", thermalStatus, "POWER_MANAGER")
        }
    } catch (e: Exception) {}

    // 9. CAMERAS
    try {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (camManager != null) {
            val camIds = camManager.cameraIdList
            add(DeviceSection.CAMERA, "Total Hardware Cameras", "${camIds.size} camera modules detected", "CAMERA_MANAGER")
            camIds.forEach { id ->
                val chars = camManager.getCameraCharacteristics(id)
                val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front-Facing (Selfie)"
                    CameraCharacteristics.LENS_FACING_BACK -> "Back-Facing (Rear Primary)"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "External USB Camera"
                    else -> "Unknown Facing"
                }
                val flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy (Camera1 HAL)"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited HAL3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full HAL3"
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3 (Advanced RAW/YUV)"
                    else -> "Standard"
                }
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val sensorStr = if (sensorSize != null) "${sensorSize.width} x ${sensorSize.height} mm" else "Integrated"
                add(DeviceSection.CAMERA, "Camera #$id ($facing)", "HW Level: $hwLevel | Flash: ${if (flash) "LED Available" else "No"} | Sensor: $sensorStr", "CAMERA_MANAGER")
            }
        }
    } catch (e: Exception) {}

    // 10. AUDIO
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            val framesPerBuffer = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            add(DeviceSection.AUDIO, "Native Output Sample Rate", "${sampleRate ?: "44100"} Hz", "AUDIO_MANAGER")
            add(DeviceSection.AUDIO, "Low Latency Buffer Size", "${framesPerBuffer ?: "Unknown"} frames", "AUDIO_MANAGER")
            add(DeviceSection.AUDIO, "Low Latency Audio Feature", if (context.packageManager.hasSystemFeature("android.hardware.audio.low_latency")) "YES (OpenSL ES / AAudio)" else "NO", "PACKAGE_MANAGER")
            add(DeviceSection.AUDIO, "Pro Audio Feature", if (context.packageManager.hasSystemFeature("android.hardware.audio.pro")) "YES" else "NO", "PACKAGE_MANAGER")
            add(DeviceSection.AUDIO, "MIDI Synthesizer Feature", if (context.packageManager.hasSystemFeature("android.software.midi")) "YES" else "NO", "PACKAGE_MANAGER")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val deviceNames = devices.joinToString(", ") { dev ->
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
                        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset"
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headphones"
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
                        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
                        else -> "Dev#${dev.type}"
                    }
                }
                add(DeviceSection.AUDIO, "Active Output Endpoints", deviceNames.ifEmpty { "Internal Speaker" }, "AUDIO_MANAGER")
            }
        }
    } catch (e: Exception) {}

    // 11. SENSORS
    try {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager != null) {
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            add(DeviceSection.SENSORS, "Total Hardware Sensors", "${sensors.size} physical & virtual sensors", "SENSOR_MANAGER")
            sensors.forEach { sensor ->
                val typeName = when (sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
                    Sensor.TYPE_GYROSCOPE -> "Gyroscope"
                    Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer (Compass)"
                    Sensor.TYPE_PROXIMITY -> "Proximity Sensor"
                    Sensor.TYPE_LIGHT -> "Ambient Light Sensor"
                    Sensor.TYPE_PRESSURE -> "Barometer / Pressure"
                    Sensor.TYPE_GRAVITY -> "Gravity Sensor"
                    Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
                    Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
                    Sensor.TYPE_STEP_COUNTER -> "Step Counter"
                    Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
                    Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
                    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic Vector"
                    else -> sensor.name
                }
                add(DeviceSection.SENSORS, typeName, "Vendor: ${sensor.vendor} | Version: ${sensor.version} | Power: ${sensor.power} mA | Max: ${sensor.maximumRange} | Res: ${sensor.resolution}", "SENSOR_MANAGER")
            }
        }
    } catch (e: Exception) {}

    // 12. NETWORK & CONNECTIVITY
    try {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val activeTransport = when {
            isWifi -> "Wi-Fi (Wireless LAN)"
            isCellular -> "Cellular Mobile Data"
            isEthernet -> "Ethernet Wired"
            isVpn -> "VPN Encrypted Tunnel"
            else -> "Disconnected / Airplane Mode"
        }
        add(DeviceSection.NETWORK, "Primary Network Transport", activeTransport, "CONNECTIVITY_MANAGER")
        if (caps != null) {
            add(DeviceSection.NETWORK, "Downlink Bandwidth", "${caps.linkDownstreamBandwidthKbps / 1000} Mbps", "NETWORK_CAPABILITIES")
            add(DeviceSection.NETWORK, "Uplink Bandwidth", "${caps.linkUpstreamBandwidthKbps / 1000} Mbps", "NETWORK_CAPABILITIES")
        }
        add(DeviceSection.NETWORK, "Wi-Fi 5GHz Band Supported", if (context.packageManager.hasSystemFeature("android.hardware.wifi.5ghz")) "YES" else "NO (2.4GHz Only)", "PACKAGE_MANAGER")
        add(DeviceSection.NETWORK, "Wi-Fi Direct (P2P)", if (context.packageManager.hasSystemFeature("android.hardware.wifi.direct")) "YES" else "NO", "PACKAGE_MANAGER")
        add(DeviceSection.NETWORK, "Bluetooth Capable", if (context.packageManager.hasSystemFeature("android.hardware.bluetooth")) "YES" else "NO", "PACKAGE_MANAGER")
        add(DeviceSection.NETWORK, "Bluetooth Low Energy (BLE)", if (context.packageManager.hasSystemFeature("android.hardware.bluetooth_le")) "YES (BT 4.0+ LE)" else "NO", "PACKAGE_MANAGER")
        add(DeviceSection.NETWORK, "NFC Hardware", if (context.packageManager.hasSystemFeature("android.hardware.nfc")) "YES" else "NO", "PACKAGE_MANAGER")
        add(DeviceSection.NETWORK, "GPS / GNSS Receiver", if (context.packageManager.hasSystemFeature("android.hardware.location.gps")) "YES (GPS/GLONASS)" else "NO", "PACKAGE_MANAGER")
    } catch (e: Exception) {}

    // 13. TELEPHONY & SIM
    try {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (telephonyManager != null) {
            val simState = when (telephonyManager.simState) {
                TelephonyManager.SIM_STATE_READY -> "SIM Ready & Active"
                TelephonyManager.SIM_STATE_ABSENT -> "No SIM Card Inserted"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Locked"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Locked"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Carrier Locked"
                else -> "Unknown State"
            }
            add(DeviceSection.TELEPHONY, "Primary SIM Card State", simState, "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "Mobile Operator Name", telephonyManager.networkOperatorName.ifEmpty { "None / Searching" }, "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "SIM Operator Name", telephonyManager.simOperatorName.ifEmpty { "None" }, "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "Country ISO", telephonyManager.networkCountryIso.uppercase().ifEmpty { "UNKNOWN" }, "TELEPHONY_MANAGER")
            @Suppress("DEPRECATION")
            val phoneTypeStr = when (telephonyManager.phoneType) {
                TelephonyManager.PHONE_TYPE_GSM -> "GSM / UMTS / LTE"
                TelephonyManager.PHONE_TYPE_CDMA -> "CDMA / EVDO"
                TelephonyManager.PHONE_TYPE_SIP -> "SIP VoIP"
                else -> "None"
            }
            add(DeviceSection.TELEPHONY, "Phone Radio Type", phoneTypeStr, "TELEPHONY_MANAGER")
            add(DeviceSection.TELEPHONY, "Network Roaming", if (telephonyManager.isNetworkRoaming) "YES (Roaming)" else "NO (Home Network)", "TELEPHONY_MANAGER")
        }
    } catch (e: Exception) {}

    // 14. USB & ACCESSORIES
    add(DeviceSection.USB, "USB Host (OTG) Support", if (context.packageManager.hasSystemFeature("android.hardware.usb.host")) "YES (OTG Flash Drives / Mice Supported)" else "NO", "PACKAGE_MANAGER")
    add(DeviceSection.USB, "USB Accessory Protocol", if (context.packageManager.hasSystemFeature("android.hardware.usb.accessory")) "YES (AOA Supported)" else "NO", "PACKAGE_MANAGER")
    add(DeviceSection.USB, "USB Configuration", getSystemProperty("sys.usb.config").ifEmpty { getSystemProperty("persist.sys.usb.config") }, "GETPROP")
    add(DeviceSection.USB, "USB State", getSystemProperty("sys.usb.state"), "GETPROP")

    // 15. SECURITY & ROOT
    val isRoot = RootShell.isRootAvailable()
    add(DeviceSection.SECURITY, "Root Superuser Access", if (isRoot) "GRANTED (UID 0 / System Root Active)" else "NO ROOT (Standard Android Sandbox)", "ROOT_PROBE")
    val selinuxEnforce = RootShell.executeCommand("getenforce").getOrNull() ?: getSystemProperty("ro.boot.selinux")
    add(DeviceSection.SECURITY, "SELinux Enforcement Mode", selinuxEnforce.ifEmpty { "Enforcing" }, "SELINUX_PROBE")
    add(DeviceSection.SECURITY, "Android Treble Architecture", getSystemProperty("ro.treble.enabled").ifEmpty { "false (Legacy MTK Non-Treble)" }, "GETPROP")
    add(DeviceSection.SECURITY, "A/B Seamless Update Slots", getSystemProperty("ro.build.ab_update").ifEmpty { "false (A-only Recovery Partition)" }, "GETPROP")
    add(DeviceSection.SECURITY, "Android Verified Boot (AVB)", getSystemProperty("ro.boot.verifiedbootstate").ifEmpty { "UNKNOWN" }, "GETPROP")
    add(DeviceSection.SECURITY, "Device Storage Encryption", getSystemProperty("ro.crypto.state").ifEmpty { "unencrypted" }, "GETPROP")
    add(DeviceSection.SECURITY, "Encryption Type", getSystemProperty("ro.crypto.type").ifEmpty { "none" }, "GETPROP")
    add(DeviceSection.SECURITY, "Samsung Knox / Warranty Bit", getSystemProperty("ro.boot.warranty_bit").ifEmpty { "0 (Official / Untripped)" }, "GETPROP_SAMSUNG")
    add(DeviceSection.SECURITY, "ADB Daemon Service", getSystemProperty("init.svc.adbd").ifEmpty { "running" }, "GETPROP")
    add(DeviceSection.SECURITY, "Hardware Keystore / Keymaster", getSystemProperty("ro.crypto.keymaster_version").ifEmpty { "Keymaster v1.0 / Hardware" }, "GETPROP")

    // 16. KERNEL & OS
    add(DeviceSection.KERNEL, "Kernel Version (OS)", System.getProperty("os.version"), "JAVA_ENV")
    val procVersion = readFileFirstLine("/proc/version")
    add(DeviceSection.KERNEL, "Linux Kernel Signature", procVersion, "PROC_VERSION")
    val procCmdline = readFileFirstLine("/proc/cmdline")
    add(DeviceSection.KERNEL, "Kernel Boot Arguments (Cmdline)", procCmdline, "PROC_CMDLINE")
    val procSysRelease = readFileFirstLine("/proc/sys/kernel/osrelease")
    add(DeviceSection.KERNEL, "Kernel OS Release", procSysRelease, "PROC_SYS_KERNEL")
    val procModules = readFileLines("/proc/modules")
    if (procModules.isNotEmpty()) {
        add(DeviceSection.KERNEL, "Loaded Kernel Modules", "${procModules.size} active LKMs (.ko) in /proc/modules", "PROC_MODULES")
    }
    val loadAvg = readFileFirstLine("/proc/loadavg")
    if (loadAvg.isNotEmpty()) {
        add(DeviceSection.KERNEL, "System Load Average", loadAvg, "PROC_LOADAVG")
    }

    // 17. SOFTWARE & BUILD
    add(DeviceSection.SOFTWARE, "Android OS Release", Build.VERSION.RELEASE, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Android SDK Level (API)", Build.VERSION.SDK_INT.toString(), "BUILD_API")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        add(DeviceSection.SOFTWARE, "Android Security Patch", Build.VERSION.SECURITY_PATCH, "BUILD_API")
    }
    add(DeviceSection.SOFTWARE, "Build Incremental Version", Build.VERSION.INCREMENTAL, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Display Version", Build.DISPLAY, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Fingerprint", Build.FINGERPRINT, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build ID", Build.ID, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Type", Build.TYPE, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Build Tags", Build.TAGS, "BUILD_API")
    add(DeviceSection.SOFTWARE, "Java VM Version", System.getProperty("java.vm.version"), "JAVA_ENV")
    add(DeviceSection.SOFTWARE, "Java VM Name", System.getProperty("java.vm.name"), "JAVA_ENV")

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
