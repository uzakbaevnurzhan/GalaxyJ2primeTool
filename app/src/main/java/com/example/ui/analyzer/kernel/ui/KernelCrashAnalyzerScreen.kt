package com.example.ui.analyzer.kernel.ui

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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.kernel.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelCrashAnalyzerScreen(
    navController: NavController,
    viewModel: KernelCrashAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var exportFormat by remember { mutableStateOf("markdown") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeUri(context, it) }
    }

    val systemMapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadSystemMap(context, it) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/*")
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                viewModel.exportReport(context, it, exportFormat)
                Toast.makeText(context, "Report exported successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Pstore selection dialog
    if (viewModel.isPstoreDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.isPstoreDialogOpen = false },
            title = { Text("Select pstore / ramoops log") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (viewModel.pstoreEntries.isEmpty()) {
                        Text("No pstore entries detected.")
                    } else {
                        viewModel.pstoreEntries.forEach { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("${entry.path} (${entry.sizeBytes} B)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.analyzePstoreFile(entry) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.isPstoreDialogOpen = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kernel Crash Analyzer", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { systemMapLauncher.launch("*/*") }) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = "Load System.map",
                            tint = if (viewModel.systemMapParser.isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (uiState is AnalyzerUiState.Success) {
                        IconButton(onClick = {
                            exportFormat = "markdown"
                            exportLauncher.launch("kernel_crash_report.md")
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export Markdown")
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
            // Action Buttons Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select Log")
                }

                OutlinedButton(onClick = { viewModel.collectLiveDmesg() }) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Collect dmesg")
                }

                OutlinedButton(onClick = { viewModel.listPstoreFiles() }) {
                    Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Collect pstore")
                }
            }

            viewModel.statusMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.statusMessage = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = uiState) {
                is AnalyzerUiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Kernel Crash & Panic Analyzer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Select a dmesg, logcat, pstore/ramoops, or last_kmsg file\nor collect live logs with root permissions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                is AnalyzerUiState.Analyzing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Analyzing kernel log stream...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Lines: ${state.progress.linesCount} | Events: ${state.progress.eventsCount} | ${state.progress.percent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.cancelAnalysis() }) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                is AnalyzerUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Analysis Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                is AnalyzerUiState.Success -> {
                    val report = state.report
                    ReportContentView(report = report, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun ReportContentView(
    report: KernelCrashReport,
    viewModel: KernelCrashAnalyzerViewModel
) {
    val tabTitles = listOf("Summary", "Crashes (${report.totalEvents})", "Call Traces", "Boot Failure", "Raw Context")

    Column(modifier = Modifier.fillMaxSize()) {
        // Metadata card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        report.fileName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Badge(
                        containerColor = if (report.criticalEvents > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ) {
                        Text("${report.criticalEvents} Critical / ${report.totalEvents} Total")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Kernel: ${report.kernelVersion ?: "Unknown"} | Arch: ${report.architecture} | SHA: ${report.fileSha256?.take(10) ?: "N/A"}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Navigation Tabs
        ScrollableTabRow(
            selectedTabIndex = viewModel.activeTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = viewModel.activeTab == index,
                    onClick = { viewModel.activeTab = index },
                    text = { Text(title, fontSize = 13.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (viewModel.activeTab) {
            0 -> SummaryTab(report = report)
            1 -> CrashEventsTab(report = report, viewModel = viewModel)
            2 -> CallTracesTab(report = report)
            3 -> BootFailureTab(report = report)
            4 -> RawContextTab(report = report, viewModel = viewModel)
        }
    }
}

@Composable
private fun SummaryTab(report: KernelCrashReport) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Stats Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    title = "Critical",
                    value = "${report.criticalEvents}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Errors",
                    value = "${report.errorEvents}",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Warnings",
                    value = "${report.warningEvents}",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (report.suspectedSubsystems.isNotEmpty()) {
            item {
                Text("Suspected Subsystems", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(report.suspectedSubsystems) { sub ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sub.type.displayName, fontWeight = FontWeight.Bold)
                            ConfidenceChip(sub.confidence)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Matched keywords: ${sub.matchedKeywords.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (report.topProcesses.isNotEmpty()) {
            item {
                Text("Top Crashing Processes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(report.topProcesses) { (proc, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(proc, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("$count event(s)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
        }

        if (report.topSymbols.isNotEmpty()) {
            item {
                Text("Top Crash Symbols", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(report.topSymbols) { (sym, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(sym, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("$count frame(s)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CrashEventsTab(
    report: KernelCrashReport,
    viewModel: KernelCrashAnalyzerViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(report.crashEvents) { ev ->
            val isSelected = viewModel.selectedEventId == ev.id
            CrashEventCard(
                event = ev,
                isSelected = isSelected,
                onClick = { viewModel.selectedEventId = ev.id }
            )
        }
    }
}

@Composable
private fun CrashEventCard(
    event: KernelCrashEvent,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val severityColor = when (event.severity) {
        KernelSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        KernelSeverity.ERROR -> Color(0xFFE65100)
        KernelSeverity.WARNING -> Color(0xFFF57F17)
        KernelSeverity.INFO -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${event.id}: ${event.type.displayName}",
                    fontWeight = FontWeight.Bold,
                    color = severityColor,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = event.timestamp ?: "Line ${event.sourceLineIndex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (event.comm != null || event.pid != null || event.cpu != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Process: ${event.comm ?: "unknown"} (PID: ${event.pid ?: "?"}) | CPU: ${event.cpu ?: "?"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (event.panicReason != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${event.panicReason}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Root cause deduction box
            event.analysis?.let { ana ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Root Cause Deduction", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            ConfidenceChip(ana.confidence)
                        }
                        if (ana.possibleCauses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Cause: ${ana.possibleCauses.first()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (ana.recommendedActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Fix: ${ana.recommendedActions.first()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Stack frames snippet
            if (event.stackFrames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Call Trace:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        event.stackFrames.take(4).forEach { frame ->
                            Text(frame.formattedDisplay, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        if (event.stackFrames.size > 4) {
                            Text("... and ${event.stackFrames.size - 4} more frames", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            // Registers snippet
            if (!event.registers.isEmpty) {
                Spacer(modifier = Modifier.height(6.dp))
                val regText = buildString {
                    if (event.registers.pc != null) append("PC=${event.registers.pc} ")
                    if (event.registers.lr != null) append("LR=${event.registers.lr} ")
                    if (event.registers.sp != null) append("SP=${event.registers.sp} ")
                    if (event.registers.faultAddress != null) append("Addr=${event.registers.faultAddress}")
                }
                if (regText.isNotBlank()) {
                    Text(
                        "Registers (${event.registers.architecture}): $regText",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CallTracesTab(report: KernelCrashReport) {
    if (report.repeatedTraces.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No call traces detected in this log.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(report.repeatedTraces) { grp ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${grp.occurrences} occurrence(s)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            grp.firstTimestamp ?: "Unknown time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Signature: ${grp.signature}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            grp.sampleEvent.stackFrames.forEach { frame ->
                                Text(frame.formattedDisplay, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootFailureTab(report: KernelCrashReport) {
    val boot = report.bootFailureAnalysis
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (boot.isBootFailureLikely) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (boot.isBootFailureLikely) "⚠️ Potential Boot Failure / Bootloop Detected" else "✅ No Critical Boot Blockers Detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (boot.isBootFailureLikely) "The analyzed log exhibits indicators of an unbootable Android system." else "The kernel and early Android services did not record fatal bootloop triggers.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (boot.detectedBlockers.isNotEmpty()) {
            item {
                Text("Detected Boot Blockers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(boot.detectedBlockers) { blocker ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(blocker, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (boot.recoveryRecommendations.isNotEmpty()) {
            item {
                Text("Recommended Recovery Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(boot.recoveryRecommendations) { rec ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(rec, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun RawContextTab(
    report: KernelCrashReport,
    viewModel: KernelCrashAnalyzerViewModel
) {
    val selectedEvent = report.crashEvents.firstOrNull { it.id == viewModel.selectedEventId } ?: report.crashEvents.firstOrNull()

    if (selectedEvent == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a crash event to view raw surrounding log lines.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Raw Context for ${selectedEvent.id} (${selectedEvent.type.displayName})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedEvent.contextLinesBefore.isNotEmpty()) {
            item {
                Text("--- Lines Before Crash (${selectedEvent.contextLinesBefore.size}) ---", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.04f))
                            .padding(6.dp)
                    ) {
                        selectedEvent.contextLinesBefore.forEach {
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Text("--- Crash Block ---", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                        .padding(6.dp)
                ) {
                    Text(selectedEvent.rawBlock, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, fontSize = 11.sp, color = color)
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: AnalysisConfidence) {
    val (color, text) = when (confidence) {
        AnalysisConfidence.HIGH -> Pair(Color(0xFF2E7D32), "HIGH")
        AnalysisConfidence.MEDIUM -> Pair(Color(0xFFEF6C00), "MEDIUM")
        AnalysisConfidence.LOW -> Pair(Color(0xFF757575), "LOW")
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
