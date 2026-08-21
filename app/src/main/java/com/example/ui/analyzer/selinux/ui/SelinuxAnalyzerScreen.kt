package com.example.ui.analyzer.selinux.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.selinux.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelinuxAnalyzerScreen(
    navController: NavController,
    viewModel: SelinuxAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }
    var exportFormatTitle by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.analyzeFile(it) }
    }

    Scaffold(
        topBar = {
            com.example.ui.common.AppTopBar(
                title = "SELinux Analyzer",
                subtitle = uiState.fileName ?: "Policy, Contexts, AVC Denial & Neverallow Engine",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Open File")
                    }
                    IconButton(
                        onClick = { viewModel.collectRootLogs() },
                        enabled = !uiState.isLoading && !uiState.isRootLoading
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = "Root Collect")
                    }
                    if (uiState.result != null) {
                        IconButton(onClick = {
                            exportContent = viewModel.exportReport("MD")
                            exportFormatTitle = "Markdown Report"
                            showExportDialog = true
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export")
                        }
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
            // Loading and Progress Bar
            if (uiState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                uiState.progressStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { viewModel.cancelAnalysis() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.fileSize > 0) {
                            LinearProgressIndicator(
                                progress = { uiState.progressPercent },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${(uiState.progressPercent * 100).toInt()}% (${uiState.fileSize / 1024} KB)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // Error Display
            uiState.errorMessage?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // If No Result & Not Loading: Initial Landing Actions
            if (uiState.result == null && !uiState.isLoading) {
                InitialSelinuxView(
                    onOpenFile = { filePicker.launch(arrayOf("*/*")) },
                    onRootCollect = { viewModel.collectRootLogs() },
                    isRootLoading = uiState.isRootLoading,
                    rootMessage = uiState.rootMessage
                )
            } else if (uiState.result != null) {
                val result = uiState.result!!

                // Check if binary sepolicy detected
                if (result.detectedType == SelinuxFileType.BINARY_SEPOLICY) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Binary SEPolicy File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Binary sepolicy parsing is not supported yet. This tool parses text-based context configurations (file_contexts, property_contexts, service_contexts, seapp_contexts, genfs_contexts) and live/historical AVC audit denial logs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                } else {
                    // Navigation Tabs
                    val tabs = listOf("Summary", "AVC Denials (${result.avcDenials.size})", "Contexts", "Diagnostics", "Export")
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, maxLines = 1) }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> SelinuxSummaryTab(result)
                        1 -> SelinuxAvcTab(result, uiState, viewModel)
                        2 -> SelinuxContextsTab(result)
                        3 -> SelinuxDiagnosticsTab(result)
                        4 -> SelinuxExportTab(
                            onExport = { format ->
                                exportContent = viewModel.exportReport(format)
                                exportFormatTitle = "$format Export"
                                showExportDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(exportFormatTitle) },
            text = {
                Box(
                    modifier = Modifier
                        .height(300.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        exportContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("SELinux Report", exportContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun InitialSelinuxView(
    onOpenFile: () -> Unit,
    onRootCollect: () -> Unit,
    isRootLoading: Boolean,
    rootMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Security,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "SELinux Policy & Denial Analyzer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Analyze Android AVC denial logs, audit logs, file_contexts, property_contexts, service_contexts, seapp_contexts, and genfs_contexts fully offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onOpenFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Log or Context File")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRootCollect,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRootLoading
        ) {
            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRootLoading) "Collecting via Root..." else "Collect Live Logs (Root)")
        }

        rootMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SelinuxSummaryTab(result: SelinuxAnalysisResult) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Status Card
        result.detectedStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (status.mode) {
                        SelinuxMode.ENFORCING -> MaterialTheme.colorScheme.primaryContainer
                        SelinuxMode.PERMISSIVE -> MaterialTheme.colorScheme.secondaryContainer
                        SelinuxMode.DISABLED -> MaterialTheme.colorScheme.errorContainer
                        SelinuxMode.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (status.mode == SelinuxMode.ENFORCING) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SELinux Mode: ${status.mode.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    for (ev in status.sourceEvidence) {
                        Text("• $ev", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Overview Stats
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Detected Type: ${result.detectedType.name}")
                Text("Total Lines Parsed: ${result.totalLinesParsed}")
                Text("Skipped / Blank Lines: ${result.skippedLinesCount}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AVC Stats
        result.avcStatistics?.let { stats ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AVC Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Denials: ${stats.totalDenials}", fontWeight = FontWeight.SemiBold)
                        Text("Unique Rules: ${stats.uniqueDenials}", fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Enforcing: ${stats.enforcingCount}", color = MaterialTheme.colorScheme.error)
                        Text("Permissive: ${stats.permissiveCount}", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    stats.mostFrequentSource?.let { Text("Top Source: ${it.first} (${it.second}x)") }
                    stats.mostFrequentTarget?.let { Text("Top Target: ${it.first} (${it.second}x)") }
                    stats.mostFrequentPermission?.let { Text("Top Permission: ${it.first} (${it.second}x)") }
                    stats.mostFrequentClass?.let { Text("Top Class: ${it.first} (${it.second}x)") }
                }
            }
        }

        // Warnings
        if (result.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Warnings (${result.warnings.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (w in result.warnings.take(10)) {
                        Text("• $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
fun SelinuxAvcTab(
    result: SelinuxAnalysisResult,
    uiState: SelinuxUiState,
    viewModel: SelinuxAnalyzerViewModel
) {
    val context = LocalContext.current
    var expandedGroupKey by remember { mutableStateOf<String?>(null) }

    val filteredGroups = remember(result.avcGroups, uiState.searchQuery, uiState.filterType, uiState.sortOption) {
        var list = result.avcGroups

        // Quick Filter
        list = when (uiState.filterType) {
            SelinuxFilterType.ONLY_DENIED -> list.filter { !it.isPermissive }
            SelinuxFilterType.ONLY_PERMISSIVE -> list.filter { it.isPermissive }
            SelinuxFilterType.ONLY_VENDOR -> list.filter { it.sourceDomain.startsWith("vendor_") || it.targetDomain.startsWith("vendor_") || it.targetDomain.startsWith("hal_") }
            SelinuxFilterType.ONLY_SYSTEM -> list.filter { it.sourceDomain.startsWith("system_") || it.sourceDomain == "init" }
            SelinuxFilterType.ONLY_FRAMEWORK -> list.filter { it.sourceDomain == "system_server" || it.sourceDomain == "surfaceflinger" }
            SelinuxFilterType.ALL -> list
        }

        // Search Query
        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim().lowercase()
            list = list.filter {
                it.sourceDomain.lowercase().contains(q) ||
                it.targetDomain.lowercase().contains(q) ||
                it.tclass.lowercase().contains(q) ||
                it.permission.lowercase().contains(q) ||
                (it.sampleDenial.comm?.lowercase()?.contains(q) == true) ||
                (it.sampleDenial.path?.lowercase()?.contains(q) == true)
            }
        }

        // Sorting
        when (uiState.sortOption) {
            AvcSortOption.COUNT_DESC -> list.sortedByDescending { it.count }
            AvcSortOption.SOURCE -> list.sortedBy { it.sourceDomain }
            AvcSortOption.TARGET -> list.sortedBy { it.targetDomain }
            AvcSortOption.PERMISSION -> list.sortedBy { it.permission }
            AvcSortOption.TIMESTAMP -> list.sortedByDescending { it.sampleDenial.timestamp ?: "" }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Filter controls
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search domain, class, path, pid...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.filterType == SelinuxFilterType.ALL,
                    onClick = { viewModel.setFilterType(SelinuxFilterType.ALL) },
                    label = { Text("All (${result.avcGroups.size})") }
                )
                FilterChip(
                    selected = uiState.filterType == SelinuxFilterType.ONLY_DENIED,
                    onClick = { viewModel.setFilterType(SelinuxFilterType.ONLY_DENIED) },
                    label = { Text("Enforcing") }
                )
                FilterChip(
                    selected = uiState.filterType == SelinuxFilterType.ONLY_PERMISSIVE,
                    onClick = { viewModel.setFilterType(SelinuxFilterType.ONLY_PERMISSIVE) },
                    label = { Text("Permissive") }
                )
                FilterChip(
                    selected = uiState.filterType == SelinuxFilterType.ONLY_VENDOR,
                    onClick = { viewModel.setFilterType(SelinuxFilterType.ONLY_VENDOR) },
                    label = { Text("Vendor") }
                )
                FilterChip(
                    selected = uiState.filterType == SelinuxFilterType.ONLY_SYSTEM,
                    onClick = { viewModel.setFilterType(SelinuxFilterType.ONLY_SYSTEM) },
                    label = { Text("System") }
                )
                FilterChip(
                    selected = uiState.filterType == SelinuxFilterType.ONLY_FRAMEWORK,
                    onClick = { viewModel.setFilterType(SelinuxFilterType.ONLY_FRAMEWORK) },
                    label = { Text("Framework") }
                )
            }
        }

        // Group List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredGroups) { group ->
                val key = "${group.sourceDomain}->${group.targetDomain}:${group.tclass}:${group.permission}"
                val isExpanded = expandedGroupKey == key

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = {
                        expandedGroupKey = if (isExpanded) null else key
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        group.sourceDomain,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(" → ", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        group.targetDomain,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Text(
                                    "class: ${group.tclass}  |  permission: { ${group.permission} }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Badge(
                                containerColor = if (group.isPermissive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            ) {
                                Text("${group.count}x", modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            val analysis = group.sampleDenial.analyticalDescription

                            Text("FACT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(analysis.fact, style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("ANALYSIS (${analysis.possibleArea})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text(analysis.possibleCause, style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    group.suggestedRule,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("SELinux Rule", group.suggestedRule)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied rule", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Rule", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelinuxContextsTab(result: SelinuxAnalysisResult) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search pattern or context...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        val q = searchQuery.trim().lowercase()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (result.fileContexts.isNotEmpty()) {
                val filtered = if (q.isEmpty()) result.fileContexts else result.fileContexts.filter {
                    it.pathRegex.lowercase().contains(q) || (it.context?.raw?.lowercase()?.contains(q) == true)
                }
                item {
                    Text("File Contexts (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(filtered.take(300)) { fc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(fc.pathRegex, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(fc.fileTypeDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text(fc.context?.raw ?: "<<none>>", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (result.propertyContexts.isNotEmpty()) {
                val filtered = if (q.isEmpty()) result.propertyContexts else result.propertyContexts.filter {
                    it.propertyPattern.lowercase().contains(q) || (it.context?.raw?.lowercase()?.contains(q) == true)
                }
                item {
                    Text("Property Contexts (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(filtered.take(300)) { pc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(pc.propertyPattern, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(pc.context?.raw ?: "none", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (result.serviceContexts.isNotEmpty()) {
                val filtered = if (q.isEmpty()) result.serviceContexts else result.serviceContexts.filter {
                    it.serviceName.lowercase().contains(q) || (it.context?.raw?.lowercase()?.contains(q) == true)
                }
                item {
                    Text("Service Contexts (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(filtered.take(300)) { sc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(sc.serviceName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(sc.context?.raw ?: "none", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            if (result.seappContexts.isNotEmpty()) {
                val filtered = if (q.isEmpty()) result.seappContexts else result.seappContexts.filter {
                    it.rawLine.lowercase().contains(q)
                }
                item {
                    Text("Seapp Contexts (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(filtered.take(300)) { sa ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("domain=${sa.domain ?: "-"} type=${sa.type ?: "-"}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("user=${sa.user ?: "-"} seinfo=${sa.seinfo ?: "-"} name=${sa.name ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (result.genfsContexts.isNotEmpty()) {
                val filtered = if (q.isEmpty()) result.genfsContexts else result.genfsContexts.filter {
                    it.rawLine.lowercase().contains(q)
                }
                item {
                    Text("Genfs Contexts (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(filtered.take(300)) { gf ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("fs: ${gf.filesystem} | path: ${gf.path}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(gf.context?.raw ?: "none", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelinuxDiagnosticsTab(result: SelinuxAnalysisResult) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Boot & Security Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (result.bootDiagnosis.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "No critical boot-blocking SELinux denials detected.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            for (diag in result.bootDiagnosis) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            diag,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelinuxExportTab(onExport: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Share,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Export Analysis Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Export the structured report to share on developer forums, issue trackers, or ROM build logs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { onExport("MD") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Code, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export as Markdown (.md)")
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(onClick = { onExport("JSON") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DataArray, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export as JSON (.json)")
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(onClick = { onExport("TXT") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export as Plain Text (.txt)")
        }
    }
}
