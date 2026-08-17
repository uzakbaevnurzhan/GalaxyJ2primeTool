package com.example.ui.studio.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.manager.SnapshotManager
import com.example.data.model.DeviceSnapshot
import com.example.data.model.DiffStatus
import com.example.data.model.SnapshotDiff
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
import com.example.ui.theme.ColorGood
import com.example.ui.theme.ColorWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotManagerScreen(navController: NavController, initialProjectId: String = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<RomProject>>(emptyList()) }
    var activeProjectId by remember { mutableStateOf(initialProjectId) }
    var snapshots by remember { mutableStateOf<List<DeviceSnapshot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newSnapshotName by remember { mutableStateOf("") }
    var newSnapshotNotes by remember { mutableStateOf("") }

    var beforeSnapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var afterSnapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var diffResult by remember { mutableStateOf<SnapshotDiff?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val rootDir = File(context.filesDir, "rom_studio")
            if (rootDir.exists()) {
                val list = rootDir.listFiles()?.mapNotNull { WorkspaceManager.loadProject(it.absolutePath) } ?: emptyList()
                projects = list
                if (activeProjectId.isBlank() && list.isNotEmpty()) {
                    activeProjectId = list.first().id
                } else if (activeProjectId.isBlank()) {
                    activeProjectId = "default_project"
                }
            } else if (activeProjectId.isBlank()) {
                activeProjectId = "default_project"
            }
        }
    }

    fun loadSnapshots() {
        if (activeProjectId.isBlank()) return
        isLoading = true
        coroutineScope.launch {
            snapshots = SnapshotManager.getSnapshotsForProject(activeProjectId, context)
            if (snapshots.size >= 2 && beforeSnapshot == null && afterSnapshot == null) {
                afterSnapshot = snapshots[0]
                beforeSnapshot = snapshots[1]
            } else if (snapshots.isNotEmpty() && beforeSnapshot == null) {
                beforeSnapshot = snapshots[0]
            }
            isLoading = false
        }
    }

    LaunchedEffect(activeProjectId) {
        if (activeProjectId.isNotBlank()) {
            loadSnapshots()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device & Project Snapshots", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadSnapshots() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Create Snapshot")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (snapshots.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No Snapshots Created", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Capture current device/project state to compare changes before and after patching.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture Initial Snapshot")
                        }
                    }
                }
            } else {
                // Comparison selector
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Compare Snapshots (BEFORE vs AFTER)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedCard(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("BEFORE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(beforeSnapshot?.name ?: "None selected", fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                            OutlinedCard(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("AFTER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(afterSnapshot?.name ?: "None selected", fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val b = beforeSnapshot
                                val a = afterSnapshot
                                if (b != null && a != null) {
                                    diffResult = SnapshotManager.compareSnapshots(b, a)
                                } else {
                                    Toast.makeText(context, "Select both before and after snapshots", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = beforeSnapshot != null && afterSnapshot != null
                        ) {
                            Icon(Icons.Filled.CompareArrows, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compute State Diff")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                diffResult?.let { diff ->
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(12.dp)) {
                            item {
                                Text("Diff Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(diff.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (diff.propertyDiffs.isNotEmpty()) {
                                item {
                                    Text("System Property Diffs (${diff.propertyDiffs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                                items(diff.propertyDiffs) { pDiff ->
                                    val statusColor = when (pDiff.status) {
                                        DiffStatus.ADDED -> ColorGood
                                        DiffStatus.REMOVED -> MaterialTheme.colorScheme.error
                                        DiffStatus.MODIFIED -> ColorWarning
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(pDiff.key, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("${pDiff.valueBefore ?: "null"} → ${pDiff.valueAfter ?: "null"}", color = statusColor, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            if (diff.partitionDiffs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Partition Diffs (${diff.partitionDiffs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                                items(diff.partitionDiffs) { partDiff ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(partDiff.key, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        Text("${partDiff.valueBefore ?: "null"} → ${partDiff.valueAfter ?: "null"}", color = ColorWarning, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text("Available Snapshots (${snapshots.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshots) { snap ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(snap.name, fontWeight = FontWeight.Bold)
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(snap.timestamp))
                                    Text("Created: $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text("Android ${snap.androidVersion} | Kernel: ${snap.kernelVersion.take(24)}... | ${snap.partitions.size} partitions", style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                    IconButton(onClick = { beforeSnapshot = snap }) {
                                        Icon(Icons.Filled.FirstPage, contentDescription = "Set as Before", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { afterSnapshot = snap }) {
                                        Icon(Icons.Filled.LastPage, contentDescription = "Set as After", tint = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Capture Device Snapshot") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newSnapshotName,
                            onValueChange = { newSnapshotName = it },
                            label = { Text("Snapshot Label") },
                            placeholder = { Text("e.g. Clean Stock State") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newSnapshotNotes,
                            onValueChange = { newSnapshotNotes = it },
                            label = { Text("Notes / Observations") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            SnapshotManager.createLiveDeviceSnapshot(
                                projectId = activeProjectId,
                                name = newSnapshotName,
                                notes = newSnapshotNotes,
                                context = context
                            )
                            showCreateDialog = false
                            newSnapshotName = ""
                            newSnapshotNotes = ""
                            loadSnapshots()
                        }
                    }) {
                        Text("Capture Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
