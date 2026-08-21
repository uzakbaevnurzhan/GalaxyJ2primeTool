package com.example.ui.analyzer.kernel.studio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ui.analyzer.kernel.studio.analyzer.KernelStudioAnalyzer
import com.example.ui.analyzer.kernel.studio.models.KernelAnalysisResult
import com.example.ui.analyzer.kernel.studio.models.KernelIssueSeverity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelStudioScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var analysisResult by remember { mutableStateOf<KernelAnalysisResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val result = KernelStudioAnalyzer.analyzeKernelOrBootImage(bytes, uri.lastPathSegment ?: "file.img")
                        analysisResult = result
                        snackbarHostState.showSnackbar("Analysis complete for ${uri.lastPathSegment}")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to open file: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            com.example.ui.common.AppTopBar(
                title = "Kernel & Device Tree Studio",
                subtitle = "vmlinux, zImage & DTB/DTBO Analyzer",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    if (analysisResult != null) {
                        IconButton(onClick = {
                            val report = KernelStudioAnalyzer.generateMarkdownReport(analysisResult!!)
                            clipboardManager.setText(AnnotatedString(report))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Report copied to clipboard!")
                            }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export Report")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load File", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isLoading = true
                            try {
                                val result = KernelStudioAnalyzer.importLiveDeviceData()
                                analysisResult = result
                                snackbarHostState.showSnackbar("Imported live device data")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Live import error: ${e.message}")
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Filled.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Live Device", maxLines = 1)
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Analyzing kernel & device tree...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                return@Scaffold
            }

            if (analysisResult == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.DeveloperBoard,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Offline Kernel & Device Tree Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Load a boot.img, raw zImage/Image, or DTB/DTBO to analyze Linux version, compiler, CONFIG_* symbols, cmdline, hardware nodes, and Android 11 porting compatibility.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                return@Scaffold
            }

            val result = analysisResult!!

            // Navigation Tabs
            val tabs = listOf(
                "Overview",
                "Kernel (${result.kernelInfo?.architecture ?: "N/A"})",
                "Configs (${result.configs.size})",
                "Cmdline (${result.cmdlineEntries.size})",
                "Device Tree (${result.dtbHardwareNodes.size})",
                "Porting (${result.portingSignals.size})",
                "Issues (${result.issues.size})"
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> KernelOverviewTab(result)
                1 -> KernelAnalysisScreen(result.kernelInfo)
                2 -> KernelConfigScreen(result.configs)
                3 -> KernelCmdlineScreen(result.cmdlineEntries, result.cmdlineComparisons)
                4 -> DtbExplorerScreen(result.rootDtbNode, result.dtbCompatibleStrings, result.dtbHardwareNodes)
                5 -> KernelPortingSignalsTab(result)
                6 -> KernelIssuesTab(result)
            }
        }
    }
}

@Composable
private fun KernelOverviewTab(result: KernelAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Quick summary banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Kernel Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        result.kernelInfo?.versionInfo?.fullString ?: "Unknown Linux Version",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Architecture: ${result.kernelInfo?.architecture} | Format: ${result.kernelInfo?.formatInfo?.format}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Hardware Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Device Tree Hardware Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val categories = listOf("CPU", "Memory", "Storage", "Display", "Camera", "Audio", "USB", "Wi-Fi", "Bluetooth")
                    categories.chunked(3).forEach { rowCats ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowCats.forEach { cat ->
                                val count = result.dtbHardwareNodes.count { it.category == cat }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(cat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Badge(
                                        containerColor = if (count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(if (count > 0) "$count detected" else "None", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // J2 Prime Reference Profile
        if (result.isGalaxyJ2PrimeMatch != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isGalaxyJ2PrimeMatch == true) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.isGalaxyJ2PrimeMatch == true) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                contentDescription = null,
                                tint = if (result.isGalaxyJ2PrimeMatch == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Samsung Galaxy J2 Prime (SM-G532F) Profile",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        result.j2PrimeNotes.forEach { note ->
                            Text("• $note", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Android 11 Readiness preview
        item {
            Text(
                "Android 11 Porting Signals Preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(result.portingSignals.take(4)) { signal ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (signal.category) {
                        "READY_SIGNAL" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        "WARNING" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        "BLOCKER" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        signal.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        signal.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun KernelPortingSignalsTab(result: KernelAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "Android 11 Subsystem Compatibility Signals",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Evaluates Binder, SELinux, Filesystems (EXT4/F2FS/EROFS), USB ConfigFS, and Kernel version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        items(result.portingSignals) { signal ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (signal.category) {
                        "READY_SIGNAL" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        "WARNING" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        "BLOCKER" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            signal.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Badge(
                            containerColor = when (signal.category) {
                                "READY_SIGNAL" -> MaterialTheme.colorScheme.primary
                                "WARNING" -> MaterialTheme.colorScheme.tertiary
                                "BLOCKER" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        ) {
                            Text(
                                signal.category,
                                color = MaterialTheme.colorScheme.surface,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        signal.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Evidence: ${signal.evidence}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KernelIssuesTab(result: KernelAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (result.issues.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Issues Detected", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(result.issues) { issue ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (issue.severity) {
                            KernelIssueSeverity.CRITICAL, KernelIssueSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            KernelIssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            KernelIssueSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                issue.type.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Badge(
                                containerColor = when (issue.severity) {
                                    KernelIssueSeverity.CRITICAL, KernelIssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                                    KernelIssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                                    KernelIssueSeverity.INFO -> MaterialTheme.colorScheme.secondary
                                }
                            ) {
                                Text(
                                    issue.severity.name,
                                    color = MaterialTheme.colorScheme.surface,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            issue.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Evidence: ${issue.evidence}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Source: ${issue.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
