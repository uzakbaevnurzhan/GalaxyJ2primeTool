package com.example.ui.analyzer.getprop.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.getprop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetpropAnalyzerScreen(
    navController: NavController,
    viewModel: GetpropAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()
    val diffResult by viewModel.diffResult.collectAsState()
    val portingCheckResult by viewModel.portingCheckResult.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Summary", "Subsystems", "Properties", "Compare", "Porting Check")

    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf("Markdown") }

    // SAF Launchers
    val openFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.analyzeFiles(context, uris)
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destUri: Uri? ->
        if (destUri != null && analysisResult != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = when (exportFormat) {
                        "Markdown" -> GetpropExporter.exportToMarkdown(analysisResult!!)
                        "JSON" -> GetpropExporter.exportToJson(analysisResult!!)
                        "CSV" -> GetpropExporter.exportToCsv(analysisResult!!)
                        else -> GetpropExporter.exportToTxt(analysisResult!!)
                    }
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Exported $exportFormat successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            com.example.ui.common.AppTopBar(
                title = "Getprop & Build.prop Analyzer",
                subtitle = if (analysisResult != null) analysisResult!!.snapshot.name else "Offline Android Properties Engine",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    if (analysisResult != null) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export Report")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Action Bar with Buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { openFilesLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalyzing
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import File(s)")
                    }

                    OutlinedButton(
                        onClick = { viewModel.collectLiveProperties() },
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalyzing
                    ) {
                        Icon(Icons.Filled.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Getprop")
                    }
                }
            }

            if (isAnalyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (analysisResult == null && (selectedTab == 0 || selectedTab == 1 || selectedTab == 2)) {
                    EmptyStateView(
                        onImportClick = { openFilesLauncher.launch(arrayOf("*/*")) },
                        onLiveClick = { viewModel.collectLiveProperties() }
                    )
                } else {
                    when (selectedTab) {
                        0 -> SummaryTab(analysisResult!!)
                        1 -> SubsystemsTab(analysisResult!!)
                        2 -> PropertiesTab(analysisResult!!, viewModel, context)
                        3 -> CompareTab(snapshots, diffResult, viewModel)
                        4 -> PortingCheckTab(snapshots, portingCheckResult, viewModel)
                    }
                }
            }
        }
    }

    if (showExportDialog && analysisResult != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Property Report") },
            text = {
                Column {
                    Text("Select desired export format:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf("Markdown", "JSON", "CSV", "Plain Text").forEach { fmt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { exportFormat = fmt }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = exportFormat == fmt,
                                onClick = { exportFormat = fmt }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(fmt, fontWeight = if (exportFormat == fmt) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        val ext = when (exportFormat) {
                            "Markdown" -> "md"
                            "JSON" -> "json"
                            "CSV" -> "csv"
                            else -> "txt"
                        }
                        createDocLauncher.launch("buildprop_report_${System.currentTimeMillis()}.$ext")
                    }
                ) {
                    Text("Save to Storage")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyStateView(onImportClick: () -> Unit, onLiveClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No Properties Loaded",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Import a build.prop, system/build.prop, default.prop, or getprop file via SAF, or capture live system properties.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImportClick) {
                Text("Select File(s)")
            }
            OutlinedButton(onClick = onLiveClick) {
                Text("Live Getprop")
            }
        }
    }
}

@Composable
fun SummaryTab(result: GetpropAnalysisResult) {
    val s = result.snapshot.deviceSummary
    val snap = result.snapshot

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Device Main Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.model,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        BadgeCard(
                            text = s.abiType,
                            isWarning = s.abiType.contains("ARM32 only"),
                            isSuccess = s.abiType.contains("64")
                        )
                    }
                    Text(
                        text = "${s.brand} • ${s.manufacturer} • Device: ${s.device} (Board: ${s.board})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Android OS & Build Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Android & Build Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("Android Version", s.androidVersion)
                    PropertyRow("API / SDK Level", if (s.sdk > 0) "${s.sdk} (${s.codename})" else "Unknown")
                    PropertyRow("Build ID", s.buildId)
                    PropertyRow("Build Display ID", s.buildDisplayId)
                    PropertyRow("Security Patch", s.securityPatch)
                    PropertyRow("Incremental", s.incremental)
                    PropertyRow("Build Tags", s.buildTags)
                }
            }
        }

        // Hardware & SoC Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CPU & Platform Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("Primary ABI", s.primaryAbi)
                    PropertyRow("Supported ABI List", s.abiList.joinToString(", ").ifEmpty { "Unknown" })
                    PropertyRow("Hardware", result.hardwareSoc.hardware)
                    PropertyRow("Platform / SoC", result.hardwareSoc.platform)
                    PropertyRow("SoC Model / Chip", result.hardwareSoc.socModel)
                    if (result.hardwareSoc.hasConflict) {
                        Spacer(modifier = Modifier.height(8.dp))
                        result.hardwareSoc.warnings.forEach { warn ->
                            Text("⚠️ $warn", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Security & SELinux Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Security & Environment Flags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("SELinux Status", s.selinuxMode)
                    PropertyRow("Debuggable (ro.debuggable)", "${s.isDebuggable}")
                    PropertyRow("Secure (ro.secure)", "${s.isSecure}")
                    PropertyRow("ADB Secure (ro.adb.secure)", "${s.isAdbSecure}")
                }
            }
        }

        // Source Files & Hash Table
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Source Files & SHA-256 Hashes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    snap.sources.forEach { src ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(src.fileName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Size: ${src.sizeBytes} bytes • Parsed: ${src.parsedCount} lines • Skipped: ${src.skippedCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (src.sha256.isNotEmpty()) {
                                Text("SHA-256: ${src.sha256}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }

        // Duplicates & Conflicts Warning
        if (result.conflictsList.isNotEmpty() || result.duplicatesList.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Property Duplicates & Conflicts (${result.conflictsList.size} conflicts, ${result.duplicatesList.size} duplicates)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        result.conflictsList.take(5).forEach { conf ->
                            Text("• ${conf.key} has conflicting values across sources", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubsystemsTab(result: GetpropAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Graphics & Display
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Graphics & Display Subsystem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("EGL Driver Hardware", result.graphics.eglHardware)
                    PropertyRow("OpenGL ES Version", result.graphics.glesVersionFormatted)
                    PropertyRow("HWUI Renderer", result.graphics.hwuiRenderer)
                    PropertyRow("LCD Density", result.display.lcdDensity)
                    PropertyRow("Graphics Properties Count", "${result.graphics.graphicsProperties.size}")
                }
            }
        }

        // Runtime / ART / Dalvik
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Runtime / Dalvik VM & ART", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("Heap Start Size", result.runtimeArt.heapStartSize)
                    PropertyRow("Heap Growth Limit", result.runtimeArt.heapGrowthLimit)
                    PropertyRow("Heap Max Size", result.runtimeArt.heapSize)
                    PropertyRow("Heap Target Utilization", result.runtimeArt.heapTargetUtilization)
                    PropertyRow("JIT Compiler (dalvik.vm.usejit)", result.runtimeArt.useJit)
                    PropertyRow("Dex2oat Filter", result.runtimeArt.dex2oatFilter)
                }
            }
        }

        // Telephony & RIL
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Telephony & RIL Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("RIL Implementation", result.telephonyRil.rilImplementation)
                    PropertyRow("RILD Lib Path", result.telephonyRil.rildLibPath)
                    PropertyRow("Telephony Properties Count", "${result.telephonyRil.telephonyProperties.size}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ℹ️ ${result.telephonyRil.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Camera & Audio
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Camera & Audio Subsystems", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    PropertyRow("Camera Properties Count", "${result.media.cameraProperties.size}")
                    PropertyRow("Audio Properties Count", "${result.media.audioProperties.size}")
                    PropertyRow("Media Properties Count", "${result.media.mediaProperties.size}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ℹ️ ${result.media.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PropertiesTab(
    result: GetpropAnalysisResult,
    viewModel: GetpropAnalyzerViewModel,
    context: Context
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    val selectedType by viewModel.selectedTypeFilter.collectAsState()
    val selectedPrefix by viewModel.selectedPrefixFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val showDuplicatesOnly by viewModel.showDuplicatesOnly.collectAsState()
    val showConflictsOnly by viewModel.showConflictsOnly.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    val filteredProperties = remember(
        result, searchQuery, selectedCategory, selectedType, selectedPrefix,
        sortOption, showDuplicatesOnly, showConflictsOnly
    ) {
        var list = result.snapshot.properties.values.toList()

        // Duplicates / Conflicts filter
        if (showConflictsOnly) {
            list = list.filter { it.conflictStatus == ConflictStatus.CONFLICT_VALUE_MISMATCH }
        } else if (showDuplicatesOnly) {
            list = list.filter { it.isDuplicate }
        }

        // Category filter
        if (selectedCategory != null) {
            list = list.filter { it.category == selectedCategory }
        }

        // Type filter
        if (selectedType != null) {
            list = list.filter { it.valueType == selectedType }
        }

        // Prefix filter
        if (selectedPrefix != null) {
            list = list.filter { it.key.startsWith(selectedPrefix!!) }
        }

        // Global Search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.key.lowercase().contains(q) ||
                it.value.lowercase().contains(q) ||
                it.category.displayName.lowercase().contains(q) ||
                it.source.lowercase().contains(q)
            }
        }

        // Sorting
        when (sortOption) {
            PropertySortOption.KEY_ASC -> list.sortedBy { it.key }
            PropertySortOption.KEY_DESC -> list.sortedByDescending { it.key }
            PropertySortOption.CATEGORY -> list.sortedBy { it.category.displayName }
            PropertySortOption.SOURCE -> list.sortedBy { it.source }
            PropertySortOption.LINE -> list.sortedBy { it.lineNumber }
            PropertySortOption.KEY_LENGTH -> list.sortedByDescending { it.key.length }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search and filter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                label = { Text("Search key, value, category...") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    PropertySortOption.values().forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt.displayName) },
                            onClick = {
                                viewModel.sortOption.value = opt
                                showSortMenu = false
                            },
                            trailingIcon = {
                                if (sortOption == opt) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Prefix Filter Chips
        val prefixes = listOf("ro.", "vendor.", "persist.", "debug.", "dalvik.", "sys.", "gsm.")
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedPrefix == null && !showDuplicatesOnly && !showConflictsOnly,
                    onClick = { viewModel.resetFilters() },
                    label = { Text("All (${result.snapshot.totalPropertiesCount})") }
                )
            }
            if (result.conflictsList.isNotEmpty()) {
                item {
                    FilterChip(
                        selected = showConflictsOnly,
                        onClick = {
                            viewModel.showConflictsOnly.value = !showConflictsOnly
                            viewModel.showDuplicatesOnly.value = false
                        },
                        label = { Text("Conflicts (${result.conflictsList.size})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.errorContainer)
                    )
                }
            }
            prefixes.forEach { prefix ->
                item {
                    FilterChip(
                        selected = selectedPrefix == prefix,
                        onClick = {
                            viewModel.selectedPrefixFilter.value = if (selectedPrefix == prefix) null else prefix
                        },
                        label = { Text(prefix + "*") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Showing ${filteredProperties.size} of ${result.snapshot.totalPropertiesCount} properties",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filteredProperties, key = { it.key + it.source + it.lineNumber }) { prop ->
                PropertyCardItem(prop = prop, onCopy = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("prop", text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}

@Composable
fun PropertyCardItem(prop: GetpropEntry, onCopy: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (prop.conflictStatus == ConflictStatus.CONFLICT_VALUE_MISMATCH)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prop.key,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BadgeChip(text = prop.category.displayName, color = MaterialTheme.colorScheme.primaryContainer)
                    BadgeChip(text = prop.valueType.name, color = MaterialTheme.colorScheme.secondaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (prop.value.isEmpty()) "<empty>" else prop.value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (prop.value.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Source: ${prop.source} (line ${prop.lineNumber})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (prop.occurrences.size > 1) {
                    Text("All occurrences:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    prop.occurrences.forEach { occ ->
                        Text("• ${occ.source}: ${occ.value} (L${occ.lineNumber})", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onCopy("${prop.key}=${prop.value}") }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Key=Value")
                    }
                }
            }
        }
    }
}

@Composable
fun CompareTab(
    snapshots: List<GetpropSnapshot>,
    diffResult: GetpropDiffResult?,
    viewModel: GetpropAnalyzerViewModel
) {
    var selectedA by remember { mutableStateOf(snapshots.firstOrNull()?.id) }
    var selectedB by remember { mutableStateOf(snapshots.getOrNull(1)?.id) }
    var filterStatus by remember { mutableStateOf<DiffStatus?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (snapshots.size < 2) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Snapshot Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please import or capture at least two property snapshots (e.g. ROM A vs ROM B or Live vs Target build.prop) to perform diff comparison.")
                }
            }
            return
        }

        // Selectors
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Snapshot A (Base):", style = MaterialTheme.typography.labelMedium)
                SnapshotDropdown(snapshots, selectedA) { selectedA = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Snapshot B (Target):", style = MaterialTheme.typography.labelMedium)
                SnapshotDropdown(snapshots, selectedB) { selectedB = it }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (selectedA != null && selectedB != null) {
                    viewModel.compareSnapshots(selectedA!!, selectedB!!)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedA != null && selectedB != null
        ) {
            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compare Snapshots")
        }

        Spacer(modifier = Modifier.height(12.dp))

        diffResult?.let { diff ->
            // Diff Stats Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DiffStatItem("Added", diff.addedCount, Color(0xFF2E7D32))
                    DiffStatItem("Removed", diff.removedCount, Color(0xFFC62828))
                    DiffStatItem("Changed", diff.changedCount, Color(0xFFEF6C00))
                    DiffStatItem("Conflict", diff.conflictCount, Color(0xFFB71C1C))
                    DiffStatItem("Unchanged", diff.unchangedCount, Color(0xFF757575))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = filterStatus == null,
                        onClick = { filterStatus = null },
                        label = { Text("All Differences (${diff.totalDifferences})") }
                    )
                }
                DiffStatus.values().forEach { st ->
                    item {
                        FilterChip(
                            selected = filterStatus == st,
                            onClick = { filterStatus = if (filterStatus == st) null else st },
                            label = { Text(st.displayName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val displayEntries = remember(diff, filterStatus) {
                if (filterStatus != null) diff.entries.filter { it.status == filterStatus }
                else diff.entries.filter { it.status != DiffStatus.UNCHANGED }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayEntries) { entry ->
                    DiffCardItem(entry)
                }
            }
        }
    }
}

@Composable
fun DiffCardItem(entry: DiffEntry) {
    val statusColor = when (entry.status) {
        DiffStatus.ADDED -> Color(0xFF2E7D32)
        DiffStatus.REMOVED -> Color(0xFFC62828)
        DiffStatus.CHANGED -> Color(0xFFEF6C00)
        DiffStatus.CONFLICT -> Color(0xFFB71C1C)
        DiffStatus.UNCHANGED -> Color(0xFF757575)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry.key, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                BadgeChip(text = entry.status.displayName, color = statusColor.copy(alpha = 0.2f), textColor = statusColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (entry.valueA != null) {
                Text("A: ${entry.valueA}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.valueB != null) {
                Text("B: ${entry.valueB}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PortingCheckTab(
    snapshots: List<GetpropSnapshot>,
    checkResult: GetpropPortingCheckResult?,
    viewModel: GetpropAnalyzerViewModel
) {
    var selectedBase by remember { mutableStateOf(snapshots.firstOrNull()?.id) }
    var selectedPort by remember { mutableStateOf(snapshots.getOrNull(1)?.id) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (snapshots.size < 2) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Android ROM Porting Compatibility Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please import both Base ROM and Port ROM properties snapshots to run automated porting compatibility heuristics.")
                }
            }
            return
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Base ROM Snapshot:", style = MaterialTheme.typography.labelMedium)
                SnapshotDropdown(snapshots, selectedBase) { selectedBase = it }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Port ROM Snapshot:", style = MaterialTheme.typography.labelMedium)
                SnapshotDropdown(snapshots, selectedPort) { selectedPort = it }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (selectedBase != null && selectedPort != null) {
                    viewModel.runPortingCheck(selectedBase!!, selectedPort!!)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedBase != null && selectedPort != null
        ) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Run Porting Compatibility Check")
        }

        Spacer(modifier = Modifier.height(12.dp))

        checkResult?.let { check ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (check.overallLevel) {
                        PortingCheckLevel.PASS -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        PortingCheckLevel.WARNING -> Color(0xFFEF6C00).copy(alpha = 0.15f)
                        PortingCheckLevel.ERROR -> Color(0xFFC62828).copy(alpha = 0.15f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Porting Status: ${check.overallLevel.displayName.uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Passed: ${check.passedCount} • Warnings: ${check.warningCount} • Errors: ${check.errorCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "⚠️ ${check.disclaimer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(check.items) { item ->
                    PortingItemCard(item)
                }
            }
        }
    }
}

@Composable
fun PortingItemCard(item: PortingCheckItem) {
    val (color, icon) = when (item.level) {
        PortingCheckLevel.PASS -> Color(0xFF2E7D32) to Icons.Filled.CheckCircle
        PortingCheckLevel.WARNING -> Color(0xFFEF6C00) to Icons.Filled.Warning
        PortingCheckLevel.ERROR -> Color(0xFFC62828) to Icons.Filled.Error
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    BadgeChip(text = item.category, color = MaterialTheme.colorScheme.surfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.message, style = MaterialTheme.typography.bodySmall)
                if (item.details != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("💡 ${item.details}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.valueA != null || item.valueB != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Base: ${item.valueA} | Port: ${item.valueB}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotDropdown(
    snapshots: List<GetpropSnapshot>,
    selectedId: String?,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = snapshots.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = current?.name ?: "Select snapshot",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            snapshots.forEach { snap ->
                DropdownMenuItem(
                    text = { Text(snap.name) },
                    onClick = {
                        onSelected(snap.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (value.contains(".") || value.contains("_")) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun BadgeCard(text: String, isWarning: Boolean = false, isSuccess: Boolean = false) {
    val bg = when {
        isWarning -> MaterialTheme.colorScheme.errorContainer
        isSuccess -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isWarning -> MaterialTheme.colorScheme.onErrorContainer
        isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BadgeChip(text: String, color: Color, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DiffStatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
