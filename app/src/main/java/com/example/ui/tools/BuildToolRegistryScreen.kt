package com.example.ui.tools

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BuildToolInfo(
    val name: String,
    val binaryPath: String?,
    val isAvailable: Boolean,
    val isVerified: Boolean,
    val version: String,
    val capabilities: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildToolRegistryScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var toolsList by remember { mutableStateOf<List<BuildToolInfo>>(emptyList()) }

    fun scanTools() {
        coroutineScope.launch(Dispatchers.IO) {
            isScanning = true
            val targets = listOf(
                Pair("simg2img", "Converts Android sparse images into raw ext4 / erofs images"),
                Pair("img2simg", "Compresses raw filesystem images into Android sparse format"),
                Pair("mkbootimg", "Compiles kernel, ramdisk, dtb, and second images into boot.img (v0-v4)"),
                Pair("unpackbootimg", "Extracts zImage, ramdisk, second, and DTB from Android boot.img"),
                Pair("avbtool", "Android Verified Boot 2.0 footer & vbmeta digest verification"),
                Pair("lpmake", "Creates dynamic super.img partition table for Android 10+ devices"),
                Pair("lpunpack", "Extracts logical partitions (system, vendor, product) from super.img"),
                Pair("brotli", "Decompresses and compresses system.new.dat.br brotli streams"),
                Pair("lz4", "LZ4 fast decompression engine for Samsung boot images"),
                Pair("gzip", "Standard GNU zip decompression for ramdisk images"),
                Pair("xz", "XZ / LZMA multi-stream compression engine"),
                Pair("toybox", "Embedded multi-call binary toolbox providing standard Linux utils"),
                Pair("toolbox", "Android core command line utilities (getprop, setprop, start, stop)"),
                Pair("tar", "Tape archive engine for Samsung TAR and TAR.MD5 firmware packages"),
                Pair("dd", "Low-level block device bit-stream copier for partition backup")
            )

            val results = mutableListOf<BuildToolInfo>()
            val searchPaths = listOf("/system/bin", "/system/xbin", "/vendor/bin", "/apex/com.android.runtime/bin", "/data/adb/magisk", "/data/adb/ksu/bin")

            for ((toolName, desc) in targets) {
                var foundPath: String? = null
                for (p in searchPaths) {
                    val f = File(p, toolName)
                    if (f.exists()) {
                        foundPath = f.absolutePath
                        break
                    }
                }

                // If not found in standard paths, probe 'which'
                if (foundPath == null) {
                    val whichRes = RootShell.executeCommand("which $toolName").getOrNull()
                    if (!whichRes.isNullOrBlank() && !whichRes.contains("not found")) {
                        foundPath = whichRes.lines().firstOrNull()?.trim()
                    }
                }

                val available = foundPath != null
                var ver = "System Built-in"
                if (available) {
                    val verRes = RootShell.executeCommand("$foundPath --version || $foundPath -v || $foundPath -V").getOrNull()
                    if (!verRes.isNullOrBlank()) {
                        ver = verRes.lines().firstOrNull()?.take(50) ?: "Available"
                    }
                }

                results.add(
                    BuildToolInfo(
                        name = toolName,
                        binaryPath = foundPath,
                        isAvailable = available,
                        isVerified = available,
                        version = if (available) ver else "UNAVAILABLE",
                        capabilities = desc
                    )
                )
            }

            withContext(Dispatchers.Main) {
                toolsList = results
                isScanning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        scanTools()
    }

    Scaffold(
        topBar = {
            com.example.ui.common.AppTopBar(
                title = "Build Tool Registry",
                subtitle = "Native Binary & Script Discovery",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { scanTools() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Scan")
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BuildCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Local Binary Toolchain Audit", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("NATIVE TOOLS ARE NOT BUNDLED BY DEFAULT (user must provide statically compiled binaries in PATH).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${toolsList.count { it.isAvailable }} of ${toolsList.size} tools detected locally. All operations use local verifiable binaries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(toolsList) { tool ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (tool.isAvailable) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(tool.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (tool.isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = if (tool.isAvailable) "AVAILABLE" else "UNSUPPORTED",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (tool.isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(tool.capabilities, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (tool.binaryPath != null) {
                                        Text("Path: ${tool.binaryPath}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
