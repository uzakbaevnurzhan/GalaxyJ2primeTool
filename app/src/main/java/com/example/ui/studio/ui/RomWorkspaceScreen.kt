package com.example.ui.studio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.example.ui.studio.rom.RomOperationResult
import com.example.ui.studio.rom.RomUnpackEngine
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Dashboard Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardCard(
                    title = "IMPORT",
                    icon = Icons.Filled.Input,
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch("*/*") }
                )
                DashboardCard(
                    title = "WORKSPACE",
                    icon = Icons.Filled.Folder,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate("file_explorer/$projectId") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardCard(
                    title = "VALIDATE",
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Operation in progress", style = MaterialTheme.typography.titleMedium)
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
                        Text("Operation Failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(unpackError!!, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text("Project Structure", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            val workspaceDir = File(project!!.rootPath, "workspace")
            val files = workspaceDir.listFiles()?.toList() ?: emptyList()
            if (files.isEmpty()) {
                Text("Workspace is empty. Import a ROM to begin.")
            } else {
                LazyColumn {
                    items(files) { file ->
                        Text(file.name)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}
