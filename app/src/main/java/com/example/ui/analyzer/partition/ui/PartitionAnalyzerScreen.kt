package com.example.ui.analyzer.partition.ui

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
import com.example.ui.analyzer.partition.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PartitionAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<PartitionAnalysisResult?>(null)
    val result: StateFlow<PartitionAnalysisResult?> = _result

    private val _fileName = MutableStateFlow("")
    val fileName: StateFlow<String> = _fileName

    private val analyzer = PartitionTableAnalyzer()

    fun analyzeUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            val res = withContext(Dispatchers.IO) {
                try {
                    val name = getFileNameFromUri(context, uri)
                    _fileName.value = name
                    val tempFile = File(context.cacheDir, "temp_part_${System.currentTimeMillis()}_$name")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val analysis = analyzer.analyzeFile(tempFile)
                    tempFile.delete()
                    analysis
                } catch (e: Exception) {
                    PartitionAnalysisResult(
                        health = PartitionTableHealth.CORRUPTED,
                        summary = "Error reading partition table: ${e.message}",
                        details = e.stackTraceToString()
                    )
                }
            }
            _result.value = res
            _isAnalyzing.value = false
        }
    }

    fun loadSampleJ2PrimeScatter() {
        val sample = """
############################################################################################################
#
#  General Setting
#
############################################################################################################
- general: MTK_PLATFORM_CFG
  info:
    - config_version: V1.1.2
      platform: MT6737T
      project: grandpplte
      storage: EMMC
      boot_channel: MSDC_0
      block_size: 0x20000
############################################################################################################
- partition_index: SYS0
  partition_name: preloader
  file_name: preloader_grandpplte.bin
  is_download: true
  type: SV5_BL_BIN
  linear_start_addr: 0x0
  physical_start_addr: 0x0
  partition_size: 0x40000
  region: EMMC_BOOT_1
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: BOOTLOADERS
  reserve: 0x00
- partition_index: SYS1
  partition_name: lk
  file_name: lk.bin
  is_download: true
  type: NORMAL_ROM
  linear_start_addr: 0x40000
  physical_start_addr: 0x40000
  partition_size: 0x100000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS2
  partition_name: boot
  file_name: boot.img
  is_download: true
  type: NORMAL_ROM
  linear_start_addr: 0x140000
  physical_start_addr: 0x140000
  partition_size: 0x2000000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS3
  partition_name: recovery
  file_name: recovery.img
  is_download: true
  type: NORMAL_ROM
  linear_start_addr: 0x2140000
  physical_start_addr: 0x2140000
  partition_size: 0x2000000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS4
  partition_name: system
  file_name: system.img
  is_download: true
  type: YAFFS_IMG
  linear_start_addr: 0x4140000
  physical_start_addr: 0x4140000
  partition_size: 0x96000000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS5
  partition_name: cache
  file_name: cache.img
  is_download: true
  type: YAFFS_IMG
  linear_start_addr: 0x9A140000
  physical_start_addr: 0x9A140000
  partition_size: 0xC800000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS6
  partition_name: userdata
  file_name: userdata.img
  is_download: true
  type: YAFFS_IMG
  linear_start_addr: 0xA6940000
  physical_start_addr: 0xA6940000
  partition_size: 0x1066C0000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS7
  partition_name: nvram
  file_name: NONE
  is_download: false
  type: NORMAL_ROM
  linear_start_addr: 0x1AD000000
  physical_start_addr: 0x1AD000000
  partition_size: 0x500000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: true
  operation_type: INVISIBLE
  reserve: 0x00
        """.trimIndent()
        _fileName.value = "MT6737T_Android_scatter_J2Prime.txt"
        _result.value = analyzer.analyzeText(sample, "MT6737T_Android_scatter_J2Prime.txt")
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "partition_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartitionAnalyzerScreen(
    navController: NavController,
    viewModel: PartitionAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val fileName by viewModel.fileName.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegionFilter by remember { mutableStateOf("ALL") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeUri(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Partition Table Analyzer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (fileName.isNotEmpty()) {
                            Text(
                                fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        result?.let {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Partition Report", it.details))
                            Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Table / Scatter")
                }

                OutlinedButton(
                    onClick = { viewModel.loadSampleJ2PrimeScatter() }
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("J2 Prime Preset")
                }
            }

            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Parsing partition table & geometry...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (result == null) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PieChart,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Partition Table Loaded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Select an MTK Scatter file (.txt), GPT / MBR raw image dump, or load the Galaxy J2 Prime profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                val res = result!!
                val tabs = listOf("Overview", "Partitions (${res.table.partitions.size})", "Visual Map", "Issues (${res.issues.size})", "Raw Config")

                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> PartitionOverviewTab(res)
                    1 -> PartitionListTab(
                        partitions = res.table.partitions,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedRegion = selectedRegionFilter,
                        onRegionChange = { selectedRegionFilter = it }
                    )
                    2 -> PartitionVisualMapTab(res)
                    3 -> PartitionIssuesTab(res.issues, res.addressGaps)
                    4 -> PartitionRawHeadersTab(res.table)
                }
            }
        }
    }
}

@Composable
private fun PartitionOverviewTab(result: PartitionAnalysisResult) {
    val table = result.table
    val healthColor = when (result.health) {
        PartitionTableHealth.VALID -> MaterialTheme.colorScheme.primary
        PartitionTableHealth.WARNING -> MaterialTheme.colorScheme.tertiary
        PartitionTableHealth.CORRUPTED -> MaterialTheme.colorScheme.error
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Health Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            table.type.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = healthColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                result.health.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = healthColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Metrics Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Partitions",
                    value = table.partitions.size.toString(),
                    subtitle = "Entries declared"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Allocated",
                    value = table.formattedAllocatedBytes,
                    subtitle = "Total Size"
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Sector Size",
                    value = "${table.sectorSize} B",
                    subtitle = "Logical sector"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Issues",
                    value = result.issues.size.toString(),
                    subtitle = "Crit: ${result.criticalIssuesCount} | Warn: ${result.warningIssuesCount}"
                )
            }
        }

        // Platform & Target info
        if (table.platformName.isNotEmpty() || table.storageType.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hardware & Storage Spec", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if (table.platformName.isNotEmpty()) {
                            InfoRow("Platform / Chipset", table.platformName)
                        }
                        if (table.projectVersion.isNotEmpty()) {
                            InfoRow("Project Config", table.projectVersion)
                        }
                        InfoRow("Storage Type", table.storageType)
                        if (table.diskGuid.isNotEmpty()) {
                            InfoRow("Disk GUID", table.diskGuid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionListTab(
    partitions: List<PartitionEntry>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedRegion: String,
    onRegionChange: (String) -> Unit
) {
    val regions = remember(partitions) {
        listOf("ALL") + partitions.map { it.region }.distinct()
    }

    val filtered = remember(partitions, searchQuery, selectedRegion) {
        partitions.filter { p ->
            (selectedRegion == "ALL" || p.region.equals(selectedRegion, ignoreCase = true)) &&
            (searchQuery.isEmpty() || p.name.contains(searchQuery, ignoreCase = true) || p.typeDescription.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search & Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search partition name or type...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // Region Chips
        if (regions.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                regions.forEach { reg ->
                    FilterChip(
                        selected = selectedRegion == reg,
                        onClick = { onRegionChange(reg) },
                        label = { Text(reg) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { part ->
                PartitionItemCard(part)
            }
        }
    }
}

@Composable
private fun PartitionItemCard(part: PartitionEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "${part.index}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        part.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    part.sizeFormatted,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${part.startAddressHex} .. ${part.endAddressHex}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    part.region,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                InfoRow("Type / Spec", part.typeDescription)
                InfoRow("Type ID / GUID", part.typeGuidOrId.ifEmpty { "N/A" })
                if (part.uniqueGuid.isNotEmpty()) {
                    InfoRow("Unique GUID", part.uniqueGuid)
                }
                InfoRow("LBA Range", "LBA ${part.startLba} - ${part.endLba} (${part.sectorCount} sectors)")
                InfoRow("Original File", part.originalFileName.ifEmpty { "None" })
                InfoRow("Operation Type", part.operationType)
                InfoRow("Read Only / Protected", if (part.isReadOnly) "YES" else "NO")
                if (part.flags.isNotEmpty()) {
                    InfoRow("Flags", part.flags.joinToString(", "))
                }
            }
        }
    }
}

@Composable
private fun PartitionVisualMapTab(result: PartitionAnalysisResult) {
    val partitions = result.table.partitions.filter { it.sizeBytes > 0 }
    val totalAllocated = partitions.sumOf { it.sizeBytes }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Disk Layout Proportions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Proportional memory allocation across partitions (${PartitionEntry.formatBytes(totalAllocated)} total)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            // Visual Segmented Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        partitions.forEachIndexed { idx, p ->
                            val weight = if (totalAllocated > 0) (p.sizeBytes.toFloat() / totalAllocated.toFloat()).coerceAtLeast(0.01f) else 1f
                            val color = when (idx % 6) {
                                0 -> MaterialTheme.colorScheme.primary
                                1 -> MaterialTheme.colorScheme.secondary
                                2 -> MaterialTheme.colorScheme.tertiary
                                3 -> MaterialTheme.colorScheme.primaryContainer
                                4 -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }

        items(partitions) { p ->
            val pct = if (totalAllocated > 0) (p.sizeBytes.toDouble() / totalAllocated.toDouble()) * 100 else 0.0
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${p.startAddressHex} (Size: ${p.sizeFormatted})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        String.format(java.util.Locale.US, "%.1f%%", pct),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PartitionIssuesTab(issues: List<PartitionIssue>, gaps: List<Pair<Long, Long>>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (issues.isEmpty() && gaps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No partition structure issues or geometry gaps detected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (gaps.isNotEmpty()) {
            item {
                Text(
                    "Unallocated Memory Gaps (${gaps.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(gaps) { (start, size) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Gap at 0x${java.lang.Long.toHexString(start).uppercase()}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Unallocated Space: ${PartitionEntry.formatBytes(size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (issues.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Advisories & Integrity Issues (${issues.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            items(issues) { issue ->
                val badgeColor = when (issue.severity) {
                    PartitionIssueSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                    PartitionIssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                    PartitionIssueSeverity.INFO -> MaterialTheme.colorScheme.primary
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(issue.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(4.dp), color = badgeColor.copy(alpha = 0.2f)) {
                                Text(issue.severity.name, color = badgeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(issue.description, style = MaterialTheme.typography.bodySmall)
                        if (issue.recommendation.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("-> ${issue.recommendation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionRawHeadersTab(table: PartitionTable) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Partition Header Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (table.rawHeaderFields.isEmpty()) {
                        Text("No header fields available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        for ((k, v) in table.rawHeaderFields) {
                            InfoRow(k, v)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, title: String, value: String, subtitle: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
