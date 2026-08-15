package com.example.ui.analyzer.kernel.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.analyzer.kernel.engine.*
import com.example.ui.analyzer.kernel.model.*
import com.example.ui.analyzer.kernel.parser.SystemMapParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

sealed class AnalyzerUiState {
    object Idle : AnalyzerUiState()
    data class Analyzing(val progress: EngineProgress) : AnalyzerUiState()
    data class Success(val report: KernelCrashReport) : AnalyzerUiState()
    data class Error(val message: String) : AnalyzerUiState()
}

class KernelCrashAnalyzerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AnalyzerUiState>(AnalyzerUiState.Idle)
    val uiState: StateFlow<AnalyzerUiState> = _uiState

    private var analysisJob: Job? = null
    val systemMapParser = SystemMapParser()

    var selectedEventId by mutableStateOf<String?>(null)
    var activeTab by mutableStateOf(0) // 0: Overview, 1: Crash Events, 2: Traces, 3: Diagnostics, 4: Raw
    var searchQuery by mutableStateOf("")
    var filterSeverity by mutableStateOf<KernelSeverity?>(null)
    var filterSubsystem by mutableStateOf<KernelSubsystemType?>(null)

    var pstoreEntries by mutableStateOf<List<PstoreEntry>>(emptyList())
    var isPstoreDialogOpen by mutableStateOf(false)
    var statusMessage by mutableStateOf<String?>(null)

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _uiState.value = AnalyzerUiState.Idle
        statusMessage = "Analysis cancelled."
    }

    fun loadSystemMap(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    systemMapParser.loadFromStream(stream)
                    statusMessage = "Loaded ${systemMapParser.symbolCount} symbols from System.map."
                }
            } catch (e: Exception) {
                statusMessage = "Failed to load System.map: ${e.message}"
            }
        }
    }

    fun analyzeUri(context: Context, uri: Uri) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = AnalyzerUiState.Analyzing(EngineProgress(0L, 0L, 0, 0L, 0))
            selectedEventId = null

            try {
                var fileName = "log.txt"
                var fileSize = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                val engine = KernelCrashEngine(
                    contextLinesBeforeCount = 20,
                    contextLinesAfterCount = 50,
                    systemMap = if (systemMapParser.isLoaded) systemMapParser else null
                )

                val report = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    engine.analyzeStream(
                        inputStream = inputStream,
                        fileName = fileName,
                        totalBytes = fileSize,
                        onProgress = { prog ->
                            _uiState.value = AnalyzerUiState.Analyzing(prog)
                        }
                    )
                }

                if (report != null) {
                    _uiState.value = AnalyzerUiState.Success(report)
                    selectedEventId = report.crashEvents.firstOrNull()?.id
                } else {
                    _uiState.value = AnalyzerUiState.Error("Could not open stream for $fileName")
                }
            } catch (e: Exception) {
                _uiState.value = AnalyzerUiState.Error("Analysis error: ${e.message}")
            }
        }
    }

    fun collectLiveDmesg() {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = AnalyzerUiState.Analyzing(EngineProgress(0L, 0L, 0, 0L, 0))
            selectedEventId = null

            val dmesgRes = RootKernelCollector.collectDmesg()
            when (dmesgRes) {
                is RootCollectResult.Success -> {
                    val bytes = dmesgRes.data.toByteArray(Charsets.UTF_8)
                    val engine = KernelCrashEngine(
                        contextLinesBeforeCount = 20,
                        contextLinesAfterCount = 50,
                        systemMap = if (systemMapParser.isLoaded) systemMapParser else null
                    )
                    val report = engine.analyzeStream(
                        inputStream = ByteArrayInputStream(bytes),
                        fileName = "live_dmesg.log",
                        totalBytes = bytes.size.toLong(),
                        onProgress = { prog ->
                            _uiState.value = AnalyzerUiState.Analyzing(prog)
                        }
                    )
                    _uiState.value = AnalyzerUiState.Success(report)
                    selectedEventId = report.crashEvents.firstOrNull()?.id
                }
                is RootCollectResult.Error -> {
                    _uiState.value = AnalyzerUiState.Error(dmesgRes.message)
                }
            }
        }
    }

    fun listPstoreFiles() {
        viewModelScope.launch {
            val res = RootKernelCollector.listPstoreFiles()
            when (res) {
                is RootCollectResult.Success -> {
                    pstoreEntries = res.data
                    isPstoreDialogOpen = true
                }
                is RootCollectResult.Error -> {
                    statusMessage = res.message
                }
            }
        }
    }

    fun analyzePstoreFile(entry: PstoreEntry) {
        isPstoreDialogOpen = false
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = AnalyzerUiState.Analyzing(EngineProgress(0L, 0L, 0, 0L, 0))
            selectedEventId = null

            val contentRes = RootKernelCollector.readRootFile(entry.path)
            when (contentRes) {
                is RootCollectResult.Success -> {
                    val bytes = contentRes.data.toByteArray(Charsets.UTF_8)
                    val engine = KernelCrashEngine(
                        contextLinesBeforeCount = 20,
                        contextLinesAfterCount = 50,
                        systemMap = if (systemMapParser.isLoaded) systemMapParser else null
                    )
                    val report = engine.analyzeStream(
                        inputStream = ByteArrayInputStream(bytes),
                        fileName = entry.name,
                        totalBytes = bytes.size.toLong(),
                        onProgress = { prog ->
                            _uiState.value = AnalyzerUiState.Analyzing(prog)
                        }
                    )
                    _uiState.value = AnalyzerUiState.Success(report)
                    selectedEventId = report.crashEvents.firstOrNull()?.id
                }
                is RootCollectResult.Error -> {
                    _uiState.value = AnalyzerUiState.Error(contentRes.message)
                }
            }
        }
    }

    suspend fun exportReport(context: Context, uri: Uri, format: String) = withContext(Dispatchers.IO) {
        val state = _uiState.value
        if (state !is AnalyzerUiState.Success) return@withContext

        val report = state.report
        val content = when (format.lowercase()) {
            "json" -> KernelCrashExporter.toJson(report)
            "markdown", "md" -> KernelCrashExporter.toMarkdown(report)
            else -> KernelCrashExporter.toPlainText(report)
        }

        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(content.toByteArray(Charsets.UTF_8))
        }
    }
}
