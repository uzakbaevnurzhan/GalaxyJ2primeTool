package com.example.ui.builder

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.manager.RomBuildStudioEngine
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
fun ROMBuilderScreen(navController: NavController, initialProjectId: String? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var projects by remember { mutableStateOf<List<RomProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<RomProject?>(null) }

    var isBuilding by remember { mutableStateOf(false) }
    var buildStage by remember { mutableStateOf("") }
    var buildProgress by remember { mutableStateOf(0f) }
    var buildResult by remember { mutableStateOf<RomBuildStudioEngine.BuildResult?>(null) }
    var buildError by remember { mutableStateOf<String?>(null) }

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
                title = { Text("ROM Build Studio", fontWeight = FontWeight.Bold) },
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
            Text("Select Workspace Project to Build", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            if (projects.isEmpty()) {
                Text("No projects available in ROM Studio. Please create a project and import ROM partitions first.", color = MaterialTheme.colorScheme.error)
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
                                buildResult = null
                                buildError = null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pipeline specification card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Build Pipeline Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Prepare → Verify directory tree & source integrity", style = MaterialTheme.typography.bodySmall)
                    Text("2. Validate → Check partition structures & manifests", style = MaterialTheme.typography.bodySmall)
                    Text("3. Build → Assemble partition images & system structures", style = MaterialTheme.typography.bodySmall)
                    Text("4. Package → Stream compressed Flashable ZIP archive", style = MaterialTheme.typography.bodySmall)
                    Text("5. Post-Validate → Check zip integrity & entry count", style = MaterialTheme.typography.bodySmall)
                    Text("6. Hash → Calculate streaming SHA-256 & MD5 signatures", style = MaterialTheme.typography.bodySmall)
                    Text("7. Report → Write certified Markdown & JSON build log", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val proj = selectedProject ?: return@Button
                    isBuilding = true
                    buildResult = null
                    buildError = null
                    coroutineScope.launch {
                        val res = RomBuildStudioEngine.executeBuildPipeline(
                            context = context,
                            project = proj
                        ) { stage, prog ->
                            buildStage = stage
                            buildProgress = prog
                        }
                        res.onSuccess {
                            buildResult = it
                        }.onFailure {
                            buildError = it.message ?: "Build failed."
                        }
                        isBuilding = false
                    }
                },
                enabled = selectedProject != null && !isBuilding,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isBuilding) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Executing Build Pipeline...")
                } else {
                    Icon(Icons.Filled.BuildCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start ROM Build")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isBuilding) {
                LinearProgressIndicator(progress = { buildProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(buildStage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }

            buildError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Build Pipeline Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            buildResult?.let { res ->
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(modifier = Modifier.padding(16.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ColorGood)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Build Successful & Certified", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ColorGood)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Output File: ${res.outputFileName}", fontWeight = FontWeight.Bold)
                            Text("Size: ${res.fileSizeBytes} bytes (${"%.2f".format(res.fileSizeBytes / (1024.0 * 1024.0))} MB)")
                            Text("Duration: ${res.durationMs / 1000} seconds")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("SHA-256 Signature:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(res.sha256, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("MD5 Signature:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(res.md5, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Report: ${res.buildReportPath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        if (res.warnings.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Build Warnings:", fontWeight = FontWeight.Bold, color = ColorWarning)
                                res.warnings.forEach {
                                    Text("- $it", style = MaterialTheme.typography.bodySmall, color = ColorWarning)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
