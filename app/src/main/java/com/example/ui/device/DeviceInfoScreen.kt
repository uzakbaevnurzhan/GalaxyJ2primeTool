package com.example.ui.device

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.DecimalFormat

data class DeviceStat(val label: String, val value: String)

@Composable
fun DeviceInfoScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val stats = remember { getDeviceStats(context) }
    
    val createTxtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = stats.joinToString("\n") { "${it.label}: ${it.value}" }
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(content.toByteArray())
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Exported to TXT", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    val createJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = JSONObject()
                    stats.forEach { stat -> json.put(stat.label, stat.value) }
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(json.toString(4).toByteArray())
                    }
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Exported to JSON", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Device Information", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val content = stats.joinToString("\n") { "${it.label}: ${it.value}" }
                    clipboard.setPrimaryClip(ClipData.newPlainText("Device Info", content))
                    Toast.makeText(context, "Copied to Clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Copy All")
            }
            OutlinedButton(onClick = { createTxtLauncher.launch("DeviceInfo.txt") }, modifier = Modifier.weight(1f)) {
                Text("Export TXT")
            }
            OutlinedButton(onClick = { createJsonLauncher.launch("DeviceInfo.json") }, modifier = Modifier.weight(1f)) {
                Text("Export JSON")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(stats.size) { index ->
                val stat = stats[index]
                InfoRow(stat.label, stat.value)
            }
        }
    }
}

fun getDeviceStats(context: Context): List<DeviceStat> {
    val list = mutableListOf<DeviceStat>()
    
    list.add(DeviceStat("Manufacturer", Build.MANUFACTURER))
    list.add(DeviceStat("Model", Build.MODEL))
    list.add(DeviceStat("Device", Build.DEVICE))
    list.add(DeviceStat("Product", Build.PRODUCT))
    list.add(DeviceStat("Board", Build.BOARD))
    list.add(DeviceStat("Hardware", Build.HARDWARE))
    list.add(DeviceStat("Host", Build.HOST))
    list.add(DeviceStat("ID", Build.ID))
    list.add(DeviceStat("Display", Build.DISPLAY))
    
    list.add(DeviceStat("Android Version", Build.VERSION.RELEASE))
    list.add(DeviceStat("SDK Int", Build.VERSION.SDK_INT.toString()))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        list.add(DeviceStat("Security Patch", Build.VERSION.SECURITY_PATCH))
    }
    
    list.add(DeviceStat("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ")))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        list.add(DeviceStat("32-bit ABIs", Build.SUPPORTED_32_BIT_ABIS.joinToString(", ")))
        list.add(DeviceStat("64-bit ABIs", Build.SUPPORTED_64_BIT_ABIS.joinToString(", ")))
    }
    
    // CPU
    list.add(DeviceStat("CPU Cores", Runtime.getRuntime().availableProcessors().toString()))
    
    // Memory
    val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actManager.getMemoryInfo(memInfo)
    list.add(DeviceStat("Total RAM", formatSize(memInfo.totalMem)))
    list.add(DeviceStat("Available RAM", formatSize(memInfo.availMem)))
    
    // Storage
    val path = Environment.getDataDirectory()
    val stat = StatFs(path.path)
    val blockSize = stat.blockSizeLong
    val totalBlocks = stat.blockCountLong
    val availableBlocks = stat.availableBlocksLong
    list.add(DeviceStat("Total Storage", formatSize(totalBlocks * blockSize)))
    list.add(DeviceStat("Available Storage", formatSize(availableBlocks * blockSize)))
    
    // Kernel
    list.add(DeviceStat("Kernel Version", System.getProperty("os.version") ?: "Unknown"))
    
    return list
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

@Composable
fun InfoRow(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(text = value.ifEmpty { "Unknown" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
