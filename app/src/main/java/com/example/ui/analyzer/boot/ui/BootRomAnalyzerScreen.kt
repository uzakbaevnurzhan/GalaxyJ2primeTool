package com.example.ui.analyzer.boot.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.boot.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BootRomAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<BootAnalysisResult?>(null)
    val result: StateFlow<BootAnalysisResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun analyzeBootImg(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            try {
                val engine = BootAnalysisEngine(context)
                val res = engine.analyzeBootImage(uri)
                _result.value = res
            } catch (e: Exception) {
                _error.value = "Boot.img analysis failed: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeWorkspace(treeUri: Uri, context: Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            try {
                val engine = BootAnalysisEngine(context)
                val res = engine.analyzeWorkspace(treeUri)
                _result.value = res
            } catch (e: Exception) {
                _error.value = "ROM Workspace analysis failed: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeLogStream(logsContent: String, context: Context) {
        viewModelScope.launch {
            val current = _result.value
            val issues = current?.allIssues?.toMutableList() ?: mutableListOf()
            val extraIssues = BootIssueDetector.detectAllIssues(
                header = current?.bootHeader,
                kernel = current?.kernelInfo,
                ramdisk = current?.ramdiskInfo,
                init = current?.initAnalysis,
                fstab = current?.fstabAnalysis,
                treble = current?.trebleInfo,
                ab = current?.abSlotInfo,
                arch = current?.architectureInfo,
                versions = current?.versionAnalysis,
                vendor = current?.vendorAnalysis,
                rawLogs = logsContent
            )
            issues.addAll(extraIssues)

            val stageRes = BootStageDetector.evaluateStages(
                header = current?.bootHeader,
                kernel = current?.kernelInfo,
                ramdisk = current?.ramdiskInfo,
                init = current?.initAnalysis,
                fstab = current?.fstabAnalysis,
                vendor = current?.vendorAnalysis,
                allIssues = issues.distinctBy { it.title + it.evidence },
                rawLogs = logsContent
            )

            _result.value = (current ?: BootAnalysisResult()).copy(
                stageResults = stageRes.stageMap,
                lastConfirmedStage = stageRes.lastConfirmedStage,
                suspectedFailureStage = stageRes.suspectedFailureStage,
                failureConfidence = stageRes.confidence,
                allIssues = issues.distinctBy { it.title + it.evidence },
                rawLogAnalysisSummary = "Analyzed ${logsContent.lines().size} lines of boot logs."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootRomAnalyzerScreen(
    navController: NavController,
    viewModel: BootRomAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportReportContent by remember { mutableStateOf("") }

    val bootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeBootImg(it, context) }
    }

    val workspaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeWorkspace(it, context) }
    }

    val tabs = listOf("Pipeline & Issues", "Boot Header & Kernel", "Ramdisk & Init", "Partitions & ROM", "Porting & J2 Prime")

    Scaffold(
        topBar = {
            com.example.ui.common.AppTopBar(
                title = "BOOT / ROM Analyzer",
                subtitle = "Boot Image, Ramdisk & Partitions",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    if (result != null) {
                        IconButton(onClick = {
                            exportReportContent = BootReportExporter.generateMarkdownReport(result!!)
                            showExportDialog = true
                        }) {
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
            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { bootLauncher.launch("*/*") },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Filled.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select boot.img", fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = { workspaceLauncher.launch(null) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select ROM Workspace", fontSize = 13.sp)
                }
            }

            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Executing deep offline analysis...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            error?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (result != null) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> PipelineAndIssuesView(result!!)
                    1 -> BootHeaderAndKernelView(result!!)
                    2 -> RamdiskAndInitView(result!!)
                    3 -> PartitionsAndRomView(result!!)
                    4 -> PortingAndJ2PrimeView(result!!)
                }
            } else if (!isAnalyzing && error == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Offline Boot & ROM Analyzer Engine",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Select a boot.img or unzipped ROM workspace folder above to start inspecting headers, kernel, ramdisk, init scripts, fstab, and boot failure chains.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Boot Analysis Report") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        Text(
                            text = exportReportContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(exportReportContent))
                    showExportDialog = false
                }) {
                    Text("Copy Markdown")
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
private fun PipelineAndIssuesView(result: BootAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Main Boot Pipeline Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BOOT PROGRESSION CHAIN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Confidence: ${result.failureConfidence}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Last Confirmed Stage: ${result.lastConfirmedStage}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (result.suspectedFailureStage != null) {
                        Text(
                            "Suspected Failure Stage: ${result.suspectedFailureStage}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Stages graphical representation
                    val stages = BootStage.values()
                    stages.forEach { stage ->
                        val stageInfo = result.stageResults[stage]
                        val status = stageInfo?.status ?: BootStageStatus.UNKNOWN

                        val (statusColor, statusIcon) = when (status) {
                            BootStageStatus.PASS -> Pair(Color(0xFF4CAF50), Icons.Filled.CheckCircle)
                            BootStageStatus.WARNING -> Pair(Color(0xFFFF9800), Icons.Filled.Warning)
                            BootStageStatus.ERROR -> Pair(MaterialTheme.colorScheme.error, Icons.Filled.Error)
                            BootStageStatus.UNKNOWN -> Pair(Color.Gray, Icons.AutoMirrored.Filled.HelpOutline)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stage.name,
                                modifier = Modifier.width(130.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stageInfo?.summary ?: "Pending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "IDENTIFIED ISSUES & EVIDENCE (${result.allIssues.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (result.allIssues.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No critical boot blocking issues detected in analyzed artifacts.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(result.allIssues) { issue ->
                val cardColor = when (issue.severity) {
                    BootIssueSeverity.CRITICAL, BootIssueSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
                    BootIssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                    BootIssueSeverity.INFO -> MaterialTheme.colorScheme.secondaryContainer
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                issue.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "[${issue.severity}]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(issue.description, style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Evidence: ${issue.evidence}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        if (issue.possibleCause != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Possible Cause: ${issue.possibleCause}",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }

                        if (issue.recommendedFix != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Fix: ${issue.recommendedFix}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootHeaderAndKernelView(result: BootAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("BOOT IMAGE HEADER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val h = result.bootHeader
            if (h != null && h.isValid) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Header Version", "v${h.headerVersion}")
                        InfoRow("Magic Signature", h.magic)
                        InfoRow("Page Size", "${h.pageSize} bytes")
                        InfoRow("Header Size", "${h.headerSize} bytes")
                        InfoRow("Kernel Payload Size", "${h.kernelSize} bytes")
                        InfoRow("Kernel Load Address", "0x${h.kernelLoadAddr.toString(16)}")
                        InfoRow("Ramdisk Size", "${h.ramdiskSize} bytes")
                        InfoRow("Ramdisk Load Address", "0x${h.ramdiskLoadAddr.toString(16)}")
                        InfoRow("OS Version", h.osVersionString)
                        InfoRow("OS Security Patch", h.osPatchLevelString)
                        InfoRow("Board Name", h.boardName.ifEmpty { "(None)" })
                        InfoRow("Kernel Offset", "0x${h.kernelOffset.toString(16)}")
                        InfoRow("Ramdisk Offset", "0x${h.ramdiskOffset.toString(16)}")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Kernel Command Line (cmdline):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            h.cmdline.ifEmpty { "(Empty cmdline)" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No valid boot image header detected.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Text("KERNEL DETAILS & STRINGS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val k = result.kernelInfo
            if (k != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Detected Format", k.detectedFormat)
                        InfoRow("Detected Architecture", k.detectedArch)
                        InfoRow("Kernel Version", k.kernelVersionString ?: "Not Found")
                        InfoRow("Compiler String", k.compilerString ?: "Not Found")
                        InfoRow("SMP Enabled", "${k.isSmp}")
                        InfoRow("Extracted CONFIG Count", "${k.kernelConfigCount}")
                        if (k.sampleConfigs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sample Kernel Configs:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            k.sampleConfigs.forEach { cfg ->
                                Text(cfg, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Kernel payload not parsed.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun RamdiskAndInitView(result: BootAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("RAMDISK STRUCTURE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val r = result.ramdiskInfo
            if (r != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Ramdisk Present", "${r.present}")
                        InfoRow("Payload Size", "${r.size} bytes")
                        InfoRow("Compression Type", r.compression)
                        InfoRow("CPIO Archive Entries", "${r.cpioEntriesCount}")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Recognized Key Files:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        r.foundKeyFiles.forEach { f ->
                            Text("• $f", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Ramdisk payload not present or not analyzed.", modifier = Modifier.padding(16.dp))
                }
            }
        }

        item {
            Text("INIT.RC ACTIONS & SERVICES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val init = result.initAnalysis
            if (init != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Total Action Stages", "${init.stagesFound.size}")
                        InfoRow("Total Services Defined", "${init.services.size}")
                        InfoRow("Import Directives", "${init.imports.size}")

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Parsed Init Services:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        init.services.take(15).forEach { s ->
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("service ${s.name} (${s.binaryPath})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("class ${s.className}, user: ${s.user}, group: ${s.group}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No init.rc script files analyzed in this workspace.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PartitionsAndRomView(result: BootAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("ROM PARTITION MAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (result.partitionMap.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No partition images (.img, .dat, .br) found in workspace.", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(result.partitionMap) { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Partition: ${p.partitionName} (${p.format})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${p.fileSize / 1024} KB", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Text("FSTAB MOUNT TARGETS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val fstab = result.fstabAnalysis
            if (fstab != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Fstab File", fstab.fileName)
                        InfoRow("Mount Entries", "${fstab.entries.size}")
                        Spacer(modifier = Modifier.height(4.dp))
                        fstab.entries.forEach { e ->
                            Text("${e.mountTarget} (${e.filesystem}) <- ${e.deviceSource}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No fstab parsed.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PortingAndJ2PrimeView(result: BootAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("ANDROID 11 PORTING READINESS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (result.android11PortingChecks.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("No porting checks evaluated.", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(result.android11PortingChecks) { chk ->
                val (tintColor, icon) = when (chk.status) {
                    BootStageStatus.PASS -> Pair(Color(0xFF4CAF50), Icons.Filled.CheckCircle)
                    BootStageStatus.WARNING -> Pair(Color(0xFFFF9800), Icons.Filled.Warning)
                    BootStageStatus.ERROR -> Pair(MaterialTheme.colorScheme.error, Icons.Filled.Error)
                    BootStageStatus.UNKNOWN -> Pair(Color.Gray, Icons.AutoMirrored.Filled.HelpOutline)
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(chk.ruleName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(chk.description, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Evidence: ${chk.evidence}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (chk.recommendation != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Recommendation: ${chk.recommendation}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            Text("GALAXY J2 PRIME (MT6737T) HARDWARE PROFILE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            val j2 = result.j2PrimeProfile
            if (j2 != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Target Device", "Samsung Galaxy J2 Prime (SM-G532)")
                        InfoRow("Expected Chipset", j2.expectedChipset)
                        InfoRow("Expected Architecture", j2.expectedArch)
                        InfoRow("Actual Detected Chipset", j2.actualChipset ?: "Unknown")
                        InfoRow("Actual Detected Architecture", j2.actualArch ?: "Unknown")
                        InfoRow("Profile Match Status", if (j2.isMatch) "ALIGNED" else "MISMATCH")
                        Spacer(modifier = Modifier.height(6.dp))
                        j2.notes.forEach { n ->
                            Text("• $n", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("J2 Prime profile check not evaluated.", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
