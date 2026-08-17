package com.example.patcher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomPatcherScreen(
    navController: NavController,
    projectId: String? = null,
    viewModel: RomPatcherViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        if (projectId != null) {
            viewModel.loadProject(projectId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ROM Patcher & Configuration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.currentPlan != null) {
                        IconButton(onClick = { viewModel.runDryRun() }) {
                            Icon(Icons.Filled.Save, "Dry Run")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.currentPlan != null) {
                FloatingActionButton(onClick = { viewModel.showAddOperationDialog = true }) {
                    Icon(Icons.Filled.Add, "Add Operation")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (projectId == null) {
            // Project selection state
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Please select a project first from the Workspace.")
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                if (state.currentPlan == null) {
                    Button(onClick = { viewModel.createNewPlan("New Patch Plan") }) {
                        Text("Create New Patch Plan")
                    }
                } else {
                    val plan = state.currentPlan!!
                    Text("Current Plan: ${plan.name}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (plan.enabledOperations.isEmpty()) {
                        Text("No operations in this plan. Click '+' to add one.")
                    } else {
                        // List operations
                        plan.enabledOperations.forEach { op ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(op.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Type: ${op.type} | Target: ${op.targetPath}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.applyPatchPlan() }, modifier = Modifier.fillMaxWidth()) {
                        Text("APPLY PATCH PLAN (TRANSACTION)")
                    }
                }
                
                if (state.executionResult != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Execution Result: ${state.executionResult?.status}")
                            Text("Message: ${state.executionResult?.errorMessage ?: "Success"}")
                        }
                    }
                }
            }
        }
    }
}
