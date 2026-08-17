package com.example.patcher.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.patcher.*
import com.example.ui.studio.workspace.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class RomPatcherUiState(
    val isLoading: Boolean = false,
    val projectId: String? = null,
    val workspaceRoot: File? = null,
    val currentPlan: PatchPlan? = null,
    val executionResult: PatchExecutionResult? = null,
    val dryRunReport: DryRunReport? = null
)

class RomPatcherViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RomPatcherUiState())
    val uiState: StateFlow<RomPatcherUiState> = _uiState.asStateFlow()
    
    var showAddOperationDialog by mutableStateOf(false)

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val root = WorkspaceManager.getProjectDir(getApplication(), projectId)
            // Load existing plan or stay empty
            _uiState.update { it.copy(isLoading = false, projectId = projectId, workspaceRoot = root) }
        }
    }

    fun createNewPlan(name: String) {
        val pid = _uiState.value.projectId ?: return
        val plan = PatchPlan(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Created from UI",
            projectId = pid
        )
        _uiState.update { it.copy(currentPlan = plan) }
    }

    fun runDryRun() {
        val root = _uiState.value.workspaceRoot ?: return
        val plan = _uiState.value.currentPlan ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val report = PatchValidator.dryRun(root, plan)
            _uiState.update { it.copy(isLoading = false, dryRunReport = report) }
        }
    }

    fun applyPatchPlan() {
        val root = _uiState.value.workspaceRoot ?: return
        val plan = _uiState.value.currentPlan ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, executionResult = null) }
            val result = PatchTransaction.runTransaction(root, plan) { progress ->
                // Can expose progress to UI
            }
            _uiState.update { it.copy(isLoading = false, executionResult = result) }
        }
    }
}
