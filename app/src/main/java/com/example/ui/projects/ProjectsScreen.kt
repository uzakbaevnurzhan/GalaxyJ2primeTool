package com.example.ui.projects

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.manager.DeviceImportEngine
import com.example.data.manager.ProjectHealthChecker
import com.example.data.model.ProjectHealthReport
import com.example.data.model.ProjectHealthStatus
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
import com.example.ui.theme.ColorGood
import com.example.ui.theme.ColorWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<RomProject>>(emptyList()) }
    var healthMap by remember { mutableStateOf<Map<String, ProjectHealthReport>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeviceImportDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<RomProject?>(null) }
    var projectToDuplicate by remember { mutableStateOf<RomProject?>(null) }
    var projectToExport by remember { mutableStateOf<RomProject?>(null) }
    var renameText by remember { mutableStateOf("") }
    var duplicateText by remember { mutableStateOf("") }

    // Progress for background tasks
    var isImportingDevice by remember { mutableStateOf(false) }
    var importStage by remember { mutableStateOf("") }
    var importProgress by remember { mutableStateOf(0f) }

    fun loadProjects() {
        isLoading = true
        coroutineScope.launch {
            val list = WorkspaceManager.loadAllProjects(context)
            projects = list
            val hMap = mutableMapOf<String, ProjectHealthReport>()
            list.forEach { p ->
                hMap[p.id] = ProjectHealthChecker.evaluateProjectHealth(context, p)
            }
            healthMap = hMap
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadProjects()
    }

    // Zip Export Launcher
    val exportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let { destUri ->
            val p = projectToExport ?: return@rememberLauncherForActivityResult
            coroutineScope.launch {
                try {
                    WorkspaceManager.exportProjectAsZip(context, p, destUri) { _, _ -> }
                    Toast.makeText(context, "Project exported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Zip Import Launcher
    val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { srcUri ->
            coroutineScope.launch {
                try {
                    val imported = WorkspaceManager.importProjectFromZip(context, srcUri) { _, _ -> }
                    Toast.makeText(context, "Project '${imported.name}' imported!", Toast.LENGTH_SHORT).show()
                    loadProjects()
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project Manager", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showDeviceImportDialog = true }) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = "Import Device as Project", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { importZipLauncher.launch("application/zip") }) {
                        Icon(Icons.Filled.FolderZip, contentDescription = "Import ZIP Project")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create New Project")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isImportingDevice) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Importing Device Subsystems...", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(importStage, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { importProgress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No ROM Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Create a new project or import device partitions directly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showCreateDialog = true }) {
                                Text("New Project")
                            }
                            OutlinedButton(onClick = { showDeviceImportDialog = true }) {
                                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Device")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(projects, key = { it.id }) { project ->
                        val health = healthMap[project.id]
                        ProjectCard(
                            project = project,
                            health = health,
                            onClick = {
                                navController.navigate("rom_workspace/${project.id}")
                            },
                            onRename = {
                                renameText = project.name
                                projectToRename = project
                            },
                            onDuplicate = {
                                duplicateText = "${project.name} (Copy)"
                                projectToDuplicate = project
                            },
                            onExport = {
                                projectToExport = project
                                exportZipLauncher.launch("${project.name.replace(" ", "_")}_Export.zip")
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    WorkspaceManager.deleteProject(context, project)
                                    loadProjects()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Create Dialog
        if (showCreateDialog) {
            var newName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New ROM Project") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Project Name") },
                        placeholder = { Text("e.g. Galaxy J2 Prime LineageOS Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                WorkspaceManager.createProject(context, newName)
                                showCreateDialog = false
                                loadProjects()
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Import Device Dialog
        if (showDeviceImportDialog) {
            var devProjName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showDeviceImportDialog = false },
                title = { Text("Import Device as Project") },
                text = {
                    Column {
                        Text(
                            "Extracts available device info, properties, partition tables, kernel cmdline, SELinux, and baseline snapshot without copying heavy binaries.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = devProjName,
                            onValueChange = { devProjName = it },
                            label = { Text("Project Name") },
                            placeholder = { Text("e.g. Device_SM-G532F_Baseline") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeviceImportDialog = false
                            isImportingDevice = true
                            coroutineScope.launch {
                                try {
                                    DeviceImportEngine.importDeviceAsProject(context, devProjName) { stage, prog ->
                                        importStage = stage
                                        importProgress = prog
                                    }
                                    Toast.makeText(context, "Device successfully imported as project!", Toast.LENGTH_SHORT).show()
                                    loadProjects()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isImportingDevice = false
                                }
                            }
                        }
                    ) {
                        Text("Start Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeviceImportDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Rename Dialog
        projectToRename?.let { p ->
            AlertDialog(
                onDismissRequest = { projectToRename = null },
                title = { Text("Rename Project") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            WorkspaceManager.renameProject(context, p, renameText)
                            projectToRename = null
                            loadProjects()
                        }
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToRename = null }) { Text("Cancel") }
                }
            )
        }

        // Duplicate Dialog
        projectToDuplicate?.let { p ->
            AlertDialog(
                onDismissRequest = { projectToDuplicate = null },
                title = { Text("Duplicate Project") },
                text = {
                    OutlinedTextField(
                        value = duplicateText,
                        onValueChange = { duplicateText = it },
                        label = { Text("Duplicate Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            WorkspaceManager.duplicateProject(context, p, duplicateText)
                            projectToDuplicate = null
                            loadProjects()
                        }
                    }) {
                        Text("Duplicate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDuplicate = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: RomProject,
    health: ProjectHealthReport?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(project.createdAt))
                    Text("Created: $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as ZIP") },
                            leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Target: ${project.device} | Android ${project.androidVersion}", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(12.dp))

            health?.let { h ->
                val (badgeColor, label) = when (h.status) {
                    ProjectHealthStatus.READY -> ColorGood to "READY (${h.score}%)"
                    ProjectHealthStatus.READY_WITH_WARNINGS -> ColorWarning to "READY W/ WARNINGS (${h.score}%)"
                    ProjectHealthStatus.NOT_READY -> ColorWarning to "NOT READY (${h.score}%)"
                    ProjectHealthStatus.BLOCKED -> MaterialTheme.colorScheme.error to "BLOCKED (${h.score}%)"
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (h.status) {
                            ProjectHealthStatus.READY -> Icons.Filled.CheckCircle
                            ProjectHealthStatus.READY_WITH_WARNINGS -> Icons.Filled.Warning
                            ProjectHealthStatus.NOT_READY -> Icons.Filled.Help
                            ProjectHealthStatus.BLOCKED -> Icons.Filled.Block
                        }
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor)
                    }
                }
            }
        }
    }
}
