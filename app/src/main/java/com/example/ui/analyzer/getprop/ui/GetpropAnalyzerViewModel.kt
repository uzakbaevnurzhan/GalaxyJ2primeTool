package com.example.ui.analyzer.getprop.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.getprop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PropertySortOption(val displayName: String) {
    KEY_ASC("Key (A-Z)"),
    KEY_DESC("Key (Z-A)"),
    CATEGORY("Category"),
    SOURCE("Source"),
    LINE("Line Number"),
    KEY_LENGTH("Key Length")
}

class GetpropAnalyzerViewModel : ViewModel() {

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<GetpropAnalysisResult?>(null)
    val analysisResult: StateFlow<GetpropAnalysisResult?> = _analysisResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Saved snapshots for comparison & history
    private val _snapshots = MutableStateFlow<List<GetpropSnapshot>>(emptyList())
    val snapshots: StateFlow<List<GetpropSnapshot>> = _snapshots.asStateFlow()

    // Diff and Porting Check states
    private val _diffResult = MutableStateFlow<GetpropDiffResult?>(null)
    val diffResult: StateFlow<GetpropDiffResult?> = _diffResult.asStateFlow()

    private val _portingCheckResult = MutableStateFlow<GetpropPortingCheckResult?>(null)
    val portingCheckResult: StateFlow<GetpropPortingCheckResult?> = _portingCheckResult.asStateFlow()

    // Filter and search states
    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow<GetpropCategory?>(null)
    val selectedTypeFilter = MutableStateFlow<PropertyValueType?>(null)
    val selectedPrefixFilter = MutableStateFlow<String?>(null)
    val selectedSourceFilter = MutableStateFlow<String?>(null)
    val sortOption = MutableStateFlow(PropertySortOption.KEY_ASC)
    val showDuplicatesOnly = MutableStateFlow(false)
    val showConflictsOnly = MutableStateFlow(false)

    // Snapshot selection for comparison
    val selectedSnapshotAId = MutableStateFlow<String?>(null)
    val selectedSnapshotBId = MutableStateFlow<String?>(null)

    fun analyzeFiles(context: Context, uris: List<Uri>, customName: String? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val name = customName ?: if (uris.size == 1) "File: " + (uris.first().lastPathSegment ?: "properties") else "Multi-file Snapshot (${uris.size} files)"
                val result = GetpropAnalyzer.analyzeUris(context, uris, name)
                _analysisResult.value = result
                saveSnapshot(result.snapshot)
            } catch (e: Exception) {
                _errorMessage.value = "Analysis failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun collectLiveProperties() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val result = GetpropAnalyzer.analyzeLive()
                if (result.status == AnalyzerStatus.ERROR && result.errors.isNotEmpty()) {
                    _errorMessage.value = result.errors.joinToString("; ")
                } else {
                    _analysisResult.value = result
                    saveSnapshot(result.snapshot)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Live collection failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeRawText(text: String, name: String = "Raw Properties") {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val result = GetpropAnalyzer.analyzeString(text, "raw_input", name)
                _analysisResult.value = result
                saveSnapshot(result.snapshot)
            } catch (e: Exception) {
                _errorMessage.value = "Parsing failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun compareSnapshots(snapshotAId: String, snapshotBId: String) {
        val snapA = _snapshots.value.find { it.id == snapshotAId }
        val snapB = _snapshots.value.find { it.id == snapshotBId }
        if (snapA != null && snapB != null) {
            val diff = GetpropDiffCalculator.compare(snapA, snapB)
            _diffResult.value = diff
        } else {
            _errorMessage.value = "Please select both Snapshot A and Snapshot B to compare."
        }
    }

    fun runPortingCheck(baseSnapshotId: String, portSnapshotId: String) {
        val base = _snapshots.value.find { it.id == baseSnapshotId }
        val port = _snapshots.value.find { it.id == portSnapshotId }
        if (base != null && port != null) {
            val check = GetpropPortingChecker.performCheck(base, port)
            _portingCheckResult.value = check
        } else {
            _errorMessage.value = "Please select Base ROM and Port ROM snapshots."
        }
    }

    private fun saveSnapshot(snapshot: GetpropSnapshot) {
        val currentList = _snapshots.value.toMutableList()
        // Replace if already present by id or add
        val existingIndex = currentList.indexOfFirst { it.id == snapshot.id }
        if (existingIndex >= 0) {
            currentList[existingIndex] = snapshot
        } else {
            currentList.add(0, snapshot)
        }
        _snapshots.value = currentList
        
        // Auto-select for compare if not set
        if (selectedSnapshotAId.value == null) {
            selectedSnapshotAId.value = snapshot.id
        } else if (selectedSnapshotBId.value == null && selectedSnapshotAId.value != snapshot.id) {
            selectedSnapshotBId.value = snapshot.id
        }
    }

    fun resetFilters() {
        searchQuery.value = ""
        selectedCategoryFilter.value = null
        selectedTypeFilter.value = null
        selectedPrefixFilter.value = null
        selectedSourceFilter.value = null
        showDuplicatesOnly.value = false
        showConflictsOnly.value = false
        sortOption.value = PropertySortOption.KEY_ASC
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
