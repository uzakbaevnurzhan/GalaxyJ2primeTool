package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.data.manager.*
import com.example.ui.analyzer.flash.DeviceProfile
import com.example.ui.analyzer.partition.PartitionEntry
import com.example.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val themeMode by ThemePreferences.themeMode.collectAsState()
    val dynamicColor by ThemePreferences.dynamicColor.collectAsState()
    val autoUpdateCheck by ThemePreferences.autoUpdateCheck.collectAsState()
    val askBeforeModify by ThemePreferences.askBeforeModify.collectAsState()
    val maxArchiveSize by ThemePreferences.maxArchiveSize.collectAsState()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var downloadProgress by remember { mutableStateOf<Pair<Float, String>?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var cacheSizeBytes by remember { mutableStateOf(0L) }

    fun refreshCacheSize() {
        coroutineScope.launch(Dispatchers.IO) {
            var size = 0L
            context.cacheDir.walkTopDown().forEach { f ->
                if (f.isFile) size += f.length()
            }
            cacheSizeBytes = size
        }
    }

    LaunchedEffect(Unit) {
        refreshCacheSize()
    }

    fun startUpdateCheck() {
        isCheckingUpdates = true
        coroutineScope.launch {
            val res = UpdateChecker.checkForUpdates()
            updateResult = res
            isCheckingUpdates = false
            showUpdateDialog = true
        }
    }

    if (showUpdateDialog && updateResult != null) {
        val res = updateResult!!
        AlertDialog(
            onDismissRequest = {
                if (downloadProgress == null) showUpdateDialog = false
            },
            title = {
                Text(
                    when (res) {
                        is UpdateCheckResult.UpdateAvailable -> "New Update Available: ${res.release.versionName}"
                        is UpdateCheckResult.UpToDate -> "Application is Up to Date"
                        is UpdateCheckResult.Error -> "Update Check Failed"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    when (res) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            Text("Current Version: ${res.currentVersion}")
                            Text("Latest Release: ${res.release.versionName} (${PartitionEntry.formatBytes(res.release.sizeBytes)})")
                            Spacer(Modifier.height(8.dp))
                            Text("Changelog:", fontWeight = FontWeight.Bold)
                            Text(res.release.changelog, style = MaterialTheme.typography.bodySmall)

                            if (downloadProgress != null) {
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress!!.first },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(downloadProgress!!.second, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Text("You are using the latest version: ${res.currentVersion} (${UpdateChecker.CURRENT_BUILD_NUMBER}).")
                        }
                        is UpdateCheckResult.Error -> {
                            Text("Error: ${res.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text("Releases URL: https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (res is UpdateCheckResult.UpdateAvailable) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                downloadProgress = 0.05f to "Initiating download..."
                                val apk = UpdateChecker.downloadUpdateApk(context, res.release.downloadUrl) { prog, msg ->
                                    downloadProgress = prog to msg
                                }
                                if (apk != null && apk.exists()) {
                                    downloadProgress = 1.0f to "Download complete. Launching installer..."
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW)
                                        val apkUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            apk
                                        )
                                        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Installer launch failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
                                }
                                showUpdateDialog = false
                                downloadProgress = null
                            }
                        },
                        enabled = downloadProgress == null
                    ) {
                        Text("Download & Install")
                    }
                } else {
                    Button(onClick = { showUpdateDialog = false }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (res is UpdateCheckResult.UpdateAvailable && downloadProgress == null) {
                    OutlinedButton(onClick = { showUpdateDialog = false }) {
                        Text("Later")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Settings & Preferences",
                subtitle = "Configuration • Galaxy J2 Prime Tool"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // General Settings
            item { SettingsSectionTitle("General & Safety") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ask Before Modifying Files", fontWeight = FontWeight.SemiBold)
                                Text("Require explicit confirmation prior to patching partitions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = askBeforeModify,
                                onCheckedChange = { ThemePreferences.setAskBeforeModify(it) }
                            )
                        }
                    }
                }
            }

            // Appearance Settings
            item { SettingsSectionTitle("Appearance & Theme") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Theme Mode", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.values().forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { ThemePreferences.setThemeMode(mode) },
                                    label = { Text(mode.name) }
                                )
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dynamic System Color (Material You)", fontWeight = FontWeight.SemiBold)
                                    Text("Apply wallpaper color scheme dynamically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = dynamicColor,
                                    onCheckedChange = { ThemePreferences.setDynamicColor(it) }
                                )
                            }
                        }
                    }
                }
            }

            // Updates Settings
            item { SettingsSectionTitle("Application Updates") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current Version: ${UpdateChecker.CURRENT_VERSION}", fontWeight = FontWeight.Bold)
                                Text("Release Channel: GitHub Releases (Offline-first)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { startUpdateCheck() },
                                enabled = !isCheckingUpdates
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Check")
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Check for Updates", fontWeight = FontWeight.SemiBold)
                                Text("Poll GitHub releases when network is available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoUpdateCheck,
                                onCheckedChange = { ThemePreferences.setAutoUpdateCheck(it) }
                            )
                        }
                    }
                }
            }

            // Target Hardware & Device Profile
            item { SettingsSectionTitle("Target Device Profile") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Active Target: ${DeviceProfile.GALAXY_J2_PRIME.marketingName}", fontWeight = FontWeight.Bold)
                        Text("Model: SM-G532F / SM-G532G / SM-G532M", style = MaterialTheme.typography.bodySmall)
                        Text("Chipset: MediaTek MT6737T (ARM32 Cortex-A53)", style = MaterialTheme.typography.bodySmall)
                        Text("Porting Focus: Android 11 (LineageOS 18.1)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Tools Navigation Shortcuts
            item { SettingsSectionTitle("Hardware & Tool Services") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { navController.navigate("samsung_firmware") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Samsung Odin & Firmware Service")
                        }
                        OutlinedButton(
                            onClick = { navController.navigate("boot_modes") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Boot Modes & Reboot Tool")
                        }
                        OutlinedButton(
                            onClick = { navController.navigate("adb_fastboot") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ADB & Fastboot Studio")
                        }
                    }
                }
            }

            // Storage & Cache
            item { SettingsSectionTitle("Storage & Temporary Files") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Cache Storage: ${PartitionEntry.formatBytes(cacheSizeBytes)}", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    context.cacheDir.deleteRecursively()
                                    context.cacheDir.mkdirs()
                                    refreshCacheSize()
                                }
                                Toast.makeText(context, "Cache wiped.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Temporary Cache & Updates")
                        }
                    }
                }
            }

            // About Section
            item { SettingsSectionTitle("About") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Galaxy J2 Prime ROM Studio", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Version: Beta 3 (v1.0-beta3)", style = MaterialTheme.typography.bodySmall)
                        Text("Min SDK: 24 (Android 7.0) • Target SDK: 36", style = MaterialTheme.typography.bodySmall)
                        Text("Host Architecture: ${System.getProperty("os.arch")} (${Build.SUPPORTED_ABIS.firstOrNull()})", style = MaterialTheme.typography.bodySmall)
                        Text("Developer: uzakbaevnurzhan", style = MaterialTheme.typography.bodySmall)
                        Text("Repository: https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Open Source License: Apache 2.0 / GPLv2", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}
