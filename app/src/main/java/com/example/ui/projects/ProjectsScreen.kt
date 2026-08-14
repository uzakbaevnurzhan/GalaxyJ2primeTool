package com.example.ui.projects

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectEntity
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProjectRepository
    val projects: StateFlow<List<ProjectEntity>>

    init {
        val projectDao = AppDatabase.getDatabase(application).projectDao()
        repository = ProjectRepository(projectDao)
        projects = repository.allProjects.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }

    fun addProject(project: ProjectEntity) = viewModelScope.launch {
        repository.insertProject(project)
    }

    fun deleteProject(project: ProjectEntity) = viewModelScope.launch {
        repository.deleteProject(project)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(navController: NavController, viewModel: ProjectsViewModel = viewModel()) {
    val projects by viewModel.projects.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Project")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            Text("Projects", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            if (projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No projects yet. Create one!")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projects) { project ->
                        ProjectItem(project = project, onDelete = { viewModel.deleteProject(project) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProjectDialog(
            onDismiss = { showAddDialog = false },
            onSave = { project ->
                viewModel.addProject(project)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProjectItem(project: ProjectEntity, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "Device: ${project.device}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Android: ${project.androidVersion} | Arch: ${project.architecture}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Treble: ${project.trebleStatus}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun AddProjectDialog(onDismiss: () -> Unit, onSave: (ProjectEntity) -> Unit) {
    var name by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("SM-G532F") }
    var androidVersion by remember { mutableStateOf("6.0.1") }
    var architecture by remember { mutableStateOf("arm32") }
    var trebleStatus by remember { mutableStateOf("non-Treble") }
    var baseRom by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project Name") })
                OutlinedTextField(value = device, onValueChange = { device = it }, label = { Text("Device") })
                OutlinedTextField(value = androidVersion, onValueChange = { androidVersion = it }, label = { Text("Android Version") })
                OutlinedTextField(value = architecture, onValueChange = { architecture = it }, label = { Text("Architecture") })
                OutlinedTextField(value = trebleStatus, onValueChange = { trebleStatus = it }, label = { Text("Treble Status") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ProjectEntity(
                            name = name,
                            device = device,
                            androidVersion = androidVersion,
                            architecture = architecture,
                            trebleStatus = trebleStatus,
                            baseRom = baseRom,
                            notes = notes
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
