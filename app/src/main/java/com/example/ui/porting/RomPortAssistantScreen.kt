package com.example.ui.porting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.data.manager.ErrorCenterManager
import com.example.data.manager.TaskManager
import com.example.data.model.ReportFormat
import com.example.porting.engine.RomPortAssistantEngine
import com.example.porting.engine.TargetDeviceAnalyzerEngine
import com.example.porting.model.*
import com.example.ui.common.AppTopBar
import com.example.ui.studio.workspace.WorkspaceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

enum class PortAssistantTab(val title: String, val icon: ImageVector) {
    SOURCE("SOURCE", Icons.Default.Archive),
    TARGET("TARGET", Icons.Default.PhoneAndroid),
    ANALYZE("ANALYZE", Icons.Default.Analytics),
    COMPATIBILITY("COMPATIBILITY", Icons.Default.CheckCircle),
    BLOCKERS("BLOCKERS", Icons.Default.Dangerous),
    PORT_PLAN("PORT PLAN", Icons.AutoMirrored.Filled.List),
    PATCH("PATCH", Icons.Default.BuildCircle),
    BUILD("BUILD", Icons.Default.Engineering),
    REPORT("REPORT", Icons.Default.Description)
}

enum class SourceSubTab(val title: String) {
    SUMMARY("Summary"),
    AUDIT_FIELDS("Audited Fields"),
    PARTITIONS("Partitions"),
    BOOT_KERNEL("Boot & Kernel"),
    ELF_HAL_RIL("ELF, HAL & RIL"),
    ISSUES("Issues & Blockers"),
    UNKNOWN_DATA("Unknown Data")
}

enum class TargetSubTab(val title: String) {
    SUMMARY("Summary"),
    AUDIT_FIELDS("Audited Fields"),
    PARTITIONS_MOUNTS("Partitions & Mounts"),
    HARDWARE_HAL("Hardware & HAL"),
    TARGET_ISSUES("Target Issues")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomPortAssistantScreen(
    navController: NavController,
    initialSessionId: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(PortAssistantTab.SOURCE) }
    var selectedSourceSubTab by remember { mutableStateOf(SourceSubTab.SUMMARY) }
    var selectedTargetSubTab by remember { mutableStateOf(TargetSubTab.SUMMARY) }

    // State
    var sourceRom by remember { mutableStateOf<SourceRomProfile?>(RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first()) }
    var targetDevice by remember { mutableStateOf<TargetDeviceProfile?>(RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first()) }
    var analysisResult by remember { mutableStateOf<PortAnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableStateOf(0f) }
    var analysisStage by remember { mutableStateOf("") }
    var generatedReportMarkdown by remember { mutableStateOf("") }
    var currentJob by remember { mutableStateOf<Job?>(null) }

    // Dialogs / Pickers
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showSnapshotPickerForTarget by remember { mutableStateOf(false) }
    var showProjectPickerForSource by remember { mutableStateOf(false) }
    var showProjectPickerForTarget by remember { mutableStateOf(false) }
    var showPipelineDialog by remember { mutableStateOf(false) }

    // File / Directory Pickers
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            currentJob = coroutineScope.launch {
                isAnalyzing = true
                analysisProgress = 0.05f
                analysisStage = "Extracting and Analyzing ROM Archive..."
                try {
                    val profile = RomPortAssistantEngine.extractSourceRomFromZip(context, uri) { stage, prog ->
                        analysisStage = stage
                        analysisProgress = prog
                    }
                    sourceRom = profile
                    analysisResult = null
                    Toast.makeText(context, "Loaded Source ROM: ${profile.name}", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    Toast.makeText(context, "ROM Analysis cancelled.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    ErrorCenterManager.recordError(
                        module = "ROM Port Assistant",
                        operation = "Import ZIP",
                        stage = "Extraction",
                        message = "Failed to parse ROM archive: ${e.message}",
                        cause = e.toString()
                    )
                    Toast.makeText(context, "Error importing ROM: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isAnalyzing = false
                    currentJob = null
                }
            }
        }
    }

    val singleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            currentJob = coroutineScope.launch {
                isAnalyzing = true
                analysisProgress = 0.1f
                analysisStage = "Inspecting Image / Partition Headers..."
                try {
                    val profile = RomPortAssistantEngine.analyzeSingleFile(context, uri) { stage, prog ->
                        analysisStage = stage
                        analysisProgress = prog
                    }
                    sourceRom = profile
                    analysisResult = null
                    Toast.makeText(context, "Loaded Image: ${profile.name}", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    Toast.makeText(context, "Image Inspection cancelled.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    ErrorCenterManager.recordError(
                        module = "ROM Port Assistant",
                        operation = "Import Image",
                        stage = "Single Image Audit",
                        message = "Failed to inspect image: ${e.message}",
                        cause = e.toString()
                    )
                    Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isAnalyzing = false
                    currentJob = null
                }
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            currentJob = coroutineScope.launch {
                isAnalyzing = true
                analysisProgress = 0.1f
                analysisStage = "Scanning ROM Directory Tree..."
                try {
                    val path = uri.path ?: "ROM_Folder"
                    val folder = File(path)
                    val profile = RomPortAssistantEngine.analyzeFromFolder(folder) { stage, prog ->
                        analysisStage = stage
                        analysisProgress = prog
                    }
                    sourceRom = profile
                    analysisResult = null
                    Toast.makeText(context, "Loaded Directory: ${profile.name}", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    Toast.makeText(context, "Directory Scan cancelled.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    ErrorCenterManager.recordError(
                        module = "ROM Port Assistant",
                        operation = "Import Folder",
                        stage = "Directory Scan",
                        message = "Failed to scan ROM folder: ${e.message}",
                        cause = e.toString()
                    )
                    Toast.makeText(context, "Error scanning folder: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isAnalyzing = false
                    currentJob = null
                }
            }
        }
    }

    fun runAnalysis() {
        val src = sourceRom
        val tgt = targetDevice
        if (src == null || tgt == null) {
            Toast.makeText(context, "Please configure both Source ROM and Target Device first.", Toast.LENGTH_SHORT).show()
            return
        }

        currentJob = coroutineScope.launch {
            isAnalyzing = true
            analysisProgress = 0f
            analysisStage = "Starting Port Compatibility Analysis..."

            TaskManager.startTask(
                title = "ROM Port Analysis",
                description = "${src.name} -> ${tgt.name}",
                type = "PORT_ANALYSIS"
            ) { updateStage, appendLog, _ ->
                updateStage("Auditing subsystem compatibility...", 0.3f)
                appendLog("Source: ${src.name} (${src.architecture}, Android ${src.androidVersion})")
                appendLog("Target: ${tgt.name} (${tgt.platform}, ${tgt.cpuArch})")
                "Completed Port Analysis"
            }

            try {
                val res = RomPortAssistantEngine.analyzePortCompatibility(src, tgt) { stage, prog ->
                    analysisStage = stage
                    analysisProgress = prog
                }
                analysisResult = res
                generatedReportMarkdown = RomPortAssistantEngine.generatePortingReport(context, res, ReportFormat.MARKDOWN)

                if (res.blockers.isNotEmpty()) {
                    selectedTab = PortAssistantTab.BLOCKERS
                } else {
                    selectedTab = PortAssistantTab.ANALYZE
                }
            } catch (e: CancellationException) {
                Toast.makeText(context, "Compatibility Audit cancelled.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                ErrorCenterManager.recordError(
                    module = "ROM Port Assistant",
                    operation = "Analysis",
                    stage = "Compatibility Audit",
                    message = "Analysis failed: ${e.message}",
                    cause = e.toString()
                )
                Toast.makeText(context, "Analysis error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isAnalyzing = false
                currentJob = null
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "ROM Port Assistant",
                subtitle = "Porting Matrix • Galaxy J2 Prime",
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("port_assistant_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { runAnalysis() },
                        enabled = !isAnalyzing && sourceRom != null && targetDevice != null,
                        modifier = Modifier.testTag("port_assistant_run_analysis_action")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run Analysis", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Global Analysis Loading Banner with Cancel Button
            AnimatedVisibility(
                visible = isAnalyzing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularProgressIndicator(
                                    progress = { analysisProgress },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = analysisStage.ifEmpty { "Analyzing subsystems..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    currentJob?.cancel()
                                    isAnalyzing = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("cancel_analysis_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { analysisProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Quick Status Top Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SRC: ${sourceRom?.name ?: "None"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "TGT: ${targetDevice?.name ?: "None"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        analysisResult?.let { res ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (res.readiness.status) {
                                    PortStatus.PASS -> Color(0xFF2E7D32)
                                    PortStatus.WARNING -> Color(0xFFF57F17)
                                    PortStatus.BLOCKER, PortStatus.ERROR -> Color(0xFFC62828)
                                    else -> Color.Gray
                                }
                            ) {
                                Text(
                                    text = "Score: ${res.readiness.score}%",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { runAnalysis() },
                            enabled = !isAnalyzing && sourceRom != null && targetDevice != null,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("run_analysis_top_btn")
                        ) {
                            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Audit", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showPipelineDialog = true },
                            enabled = !isAnalyzing && sourceRom != null && targetDevice != null,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.testTag("run_pipeline_top_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pipeline", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Scrollable Tab Bar
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                PortAssistantTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title, fontSize = 13.sp) },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    PortAssistantTab.SOURCE -> {
                        SourceRomTabContent(
                            profile = sourceRom,
                            targetProfile = targetDevice,
                            selectedSubTab = selectedSourceSubTab,
                            onSubTabSelected = { selectedSourceSubTab = it },
                            onSelectReference = { showSourcePicker = true },
                            onImportZip = { zipPickerLauncher.launch("application/zip") },
                            onImportSingleFile = { singleImagePickerLauncher.launch("*/*") },
                            onImportFolder = { folderPickerLauncher.launch(null) },
                            onSelectProject = { showProjectPickerForSource = true }
                        )
                    }
                    PortAssistantTab.TARGET -> {
                        TargetDeviceSection(
                            profile = targetDevice,
                            selectedSubTab = selectedTargetSubTab,
                            onSubTabSelected = { selectedTargetSubTab = it },
                            onSelectReference = { showTargetPicker = true },
                            onScanLiveDevice = {
                                coroutineScope.launch {
                                    isAnalyzing = true
                                    analysisProgress = 0.2f
                                    analysisStage = "Querying Live Device Telemetry..."
                                    try {
                                        val dev = RomPortAssistantEngine.extractLiveDeviceProfile(context) { stage, prog ->
                                            analysisStage = stage
                                            analysisProgress = prog
                                        }
                                        targetDevice = dev
                                        analysisResult = null
                                        Toast.makeText(context, "Scanned Live Device: ${dev.name}", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            },
                            onSelectSnapshot = { showSnapshotPickerForTarget = true },
                            onSelectProject = { showProjectPickerForTarget = true }
                        )
                    }
                    PortAssistantTab.ANALYZE -> {
                        AnalysisOverviewSection(
                            result = analysisResult,
                            onRunAnalysis = { runAnalysis() },
                            onNavigateToTab = { selectedTab = it }
                        )
                    }
                    PortAssistantTab.COMPATIBILITY -> {
                        CompatibilityMatrixSection(result = analysisResult)
                    }
                    PortAssistantTab.BLOCKERS -> {
                        BlockersSection(result = analysisResult)
                    }
                    PortAssistantTab.PORT_PLAN -> {
                        PortPlanStructuredTab(
                            result = analysisResult,
                            navController = navController,
                            onRunPipeline = { showPipelineDialog = true }
                        )
                    }
                    PortAssistantTab.PATCH -> {
                        MigrationCandidatesTab(
                            result = analysisResult,
                            onNavigateToTab = { selectedTab = it },
                            onRunPipeline = { showPipelineDialog = true }
                        )
                    }
                    PortAssistantTab.BUILD -> {
                        BuildIntegrationSection(
                            result = analysisResult,
                            navController = navController,
                            onRunPipeline = { showPipelineDialog = true }
                        )
                    }
                    PortAssistantTab.REPORT -> {
                        ReportExportSection(
                            result = analysisResult,
                            onRunPipeline = { showPipelineDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Reference Picker Dialogs
    if (showSourcePicker) {
        SourcePickerBottomSheet(
            onDismiss = { showSourcePicker = false },
            onSelect = { selected ->
                sourceRom = selected
                analysisResult = null
                showSourcePicker = false
            }
        )
    }

    if (showTargetPicker) {
        TargetPickerBottomSheet(
            onDismiss = { showTargetPicker = false },
            onSelect = { selected ->
                targetDevice = selected
                analysisResult = null
                showTargetPicker = false
            }
        )
    }

    if (showProjectPickerForSource) {
        ProjectPickerBottomSheet(
            onDismiss = { showProjectPickerForSource = false },
            onSelect = { proj ->
                coroutineScope.launch {
                    isAnalyzing = true
                    analysisProgress = 0.2f
                    analysisStage = "Loading ROM Project..."
                    try {
                        val prof = RomPortAssistantEngine.loadSourceRomFromProject(proj, context)
                        sourceRom = prof
                        analysisResult = null
                        Toast.makeText(context, "Loaded Project: ${prof.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAnalyzing = false
                        showProjectPickerForSource = false
                    }
                }
            }
        )
    }

    if (showSnapshotPickerForTarget) {
        SnapshotPickerBottomSheet(
            onDismiss = { showSnapshotPickerForTarget = false },
            onSelect = { snap ->
                coroutineScope.launch {
                    isAnalyzing = true
                    analysisProgress = 0.2f
                    analysisStage = "Loading Target from Device Snapshot..."
                    try {
                        val prof = TargetDeviceAnalyzerEngine.analyzeFromSnapshot(snap) { stage, prog ->
                            analysisStage = stage
                            analysisProgress = prog
                        }
                        targetDevice = prof
                        analysisResult = null
                        Toast.makeText(context, "Loaded Target from Snapshot: ${prof.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Snapshot Load Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAnalyzing = false
                        showSnapshotPickerForTarget = false
                    }
                }
            }
        )
    }

    if (showProjectPickerForTarget) {
        ProjectPickerBottomSheet(
            onDismiss = { showProjectPickerForTarget = false },
            onSelect = { proj ->
                coroutineScope.launch {
                    isAnalyzing = true
                    analysisProgress = 0.2f
                    analysisStage = "Analyzing Target Device from Project..."
                    try {
                        val prof = TargetDeviceAnalyzerEngine.analyzeFromProject(proj)
                        targetDevice = prof
                        analysisResult = null
                        Toast.makeText(context, "Loaded Target from Project: ${prof.name}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Target Project Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isAnalyzing = false
                        showProjectPickerForTarget = false
                    }
                }
            }
        )
    }

    if (showPipelineDialog && sourceRom != null && targetDevice != null) {
        val candidates = analysisResult?.migrationCandidates ?: emptyList()
        PortPipelineRunnerDialog(
            sourceRom = sourceRom!!,
            targetDevice = targetDevice!!,
            selectedCandidates = candidates,
            onDismiss = { showPipelineDialog = false },
            onViewReport = { reportPath ->
                showPipelineDialog = false
                selectedTab = PortAssistantTab.REPORT
                Toast.makeText(context, "Report saved to $reportPath", Toast.LENGTH_LONG).show()
            }
        )
    }
}

// =========================================================================
// SECTION 1: SOURCE ROM TAB CONTENT & SUB-ANALYZERS
// =========================================================================

@Composable
fun SourceRomTabContent(
    profile: SourceRomProfile?,
    targetProfile: TargetDeviceProfile?,
    selectedSubTab: SourceSubTab,
    onSubTabSelected: (SourceSubTab) -> Unit,
    onSelectReference: () -> Unit,
    onImportZip: () -> Unit,
    onImportSingleFile: () -> Unit,
    onImportFolder: () -> Unit,
    onSelectProject: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Source ROM Input",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select donor ROM to analyze. Deep inspection audits CPU ABI, partition images, boot headers, ELF binaries, HALs, RIL and SELinux.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Selection buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onSelectReference,
                            modifier = Modifier.weight(1f).testTag("src_presets_btn"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Presets", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onImportZip,
                            modifier = Modifier.weight(1f).testTag("src_zip_btn"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("ZIP", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onImportSingleFile,
                            modifier = Modifier.weight(1f).testTag("src_img_btn"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Image", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onImportFolder,
                            modifier = Modifier.weight(1f).testTag("src_folder_btn"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Folder", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onSelectProject,
                            modifier = Modifier.weight(1f).testTag("src_project_btn"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Project", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (profile == null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("No Source ROM Selected", fontWeight = FontWeight.SemiBold)
                        Text("Choose a preset, import a ZIP/Image/Folder, or select a workspace project above.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            // Source Profile Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${profile.brand} ${profile.model} • ${profile.device}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SourceTypeBadge(profile.source)
                        }

                        HorizontalDivider()

                        // Android Comparison Strip
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("CURRENT SOURCE ANDROID:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${profile.androidVersion} (API ${if (profile.sdkInt > 0) profile.sdkInt else "UNKNOWN"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (profile.androidVersion == "UNKNOWN") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TARGET DEVICE BASE:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        text = "${targetProfile?.properties?.get("ro.build.version.release") ?: "6.0.1 (Stock) / 11 (Lineage)"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // Core Quick Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoBadge(
                                label = "Architecture",
                                value = profile.architecture,
                                modifier = Modifier.weight(1f),
                                isHighlight = profile.is64Bit
                            )
                            InfoBadge(
                                label = "System Size",
                                value = "${profile.systemSizeBytes / (1024 * 1024)} MB",
                                modifier = Modifier.weight(1f),
                                isHighlight = profile.systemSizeBytes > 1719664640L
                            )
                            InfoBadge(
                                label = "Project Treble",
                                value = if (profile.isTreble) "Enabled" else "Legacy",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Sub-Navigation Pills
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedSubTab.ordinal,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SourceSubTab.values().forEach { subTab ->
                        Tab(
                            selected = selectedSubTab == subTab,
                            onClick = { onSubTabSelected(subTab) },
                            text = {
                                val count = when (subTab) {
                                    SourceSubTab.ISSUES -> profile.sourceIssues.size
                                    SourceSubTab.UNKNOWN_DATA -> profile.unknownFieldsList.size
                                    SourceSubTab.PARTITIONS -> profile.partitions.size
                                    else -> null
                                }
                                Text(
                                    text = if (count != null && count > 0) "${subTab.title} ($count)" else subTab.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedSubTab == subTab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // Sub-Tab Content rendering
            when (selectedSubTab) {
                SourceSubTab.SUMMARY -> {
                    item {
                        SourceSummarySection(profile)
                    }
                }
                SourceSubTab.AUDIT_FIELDS -> {
                    item {
                        SourceAuditFieldsSection(profile)
                    }
                }
                SourceSubTab.PARTITIONS -> {
                    item {
                        SourcePartitionsSection(profile)
                    }
                }
                SourceSubTab.BOOT_KERNEL -> {
                    item {
                        SourceBootKernelSection(profile)
                    }
                }
                SourceSubTab.ELF_HAL_RIL -> {
                    item {
                        SourceElfHalRilSection(profile)
                    }
                }
                SourceSubTab.ISSUES -> {
                    item {
                        SourceIssuesSection(profile)
                    }
                }
                SourceSubTab.UNKNOWN_DATA -> {
                    item {
                        SourceUnknownDataSection(profile)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBadge(label: String, value: String, modifier: Modifier = Modifier, isHighlight: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isHighlight) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (isHighlight) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isHighlight) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SourceSummarySection(profile: SourceRomProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Source ROM Hardware & OS Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            ProfileDetailRow("Device Model", profile.model)
            ProfileDetailRow("Product Device", profile.device)
            ProfileDetailRow("Brand / Manufacturer", "${profile.brand} / ${profile.manufacturer}")
            ProfileDetailRow("Android Release", profile.androidVersion)
            ProfileDetailRow("API / SDK Level", if (profile.sdkInt > 0) "API ${profile.sdkInt}" else "UNKNOWN")
            ProfileDetailRow("Security Patch", profile.securityPatch)
            ProfileDetailRow("CPU Architecture", profile.architecture)
            ProfileDetailRow("64-bit ELF Blobs", if (profile.is64Bit) "YES (${profile.elfDetails.elf64Count} files)" else "NO (Pure 32-bit)")
            ProfileDetailRow("System Partition Size", "${profile.systemSizeBytes / (1024 * 1024)} MB (${profile.systemFsType})")
            ProfileDetailRow("Treble Architecture", if (profile.isTreble) "Enabled (VNDK ${profile.halDetails.vndkVersion})" else "Disabled (Legacy)")
            ProfileDetailRow("Partition Slotting", if (profile.isAb) "A/B Seamless" else "A-only")
            ProfileDetailRow("Target Chipset", profile.targetChipset)
            ProfileDetailRow("Build Display ID", profile.buildDisplayId)
            ProfileDetailRow("Fingerprint", profile.fingerprint)
            ProfileDetailRow("SELinux Default Mode", profile.selinuxMode)
        }
    }
}

@Composable
fun SourceAuditFieldsSection(profile: SourceRomProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Audited Properties & Provenance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Every analyzed field with its origin, value, confidence, and unknown status.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            if (profile.auditedFields.isEmpty()) {
                Text("No audited fields found.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                profile.auditedFields.forEach { field ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (field.isUnknown) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(field.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                val confPercent = (field.confidence * 100).toInt()
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (field.isUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = if (field.isUnknown) "UNKNOWN (0%)" else "$confPercent% Confidence",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Value: ${field.value}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (field.isUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Origin: ${field.sourceOrigin}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourcePartitionsSection(profile: SourceRomProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Partition Images & Filesystems (${profile.partitions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            if (profile.partitions.isEmpty()) {
                Text("No partition images detected in source ROM.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                profile.partitions.forEach { part ->
                    val mb = part.sizeBytes / (1024 * 1024)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "/${part.name}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${part.fileName} • ${part.format}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = "${mb} MB",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (part.name == "system" && part.sizeBytes > 1719664640L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceBootKernelSection(profile: SourceRomProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Boot Image & Kernel Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            val boot = profile.bootDetails
            ProfileDetailRow("Boot Header Version", "v${boot.headerVersion}")
            ProfileDetailRow("Page Size", "${boot.pageSize} bytes")
            ProfileDetailRow("Kernel Payload Size", "${boot.kernelSizeBytes / 1024} KB")
            ProfileDetailRow("Ramdisk Size", "${boot.ramdiskSizeBytes / 1024} KB")
            ProfileDetailRow("DTB Size", "${boot.dtbSizeBytes / 1024} KB")
            ProfileDetailRow("OS Version in Boot", boot.osVersion)
            ProfileDetailRow("OS Patch Level", boot.osPatchLevel)
            ProfileDetailRow("Signature Present", if (boot.signatureVerified) "YES (Signed)" else "NO (Raw)")

            if (boot.cmdline.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Kernel Command Line (cmdline):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = boot.cmdline,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SourceElfHalRilSection(profile: SourceRomProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ELF Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ELF Native Binary Audit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                val elf = profile.elfDetails
                ProfileDetailRow("Total Scanned Binaries", "${elf.totalBinariesScanned}")
                ProfileDetailRow("32-Bit ARMv7 Binaries", "${elf.elf32Count}")
                ProfileDetailRow("64-Bit ARM64 Binaries", "${elf.elf64Count}")
                ProfileDetailRow("32-bit Compatibility", if (elf.isPure32Bit) "100% Pure 32-bit (Compatible)" else "INCOMPATIBLE (${elf.elf64Count} 64-bit files)")

                if (elf.sample64BitBinaries.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Detected 64-Bit Blobs:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    elf.sample64BitBinaries.forEach { bin ->
                        Text("• $bin", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // HAL & RIL Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("HAL & Telephony RIL Matrix", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                ProfileDetailRow("RIL Implementation", profile.rilDetails.rilImplementation)
                ProfileDetailRow("Multi-SIM Config", profile.rilDetails.multiSimConfig)
                ProfileDetailRow("Camera HAL", profile.halDetails.cameraHalVersion)
                ProfileDetailRow("Audio HAL", profile.halDetails.audioHalVersion)
                ProfileDetailRow("Graphics Allocator", profile.halDetails.graphicsHalVersion)
                ProfileDetailRow("VNDK Version", profile.halDetails.vndkVersion)
            }
        }
    }
}

@Composable
fun SourceIssuesSection(profile: SourceRomProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Source ROM Issues & Blockers (${profile.sourceIssues.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()

            if (profile.sourceIssues.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                    Text("No fatal issues detected in source ROM.", fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                }
            } else {
                profile.sourceIssues.forEach { issue ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(issue.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.error) {
                                    Text("BLOCKER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text(issue.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Recommendation: ${issue.recommendation}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            if (issue.fixStrategy != null) {
                                Text("Fix Strategy: ${issue.fixStrategy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceUnknownDataSection(profile: SourceRomProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Source Unknown / Unverified Data (${profile.unknownFieldsList.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Fields that could not be reliably determined from ROM metadata or headers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            if (profile.unknownFieldsList.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                    Text("All source properties were identified with high confidence.", fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                }
            } else {
                profile.unknownFieldsList.forEach { unk ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(unk.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Reason: ${unk.sourceOrigin}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.outlineVariant) {
                                Text("UNKNOWN", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 2: TARGET DEVICE
// =========================================================================

@Composable
fun TargetDeviceSection(
    profile: TargetDeviceProfile?,
    selectedSubTab: TargetSubTab,
    onSubTabSelected: (TargetSubTab) -> Unit,
    onSelectReference: () -> Unit,
    onScanLiveDevice: () -> Unit,
    onSelectSnapshot: () -> Unit,
    onSelectProject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Target Source Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target Device Analyzer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Analyze physical hardware (Live Device via Root/ADB), restore from Device Snapshot, inspect Workspace Project, or use Galaxy J2 Prime Reference Baseline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 4 Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSelectReference,
                        modifier = Modifier.weight(1f).testTag("tgt_presets_btn"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Presets", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onScanLiveDevice,
                        modifier = Modifier.weight(1f).testTag("tgt_scan_live_btn"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Live Scan", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onSelectSnapshot,
                        modifier = Modifier.weight(1f).testTag("tgt_snapshot_btn"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Snapshot", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onSelectProject,
                        modifier = Modifier.weight(1f).testTag("tgt_project_btn"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Project", fontSize = 11.sp)
                    }
                }
            }
        }

        if (profile != null) {
            // Sub Tabs Row
            ScrollableTabRow(
                selectedTabIndex = selectedSubTab.ordinal,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                TargetSubTab.values().forEach { tab ->
                    Tab(
                        selected = selectedSubTab == tab,
                        onClick = { onSubTabSelected(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedSubTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Sub Tab Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedSubTab) {
                    TargetSubTab.SUMMARY -> TargetSummarySection(profile)
                    TargetSubTab.AUDIT_FIELDS -> TargetAuditFieldsSection(profile)
                    TargetSubTab.PARTITIONS_MOUNTS -> TargetPartitionsMountsSection(profile)
                    TargetSubTab.HARDWARE_HAL -> TargetHardwareHalSection(profile)
                    TargetSubTab.TARGET_ISSUES -> TargetIssuesSection(profile)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Select or scan a target device profile above.", color = Color.Gray)
            }
        }
    }
}

@Composable
fun TargetSummarySection(profile: TargetDeviceProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("ID: ${profile.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        SourceTypeBadge(profile.source)
                    }

                    HorizontalDivider()

                    // Key Specs
                    ProfileDetailRow("Device Model", profile.model)
                    ProfileDetailRow("Device / Board", "${profile.device} / ${profile.board}")
                    ProfileDetailRow("Hardware / Platform", "${profile.hardware} (${profile.platform})")
                    ProfileDetailRow("SoC", profile.soc)
                    ProfileDetailRow("CPU", "${profile.cpuArch} • ${profile.cpuCores} cores")
                    ProfileDetailRow("GPU", profile.maliGpu)
                    ProfileDetailRow("RAM", "${profile.ramTotalMb} MB")
                    ProfileDetailRow("Storage", if (profile.storageTotalBytes > 0) "${profile.storageTotalBytes / (1024 * 1024 * 1024)} GB" else "8.0 GB eMMC")
                    ProfileDetailRow("Android / SDK", "Android ${profile.androidVersion} (SDK ${profile.sdkInt})")
                    ProfileDetailRow("Kernel", profile.maxKernelVersion)
                    ProfileDetailRow("ABI", profile.supportedAbis.joinToString(", "))
                    ProfileDetailRow("Treble Supported", if (profile.isTrebleSupported) "Yes" else "No (Legacy System-as-Root off)")
                    ProfileDetailRow("A/B Partitioning", if (profile.isAbSupported) "A/B Seamless" else "A-Only (Standard)")
                    ProfileDetailRow("AVB / Verified Boot", if (profile.isAvbSupported) "Enabled" else "Disabled / None")
                    ProfileDetailRow("SELinux Status", profile.selinuxMode)
                    ProfileDetailRow("Storage Encryption", profile.encryptionState)
                    ProfileDetailRow("Root Access", if (profile.rootAvailable) "Available (UID=0 / DeviceImportEngine)" else "Unrooted")

                    if (profile.targetIssues.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = "${profile.targetIssues.size} hardware constraints / issues identified for ROM porting.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        if (profile.summary.headline.isNotBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Executive Target Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(profile.summary.headline, style = MaterialTheme.typography.bodySmall)
                        if (profile.summary.limitations.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Porting Constraints:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            profile.summary.limitations.forEach { lim ->
                                Text("• $lim", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TargetAuditFieldsSection(profile: TargetDeviceProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Audited Target Fields (${profile.auditedFields.size} fields)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (profile.auditedFields.isEmpty() && profile.evidenceList.isNotEmpty()) {
            items(profile.evidenceList) { ev ->
                EvidenceCard(ev)
            }
        } else if (profile.auditedFields.isEmpty()) {
            item {
                Text("No audited fields recorded.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        } else {
            items(profile.auditedFields) { audit ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(audit.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = audit.source.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text("Value: ${audit.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Origin: ${audit.sourceOrigin} (Confidence: ${(audit.confidence * 100).toInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TargetPartitionsMountsSection(profile: TargetDeviceProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Partition Budgets & Limits", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    ProfileDetailRow("Max System Size", "${profile.maxSystemPartitionBytes / (1024 * 1024)} MB (Budget: 1.60 GB)")
                    ProfileDetailRow("Max Boot Size", "${profile.maxBootPartitionBytes / (1024 * 1024)} MB (16 MB limit)")
                    ProfileDetailRow("Partition Count", "${profile.partitionsList.size} detected partitions")
                }
            }
        }

        if (profile.partitionsList.isNotEmpty()) {
            item {
                Text("Detected Partitions (/proc/partitions & by-name)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            items(profile.partitionsList) { part ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(part.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${part.sizeBytes / (1024 * 1024)} MB (${part.sizeBytes} bytes)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (part.name.contains("system") && part.sizeBytes > 1719664640L) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.error) {
                                Text("EXCEEDS J2P", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                }
            }
        }

        if (profile.mountsList.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Active Mounts (/proc/mounts)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            items(profile.mountsList) { mount ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${mount.mountPoint} (${mount.fsType})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Device: ${mount.deviceBlock}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun TargetHardwareHalSection(profile: TargetDeviceProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Hardware & SoC Telemetry", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    ProfileDetailRow("SoC Platform", profile.platform)
                    ProfileDetailRow("CPU Model", profile.cpuArch)
                    ProfileDetailRow("CPU Cores / ABI", "${profile.cpuCores} cores • ${profile.supportedAbis.joinToString(", ")}")
                    ProfileDetailRow("GPU Architecture", profile.maliGpu)
                    ProfileDetailRow("RAM Allocation", "${profile.ramTotalMb} MB")
                    ProfileDetailRow("Kernel", profile.maxKernelVersion)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("HAL & Subsystem Interfaces", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    ProfileDetailRow("Camera HAL", profile.cameraHal)
                    ProfileDetailRow("Audio Driver / HAL", profile.audioDriver)
                    ProfileDetailRow("RIL Telephony", profile.rilInterface)
                    ProfileDetailRow("SELinux Mode", profile.selinuxMode)
                    ProfileDetailRow("Encryption Status", profile.encryptionState)
                }
            }
        }

        if (profile.halServices.isNotEmpty()) {
            item {
                Text("Registered HAL / HIDL Services", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            items(profile.halServices) { hal ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(hal, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun TargetIssuesSection(profile: TargetDeviceProfile) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (profile.targetIssues.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp))
                        Text("No Target Hardware Issues Detected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Device specifications meet default porting compatibility baselines.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Hardware Constraints & Porting Hazards (${profile.targetIssues.size} items)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            items(profile.targetIssues) { issue ->
                val (borderCol, bgCol) = when (issue.status) {
                    PortStatus.BLOCKER -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    PortStatus.ERROR -> Color(0xFFE65100) to Color(0xFFFFE0B2).copy(alpha = 0.4f)
                    PortStatus.WARNING -> Color(0xFFF57F17) to Color(0xFFFFF9C4).copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.outline to MaterialTheme.colorScheme.surfaceVariant
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bgCol,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(issue.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Surface(shape = RoundedCornerShape(4.dp), color = borderCol) {
                                Text(
                                    text = if (issue.isBlocker) "FATAL BLOCKER" else issue.status.name,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(issue.description, style = MaterialTheme.typography.bodySmall)
                        if (issue.recommendation.isNotBlank()) {
                            Text(
                                text = "Recommendation: ${issue.recommendation}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}


// =========================================================================
// SECTION 3: ANALYSIS OVERVIEW
// =========================================================================

@Composable
fun AnalysisOverviewSection(
    result: PortAnalysisResult?,
    onRunAnalysis: () -> Unit,
    onNavigateToTab: ((PortAssistantTab) -> Unit)? = null
) {
    if (result == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Compatibility Analysis Required", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Run the 8-stage audit engine to calculate readiness and porting steps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Button(onClick = onRunAnalysis) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Run Port Compatibility Audit")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ReadinessHeroCard(result.readiness)
        }

        // [WHAT SHOULD I FIX FIRST?] Hero Triage Card
        if (result.whatToFixFirst != null) {
            item {
                WhatShouldIFixFirstCard(
                    recommendation = result.whatToFixFirst,
                    onOpenPlan = { onNavigateToTab?.invoke(PortAssistantTab.PORT_PLAN) }
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Root Cause & Subsystem Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fatal Blockers (BLOCKER)", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text("${result.readiness.blockerCount}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Critical / High Risk Issues", color = Color(0xFFD84315), fontWeight = FontWeight.Bold)
                        Text("${result.readiness.criticalCount + result.readiness.highCount}", color = Color(0xFFD84315), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Adaptations & Warnings", color = Color(0xFFF57F17), fontWeight = FontWeight.Bold)
                        Text("${result.readiness.mediumCount + result.readiness.lowCount}", color = Color(0xFFF57F17), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Verified Passed Subsystems", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        Text("${result.readiness.verifiedPassCount}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Discovered Migration Candidates", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                        Text("${result.migrationCandidates.size}", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Port Plan Sections & Tasks", fontWeight = FontWeight.Bold)
                        Text("${result.structuredPortPlan?.sections?.size ?: 11} sections (${result.structuredPortPlan?.totalTasks ?: result.generatedPortPlan.steps.size} tasks)", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateToTab?.invoke(PortAssistantTab.PATCH) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.BuildCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("View Candidates (${result.migrationCandidates.size})", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onNavigateToTab?.invoke(PortAssistantTab.PORT_PLAN) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open Port Plan", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Source vs Target Android Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    ProfileDetailRow("CURRENT SOURCE ANDROID", result.sourceRom.androidVersion)
                    ProfileDetailRow("TARGET ANDROID BASE", result.targetDevice.properties["ro.build.version.release"] ?: "6.0.1 (Stock) / 11 (Lineage)")
                    ProfileDetailRow("Source ABI", result.sourceRom.architecture)
                    ProfileDetailRow("Target ABI", result.targetDevice.cpuArch)
                }
            }
        }
    }
}

// =========================================================================
// SECTION 4: COMPATIBILITY MATRIX
// =========================================================================

@Composable
fun CompatibilityMatrixSection(result: PortAnalysisResult?) {
    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    val compat = result.compatibilityResult
    var selectedFilter by remember { mutableStateOf<CompatibilityStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val allItems = compat?.items ?: emptyList()
    val filteredItems = allItems.filter { item ->
        (selectedFilter == null || item.status == selectedFilter) &&
                (searchQuery.isBlank() || item.subsystem.contains(searchQuery, ignoreCase = true) ||
                        item.label.contains(searchQuery, ignoreCase = true) ||
                        item.category.contains(searchQuery, ignoreCase = true) ||
                        item.sourceValue.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "25-Subsystem Compatibility Matrix",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "${compat?.overallScore ?: result.readiness.score}% Score",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = compat?.summary ?: result.readiness.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Breakdown counts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusCountBadge(label = "MATCH", count = compat?.matchCount ?: 0, color = Color(0xFF2E7D32))
                        StatusCountBadge(label = "DIFFERENT", count = compat?.differentCount ?: 0, color = Color(0xFF0288D1))
                        StatusCountBadge(label = "MISSING", count = compat?.missingCount ?: 0, color = Color(0xFFEF6C00))
                        StatusCountBadge(label = "CONFLICT", count = compat?.conflictCount ?: 0, color = Color(0xFFC62828))
                        StatusCountBadge(label = "UNKNOWN", count = compat?.unknownCount ?: 0, color = Color.Gray)
                    }
                }
            }
        }

        // Search & Filter Row
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search 25 subsystems (e.g. CPU, HAL, RIL, Kernel)...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
        }

        // Filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("ALL (${allItems.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == CompatibilityStatus.MATCH,
                    onClick = { selectedFilter = if (selectedFilter == CompatibilityStatus.MATCH) null else CompatibilityStatus.MATCH },
                    label = { Text("MATCH", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == CompatibilityStatus.DIFFERENT,
                    onClick = { selectedFilter = if (selectedFilter == CompatibilityStatus.DIFFERENT) null else CompatibilityStatus.DIFFERENT },
                    label = { Text("DIFFERENT", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = selectedFilter == CompatibilityStatus.CONFLICT,
                    onClick = { selectedFilter = if (selectedFilter == CompatibilityStatus.CONFLICT) null else CompatibilityStatus.CONFLICT },
                    label = { Text("CONFLICT", fontSize = 11.sp) }
                )
            }
        }

        // Evaluated Subsystem Cards
        if (filteredItems.isNotEmpty()) {
            items(filteredItems) { item ->
                CompatibilityComparisonCard(item)
            }
        } else if (compat == null || allItems.isEmpty()) {
            // Fallback to evaluatedProperties if compatibilityResult not present
            items(result.evaluatedProperties) { prop ->
                PropertyCard(prop)
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No subsystems match the selected filter.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCountBadge(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(
                text = "$label: $count",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun CompatibilityComparisonCard(item: CompatibilityComparisonItem) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (item.status) {
        CompatibilityStatus.MATCH -> Color(0xFF2E7D32)
        CompatibilityStatus.DIFFERENT -> Color(0xFF0288D1)
        CompatibilityStatus.MISSING -> Color(0xFFEF6C00)
        CompatibilityStatus.CONFLICT -> Color(0xFFC62828)
        CompatibilityStatus.UNKNOWN -> Color.Gray
    }

    val severityColor = when (item.severity) {
        CompatibilitySeverity.BLOCKER -> Color(0xFFB71C1C)
        CompatibilitySeverity.ERROR -> Color(0xFFD32F2F)
        CompatibilitySeverity.WARNING -> Color(0xFFF57F17)
        CompatibilitySeverity.INFO -> Color(0xFF0288D1)
        CompatibilitySeverity.PASS -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = if (item.isBlocker) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header Row: Subsystem Tag + Label + Status & Severity Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "[${item.subsystem}]",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.status.name,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = severityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = item.severity.name,
                            color = severityColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Values Row: Source vs Target
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Source: ${item.sourceValue}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Target: ${item.targetValue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Reason
            Text(
                text = item.reason,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isBlocker) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            // Action Required if any
            if (item.actionRequired != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = severityColor.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = severityColor, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Action: ${item.actionRequired}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = severityColor
                        )
                    }
                }
            }

            // Expandable Evidence Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence: ${(item.confidence * 100).toInt()}% • Evidence: ${item.evidence.sourceDescription}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = if (expanded) "Hide Evidence" else "View Evidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                EvidenceCard(item.evidence)
            }
        }
    }
}

// =========================================================================
// SECTION 5: BLOCKERS & WARNINGS
// =========================================================================

@Composable
fun BlockersSection(result: PortAnalysisResult?) {
    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    val totalBlockers = if (result.portBlockers.isNotEmpty()) result.portBlockers.size else result.blockers.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fatal Blockers ($totalBlockers)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (totalBlockers > 0) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = if (totalBlockers > 0) "BUILD PROHIBITED" else "CLEAR",
                        color = if (totalBlockers > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (totalBlockers == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("No fatal blockers detected!", fontWeight = FontWeight.Bold)
                            Text("The source ROM architecture is structurally compatible with Galaxy J2 Prime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        } else if (result.portBlockers.isNotEmpty()) {
            items(result.portBlockers) { blocker ->
                PortBlockerCard(blocker)
            }
        } else {
            items(result.blockers) { issue ->
                IssueCard(issue, isBlocker = true)
            }
        }
    }
}

@Composable
fun WarningsSection(result: PortAnalysisResult?) {
    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    val totalWarnings = if (result.portWarnings.isNotEmpty()) result.portWarnings.size else result.warnings.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Adaptations & Warnings ($totalWarnings)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFF8E1)
                ) {
                    Text(
                        text = "NON-FATAL",
                        color = Color(0xFFF57F17),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (totalWarnings == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
                        Text("No warnings or non-fatal adaptations required.", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else if (result.portWarnings.isNotEmpty()) {
            items(result.portWarnings) { warning ->
                PortWarningCard(warning)
            }
        } else {
            items(result.warnings) { issue ->
                IssueCard(issue, isBlocker = false)
            }
        }
    }
}

// =========================================================================
// SECTION 6: PORT PLAN
// =========================================================================

@Composable
fun PortPlanSection(result: PortAnalysisResult?, navController: NavController) {
    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    val plan = result.generatedPortPlan

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plan.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = "Follow these sequential steps in Galaxy J2 Prime ROM Studio to assemble and compile the final ported flashable package.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        items(plan.steps) { step ->
            StepCard(step, navController)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pre-Flash Verification Checklist", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    plan.preCheckList.forEach { check ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(check, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 7: BUILD STUDIO & VERIFICATION INTEGRATION
// =========================================================================

@Composable
fun BuildIntegrationSection(
    result: PortAnalysisResult?,
    navController: NavController,
    onRunPipeline: () -> Unit
) {
    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    val canBuild = result.readiness.canProceedToBuild
    val hasBlockers = result.blockers.isNotEmpty() || result.portBlockers.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (canBuild) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (canBuild) Icons.Default.CheckCircle else Icons.Default.Dangerous,
                                contentDescription = null,
                                tint = if (canBuild) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = if (canBuild) "READY FOR ROM BUILD STUDIO" else "PORT BUILD BLOCKED",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (canBuild) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (canBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = if (canBuild) "SAFE TO ASSEMBLE" else "EXECUTION PROHIBITED",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (canBuild) {
                            "All pre-merge compatibility checks passed. You can execute the automated 9-stage pipeline or open ROM Build Studio to manually compile the flashable package."
                        } else {
                            "Unresolved fatal blockers detected (${result.readiness.blockerCount}). You must resolve all architectural and kernel constraints in the Port Plan tab before attempting to package this ROM."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (canBuild) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRunPipeline,
                            enabled = canBuild,
                            modifier = Modifier.weight(1f).testTag("build_run_pipeline_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run 9-Stage Pipeline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { navController.navigate("studio") },
                            modifier = Modifier.weight(1f).testTag("build_open_studio_btn")
                        ) {
                            Icon(Icons.Default.Engineering, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open ROM Studio", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Target Hardware Constraints Checklist
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target Device Hardware Baseline (Galaxy J2 Prime)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()

                    ProfileDetailRow("SoC / Platform", "MediaTek MT6737T (Cortex-A53 quad-core)")
                    ProfileDetailRow("Architecture / ABI", "32-bit pure (armeabi-v7a) • armv7l")
                    ProfileDetailRow("GPU / Driver", "Mali-T720 MP2 • GLES 3.0 / DDK r8p0")
                    ProfileDetailRow("Max System Partition", "1,719,664,640 bytes (1.60 GB)")
                    ProfileDetailRow("Max Boot Partition", "16,777,216 bytes (16.0 MB)")
                    ProfileDetailRow("Treble Architecture", "Non-Treble (A-only legacy partition layout)")
                    ProfileDetailRow("Kernel Tree", "android_kernel_samsung_grandpplte (3.18.35)")
                }
            }
        }

        // Post-Merge & Output Verification Checklist
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Automated Artifact & Output Verification Rules", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Every build artifact must satisfy the following post-compile security & magic checks:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    HorizontalDivider()

                    val checks = listOf(
                        "Re-open output ZIP & verify PK\\x03\\x04 zip header magic bytes",
                        "Verify boot.img header magic matches 'ANDROID!'",
                        "Verify all compiled system binaries & .so match ELF magic \\x7fELF (32-bit LSB)",
                        "Calculate & record SHA-256 and MD5 cryptographic integrity hashes",
                        "Verify total system payload size is strictly below 1.60 GB budget",
                        "Verify boot image size is strictly below 16.0 MB budget",
                        "Verify updater-script metadata & partition mounting symlinks",
                        "ProjectHealthChecker full sanity audit and zero-orphan validation"
                    )

                    checks.forEach { check ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(check, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// SECTION 8: REPORT EXPORT (MARKDOWN, JSON, TXT, CSV)
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportExportSection(
    result: PortAnalysisResult?,
    onRunPipeline: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    var selectedFormat by remember { mutableStateOf(ReportFormat.MARKDOWN) }
    var reportContent by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(result, selectedFormat) {
        isGenerating = true
        reportContent = RomPortAssistantEngine.generatePortingReport(context, result, selectedFormat)
        isGenerating = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Format Selector & Top Controls
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ROM Porting Comprehensive Audit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("21 Audited Subsystem Sections • Beta 3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (result.readiness.canProceedToBuild) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = result.readiness.state.label,
                            color = if (result.readiness.canProceedToBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalDivider()

                // Format Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReportFormat.values().forEach { fmt ->
                        val isSelected = selectedFormat == fmt
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFormat = fmt },
                            label = {
                                Text(
                                    text = fmt.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("report_fmt_${fmt.name.lowercase()}")
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isGenerating = true
                                reportContent = RomPortAssistantEngine.generatePortingReport(context, result, selectedFormat)
                                isGenerating = false
                                Toast.makeText(context, "Report regenerated (${selectedFormat.name})", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("report_refresh_btn"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("ROM Porting Report (${selectedFormat.name})", reportContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "${selectedFormat.name} report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("report_copy_btn"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy ${selectedFormat.name}", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, reportContent)
                                    type = if (selectedFormat == ReportFormat.JSON) "application/json" else "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share ROM Porting Report (${selectedFormat.name})")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("report_share_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", fontSize = 11.sp)
                    }
                }
            }
        }

        // Report Preview Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isGenerating) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text("Formatting ${selectedFormat.name} report...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = reportContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// =========================================================================
// REUSABLE HELPER WIDGETS
// =========================================================================

@Composable
fun ReadinessHeroCard(readiness: PortReadiness) {
    val state = readiness.state
    val containerColor = when (state) {
        PortReadinessState.READY -> Color(0xFFE8F5E9)
        PortReadinessState.READY_WITH_WARNINGS -> Color(0xFFFFF8E1)
        PortReadinessState.HIGH_RISK -> Color(0xFFFFF3E0)
        PortReadinessState.BLOCKED -> Color(0xFFFFEBEE)
        PortReadinessState.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (state) {
        PortReadinessState.READY -> Color(0xFF1B5E20)
        PortReadinessState.READY_WITH_WARNINGS -> Color(0xFFF57F17)
        PortReadinessState.HIGH_RISK -> Color(0xFFD84315)
        PortReadinessState.BLOCKED -> Color(0xFFB71C1C)
        PortReadinessState.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stateIcon = when (state) {
        PortReadinessState.READY -> Icons.Default.CheckCircle
        PortReadinessState.READY_WITH_WARNINGS -> Icons.Default.Warning
        PortReadinessState.HIGH_RISK -> Icons.Default.WarningAmber
        PortReadinessState.BLOCKED -> Icons.Default.Cancel
        PortReadinessState.INSUFFICIENT_DATA -> Icons.Default.HelpOutline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.15f))
                ) {
                    Icon(stateIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PORT READINESS:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = contentColor
                        ) {
                            Text(
                                text = state.label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (readiness.canProceedToBuild) "Compilation Viable" else "Execution Prohibited",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                }
            }

            Text(
                text = readiness.summary,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )

            // Diagnostic indicators row
            HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fatal Blockers: ${readiness.blockerCount}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (readiness.blockerCount > 0) Color(0xFFB71C1C) else Color(0xFF2E7D32)
                )
                Text(
                    text = "High/Crit Risk: ${readiness.criticalCount + readiness.highCount}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (readiness.criticalCount + readiness.highCount > 0) Color(0xFFD84315) else contentColor
                )
                Text(
                    text = "Warnings: ${readiness.warningCount}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = contentColor
                )
                Text(
                    text = "Pass: ${readiness.verifiedPassCount}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun WhatShouldIFixFirstCard(
    recommendation: FixFirstRecommendation,
    onOpenPlan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        border = BorderStroke(1.5.dp, Color(0xFFE65100))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFFE65100))
                    Text(
                        text = "[WHAT SHOULD I FIX FIRST?]",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE65100)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE65100)
                ) {
                    Text(
                        text = "PRIORITY 1",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE65100).copy(alpha = 0.3f))

            // PROBLEM
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("PROBLEM", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFBF360C))
                Text(recommendation.problem, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }

            // EVIDENCE
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.8f),
                border = BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("DIRECT EVIDENCE (VERIFIED)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFFBF360C))
                    Text(recommendation.evidence, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // TOOL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Handyman, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFE65100))
                Text("RECOMMENDED TOOL:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFBF360C))
                Text(recommendation.tool, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE65100))
            }

            // NEXT ACTION
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NEXT ACTION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFBF360C))
                Text(recommendation.nextAction, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = onOpenPlan,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Jump to Resolution Step in Port Plan")
            }
        }
    }
}

@Composable
fun PortBlockerCard(blocker: PortBlocker) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(
                        text = blocker.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = blocker.severity.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Meta tags row: Component, Confidence, Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = blocker.component,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE0F2F1)) {
                    Text(
                        text = "Confidence: ${blocker.confidence.name}",
                        fontSize = 10.sp,
                        color = Color(0xFF004D40),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = blocker.source.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(blocker.description, style = MaterialTheme.typography.bodySmall)

            // Direct Boot Failure Proof (Strict evidence rule)
            if (blocker.directBootFailureEvidence != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("DIRECT BOOT FAILURE PROOF", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        Text(blocker.directBootFailureEvidence, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                EvidenceCard(blocker.primaryEvidence)
            }

            // Related Files & Analyzers chips
            if (blocker.relatedFiles.isNotEmpty()) {
                Text(
                    text = "Related files: ${blocker.relatedFiles.joinToString()}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Fix Recommendation & Tool
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Recommended Fix:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                Text(blocker.recommendation, style = MaterialTheme.typography.bodySmall)
                if (blocker.fixStrategy != null) {
                    Text("Fix Strategy: ${blocker.fixStrategy}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
                Text("Suggested Tool: ${blocker.suggestedTool}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PortWarningCard(warning: PortWarning) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1).copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFFF57F17))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(20.dp))
                    Text(
                        text = warning.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFE65100)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF57F17)
                ) {
                    Text(
                        text = warning.severity.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Meta tags row: Component, Confidence, Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = warning.component,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE0F2F1)) {
                    Text(
                        text = "Confidence: ${warning.confidence.name}",
                        fontSize = 10.sp,
                        color = Color(0xFF004D40),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(warning.description, style = MaterialTheme.typography.bodySmall)

            EvidenceCard(warning.primaryEvidence)

            if (warning.relatedFiles.isNotEmpty()) {
                Text(
                    text = "Related files: ${warning.relatedFiles.joinToString()}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Recommendation: ${warning.recommendation}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100))
                if (warning.fixStrategy != null) {
                    Text("Fix Strategy: ${warning.fixStrategy}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
                Text("Suggested Tool: ${warning.suggestedTool}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PropertyCard(prop: PortEvaluatedProperty) {
    val statusColor = when (prop.status) {
        PortStatus.PASS -> Color(0xFF2E7D32)
        PortStatus.WARNING -> Color(0xFFF57F17)
        PortStatus.BLOCKER, PortStatus.ERROR -> Color(0xFFC62828)
        else -> Color.Gray
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(prop.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = prop.status.name,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text("Value: ${prop.value}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Evidence: ${prop.evidence.rawValue} (${prop.evidence.sourceDescription})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun IssueCard(issue: PortIssue, isBlocker: Boolean) {
    val color = if (isBlocker) MaterialTheme.colorScheme.error else Color(0xFFF57F17)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(issue.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = color)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color
                ) {
                    Text(
                        text = if (isBlocker) "FATAL BLOCKER" else "WARNING",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(issue.description, style = MaterialTheme.typography.bodySmall)
            Text("Recommendation: ${issue.recommendation}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = color)
            if (issue.fixStrategy != null) {
                Text("Fix Strategy: ${issue.fixStrategy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun StepCard(step: PortPlanStep, navController: NavController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Step ${step.stepNumber}: ${step.title}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(step.category, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Text(step.description, style = MaterialTheme.typography.bodySmall)
            if (step.commandHint != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$ ${step.commandHint}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EvidenceCard(ev: PortEvidence) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(ev.key, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text("Value: ${ev.rawValue}", style = MaterialTheme.typography.bodySmall)
            Text("Source: ${ev.sourceDescription} (${ev.originFileOrCommand ?: "N/A"})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SourceTypeBadge(source: ProfileSourceType) {
    val (label, col) = when (source) {
        ProfileSourceType.LIVE_DEVICE -> "LIVE DEVICE" to Color(0xFF2E7D32)
        ProfileSourceType.DEVICE_SNAPSHOT -> "SNAPSHOT" to Color(0xFF673AB7)
        ProfileSourceType.IMPORTED_FILE -> "IMPORTED ZIP" to Color(0xFF1565C0)
        ProfileSourceType.ROM_FOLDER -> "ROM FOLDER" to Color(0xFF6A1B9A)
        ProfileSourceType.PROJECT -> "PROJECT" to Color(0xFF00838F)
        ProfileSourceType.SINGLE_IMAGE -> "IMAGE FILE" to Color(0xFFE65100)
        ProfileSourceType.DAT_ARCHIVE -> "DAT ARCHIVE" to Color(0xFFAD1457)
        ProfileSourceType.SAMSUNG_TAR -> "SAMSUNG TAR" to Color(0xFF283593)
        ProfileSourceType.REFERENCE_PROFILE -> "PRESET" to Color(0xFF424242)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = col.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            color = col,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyAnalysisPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("No analysis data available. Run analysis from the Overview tab.", color = Color.Gray)
    }
}

// =========================================================================
// PICKER BOTTOM SHEETS / DIALOGS
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (SourceRomProfile) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Source ROM Preset", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Reference profiles tested for Galaxy J2 Prime porting matrix.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RomPortAssistantEngine.REFERENCE_SOURCE_ROMS) { src ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(src) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${src.androidVersion} • ${src.architecture}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (src.is64Bit) {
                                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.error) {
                                    Text("64-BIT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetPickerBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (TargetDeviceProfile) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Target J2 Prime Variant", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Target hardware profiles for Galaxy J2 Prime (SM-G532F/G/M).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RomPortAssistantEngine.REFERENCE_TARGET_DEVICES) { dev ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(dev) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${dev.platform} • ${dev.cpuArch} • 1.6GB eMMC", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotPickerBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (com.example.data.model.DeviceSnapshot) -> Unit
) {
    val context = LocalContext.current
    var snapshots by remember { mutableStateOf<List<com.example.data.model.DeviceSnapshot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        snapshots = com.example.data.manager.SnapshotManager.getAllSnapshots(context)
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Device Snapshot", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Load captured device snapshot as Target Device baseline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (snapshots.isEmpty()) {
                Text("No device snapshots found. Take a snapshot in the Snapshot / Diagnostics tab first.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshots) { snap ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(snap) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(snap.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Android ${snap.androidVersion} (SDK ${snap.sdkInt}) • ${snap.primaryAbi} • ${snap.partitions.size} partitions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (com.example.ui.studio.workspace.RomProject) -> Unit
) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf<List<com.example.ui.studio.workspace.RomProject>>(emptyList()) }
    LaunchedEffect(Unit) {
        projects = WorkspaceManager.loadAllProjects(context)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Workspace Project", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            if (projects.isEmpty()) {
                Text("No ROM Studio projects found in workspace.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects) { proj ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(proj) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(proj.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(proj.device, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
