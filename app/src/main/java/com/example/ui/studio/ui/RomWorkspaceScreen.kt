package com.example.ui.studio.ui

import android.net.Uri
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
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.example.data.model.AndroidVersionInfo
import com.example.ui.common.AppTopBar
import com.example.ui.studio.rom.RomOperationResult
import com.example.ui.studio.rom.RomUnpackEngine
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomWorkspaceScreen(navController: NavController, projectId: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var project by remember { mutableStateOf<RomProject?>(null) }
    var isUnpacking by remember { mutableStateOf(false) }
    var unpackProgress by remember { mutableStateOf(0f) }
    var unpackStatus by remember { mutableStateOf("") }
    var unpackError by remember { mutableStateOf<String?>(null) }

    var showMenu by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isUnpacking = true
            unpackError = null
            coroutineScope.launch {
                val documentFile = DocumentFile.fromSingleUri(context, it)
                val fileName = documentFile?.name ?: "import.img"
                val result = RomUnpackEngine.unpack(context, project!!, it, fileName) { status, progress ->
                    unpackStatus = status
                    unpackProgress = progress
                }
                when (result) {
                    is RomOperationResult.Success -> {
                        unpackStatus = result.message
                    }
                    is RomOperationResult.Error -> {
                        unpackError = result.reason
                    }
                    else -> {}
                }
                isUnpacking = false
            }
        }
    }

    LaunchedEffect(projectId) {
        val rootPath = File(context.filesDir, "rom_studio/$projectId").absolutePath
        project = WorkspaceManager.loadProject(rootPath)
    }

    val targetVerInfo = remember(project) {
        project?.let { AndroidVersionInfo.fromProjectTarget(it.androidVersion) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = project?.name ?: "Project Workspace",
                subtitle = project?.let { "${it.device} • Target: ${it.androidVersion} [${targetVerInfo?.source?.displayName}]" } ?: "Loading...",
                onNavigateBack = { navController.popBackStack() },
                actions = {
                    IconButton(onClick = { navController.navigate("global_search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search Project")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Snapshot Manager") },
                                leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("snapshot_manager/$projectId")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ROM Patcher") },
                                leadingIcon = { Icon(Icons.Filled.AutoFixHigh, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("rom_patcher/$projectId")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("ROM Merge & Diff") },
                                leadingIcon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("rom_merge/$projectId")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (project == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Dashboard Action Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardCard(
                    title = "IMPORT ROM",
                    icon = Icons.Filled.Input,
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch("*/*") }
                )
                DashboardCard(
                    title = "FILE EXPLORER",
                    icon = Icons.Filled.Folder,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("file_explorer/$projectId") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardCard(
                    title = "VALIDATE ROM",
                    icon = Icons.Filled.CheckCircle,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val result = com.example.ui.studio.rom.RomValidator.validatePreRepack(project!!)
                        unpackStatus = "Validation: ${result.status} - ${result.messages.joinToString()}"
                        unpackProgress = 1.0f
                        isUnpacking = true
                    }
                )
                DashboardCard(
                    title = "BUILD STUDIO",
                    icon = Icons.Filled.Build,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        navController.navigate("rom_build/$projectId")
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isUnpacking) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Operation in progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { unpackProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(unpackStatus, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (unpackError != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Operation Failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                        Text(unpackError!!, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Project Structure", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val workspaceDir = File(project!!.rootPath, "workspace")
            val files = workspaceDir.listFiles()?.toList() ?: emptyList()
            if (files.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("Workspace is empty. Click 'IMPORT ROM' to unpack a ROM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(files) { file ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
