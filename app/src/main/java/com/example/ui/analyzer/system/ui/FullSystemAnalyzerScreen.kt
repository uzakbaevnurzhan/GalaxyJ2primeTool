package com.example.ui.analyzer.system.ui

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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.example.data.manager.ActivityTracker
import com.example.data.model.ReportFormat
import com.example.ui.analyzer.system.engine.FullSystemAnalyzerEngine
import com.example.ui.analyzer.system.engine.FullSystemHistoryManager
import com.example.ui.analyzer.system.engine.FullSystemReportFormatter
import com.example.ui.analyzer.system.engine.HardwareRuntimeTestManager
import com.example.ui.analyzer.system.engine.HardwareRuntimeTestResult
import com.example.ui.analyzer.system.models.*
import com.example.ui.common.AppTopBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class SystemAnalyzerTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    MATRIX("Matrix", Icons.Default.GridOn),
    LOCATION("Error Location", Icons.Default.MyLocation),
    ERRORS("Errors & Timeline", Icons.Default.BugReport),
    LOGS("Live Logs", Icons.AutoMirrored.Filled.ReceiptLong),
    SPECS("Device & Kernel", Icons.Default.Memory),
    HARDWARE_TEST("HW Tests", Icons.Default.Hardware),
    REGRESSION("Regression", Icons.AutoMirrored.Filled.CompareArrows),
    REPORT("Report", Icons.Default.Description)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullSystemAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(SystemAnalyzerTab.OVERVIEW) }
    var selectedMode by remember { mutableStateOf(AnalysisMode.DEEP) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var progressStage by remember { mutableStateOf("Ready to start analysis") }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    var analysisResult by remember { mutableStateOf<FullSystemAnalysisResult?>(FullSystemHistoryManager.latestResult.value) }
    var previousResult by remember { mutableStateOf<FullSystemAnalysisResult?>(null) }
    var regressionDiff by remember { mutableStateOf<AnalysisRegressionDiff?>(null) }

    var reportFormat by remember { mutableStateOf(ReportFormat.MARKDOWN) }
    var showExportDialog by remember { mutableStateOf(false) }
    var matrixFilterStatus by remember { mutableStateOf<ComponentStatus?>(null) }
    var errorSearchQuery by remember { mutableStateOf("") }
    var selectedErrorForLocation by remember { mutableStateOf<SystemErrorItem?>(null) }

    // Interactive hardware test state
    var hwTestResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isRunningHwTest by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            when (reportFormat) {
                ReportFormat.JSON -> "application/json"
                ReportFormat.TXT -> "text/plain"
                ReportFormat.MARKDOWN -> "text/markdown"
                ReportFormat.CSV -> "text/csv"
            }
        )
    ) { uri: Uri? ->
        uri?.let {
            analysisResult?.let { res ->
                try {
                    val reportContent = FullSystemReportFormatter.formatReport(res, reportFormat)
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(reportContent.toByteArray())
                    }
                    Toast.makeText(context, "Report exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startAnalysis() {
        if (isAnalyzing) return
        isAnalyzing = true
        progressFraction = 0f
        progressStage = "Initializing analyzer engine..."

        analysisJob = coroutineScope.launch {
            try {
                ActivityTracker.recordActivity("Full System Analysis", "Audit initiated in ${selectedMode.name} mode", "FullSystemAnalyzer")
                val result = FullSystemAnalyzerEngine.runFullAnalysis(
                    context = context,
                    mode = selectedMode,
                    onProgress = { stage, prog ->
                        progressStage = stage
                        progressFraction = prog
                    }
                )
                val old = analysisResult
                if (old != null) {
                    previousResult = old
                    regressionDiff = FullSystemHistoryManager.computeRegressionDiff(old, result)
                }
                analysisResult = result
                FullSystemHistoryManager.recordAnalysis(result)
                ActivityTracker.recordActivity("Full System Analysis", "Completed: ${result.healthStatus.label}", "FullSystemAnalyzer")
                Toast.makeText(context, "Full System Analysis Complete: ${result.healthStatus.label}", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                progressStage = "Analysis stopped by user"
                ActivityTracker.recordActivity("Full System Analysis", "Analysis cancelled by user", "FullSystemAnalyzer")
            } catch (e: Exception) {
                progressStage = "Analysis failed: ${e.message}"
                ErrorCenterManager.recordError("FullSystemAnalyzer", "RunAnalysis", "Engine", e.message ?: "Unknown error", stackTrace = e.stackTraceToString())
                ActivityTracker.recordActivity("Full System Analysis", "Failed: ${e.message}", "FullSystemAnalyzer")
                Toast.makeText(context, "Analysis error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun stopAnalysis() {
        analysisJob?.cancel()
        isAnalyzing = false
        progressStage = "Analysis cancelled"
        Toast.makeText(context, "Analysis stopped", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Full System Analyzer",
                subtitle = "Master Diagnostic Suite • Beta 3",
                actions = {
                    IconButton(
                        onClick = {
                            analysisResult?.let { res ->
                                val report = FullSystemReportFormatter.formatReport(res, ReportFormat.MARKDOWN)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Galaxy J2 Prime System Analysis Report")
                                    putExtra(Intent.EXTRA_TEXT, report)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report"))
                            } ?: Toast.makeText(context, "Run analysis first", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("action_share_report")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share Report")
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
            // Mode selector & Action Buttons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ANALYSIS MODE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AnalysisMode.values().forEach { mode ->
                                FilterChip(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode },
                                    label = { Text(mode.title, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("chip_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { startAnalysis() },
                            enabled = !isAnalyzing,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("btn_start_full_analysis"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isAnalyzing) "ANALYZING..." else "START FULL SCAN", style = MaterialTheme.typography.labelMedium)
                        }

                        if (isAnalyzing) {
                            OutlinedButton(
                                onClick = { stopAnalysis() },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("btn_stop_analysis"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { selectedTab = SystemAnalyzerTab.REPORT },
                                enabled = analysisResult != null,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("btn_export_report")
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("REPORT", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Progress
                    if (isAnalyzing || progressFraction > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = progressStage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Top Status KPI Bar
            analysisResult?.let { res ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (res.healthStatus) {
                            SystemHealthStatus.HEALTHY -> Color(0xFF1B5E20).copy(alpha = 0.2f)
                            SystemHealthStatus.HEALTHY_WITH_WARNINGS -> Color(0xFFF57F17).copy(alpha = 0.2f)
                            SystemHealthStatus.DEGRADED -> Color(0xFFE65100).copy(alpha = 0.2f)
                            SystemHealthStatus.CRITICAL, SystemHealthStatus.BLOCKED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            SystemHealthStatus.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SYSTEM HEALTH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = res.healthStatus.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = when (res.healthStatus) {
                                        SystemHealthStatus.HEALTHY -> Color(0xFF2E7D32)
                                        SystemHealthStatus.HEALTHY_WITH_WARNINGS -> Color(0xFFF57F17)
                                        SystemHealthStatus.DEGRADED -> Color(0xFFE65100)
                                        SystemHealthStatus.CRITICAL, SystemHealthStatus.BLOCKED -> MaterialTheme.colorScheme.error
                                        SystemHealthStatus.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "LAST WORKING STAGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = res.lastConfirmedWorkingStage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            KpiBadge("WORKING", "${res.workingCount}", Color(0xFF2E7D32))
                            KpiBadge("FAILED", "${res.failedCount}", MaterialTheme.colorScheme.error)
                            KpiBadge("PARTIAL", "${res.partialCount}", Color(0xFFF57F17))
                            KpiBadge("UNKNOWN", "${res.unknownCount}", Color.Gray)
                            KpiBadge("ERRORS", "${res.totalErrorsCount}", if (res.totalErrorsCount > 0) MaterialTheme.colorScheme.error else Color.Gray)
                            KpiBadge("BLOCKERS", "${res.blockersCount}", if (res.blockersCount > 0) MaterialTheme.colorScheme.error else Color.Gray)
                        }
                    }
                }
            }

            // Scrollable Sub-Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                SystemAnalyzerTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text(tab.title, style = MaterialTheme.typography.labelMedium) },
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
                val res = analysisResult
                if (res == null && selectedTab != SystemAnalyzerTab.HARDWARE_TEST) {
                    EmptyAnalysisState(onStart = { startAnalysis() })
                } else {
                    when (selectedTab) {
                        SystemAnalyzerTab.OVERVIEW -> OverviewTab(res!!, navController, onSelectTab = { selectedTab = it })
                        SystemAnalyzerTab.MATRIX -> MatrixTab(res!!, matrixFilterStatus, onFilter = { matrixFilterStatus = it }, navController = navController)
                        SystemAnalyzerTab.LOCATION -> LocationTab(res!!, selectedErrorForLocation, navController = navController)
                        SystemAnalyzerTab.ERRORS -> ErrorsTimelineTab(res!!, errorSearchQuery, onSearch = { errorSearchQuery = it }, onSelectError = {
                            selectedErrorForLocation = it
                            selectedTab = SystemAnalyzerTab.LOCATION
                        })
                        SystemAnalyzerTab.LOGS -> LiveLogsTab(res!!)
                        SystemAnalyzerTab.SPECS -> DeviceKernelSpecsTab(res!!)
                        SystemAnalyzerTab.HARDWARE_TEST -> HardwareTestsTab(
                            context = context,
                            testResults = hwTestResults,
                            isRunning = isRunningHwTest,
                            onRunTest = { testName ->
                                coroutineScope.launch {
                                    isRunningHwTest = true
                                    val r = when (testName) {
                                        "Speaker" -> HardwareRuntimeTestManager.testSpeaker(context)
                                        "Vibrator" -> HardwareRuntimeTestManager.testVibrator(context)
                                        "Camera" -> HardwareRuntimeTestManager.testCameraSensors(context)
                                        "Microphone" -> HardwareRuntimeTestManager.testMicrophone(context)
                                        "Sensors" -> HardwareRuntimeTestManager.testSensorsPresence(context)
                                        "Flashlight_ON" -> HardwareRuntimeTestManager.testFlashlight(context, true)
                                        "Flashlight_OFF" -> HardwareRuntimeTestManager.testFlashlight(context, false)
                                        else -> HardwareRuntimeTestResult(testName, ComponentStatus.UNKNOWN, "Unknown test", "")
                                    }
                                    hwTestResults = hwTestResults + (testName to "${r.status.label}: ${r.message}")
                                    isRunningHwTest = false
                                }
                            }
                        )
                        SystemAnalyzerTab.REGRESSION -> RegressionTab(res, previousResult, regressionDiff)
                        SystemAnalyzerTab.REPORT -> ReportExportTab(
                            result = res!!,
                            format = reportFormat,
                            onFormatChange = { reportFormat = it },
                            onExportFile = { exportLauncher.launch("system_analysis_${System.currentTimeMillis()}.${reportFormat.name.lowercase()}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KpiBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun EmptyAnalysisState(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Troubleshoot,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Full System Analyzer Ready",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Execute deep multi-subsystem diagnostic audit across Android, Kernel 3.18, partitions, HALs, RIL, SELinux, and ELF binaries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.testTag("btn_empty_start_analysis")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("RUN FULL ANALYSIS NOW")
        }
    }
}

@Composable
fun OverviewTab(
    res: FullSystemAnalysisResult,
    navController: NavController,
    onSelectTab: (SystemAnalyzerTab) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Capabilities card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "DIAGNOSTIC CAPABILITIES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        CapabilityItem("Root", res.capabilities.rootAvailable)
                        CapabilityItem("Procfs", res.capabilities.procAvailable)
                        CapabilityItem("Sysfs", res.capabilities.sysAvailable)
                        CapabilityItem("Partitions", res.capabilities.partitionsAvailable)
                        CapabilityItem("Pstore", res.capabilities.pstoreAvailable)
                        CapabilityItem("ADB", res.capabilities.adbEnabled)
                    }
                }
            }
        }

        // Android Version Conflict Warning if any
        if (res.androidVersionAudit.hasConflict) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("ANDROID VERSION CONFLICT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            Text(res.androidVersionAudit.conflictSummary ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Root Causes
        if (res.rootCauses.isNotEmpty()) {
            item {
                Text(
                    text = "ROOT CAUSE CANDIDATES (${res.rootCauses.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(res.rootCauses) { rc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, if (rc.severity == SystemSeverity.BLOCKER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(rc.problem, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("${rc.confidence}% Confidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(rc.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("CAUSAL CHAIN:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        rc.causeChain.forEach { step ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(step, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val route = when {
                                    rc.nextTool.contains("ELF", ignoreCase = true) -> "elf_analyzer"
                                    rc.nextTool.contains("SELinux", ignoreCase = true) -> "selinux_analyzer"
                                    rc.nextTool.contains("Port", ignoreCase = true) -> "rom_port_assistant"
                                    else -> "error_center"
                                }
                                navController.navigate(route)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("RESOLVE WITH ${rc.nextTool}")
                        }
                    }
                }
            }
        }

        // Actionable Recommendations
        if (res.fixSuggestions.isNotEmpty()) {
            item {
                Text(
                    text = "ACTIONABLE RECOMMENDATIONS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(res.fixSuggestions) { fix ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(fix.problem, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(fix.priority.label, style = MaterialTheme.typography.labelSmall, color = if (fix.priority == SystemSeverity.BLOCKER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(fix.nextAction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { navController.navigate(fix.nextToolRoute) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Open ${fix.nextTool}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilityItem(label: String, isOk: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isOk) Color(0xFF2E7D32) else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
    }
}

@Composable
fun MatrixTab(
    res: FullSystemAnalysisResult,
    filterStatus: ComponentStatus?,
    onFilter: (ComponentStatus?) -> Unit,
    navController: NavController
) {
    val items = remember(res, filterStatus) {
        if (filterStatus == null) res.halComponentMatrix
        else res.halComponentMatrix.filter { it.status == filterStatus }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Status filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterStatus == null,
                onClick = { onFilter(null) },
                label = { Text("All (${res.halComponentMatrix.size})", style = MaterialTheme.typography.labelSmall) }
            )
            ComponentStatus.values().forEach { st ->
                val count = res.halComponentMatrix.count { it.status == st }
                if (count > 0) {
                    FilterChip(
                        selected = filterStatus == st,
                        onClick = { onFilter(if (filterStatus == st) null else st) },
                        label = { Text("${st.label} ($count)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { comp ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (comp.status) {
                                        ComponentStatus.WORKING -> Color(0xFF2E7D32)
                                        ComponentStatus.FAILED -> MaterialTheme.colorScheme.error
                                        ComponentStatus.PARTIAL -> Color(0xFFF57F17)
                                        ComponentStatus.UNKNOWN, ComponentStatus.NOT_TESTED, ComponentStatus.UNAVAILABLE -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(comp.componentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(comp.status.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = when (comp.status) {
                                    ComponentStatus.WORKING -> Color(0xFF2E7D32)
                                    ComponentStatus.FAILED -> MaterialTheme.colorScheme.error
                                    ComponentStatus.PARTIAL -> Color(0xFFF57F17)
                                    else -> Color.Gray
                                })
                            }
                            Text(comp.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Text("Source: ${comp.source.label}", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (comp.relatedToolRoute != null) {
                            IconButton(onClick = { navController.navigate(comp.relatedToolRoute) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open Tool", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationTab(
    res: FullSystemAnalysisResult,
    selectedError: SystemErrorItem?,
    navController: NavController
) {
    val errorToShow = selectedError ?: res.deduplicatedErrors.firstOrNull()

    if (errorToShow == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No Critical Errors Found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("The system did not exhibit any critical failure signatures.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ERROR LOCATION & DIAGNOSIS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Subsystem: ${errorToShow.subsystem.label}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(errorToShow.severity.label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }

                    Text("Component: ${errorToShow.component}", style = MaterialTheme.typography.bodySmall)
                    Text("Failure Stage: ${errorToShow.stage}", style = MaterialTheme.typography.bodySmall)
                    if (errorToShow.sourceFile != null) {
                        Text("Origin File / Node: ${errorToShow.sourceFile}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("ERROR MESSAGE:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(errorToShow.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)

                    Text("EVIDENCE / LOG DATA:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = errorToShow.rawEvidence,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text("RECOMMENDED ACTION:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(errorToShow.suggestedAction, style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = { navController.navigate(errorToShow.relatedTool) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OPEN ${errorToShow.relatedTool.uppercase()}")
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorsTimelineTab(
    res: FullSystemAnalysisResult,
    search: String,
    onSearch: (String) -> Unit,
    onSelectError: (SystemErrorItem) -> Unit
) {
    val filtered = remember(res, search) {
        if (search.isBlank()) res.deduplicatedErrors
        else res.deduplicatedErrors.filter { it.message.contains(search, ignoreCase = true) || it.subsystem.label.contains(search, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            placeholder = { Text("Search error messages or subsystems...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectError(err) },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(err.subsystem.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (err.repeatCount > 1) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("x${err.repeatCount}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (err.severity == SystemSeverity.BLOCKER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        err.severity.label,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (err.severity == SystemSeverity.BLOCKER) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err.message, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(err.rawEvidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveLogsTab(res: FullSystemAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("LOG SUBSYSTEM AUDIT", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Logcat Lines Read: ${res.logAudit.logcatLinesRead} | Dmesg Lines: ${res.logAudit.dmesgLinesRead} | Pstore: ${if (res.logAudit.pstoreAvailable) "Available" else "None"}", style = MaterialTheme.typography.bodySmall)
        }

        if (res.logAudit.fatalSignalsFound.isNotEmpty()) {
            item {
                Text("FATAL SIGNALS CAPTURED (${res.logAudit.fatalSignalsFound.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            items(res.logAudit.fatalSignalsFound) { fatal ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(fatal, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (res.logAudit.kernelPanicsFound.isNotEmpty()) {
            item {
                Text("KERNEL PANIC / OOPS (${res.logAudit.kernelPanicsFound.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            items(res.logAudit.kernelPanicsFound) { panic ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(panic, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun DeviceKernelSpecsTab(res: FullSystemAnalysisResult) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("DEVICE SPECIFICATIONS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(res.deviceSummary.entries.toList()) { (key, item) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(item.source.label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text("PARTITIONS TABLE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        items(res.partitionAudit) { part ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(part.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(part.path, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(part.status.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = if (part.status == ComponentStatus.WORKING) Color(0xFF2E7D32) else Color.Gray)
                        if (part.sizeBytes > 0) {
                            Text("${part.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareTestsTab(
    context: Context,
    testResults: Map<String, String>,
    isRunning: Boolean,
    onRunTest: (String) -> Unit
) {
    val tests = listOf(
        "Speaker" to "Play test acoustic tone on STREAM_MUSIC",
        "Vibrator" to "Trigger 300ms vibration impulse",
        "Camera" to "Enumerate camera sensors & HAL levels",
        "Microphone" to "Capture PCM 16-bit audio stream & peak amplitude",
        "Sensors" to "Query hardware accelerometer, light & proximity",
        "Flashlight_ON" to "Turn flashlight torch ON",
        "Flashlight_OFF" to "Turn flashlight torch OFF"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("INTERACTIVE HARDWARE RUNTIME TESTS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Real hardware probes. No mock PASS values.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(tests) { (name, desc) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val resStr = testResults[name]
                        if (resStr != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(resStr, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Button(
                        onClick = { onRunTest(name) },
                        enabled = !isRunning,
                        modifier = Modifier.testTag("btn_test_$name")
                    ) {
                        Text("TEST")
                    }
                }
            }
        }
    }
}

@Composable
fun RegressionTab(
    current: FullSystemAnalysisResult?,
    previous: FullSystemAnalysisResult?,
    diff: AnalysisRegressionDiff?
) {
    if (diff == null || previous == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text("No Previous Analysis To Compare", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Run another analysis to perform automated regression detection and track fixed vs newly introduced errors.", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("REGRESSION & PROGRESS ANALYSIS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Comparing Session ${diff.oldSessionId.take(8)} ➔ ${diff.newSessionId.take(8)}", style = MaterialTheme.typography.bodySmall)
            }

            if (diff.regressedComponents.isNotEmpty()) {
                item {
                    Text("REGRESSED COMPONENTS (${diff.regressedComponents.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                items(diff.regressedComponents) { comp ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(comp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (diff.improvedComponents.isNotEmpty()) {
                item {
                    Text("IMPROVED COMPONENTS (${diff.improvedComponents.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                }
                items(diff.improvedComponents) { comp ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.2f))) {
                        Text(comp, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }

            if (diff.fixedErrors.isNotEmpty()) {
                item {
                    Text("FIXED ERRORS (${diff.fixedErrors.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                }
                items(diff.fixedErrors) { err ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("✔ ${err.message}", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (diff.newErrors.isNotEmpty()) {
                item {
                    Text("NEW ERRORS INTRODUCED (${diff.newErrors.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                items(diff.newErrors) { err ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("✖ ${err.message}", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ReportExportTab(
    result: FullSystemAnalysisResult,
    format: ReportFormat,
    onFormatChange: (ReportFormat) -> Unit,
    onExportFile: () -> Unit
) {
    val context = LocalContext.current
    val reportText = remember(result, format) {
        FullSystemReportFormatter.formatReport(result, format)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("EXPORT FORMAT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReportFormat.values().forEach { fmt ->
                    FilterChip(
                        selected = format == fmt,
                        onClick = { onFormatChange(fmt) },
                        label = { Text(fmt.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("System Diagnostic Report", reportText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("COPY REPORT")
            }

            FilledTonalButton(
                onClick = onExportFile,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SAVE AS FILE")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = reportText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
