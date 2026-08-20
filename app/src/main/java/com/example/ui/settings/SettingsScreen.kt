package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.data.manager.*
import com.example.ui.analyzer.flash.DeviceProfile
import com.example.ui.analyzer.partition.PartitionEntry
import com.example.ui.common.AppTopBar
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var logsSizeBytes by remember { mutableStateOf(0L) }
    var rootStatus by remember { mutableStateOf("Checking...") }

    fun refreshStorageMetrics() {
        coroutineScope.launch(Dispatchers.IO) {
            var cSize = 0L
            context.cacheDir.walkTopDown().forEach { f -> if (f.isFile) cSize += f.length() }
            cacheSizeBytes = cSize

            val isRoot = RootShell.isRootAvailable()
            rootStatus = if (isRoot) "Granted (Rooted)" else "Not Available (Non-Root)"
        }
    }

    LaunchedEffect(Unit) {
        refreshStorageMetrics()
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
                            Text("You are using the latest version: ${res.currentVersion} (Build ${UpdateChecker.CURRENT_BUILD_NUMBER}).")
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
                title = "Settings & Configuration",
                subtitle = "Galaxy J2 Prime Tool • v0.3.0 Beta"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. GENERAL
            item { SettingsSectionTitle("General") }
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        title = "Ask Before Modifying Partitions / Files",
                        description = "Require explicit user confirmation before applying dangerous binary operations",
                        checked = askBeforeModify,
                        onCheckedChange = { ThemePreferences.setAskBeforeModify(it) }
                    )
                }
            }

            // 2. APPEARANCE
            item { SettingsSectionTitle("Appearance") }
            item {
                SettingsCard {
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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        SettingsSwitchItem(
                            title = "Material You Dynamic Colors",
                            description = "Match UI palette with system wallpaper",
                            checked = dynamicColor,
                            onCheckedChange = { ThemePreferences.setDynamicColor(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    val reduceMotion by ThemePreferences.reduceMotion.collectAsState()
                    SettingsSwitchItem(
                        title = "Reduce Motion / Animations",
                        description = "Optimize rendering performance for low-end devices",
                        checked = reduceMotion,
                        onCheckedChange = { ThemePreferences.setReduceMotion(it) }
                    )
                }
            }

            // 3. DEVICE
            item { SettingsSectionTitle("Device Profile") }
            item {
                SettingsCard {
                    SettingsNavRow("Device Information Center", "Audit 16-category specs, sensors, and thermal matrix", Icons.Filled.PhoneAndroid) {
                        navController.navigate("device_info")
                    }
                }
            }

            // 4. TOOLS
            item { SettingsSectionTitle("Tools & Toolchain") }
            item {
                SettingsCard {
                    SettingsNavRow("Build Tool Registry", "Verify simg2img, mkbootimg, brotli, and lz4 binaries", Icons.Filled.Handyman) {
                        navController.navigate("build_tool_registry")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsNavRow("Hash Calculator", "MD5, SHA-1, SHA-256 verification", Icons.Filled.Verified) {
                        navController.navigate("hash_calculator")
                    }
                }
            }

            // 5. ROOT & ROOT MODULES
            item { SettingsSectionTitle("Root & Modules") }
            item {
                SettingsCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Root Access Status", fontWeight = FontWeight.SemiBold)
                            Text(rootStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            if (rootStatus.contains("Granted")) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (rootStatus.contains("Granted")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsNavRow("Root Center & Magisk Modules", "Manage privileged commands and inspect modules", Icons.Filled.Security) {
                        navController.navigate("root_center")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsNavRow("Boot Modes & Reboot Triggers", "System, Recovery, Download, Bootloader reboot", Icons.Filled.RestartAlt) {
                        navController.navigate("boot_modes")
                    }
                }
            }

            // 6. ADB & FASTBOOT
            item { SettingsSectionTitle("ADB & Fastboot Bridge") }
            item {
                SettingsCard {
                    SettingsNavRow("ADB & Fastboot Studio", "Multi-mode terminal with command presets & diagnostics", Icons.Filled.Terminal) {
                        navController.navigate("adb_fastboot")
                    }
                }
            }

            // 7. SAMSUNG & FLASHING
            item { SettingsSectionTitle("Samsung & Flashing") }
            item {
                SettingsCard {
                    SettingsNavRow("Samsung Odin Firmware Tool", "Tar.md5 unpacker, CSC extractor & flash plan", Icons.Filled.CloudDownload) {
                        navController.navigate("samsung_firmware")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsNavRow("Safe Flash Pre-Check", "Partition overflow detection & risk calculator", Icons.Filled.Shield) {
                        navController.navigate("flash_precheck")
                    }
                }
            }

            // 8. USB
            item { SettingsSectionTitle("USB & OTG") }
            item {
                SettingsCard {
                    SettingsNavRow("USB Host Center", "OTG device probe, Samsung VID/PID scanner & descriptors", Icons.Filled.Usb) {
                        navController.navigate("usb_host_center")
                    }
                }
            }

            // 9. LOGS & DIAGNOSTICS
            item { SettingsSectionTitle("Logs & Diagnostics") }
            item {
                SettingsCard {
                    SettingsNavRow("System Log Analyzer", "Live logcat, dmesg, and pstore streamer", Icons.Filled.Monitor) {
                        navController.navigate("log_analyzer")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsNavRow("Boot Diagnostic Pipeline", "12-stage boot sequence analyzer", Icons.Filled.Troubleshoot) {
                        navController.navigate("boot_diagnostic")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsNavRow("Kernel Crash & Ram-oops", "Analyze last_kmsg and crash signals", Icons.Filled.BugReport) {
                        navController.navigate("kernel_crash_analyzer")
                    }
                }
            }

            // 10. STORAGE & CACHE
            item { SettingsSectionTitle("Storage & Cache") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cache Storage", fontWeight = FontWeight.SemiBold)
                            Text(PartitionEntry.formatBytes(cacheSizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    context.cacheDir.deleteRecursively()
                                    context.cacheDir.mkdirs()
                                    refreshStorageMetrics()
                                }
                                Toast.makeText(context, "Cache cleared.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Cache")
                        }
                    }
                }
            }

            // 11. PERFORMANCE & WORKFLOW
            item { SettingsSectionTitle("Performance & Workflows") }
            item {
                SettingsCard {
                    val concurrentTasks by ThemePreferences.concurrentTasks.collectAsState()
                    val backgroundScan by ThemePreferences.backgroundScan.collectAsState()
                    val memoryMode by ThemePreferences.memoryMode.collectAsState()

                    Text("Memory Management Mode: $memoryMode", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Low RAM", "Balanced", "High Perf").forEach { mode ->
                            FilterChip(
                                selected = memoryMode == mode,
                                onClick = { ThemePreferences.setMemoryMode(mode) },
                                label = { Text(mode, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    SettingsSwitchItem(
                        title = "Background Diagnostics Scanning",
                        description = "Allow passive hardware & sensor telemetry polling",
                        checked = backgroundScan,
                        onCheckedChange = { ThemePreferences.setBackgroundScan(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Max Concurrent Tasks ($concurrentTasks)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Limit background worker threads for MT6737T 4-core stability", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            IconButton(onClick = { if (concurrentTasks > 1) ThemePreferences.setConcurrentTasks(concurrentTasks - 1) }) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                            }
                            IconButton(onClick = { if (concurrentTasks < 4) ThemePreferences.setConcurrentTasks(concurrentTasks + 1) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
            }

            // 11. SECURITY & SELINUX
            item { SettingsSectionTitle("Security") }
            item {
                SettingsCard {
                    SettingsNavRow("SELinux Policy Analyzer", "Audit contexts, sepolicy rules & AVC denials", Icons.Filled.Security) {
                        navController.navigate("selinux_analyzer")
                    }
                }
            }

            // 12. REPORTS
            item { SettingsSectionTitle("Reports") }
            item {
                SettingsCard {
                    SettingsNavRow("Technical Report Generator", "Generate and export markdown audit reports", Icons.Filled.Description) {
                        navController.navigate("report_generator")
                    }
                }
            }

            // 13. UPDATES
            item { SettingsSectionTitle("Updates") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Version: 0.3.0 Beta", fontWeight = FontWeight.Bold)
                            Text("Release: Beta 3 (Code 3)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingsSwitchItem(
                        title = "Auto-Check for Updates",
                        description = "Poll GitHub release feed on network connect",
                        checked = autoUpdateCheck,
                        onCheckedChange = { ThemePreferences.setAutoUpdateCheck(it) }
                    )
                }
            }

            // 14. ABOUT
            item { SettingsSectionTitle("About") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.app_logo_foreground_1787245853129),
                            contentDescription = "Galaxy J2 Prime Tool Logo",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Galaxy J2 Prime Tool", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Version: 0.3.0 Beta (Release: Beta 3)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text("Version code: 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Build Metadata:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text("Target SDK: 36 • Min SDK: 24 (Android 7.0+)", style = MaterialTheme.typography.bodySmall)
                    Text("Host Architecture: ${System.getProperty("os.arch")} (${Build.SUPPORTED_ABIS.joinToString()})", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Target Hardware Profile:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text("Samsung Galaxy J2 Prime (SM-G532F / SM-G532G / SM-G532M)", style = MaterialTheme.typography.bodySmall)
                    Text("Chipset: MediaTek MT6737T (ARM32 Cortex-A53 / MT6735 base)", style = MaterialTheme.typography.bodySmall)
                    Text("Android Target: Android 11 (LineageOS 18.1)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Open Source Licenses:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text("Apache License 2.0 & GNU General Public License v2", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Repository:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
    }
}
