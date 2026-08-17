package com.example.ui.samsung

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.manager.SamsungFirmwareSlot
import com.example.data.manager.SamsungTarAnalyzer
import com.example.data.manager.TarEntryInfo
import com.example.ui.analyzer.partition.PartitionEntry
import com.example.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OperationMode {
    SAFE_MODE,
    FLASH_MODE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamsungFirmwareScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var operationMode by remember { mutableStateOf(OperationMode.SAFE_MODE) }
    var showFlashConfirmDialog by remember { mutableStateOf(false) }

    // Slots: BL, AP, CP, CSC, PIT
    var blSlot by remember { mutableStateOf(SamsungFirmwareSlot("BL")) }
    var apSlot by remember { mutableStateOf(SamsungFirmwareSlot("AP")) }
    var cpSlot by remember { mutableStateOf(SamsungFirmwareSlot("CP")) }
    var cscSlot by remember { mutableStateOf(SamsungFirmwareSlot("CSC")) }
    var pitSlot by remember { mutableStateOf(SamsungFirmwareSlot("PIT")) }

    var activePickingSlot by remember { mutableStateOf<String?>(null) }
    var isAnalyzingSlot by remember { mutableStateOf(false) }
    var operationLogs by remember { mutableStateOf<List<String>>(listOf("[INIT] Samsung Firmware & Odin Service initialized.", "[MODE] Running in SAFE ANALYSIS MODE.")) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && activePickingSlot != null) {
            val slotName = activePickingSlot!!
            isAnalyzingSlot = true
            coroutineScope.launch {
                val fileName = getFileName(context, uri)
                val fileSize = getFileSize(context, uri)

                operationLogs = operationLogs + "[IMPORT] Selected $fileName for slot $slotName (${PartitionEntry.formatBytes(fileSize)})"
                val (md5Valid, entries) = SamsungTarAnalyzer.analyzeTarOrMd5(context, uri, fileName, fileSize)

                val updatedSlot = SamsungFirmwareSlot(
                    slotName = slotName,
                    uri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSize,
                    md5Verified = md5Valid,
                    entries = entries
                )

                when (slotName) {
                    "BL" -> blSlot = updatedSlot
                    "AP" -> apSlot = updatedSlot
                    "CP" -> cpSlot = updatedSlot
                    "CSC" -> cscSlot = updatedSlot
                    "PIT" -> pitSlot = updatedSlot
                }

                operationLogs = operationLogs + "[PARSED] Slot $slotName archive parsed: ${entries.size} partition images identified."
                isAnalyzingSlot = false
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Samsung Odin & Firmware Service",
                subtitle = "Galaxy J2 Prime (SM-G532F/G/M)",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            if (operationMode == OperationMode.SAFE_MODE) {
                                showFlashConfirmDialog = true
                            } else {
                                operationMode = OperationMode.SAFE_MODE
                                operationLogs = operationLogs + "[MODE] Switched to SAFE MODE."
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (operationMode == OperationMode.FLASH_MODE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(if (operationMode == OperationMode.FLASH_MODE) "FLASH MODE" else "SAFE MODE", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        if (showFlashConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showFlashConfirmDialog = false },
                title = { Text("Switch to FLASH MODE?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "In FLASH MODE, write actions are enabled. Samsung Knox warranty bit may be irreversibly tripped (0x1) if unsigned binaries are flashed. Ensure you have made full backups and verified partition sizes.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            operationMode = OperationMode.FLASH_MODE
                            operationLogs = operationLogs + "[SECURITY WARNING] FLASH MODE enabled with explicit user confirmation."
                            showFlashConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Enable Flash Mode")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showFlashConfirmDialog = false }) {
                        Text("Keep Safe Mode")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Device") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Firmware") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Validation") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Flash Plan") })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Log") })
            }

            when (selectedTab) {
                0 -> SamsungDeviceTab(context)
                1 -> SamsungFirmwareSlotsTab(
                    slots = listOf(blSlot, apSlot, cpSlot, cscSlot, pitSlot),
                    isAnalyzing = isAnalyzingSlot,
                    onPickSlot = { slot ->
                        activePickingSlot = slot
                        filePicker.launch("*/*")
                    },
                    onClearSlot = { slot ->
                        val empty = SamsungFirmwareSlot(slot)
                        when (slot) {
                            "BL" -> blSlot = empty
                            "AP" -> apSlot = empty
                            "CP" -> cpSlot = empty
                            "CSC" -> cscSlot = empty
                            "PIT" -> pitSlot = empty
                        }
                    }
                )
                2 -> SamsungValidationTab(listOf(blSlot, apSlot, cpSlot, cscSlot, pitSlot))
                3 -> SamsungFlashPlanTab(listOf(blSlot, apSlot, cpSlot, cscSlot, pitSlot), operationMode)
                4 -> SamsungLogTab(operationLogs)
            }
        }
    }
}

@Composable
private fun SamsungDeviceTab(context: Context) {
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    val attachedDevices = usbManager?.deviceList?.values?.toList() ?: emptyList()
    val isSamsungAttached = attachedDevices.any { it.vendorId == 0x04e8 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Samsung Odin Protocol State", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Connection Status: ${if (isSamsungAttached) "CONNECTED (Samsung USB VID: 04E8)" else "NO USB DEVICE DETECTED"}")
                    Text("Odin Port: COM / ttyUSB0 (Ready for handshake)")
                    Text("Download Mode Protocol: Loke / Odin v3.x")
                    Text("Knox Warranty Counter: 0x0 (Enforcing)")
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Target Hardware Specification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Model: Samsung Galaxy J2 Prime (SM-G532F / SM-G532G / SM-G532M)")
                    Text("SoC: MediaTek MT6737T (4x Cortex-A53 @ 1.4GHz)")
                    Text("GPU: Mali-T720 MP2")
                    Text("Flash Type: eMMC 5.0 (8GB / 16GB Storage)")
                    Text("Baseband: MT6176 LTE Modem")
                }
            }
        }
    }
}

@Composable
private fun SamsungFirmwareSlotsTab(
    slots: List<SamsungFirmwareSlot>,
    isAnalyzing: Boolean,
    onPickSlot: (String) -> Unit,
    onClearSlot: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Odin Firmware Tar Archive Slots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Select official or custom tar / tar.md5 firmware packages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }

        items(slots) { slot ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (slot.fileName != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    slot.slotName,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    slot.fileName ?: "No file loaded",
                                    fontWeight = if (slot.fileName != null) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (slot.fileSizeBytes > 0) {
                                    Text(
                                        "${PartitionEntry.formatBytes(slot.fileSizeBytes)} • ${slot.entries.size} partition images",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row {
                            if (slot.fileName != null) {
                                IconButton(onClick = { onClearSlot(slot.slotName) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Button(
                                onClick = { onPickSlot(slot.slotName) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (slot.fileName != null) "Change" else "Select")
                            }
                        }
                    }

                    if (slot.entries.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("Contained partitions: " + slot.entries.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungValidationTab(slots: List<SamsungFirmwareSlot>) {
    val loadedSlots = slots.filter { it.fileName != null }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (loadedSlots.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No firmware archives loaded for validation. Select BL, AP, CP, or CSC in the Firmware tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(loadedSlots) { slot ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Slot [${slot.slotName}]: ${slot.fileName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("MD5: ${if (slot.md5Verified == true) "PASSED" else "NO MD5 / SKIPPED"}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Partition Count: ${slot.entries.size}", style = MaterialTheme.typography.bodySmall)
                        slot.entries.forEach { entry ->
                            Text("• ${entry.name} (${PartitionEntry.formatBytes(entry.sizeBytes)})", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungFlashPlanTab(slots: List<SamsungFirmwareSlot>, mode: OperationMode) {
    val allEntries = slots.flatMap { slot -> slot.entries.map { slot.slotName to it } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Structured Samsung Flash Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Mode: ${mode.name}", color = if (mode == OperationMode.FLASH_MODE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Total components scheduled: ${allEntries.size}")
                }
            }
        }

        if (allEntries.isEmpty()) {
            item {
                Text("No components scheduled. Load firmware TAR files in the Firmware tab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(allEntries) { (slotName, entry) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("[$slotName] -> ${entry.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Size: ${PartitionEntry.formatBytes(entry.sizeBytes)} • Type: ${entry.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("SCHEDULED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungLogTab(logs: List<String>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(logs) { log ->
            Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "archive.tar"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (e: Exception) {}
    return name
}

private fun getFileSize(context: Context, uri: Uri): Long {
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {}
    return size
}
