package com.example.ui.analyzer.vendor.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.vendor.models.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorHalRilScreen(
    navController: NavController,
    viewModel: VendorHalRilViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vendor, HAL & RIL Analyzer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = analysisResult?.targetName ?: "Hardware & Subsystem Diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (analysisResult != null) {
                        IconButton(onClick = {
                            val report = viewModel.exportReport()
                            clipboardManager.setText(AnnotatedString(report))
                            Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Export Report")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isAnalyzing) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing Vendor partitions, HAL manifests & RIL stack...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (analysisResult == null) {
                EmptyOrSetupState(
                    onAnalyzeLive = { viewModel.analyzeDeviceLive(context) },
                    onAnalyzeSampleMtk = { viewModel.runSampleAnalysis("mtk_legacy") },
                    onAnalyzeSampleTreble = { viewModel.runSampleAnalysis("treble_arm64") },
                    onSelectDirectory = { path ->
                        val dir = File(path)
                        if (dir.exists()) {
                            viewModel.analyzeDirectory(dir)
                        } else {
                            Toast.makeText(context, "Directory not found: $path", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } else {
                val res = analysisResult!!
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Overview Card
                    VendorOverviewHeader(res)

                    // Scrollable Tab Row
                    val tabs = listOf("Matrix", "RIL & Modem", "HALs & Services", "ELF & Libs", "Permissions", "Issues (${res.allIssues.size})")
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        edgePadding = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { viewModel.setSelectedTab(index) },
                                text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }

                    // Tab Content
                    when (selectedTab) {
                        0 -> HardwareMatrixTab(res.hardwareMatrix)
                        1 -> RilDeepDiveTab(res.rilInfo)
                        2 -> HalServicesTab(res.halInfo)
                        3 -> VendorBinaryTab(res.vendorInfo, res.dependencyGraph)
                        4 -> PermissionsPropsTab(res.vendorInfo)
                        5 -> IssuesDiagnosticsTab(res.allIssues)
                    }
                }
            }

            errorMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { /* dismiss */ }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }
}

@Composable
fun EmptyOrSetupState(
    onAnalyzeLive: () -> Unit,
    onAnalyzeSampleMtk: () -> Unit,
    onAnalyzeSampleTreble: () -> Unit,
    onSelectDirectory: (String) -> Unit
) {
    var manualPath by remember { mutableStateOf("/system/vendor") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Android Vendor, HAL & RIL Analyzer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Analyze device hardware compatibility, VINTF HAL manifests, RIL telephony stack, DT_NEEDED binary dependencies, missing shared libraries, and SELinux avc rules.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Text("Quick Analysis Modes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnalyzeLive() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Analyze Current Device / ROM", fontWeight = FontWeight.Bold)
                        Text("Inspect live /vendor partition, properties, and HAL services", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnalyzeSampleMtk() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DeveloperBoard, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sample: MediaTek MT6737 (Legacy ARM32)", fontWeight = FontWeight.Bold)
                        Text("Simulate Galaxy J2 Prime / MTK 32-bit direct IPC RIL and Legacy HALs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAnalyzeSampleTreble() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sample: Samsung Exynos (Treble ARM64)", fontWeight = FontWeight.Bold)
                        Text("Simulate Android 9.0+ Treble VINTF HIDL HALs & Binderized RIL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }

        item {
            Text("Analyze Extracted Vendor Directory", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = manualPath,
                onValueChange = { manualPath = it },
                label = { Text("Directory Path") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onSelectDirectory(manualPath) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Directory")
            }
        }
    }
}

@Composable
fun VendorOverviewHeader(result: VendorHalRilAnalysisResult) {
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${result.vendorInfo.chipsetPlatform} (${result.vendorInfo.vendorManufacturer})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Arch: ${result.vendorInfo.primaryArch} | Target: ${result.vendorInfo.androidTargetVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(
                    status = if (result.vendorInfo.trebleStatus == TrebleStatus.TREBLE) "TREBLE" else "NON-TREBLE",
                    color = if (result.vendorInfo.trebleStatus == TrebleStatus.TREBLE) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill("HALs", "${result.halInfo.hals.size}", Modifier.weight(1f))
                StatPill("Libs", "${result.vendorInfo.libraries.size}", Modifier.weight(1f))
                StatPill("RIL Score", "${result.rilInfo.readinessScore.readinessPercentage}%", Modifier.weight(1f))
                StatPill("Issues", "${result.allIssues.size}", Modifier.weight(1f), isAlert = result.allIssues.any { it.severity == Severity.ERROR || it.severity == Severity.CRITICAL })
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier, isAlert: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = if (isAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusBadge(status: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HardwareMatrixTab(matrix: HardwareFunctionMatrix) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = matrix.summary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        items(matrix.items) { item ->
            var expanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(item.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        val badgeColor = when (item.overallStatus) {
                            HardwarePresenceStatus.LIKELY_PRESENT -> Color(0xFF2E7D32)
                            HardwarePresenceStatus.PARTIALLY_PRESENT -> Color(0xFFF57F17)
                            HardwarePresenceStatus.CONFLICT -> Color(0xFFC62828)
                            HardwarePresenceStatus.MISSING -> Color(0xFF757575)
                            HardwarePresenceStatus.UNKNOWN -> Color(0xFF9E9E9E)
                        }

                        StatusBadge(item.overallStatus.name.replace("_", " "), badgeColor)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("HAL: ${item.halStatus}", style = MaterialTheme.typography.labelSmall)
                        Text("Service: ${item.serviceStatus}", style = MaterialTheme.typography.labelSmall)
                        Text("Libs: ${item.librariesStatus}", style = MaterialTheme.typography.labelSmall)
                    }

                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Evidence & Diagnosis:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                        item.evidenceList.forEach { ev ->
                            Text("• $ev", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Notes: ${item.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RilDeepDiveTab(rilInfo: RilInfo) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Readiness Score Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Telephony Readiness Score", fontWeight = FontWeight.Bold)
                        Text("${rilInfo.readinessScore.readinessPercentage}%", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                    }
                    LinearProgressIndicator(
                        progress = { (rilInfo.readinessScore.readinessPercentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                    Text(rilInfo.readinessScore.diagnosticSummary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Dependency Chain
        item {
            Text("RIL Execution & Dependency Chain", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChainStepRow("1. Init Service", rilInfo.dependencyChain.initService, rilInfo.dependencyChain.initStatus)
                    ChainStepRow("2. Daemon Binary", rilInfo.dependencyChain.daemonBinary, rilInfo.dependencyChain.daemonStatus)
                    ChainStepRow("3. Generic RIL Lib", rilInfo.dependencyChain.rilLibrary, rilInfo.dependencyChain.rilLibStatus)
                    ChainStepRow("4. Vendor RIL Impl", rilInfo.dependencyChain.vendorImplLibrary, rilInfo.dependencyChain.vendorImplStatus)
                    ChainStepRow("5. HAL / IPC Interface", rilInfo.dependencyChain.halService, rilInfo.dependencyChain.halStatus)
                }
            }
        }

        // RIL Libraries
        item {
            Text("Detected RIL Libraries (${rilInfo.libraries.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        items(rilInfo.libraries) { lib ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(lib.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(lib.arch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Vendor Flavor: ${lib.vendorFlavor}", style = MaterialTheme.typography.bodySmall)
                    if (lib.missingLibraries.isNotEmpty()) {
                        Text("Missing DT_NEEDED: ${lib.missingLibraries.joinToString()}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // RIL Properties
        if (rilInfo.properties.isNotEmpty()) {
            item {
                Text("RIL System Properties (${rilInfo.properties.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }

            items(rilInfo.properties) { prop ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = prop.key, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(text = prop.value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (prop.description.isNotEmpty()) {
                            Text(text = prop.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // SELinux AVC Denials
        if (rilInfo.selinuxDenials.isNotEmpty()) {
            item {
                Text("RIL SELinux AVC Denials (${rilInfo.selinuxDenials.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            }

            items(rilInfo.selinuxDenials) { denial ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("scontext=${denial.scontext} -> tcontext=${denial.tcontext}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("Perm: ${denial.permission} (${denial.tclass})", style = MaterialTheme.typography.labelSmall)
                        Text(denial.impact, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ChainStepRow(step: String, name: String, status: StageStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(step, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        }
        val (txt, col) = when (status) {
            StageStatus.FOUND -> "OK" to Color(0xFF2E7D32)
            StageStatus.MISSING -> "MISSING" to Color(0xFFC62828)
            StageStatus.CONFLICT -> "CONFLICT" to Color(0xFFF57F17)
            StageStatus.UNKNOWN -> "UNKNOWN" to Color(0xFF757575)
        }
        StatusBadge(txt, col)
    }
}

@Composable
fun HalServicesTab(halInfo: HalInfo) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Hardware Abstraction Layer (HAL) Modules (${halInfo.hals.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        items(halInfo.hals) { hal ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(hal.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(hal.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Type: ${hal.type} | Format: ${hal.format} | Version: ${hal.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (hal.interfaces.isNotEmpty()) {
                        Text("Interfaces: ${hal.interfaces.joinToString { it.interfaceName }}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (halInfo.services.isNotEmpty()) {
            item {
                Text("Registered HAL Services (${halInfo.services.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }

            items(halInfo.services) { srv ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(srv.serviceName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Binary: ${srv.binaryPath ?: "unknown"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Arch: ${srv.arch} | Vintf declared: ${srv.isDeclaredInVintf}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun VendorBinaryTab(vendorInfo: VendorInfo, depGraph: DependencyGraphData) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search binaries & libraries...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        val filteredLibs = vendorInfo.libraries.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Libraries & ELF Binaries (${filteredLibs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }

            items(filteredLibs) { lib ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(lib.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            Text(lib.arch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(lib.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (lib.dependencies.isNotEmpty()) {
                            Text("DT_NEEDED: ${lib.dependencies.take(4).joinToString()}${if (lib.dependencies.size > 4) " +${lib.dependencies.size - 4} more" else ""}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (lib.missingLibraries.isNotEmpty()) {
                            Text("MISSING: ${lib.missingLibraries.joinToString()}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsPropsTab(vendorInfo: VendorInfo) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Hardware Feature Permissions (${vendorInfo.permissions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        items(vendorInfo.permissions) { perm ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(perm.featureName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("Source: ${perm.sourceFile}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!perm.isAvailableInVendor) {
                        Text("Warning: Feature declared but corresponding HAL or library was not found in vendor.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            Text("Vendor System Properties (${vendorInfo.properties.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        items(vendorInfo.properties) { prop ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = prop.key, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(text = prop.category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(text = prop.value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun IssuesDiagnosticsTab(issues: List<VendorIssue>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Detected Issues & Actionable Fixes (${issues.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }

        if (issues.isEmpty()) {
            item {
                Text("No compatibility or vendor issues detected!", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodyMedium)
            }
        }

        items(issues) { issue ->
            val containerColor = when (issue.severity) {
                Severity.CRITICAL, Severity.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                Severity.WARNING -> Color(0xFFFFF3E0)
                Severity.INFO -> MaterialTheme.colorScheme.surfaceVariant
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${issue.severity}] ${issue.type.name.replace("_", " ")}",
                            fontWeight = FontWeight.Bold,
                            color = if (issue.severity == Severity.ERROR || issue.severity == Severity.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(issue.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(issue.message, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

                    if (issue.evidence.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Evidence: ${issue.evidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    issue.recommendation?.let { rec ->
                        if (rec.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Recommendation:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(rec, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
