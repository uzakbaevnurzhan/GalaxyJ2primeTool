package com.example.ui.compare

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.data.manager.RomMergeEngine
import com.example.data.model.MergePlan
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
import com.example.ui.theme.ColorGood
import com.example.ui.theme.ColorWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomMergeScreen(navController: NavController, initialProjectId: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<RomProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<RomProject?>(null) }

    var targetUri by remember { mutableStateOf<Uri?>(null) }
    var targetName by remember { mutableStateOf("Select Target ROM Archive (.zip)") }

    var mergePlan by remember { mutableStateOf<MergePlan?>(null) }
    var isPlanning by remember { mutableStateOf(false) }
    var isMerging by remember { mutableStateOf(false) }
    var mergeProgress by remember { mutableStateOf(0f) }
    var mergeStage by remember { mutableStateOf("") }
    var mergeResult by remember { mutableStateOf<String?>(null) }
    var mergeError by remember { mutableStateOf<String?>(null) }

    var forceOverwriteConflicts by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            targetUri = it
            var dName = "Target ROM.zip"
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) dName = cursor.getString(idx)
                }
            }
            targetName = dName
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val rootDir = File(context.filesDir, "rom_studio")
            if (rootDir.exists()) {
                val list = rootDir.listFiles()?.mapNotNull { WorkspaceManager.loadProject(it.absolutePath) } ?: emptyList()
                projects = list
                if (initialProjectId != null) {
                    selectedProject = list.find { it.id == initialProjectId }
                } else if (list.isNotEmpty()) {
                    selectedProject = list.first()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ROM Selective Merge Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Project Selector
            Text("1. Base Workspace Project", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (projects.isEmpty()) {
                Text("No ROM projects found. Please create a project first.", color = MaterialTheme.colorScheme.error)
            } else {
                var expanded by remember { mutableStateOf(false) }
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { expanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedProject?.name ?: "Select Project", fontWeight = FontWeight.Medium)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    projects.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                selectedProject = p
                                expanded = false
                                mergePlan = null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target ROM Picker
            Text("2. Target Donor ROM (.zip)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = { filePicker.launch("application/zip") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(targetName, maxLines = 1)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Plan Merge Button
            Button(
                onClick = {
                    val proj = selectedProject ?: return@Button
                    val uri = targetUri ?: return@Button
                    isPlanning = true
                    mergeResult = null
                    mergeError = null
                    coroutineScope.launch {
                        try {
                            val plan = RomMergeEngine.createMergePlan(context, proj, uri, targetName, emptyList())
                            mergePlan = plan
                        } catch (e: Exception) {
                            mergeError = e.message
                        } finally {
                            isPlanning = false
                        }
                    }
                },
                enabled = selectedProject != null && targetUri != null && !isPlanning && !isMerging,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPlanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing Conflicts & ABI...")
                } else {
                    Icon(Icons.Filled.CallMerge, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze & Create Merge Plan")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Merge Plan Display
            mergePlan?.let { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        item {
                            Text("Merge Plan Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Files to merge: ${plan.selectedFiles.size}")
                            Text("Conflicts detected: ${plan.conflicts.size}", color = if (plan.conflicts.isNotEmpty()) ColorWarning else ColorGood)
                            Text("ABI Compatible: ${if (plan.abiCompatible) "YES (arm32 aligned)" else "WARNING (64-bit libraries detected)"}")
                        }

                        if (plan.dependencyWarnings.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Warnings:", fontWeight = FontWeight.Bold, color = ColorWarning)
                                plan.dependencyWarnings.forEach {
                                    Text("- $it", style = MaterialTheme.typography.bodySmall, color = ColorWarning)
                                }
                            }
                        }

                        if (plan.conflicts.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = forceOverwriteConflicts,
                                        onCheckedChange = { forceOverwriteConflicts = it }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Force overwrite conflicted files", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Conflict Details:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                            items(plan.conflicts) { conflict ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(conflict.relativePath, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Text(conflict.conflictReason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val proj = selectedProject ?: return@Button
                                    val uri = targetUri ?: return@Button
                                    isMerging = true
                                    mergeError = null
                                    mergeResult = null
                                    coroutineScope.launch {
                                        val res = RomMergeEngine.executeMerge(
                                            context = context,
                                            project = proj,
                                            targetRomUri = uri,
                                            plan = plan,
                                            forceOverwriteConflicts = forceOverwriteConflicts
                                        ) { stage, prog ->
                                            mergeStage = stage
                                            mergeProgress = prog
                                        }
                                        res.onSuccess {
                                            mergeResult = it
                                        }.onFailure {
                                            mergeError = it.message
                                        }
                                        isMerging = false
                                    }
                                },
                                enabled = !isMerging,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(if (isMerging) "Executing Safe Merge..." else "Execute Merge (with Rollback Snapshot)")
                            }
                        }
                    }
                }
            }

            if (isMerging) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = { mergeProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(mergeStage, style = MaterialTheme.typography.bodySmall)
            }

            mergeResult?.let { res ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = ColorGood.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(res, color = ColorGood, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            mergeError?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
