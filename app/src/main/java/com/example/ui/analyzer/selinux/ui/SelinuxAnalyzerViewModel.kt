package com.example.ui.analyzer.selinux.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.analyzer.selinux.engine.RootCollectionResult
import com.example.ui.analyzer.selinux.engine.RootLogCollector
import com.example.ui.analyzer.selinux.engine.SelinuxAnalyzerEngine
import com.example.ui.analyzer.selinux.engine.SelinuxExporter
import com.example.ui.analyzer.selinux.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

enum class AvcSortOption {
    COUNT_DESC,
    TIMESTAMP,
    SOURCE,
    TARGET,
    PERMISSION
}

enum class SelinuxFilterType {
    ALL,
    ONLY_DENIED,
    ONLY_PERMISSIVE,
    ONLY_VENDOR,
    ONLY_SYSTEM,
    ONLY_FRAMEWORK
}

data class SelinuxUiState(
    val isLoading: Boolean = false,
    val progressPercent: Float = 0f,
    val progressStatus: String = "",
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val result: SelinuxAnalysisResult? = null,
    val errorMessage: String? = null,
    
    // AVC Filter and Search
    val searchQuery: String = "",
    val sortOption: AvcSortOption = AvcSortOption.COUNT_DESC,
    val filterType: SelinuxFilterType = SelinuxFilterType.ALL,
    val selectedSourceFilter: String? = null,
    val selectedTargetFilter: String? = null,
    val selectedClassFilter: String? = null,
    val selectedPermissionFilter: String? = null,

    // Root collection
    val isRootLoading: Boolean = false,
    val rootMessage: String? = null
)

class SelinuxAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SelinuxUiState())
    val uiState: StateFlow<SelinuxUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun analyzeFile(uri: Uri) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val context = getApplication<Application>()
            var fName = "selinux_input"
            var fSize = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nIdx >= 0) fName = cursor.getString(nIdx)
                        if (sIdx >= 0) fSize = cursor.getLong(sIdx)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    progressPercent = 0f,
                    progressStatus = "Opening $fName...",
                    fileName = fName,
                    fileSize = fSize,
                    errorMessage = null,
                    result = null
                )

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val analysisResult = SelinuxAnalyzerEngine.analyzeStream(
                        inputStream = inputStream,
                        totalBytes = fSize,
                        fileName = fName,
                        onProgress = { _, _, pct, status ->
                            _uiState.value = _uiState.value.copy(
                                progressPercent = pct,
                                progressStatus = status
                            )
                        }
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = analysisResult,
                        progressStatus = "Analysis completed"
                    )
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Could not open stream for chosen file."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Analysis error: ${e.message}"
                )
            }
        }
    }

    fun analyzeDirectText(text: String, title: String = "Raw Text / Live Log") {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                progressPercent = 0f,
                progressStatus = "Analyzing text...",
                fileName = title,
                fileSize = text.length.toLong(),
                errorMessage = null,
                result = null
            )

            try {
                val inputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
                val analysisResult = SelinuxAnalyzerEngine.analyzeStream(
                    inputStream = inputStream,
                    totalBytes = text.length.toLong(),
                    fileName = title
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = analysisResult,
                    progressStatus = "Completed"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Analysis error: ${e.message}"
                )
            }
        }
    }

    fun collectRootLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRootLoading = true,
                rootMessage = "Checking root permission and gathering logs..."
            )

            when (val res = RootLogCollector.collectLiveLogs()) {
                is RootCollectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isRootLoading = false,
                        rootMessage = "Collected ${res.lineCount} lines (getenforce: ${res.getenforce})"
                    )
                    analyzeDirectText(res.logs, "Live Root AVC Logs (getenforce: ${res.getenforce})")
                }
                is RootCollectionResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRootLoading = false,
                        rootMessage = null,
                        errorMessage = "Root Collection Failed: ${res.message}"
                    )
                }
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            progressStatus = "Cancelled by user"
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setSortOption(sort: AvcSortOption) {
        _uiState.value = _uiState.value.copy(sortOption = sort)
    }

    fun setFilterType(filter: SelinuxFilterType) {
        _uiState.value = _uiState.value.copy(filterType = filter)
    }

    fun setSourceFilter(source: String?) {
        _uiState.value = _uiState.value.copy(selectedSourceFilter = source)
    }

    fun setTargetFilter(target: String?) {
        _uiState.value = _uiState.value.copy(selectedTargetFilter = target)
    }

    fun setClassFilter(tclass: String?) {
        _uiState.value = _uiState.value.copy(selectedClassFilter = tclass)
    }

    fun setPermissionFilter(perm: String?) {
        _uiState.value = _uiState.value.copy(selectedPermissionFilter = perm)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            sortOption = AvcSortOption.COUNT_DESC,
            filterType = SelinuxFilterType.ALL,
            selectedSourceFilter = null,
            selectedTargetFilter = null,
            selectedClassFilter = null,
            selectedPermissionFilter = null
        )
    }

    fun exportReport(format: String): String {
        val result = _uiState.value.result ?: return "No analysis available"
        return when (format.uppercase()) {
            "JSON" -> SelinuxExporter.exportToJson(result)
            "MARKDOWN", "MD" -> SelinuxExporter.exportToMarkdown(result)
            else -> SelinuxExporter.exportToText(result)
        }
    }
}
