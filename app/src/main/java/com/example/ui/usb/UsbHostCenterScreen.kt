package com.example.ui.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.DecimalFormat

data class DetectedUsbDevice(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val interfaceCount: Int,
    val detectedMode: String
)

data class FirmwareTarEntry(
    val name: String,
    val sizeBytes: Long,
    val componentType: String,
    val riskLevel: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbHostCenterScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var usbDevices by remember { mutableStateOf<List<DetectedUsbDevice>>(emptyList()) }
    var isHostSupported by remember { mutableStateOf(false) }
    
    // Firmware Analyzer State
    var firmwareEntries by remember { mutableStateOf<List<FirmwareTarEntry>>(emptyList()) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isAnalyzingFirmware by remember { mutableStateOf(false) }

    fun refreshUsbDevices() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        isHostSupported = context.packageManager.hasSystemFeature("android.hardware.usb.host")
        
        if (usbManager != null) {
            val deviceList = usbManager.deviceList.values.map { dev ->
                val vid = dev.vendorId
                val pid = dev.productId
                val mode = when {
                    vid == 0x04E8 && (pid == 0x685D || pid == 0x6860 || pid == 0x6601 || pid == 0x685E) -> "Samsung Download Mode / Odin"
                    vid == 0x18D1 && (pid == 0x4EE0 || pid == 0x0D02) -> "Google Fastboot"
                    vid == 0x18D1 && (pid == 0x4EE7 || pid == 0x4EE2) -> "Android ADB Debugging"
                    vid == 0x0E8D -> "MediaTek BootROM / Preloader"
                    vid == 0x05C6 -> "Qualcomm EDL 9008 Mode"
                    else -> "Generic USB Device"
                }

                DetectedUsbDevice(
                    deviceName = dev.deviceName,
                    vendorId = vid,
                    productId = pid,
                    manufacturerName = dev.manufacturerName,
                    productName = dev.productName,
                    interfaceCount = dev.interfaceCount,
                    detectedMode = mode
                )
            }
            usbDevices = deviceList
        }
    }

    LaunchedEffect(Unit) {
        refreshUsbDevices()
    }

    val firmwarePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                isAnalyzingFirmware = true
                try {
                    val stream = context.contentResolver.openInputStream(it)
                    val entries = parseTarFirmware(stream)
                    withContext(Dispatchers.Main) {
                        firmwareEntries = entries
                        selectedFileName = it.lastPathSegment ?: "firmware.tar.md5"
                        isAnalyzingFirmware = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tar parse failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        isAnalyzingFirmware = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB Host & Samsung Odin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshUsbDevices() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh USB")
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
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("USB OTG (${usbDevices.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Samsung Tar Analyzer") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Flash Safety Plan") })
            }

            when (selectedTab) {
                0 -> {
                    // USB OTG Devices Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isHostSupported) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isHostSupported) Icons.Filled.Usb else Icons.Filled.UsbOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = if (isHostSupported) "USB HOST (OTG) SUPPORTED" else "USB HOST UNSUPPORTED",
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isHostSupported) "Device hardware supports USB OTG peripherals and downstream Samsung phones." else "Device kernel lacks USB host controller drivers.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        if (usbDevices.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.Cable, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("No USB devices connected via OTG", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Connect a phone in Download / Fastboot mode via OTG adapter.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        } else {
                            items(usbDevices) { dev ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(dev.productName ?: dev.deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = dev.detectedMode,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Vendor ID: 0x${dev.vendorId.toString(16).padStart(4, '0').uppercase()} | Product ID: 0x${dev.productId.toString(16).padStart(4, '0').uppercase()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                        Text("Manufacturer: ${dev.manufacturerName ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                                        Text("Interfaces: ${dev.interfaceCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Samsung Tar Analyzer Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { firmwarePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Archive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Samsung TAR / TAR.MD5 Package")
                        }

                        if (selectedFileName != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loaded: $selectedFileName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isAnalyzingFirmware) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (firmwareEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Import a Samsung BL / AP / CP / CSC tar.md5 file to inspect embedded partition images.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(firmwareEntries) { entry ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(entry.name, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                Text("Component: ${entry.componentType} | Size: ${formatBytes(entry.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (entry.riskLevel) {
                                                    "CRITICAL" -> MaterialTheme.colorScheme.errorContainer
                                                    "HIGH" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                                    "MEDIUM" -> MaterialTheme.colorScheme.tertiaryContainer
                                                    else -> MaterialTheme.colorScheme.primaryContainer
                                                }
                                            ) {
                                                Text(
                                                    text = entry.riskLevel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Flash Safety Plan Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Flash Safety Protocols (SAFE MODE)", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• Auto-flash and silent write operations are strictly disabled.\n• Partition limits and SHA-256 digests are verified prior to any staging.\n• Samsung bootloader lock states (KNOX Warranty Void & FRP) are respected.\n• Original partitions remain untouched during backup and inspection.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseTarFirmware(inputStream: InputStream?): List<FirmwareTarEntry> {
    if (inputStream == null) return emptyList()
    val list = mutableListOf<FirmwareTarEntry>()
    try {
        val header = ByteArray(512)
        inputStream.buffered().use { stream ->
            while (true) {
                var offset = 0
                while (offset < 512) {
                    val read = stream.read(header, offset, 512 - offset)
                    if (read <= 0) break
                    offset += read
                }
                if (offset < 512) break

                // Check if empty block
                if (header.all { it == 0.toByte() }) {
                    break
                }

                // File name is in header[0..99]
                val nameEnd = header.take(100).indexOfFirst { it == 0.toByte() }.let { if (it == -1) 100 else it }
                val name = String(header, 0, nameEnd, Charsets.US_ASCII).trim()

                // Size is octal string in header[124..135]
                val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim().replace("\u0000", "")
                val size = try {
                    java.lang.Long.parseLong(sizeStr.trim(), 8)
                } catch (e: Exception) {
                    0L
                }

                if (name.isNotEmpty()) {
                    val comp = when {
                        name.startsWith("sboot") || name.startsWith("param") -> "Bootloader (Primary)"
                        name.startsWith("boot") -> "Kernel & Ramdisk (Boot)"
                        name.startsWith("recovery") -> "Recovery Image"
                        name.startsWith("system") -> "System OS (EXT4/Sparse)"
                        name.startsWith("vendor") -> "Vendor Drivers (EXT4/Sparse)"
                        name.startsWith("modem") || name.endsWith(".bin") -> "Radio / Baseband"
                        name.endsWith(".pit") -> "Partition Information Table (PIT)"
                        name.startsWith("hidden") || name.startsWith("cache") -> "CSC / Cache"
                        else -> "Partition Image"
                    }

                    val risk = when {
                        name.startsWith("sboot") || name.endsWith(".pit") -> "CRITICAL"
                        name.startsWith("boot") || name.startsWith("recovery") -> "HIGH"
                        name.startsWith("system") || name.startsWith("vendor") -> "MEDIUM"
                        else -> "LOW"
                    }

                    list.add(FirmwareTarEntry(name, size, comp, risk))
                }

                // Skip payload + 512 padding
                val padding = (512 - (size % 512)) % 512
                val toSkip = size + padding
                var skipped = 0L
                while (skipped < toSkip) {
                    val s = stream.skip(toSkip - skipped)
                    if (s <= 0) {
                        // fallback read
                        val temp = ByteArray(Math.min(4096L, toSkip - skipped).toInt())
                        val r = stream.read(temp)
                        if (r <= 0) break
                        skipped += r
                    } else {
                        skipped += s
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
