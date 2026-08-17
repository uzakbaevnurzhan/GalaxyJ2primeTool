package com.example.ui.analyzer.flash.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.ui.analyzer.flash.*
import com.example.ui.analyzer.partition.PartitionEntry
import com.example.ui.analyzer.partition.PartitionIssue
import com.example.ui.analyzer.partition.PartitionIssueSeverity
import com.example.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FlashPrecheckViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<FlashPrecheckResult?>(null)
    val result: StateFlow<FlashPrecheckResult?> = _result

    private val _selectedProfile = MutableStateFlow(DeviceProfile.GALAXY_J2_PRIME)
    val selectedProfile: StateFlow<DeviceProfile> = _selectedProfile

    private val _loadedFiles = MutableStateFlow<List<File>>(emptyList())
    val loadedFiles: StateFlow<List<File>> = _loadedFiles

    private val analyzer = FlashPrecheckAnalyzer()

    fun setProfile(profile: DeviceProfile) {
        _selectedProfile.value = profile
        recalculate()
    }

    fun addFiles(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            val newFiles = withContext(Dispatchers.IO) {
                val list = mutableListOf<File>()
                for (uri in uris) {
                    val name = getFileNameFromUri(context, uri)
                    val temp = File(context.cacheDir, "flash_cand_${System.currentTimeMillis()}_$name")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    list.add(temp)
                }
                list
            }
            _loadedFiles.value = _loadedFiles.value + newFiles
            recalculate()
            _isAnalyzing.value = false
        }
    }

    fun loadSampleJ2PrimeFirmwareSet(context: Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            withContext(Dispatchers.IO) {
                // Create lightweight placeholder references without huge heap allocations
                val files = mutableListOf<File>()
                val bootFile = File(context.cacheDir, "boot_a11_g532.img")
                if (!bootFile.exists()) bootFile.writeBytes(ByteArray(1024))

                val recoveryFile = File(context.cacheDir, "twrp_3.7_g532f.img")
                if (!recoveryFile.exists()) recoveryFile.writeBytes(ByteArray(1024))

                val systemFile = File(context.cacheDir, "system_a11_arm32.img")
                if (!systemFile.exists()) systemFile.writeBytes(ByteArray(1024))

                val logoFile = File(context.cacheDir, "logo.bin")
                if (!logoFile.exists()) logoFile.writeBytes(ByteArray(1024))

                files.addAll(listOf(bootFile, recoveryFile, systemFile, logoFile))
                _loadedFiles.value = files
            }
            _selectedProfile.value = DeviceProfile.GALAXY_J2_PRIME
            recalculate()
            _isAnalyzing.value = false
        }
    }

    fun clearFiles() {
        _loadedFiles.value = emptyList()
        recalculate()
    }

    private fun recalculate() {
        try {
            val res = analyzer.performPrecheck(
                partitionTable = null,
                imageFiles = _loadedFiles.value,
                targetProfile = _selectedProfile.value
            )
            _result.value = res
        } catch (e: Exception) {
            _result.value = null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "firmware_image.img"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashPrecheckScreen(
    navController: NavController,
    viewModel: FlashPrecheckViewModel = viewModel()
) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val loadedFiles by viewModel.loadedFiles.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(context, uris)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Safe Flash Pre-Check",
                subtitle = "Target: ${selectedProfile.marketingName}",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = {
                        result?.let {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Flash Pre-check Report", it.detailedReport))
                            Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report")
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
            // Profile & Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { multiFilePicker.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Select Images")
                }

                OutlinedButton(
                    onClick = { viewModel.loadSampleJ2PrimeFirmwareSet(context) }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Load Sample Set")
                }

                if (loadedFiles.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearFiles() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Images", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Target Profile Selection Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeviceProfile.ALL_PROFILES.forEach { prof ->
                    FilterChip(
                        selected = selectedProfile.id == prof.id,
                        onClick = { viewModel.setProfile(prof) },
                        label = { Text(prof.modelName) }
                    )
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
                        Text("Verifying partition bounds & ROM compatibility...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (loadedFiles.isEmpty()) {
                // Empty state - safe and clean
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Safe Flash Pre-Check Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No images or partition tables currently loaded. Select firmware image files (boot.img, recovery.img, system.img) or load sample presets to verify flash compatibility against ${selectedProfile.modelName}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { multiFilePicker.launch("*/*") }) {
                                    Text("Select Images")
                                }
                                OutlinedButton(onClick = { viewModel.loadSampleJ2PrimeFirmwareSet(context) }) {
                                    Text("Load Sample Set")
                                }
                            }
                        }
                    }
                }
            } else if (result != null) {
                val res = result!!
                val tabs = listOf("Verdict & Plan", "Safety Risks (${res.issues.size})", "Checklist", "Compatibility")

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
                    0 -> FlashPlanTab(res)
                    1 -> FlashRisksTab(res.issues)
                    2 -> FlashChecklistTab(res.preFlashChecklist)
                    3 -> FlashCompatibilityTab(selectedProfile)
                }
            }
        }
    }
}

@Composable
private fun FlashPlanTab(result: FlashPrecheckResult) {
    val plan = result.plan
    val verdictColor = when (result.verdict) {
        FlashVerdict.SAFE_TO_FLASH -> MaterialTheme.colorScheme.primary
        FlashVerdict.WARNING_CAUTION -> MaterialTheme.colorScheme.tertiary
        FlashVerdict.UNSAFE_DO_NOT_FLASH -> MaterialTheme.colorScheme.error
        FlashVerdict.FATAL_SIZE_MISMATCH -> MaterialTheme.colorScheme.error
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Verdict Card
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
                            "Safety Pre-Check Verdict",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = verdictColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                result.verdict.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = verdictColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
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

        // Partition Action Plan List
        item {
            Text(
                "Partition Flashing Matrix (${plan.items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(plan.items) { item ->
            FlashPartitionCard(item)
        }
    }
}

@Composable
private fun FlashPartitionCard(item: FlashPartitionItem) {
    var expanded by remember { mutableStateOf(false) }

    val actionColor = when (item.action) {
        FlashAction.FLASH -> MaterialTheme.colorScheme.primary
        FlashAction.PROTECT -> MaterialTheme.colorScheme.error
        FlashAction.WARNING_OVERWRITE -> MaterialTheme.colorScheme.error
        FlashAction.ERASE -> MaterialTheme.colorScheme.tertiary
        FlashAction.SKIP -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.partition.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Max Cap: ${item.maxPartitionSizeFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = actionColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        item.action.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = actionColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (item.matchedImageFile != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Image: ${item.sizeFormatted} (${item.utilizationPercent}% of partition)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        item.riskLevel.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (item.riskLevel == FlashRiskLevel.SAFE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (item.utilizationPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (item.isSizeValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                if (item.matchedImageFile != null) {
                    Text("Source: ${item.matchedImageFile}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Text("Detected Image Format: ${item.detectedFormat.displayName}", style = MaterialTheme.typography.bodySmall)
                }
                for (note in item.validationNotes) {
                    Text("* $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FlashRisksTab(issues: List<PartitionIssue>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (issues.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No flashing risk factors or partition overflows detected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
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
                            Text("-> Recommendation: ${issue.recommendation}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashChecklistTab(checklist: List<String>) {
    val checkedStates = remember(checklist) {
        mutableStateMapOf<Int, Boolean>()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Pre-Flash Readiness Checklist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Verify every condition before attempting to flash via Odin, SP Flash Tool, or Fastboot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        items(checklist.indices.toList()) { idx ->
            val text = checklist[idx]
            val isChecked = checkedStates[idx] ?: false

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checkedStates[idx] = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FlashCompatibilityTab(profile: DeviceProfile) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${profile.marketingName} Porting Specs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    InfoRowItem("Model", profile.modelName)
                    InfoRowItem("SoC / Chipset", profile.chipset)
                    InfoRowItem("Architecture", profile.arch.uppercase())
                    InfoRowItem("RAM Capacity", PartitionEntry.formatBytes(profile.totalRamBytes))
                    InfoRowItem("Stock OS", profile.stockAndroidVersion)
                    InfoRowItem("Stock Kernel", profile.stockKernelVersion)
                    InfoRowItem("Project Treble", if (profile.isTreble) "Supported" else "Non-Treble (Legacy)")
                    InfoRowItem("Dynamic Partitions", if (profile.hasDynamicPartitions) "Yes" else "No (Static Partitions)")
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Galaxy J2 Prime Android 11 Checklist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. **Binder IPC**: Linux 3.18 kernel uses 32-bit Binder. Android 11 standard framework expects 64-bit binder IPC unless BINDER_IPC_32BIT or backported binder drivers are active.\n\n" +
                        "2. **RAM Constraints**: Device has 1.5GB RAM. Ensure `ro.config.low_ram=true` is set in build.prop.\n\n" +
                        "3. **Audio / Camera HALs**: 32-bit vendor camera and audio HALs require 32-bit HIDL wrappers or legacy binder services.\n\n" +
                        "4. **SELinux Policy**: Enforcing mode on Android 11 requires updated sepolicy rules for mediatek HALs.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRowItem(label: String, value: String) {
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
