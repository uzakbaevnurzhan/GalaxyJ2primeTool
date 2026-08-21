package com.example.ui.analyzer.image.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.image.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<ImageAnalysisResult?>(null)
    val result: StateFlow<ImageAnalysisResult?> = _result

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _currentPhase = MutableStateFlow("")
    val currentPhase: StateFlow<String> = _currentPhase

    private val _speedMb = MutableStateFlow(0.0)
    val speedMb: StateFlow<Double> = _speedMb

    private var currentJob: Job? = null
    var currentUri by mutableStateOf<Uri?>(null)
    var isDirectoryMode by mutableStateOf(false)

    fun analyzeUri(context: Context, uri: Uri) {
        currentJob?.cancel()
        currentUri = uri
        isDirectoryMode = false
        _isAnalyzing.value = true
        _progress.value = 0f
        _currentPhase.value = "Starting analysis..."

        currentJob = viewModelScope.launch {
            try {
                val res = ImageAnalyzer.analyzeUri(
                    context = context,
                    uri = uri,
                    onProgress = { p, phase, speed ->
                        _progress.value = p
                        _currentPhase.value = phase
                        _speedMb.value = speed
                    },
                    isCancelled = { currentJob?.isCancelled == true }
                )
                _result.value = res
            } catch (e: Exception) {
                _result.value = ImageAnalysisResult(
                    status = AnalyzerStatus.ERROR,
                    summary = "Analysis error",
                    details = e.message ?: "Unknown error"
                )
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeDirectory(context: Context, dirUri: Uri) {
        currentJob?.cancel()
        currentUri = dirUri
        isDirectoryMode = true
        _isAnalyzing.value = true
        _progress.value = 0f
        _currentPhase.value = "Starting directory analysis..."

        currentJob = viewModelScope.launch {
            try {
                val res = ImageAnalyzer.analyzeDirectory(
                    context = context,
                    dirUri = dirUri,
                    onProgress = { p, phase, speed ->
                        _progress.value = p
                        _currentPhase.value = phase
                        _speedMb.value = speed
                    },
                    isCancelled = { currentJob?.isCancelled == true }
                )
                _result.value = res
            } catch (e: Exception) {
                _result.value = ImageAnalysisResult(
                    status = AnalyzerStatus.ERROR,
                    summary = "Analysis error",
                    details = e.message ?: "Unknown error"
                )
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun cancelAnalysis() {
        currentJob?.cancel()
        _isAnalyzing.value = false
        _currentPhase.value = "Analysis cancelled"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAnalyzerScreen(
    navController: NavController,
    viewModel: ImageAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPhase by viewModel.currentPhase.collectAsState()
    val speedMb by viewModel.speedMb.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFormatToSave by remember { mutableStateOf("md") }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeUri(context, it) }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.analyzeDirectory(context, it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let { destUri ->
            result?.let { res ->
                val content = when (exportFormatToSave) {
                    "json" -> ImageReportExporter.exportToJson(res)
                    "txt" -> ImageReportExporter.exportToTxt(res)
                    "csv" -> ImageReportExporter.exportToCsv(res)
                    else -> ImageReportExporter.exportToMarkdown(res)
                }
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            out.write(content.toByteArray(Charsets.UTF_8))
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    val tabs = listOf("SUMMARY", "HEADER", "FILESYSTEM", "PARTITIONS", "ABI & PORTING", "ISSUES", "HASH")

    Scaffold(
        topBar = {
            com.example.ui.common.AppTopBar(
                title = "ROM Image Analyzer",
                subtitle = result?.metadata?.format?.displayName ?: "Sparse, EXT4, EROFS, F2FS & Payload",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    if (result != null) {
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
                .padding(horizontal = 16.dp)
        ) {
            // Source Picker Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    enabled = !isAnalyzing
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select Image", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.weight(1f),
                    enabled = !isAnalyzing
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select ROM Dir", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Progress Banner
            if (isAnalyzing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentPhase,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (speedMb > 0.0) {
                                Text(
                                    text = "${"%.1f".format(speedMb)} MB/s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.cancelAnalysis() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.labelMedium)
                                if (title == "ISSUES" && result != null && result!!.issues.isNotEmpty()) {
                                    Spacer(Modifier.width(4.dp))
                                    Badge(
                                        containerColor = if (result!!.criticalIssuesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Text("${result!!.issues.size}", color = Color.White)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Main Content Area
            if (result == null && !isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Select an Android Partition Image or ROM Folder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Supports EXT4, EROFS, F2FS, SquashFS, Android Sparse (simg), Super (Dynamic Partitions), DAT / DAT.BR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (result != null) {
                val res = result!!
                when (selectedTabIndex) {
                    0 -> SummaryTabContent(res)
                    1 -> HeaderTabContent(res)
                    2 -> FilesystemTabContent(res)
                    3 -> PartitionsTabContent(res)
                    4 -> AbiPortingTabContent(res)
                    5 -> IssuesTabContent(res)
                    6 -> HashTabContent(res)
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog && result != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Report") },
            text = {
                Column {
                    Text("Select report format:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "Markdown (.md)" to "md",
                        "JSON (.json)" to "json",
                        "Plain Text (.txt)" to "txt",
                        "CSV (.csv)" to "csv"
                    ).forEach { (label, ext) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    exportFormatToSave = ext
                                    showExportDialog = false
                                    exportLauncher.launch("rom_image_report.$ext")
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SummaryTabContent(res: ImageAnalysisResult) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Image Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    MetricRow("File Name", res.metadata.fileName)
                    MetricRow("Format", res.metadata.format.displayName)
                    MetricRow("Filesystem", res.metadata.filesystemType)
                    MetricRow("File Size", formatBytes(res.metadata.fileSize))
                    MetricRow("Raw / Uncompressed", formatBytes(res.metadata.uncompressedSize))
                    if (res.metadata.compressionRatio > 1.05) {
                        MetricRow("Compression Ratio", "${"%.2f".format(res.metadata.compressionRatio)}x")
                    }
                    MetricRow("Block Size", "${res.metadata.blockSize} bytes")
                    MetricRow("Total Blocks", "${res.metadata.totalBlocks}")
                    if (res.metadata.volumeName.isNotEmpty()) {
                        MetricRow("Volume Name", res.metadata.volumeName)
                    }
                    if (res.metadata.uuid.isNotEmpty()) {
                        MetricRow("UUID", res.metadata.uuid)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (res.criticalIssuesCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Diagnostics Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Critical: ${res.criticalIssuesCount} | Warning: ${res.warningIssuesCount} | Info: ${res.infoIssuesCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Icon(
                        if (res.criticalIssuesCount > 0) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderTabContent(res: ImageAnalysisResult) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Header & Superblock Fields", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (res.metadata.rawHeaderFields.isEmpty()) {
            item {
                Text("No raw header fields available for this format.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(res.metadata.rawHeaderFields.entries.toList()) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.key, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(entry.value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun FilesystemTabContent(res: ImageAnalysisResult) {
    val meta = res.metadata
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Filesystem Usage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    if (meta.totalBlocks > 0) {
                        val usage = meta.usagePercentage.toFloat() / 100f
                        LinearProgressIndicator(
                            progress = { usage },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Used: ${formatBytes(meta.usedBytes)} (${"%.1f".format(meta.usagePercentage)}%)", style = MaterialTheme.typography.bodySmall)
                            Text("Free: ${formatBytes(meta.freeBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Inodes Count", "${meta.inodeCount}")
                    MetricRow("Free Inodes", "${meta.freeInodes}")
                    MetricRow("Mount Point", meta.mountPointHint.ifEmpty { "(root/default)" })
                    MetricRow("Read-Only", if (meta.isReadOnly) "Yes" else "No")
                }
            }
        }

        if (meta.features.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Filesystem Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            meta.features.forEach { feat ->
                                SuggestionChip(onClick = {}, label = { Text(feat, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PartitionsTabContent(res: ImageAnalysisResult) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Partitions List (${res.partitions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        if (res.partitions.isEmpty()) {
            item {
                Text("No sub-partitions detected (Single partition).", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(res.partitions) { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(formatBytes(p.sizeBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Filesystem: ${p.filesystem.displayName} | Group: ${p.groupName}", style = MaterialTheme.typography.bodySmall)
                        if (p.extents.isNotEmpty()) {
                            Text("Extents: ${p.extents.size} (Offset: 0x${java.lang.Long.toHexString(p.startOffset)})", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AbiPortingTabContent(res: ImageAnalysisResult) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device & Architecture Checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    MetricRow("Treble Support", if (res.hasTrebleSupport) "Enabled" else "Disabled / Legacy")
                    MetricRow("Dynamic Partitions", if (res.isDynamicPartitions || res.metadata.format == ImageFormat.SUPER) "Yes (Super)" else "Traditional / RAW")
                    MetricRow("System-as-Root", if (res.isSystemAsRoot) "Yes" else "No")
                    MetricRow("Target SDK", if (res.sdkLevel > 0) "${res.sdkLevel} (${res.androidTargetVersion})" else "Unknown")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Galaxy J2 Prime Porting Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• CPU Architecture: 32-bit ARM Cortex-A53 (arm32 / armeabi-v7a)\n" +
                        "• Kernel: 3.18.14 (Legacy without native EROFS/binder64 unless backported)\n" +
                        "• Partition Style: Non-dynamic (standard EXT4 block partitions)\n" +
                        "• Treble: Requires vndk-28/30 legacy vendor shims for Android 10/11 GSI.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun IssuesTabContent(res: ImageAnalysisResult) {
    var severityFilter by remember { mutableStateOf<IssueSeverity?>(null) }
    val filteredIssues = remember(res.issues, severityFilter) {
        if (severityFilter == null) res.issues else res.issues.filter { it.severity == severityFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = severityFilter == null,
                    onClick = { severityFilter = null },
                    label = { Text("All (${res.issues.size})") }
                )
                FilterChip(
                    selected = severityFilter == IssueSeverity.CRITICAL,
                    onClick = { severityFilter = IssueSeverity.CRITICAL },
                    label = { Text("Critical (${res.criticalIssuesCount})") }
                )
                FilterChip(
                    selected = severityFilter == IssueSeverity.WARNING,
                    onClick = { severityFilter = IssueSeverity.WARNING },
                    label = { Text("Warning (${res.warningIssuesCount})") }
                )
            }
        }

        if (filteredIssues.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No issues matching selected filter.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(filteredIssues) { issue ->
                val containerColor = when (issue.severity) {
                    IssueSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                    IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                    IssueSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (issue.severity) {
                                    IssueSeverity.CRITICAL -> Icons.Filled.Error
                                    IssueSeverity.WARNING -> Icons.Filled.Warning
                                    IssueSeverity.INFO -> Icons.Filled.Info
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(issue.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(issue.description, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Recommendation: ${issue.recommendation}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Affected: ${issue.affectedPartition} | Category: ${issue.category}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun HashTabContent(res: ImageAnalysisResult) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyToClipboard(label: String, value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Cryptographic Hashes & Checksums", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        listOf(
            "MD5" to res.metadata.md5Hash,
            "SHA-1" to res.metadata.sha1Hash,
            "SHA-256" to res.metadata.sha256Hash
        ).forEach { (label, hashVal) ->
            if (hashVal.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(hashVal, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(onClick = { copyToClipboard(label, hashVal) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String {
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        bytes >= gib -> String.format(java.util.Locale.US, "%.2f GB", bytes / gib)
        bytes >= mib -> String.format(java.util.Locale.US, "%.2f MB", bytes / mib)
        bytes >= kib -> String.format(java.util.Locale.US, "%.2f KB", bytes / kib)
        else -> "$bytes B"
    }
}
