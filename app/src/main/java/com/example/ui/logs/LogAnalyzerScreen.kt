package com.example.ui.logs

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class LogAnalysisResult(
    val fileName: String,
    val criticalCount: Int,
    val highCount: Int,
    val warningCount: Int,
    val lines: List<String>
)

class LogAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _analysisResult = MutableStateFlow<LogAnalysisResult?>(null)
    val analysisResult: StateFlow<LogAnalysisResult?> = _analysisResult

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun analyzeLog(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _analysisResult.value = null

            try {
                val result = withContext(Dispatchers.IO) {
                    var fileName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                        }
                    }

                    var criticalCount = 0
                    var highCount = 0
                    var warningCount = 0
                    val linesToDisplay = mutableListOf<String>()

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            var line = reader.readLine()
                            var count = 0
                            while (line != null && count < 2000) { // Limit
                                if (line.contains("FATAL EXCEPTION") || line.contains("SIGSEGV")) criticalCount++
                                else if (line.contains("cannot locate symbol") || line.contains("library not found")) highCount++
                                else if (line.contains("avc: denied")) warningCount++
                                
                                if (line.contains("FATAL") || line.contains("Exception") || line.contains("Error") || line.contains("denied")) {
                                    linesToDisplay.add(line)
                                }
                                line = reader.readLine()
                                count++
                            }
                        }
                    }
                    LogAnalysisResult(fileName, criticalCount, highCount, warningCount, linesToDisplay)
                }
                _analysisResult.value = result
            } catch (e: Exception) {
                _error.value = "Failed to analyze log: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun LogAnalyzerScreen(navController: NavController, viewModel: LogAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeLog(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Log Analyzer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { launcher.launch("text/plain") }, modifier = Modifier.fillMaxWidth()) {
            Text("Select Log File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Analyzing Log...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        analysisResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File: ${result.fileName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("CRITICAL: ${result.criticalCount} errors", color = MaterialTheme.colorScheme.error)
                    Text("HIGH: ${result.highCount} errors", color = MaterialTheme.colorScheme.primary)
                    Text("WARNING: ${result.warningCount} warnings", color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            var aiAnalysis by remember { mutableStateOf<String?>(null) }
            var isAiAnalyzing by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            Button(
                onClick = {
                    isAiAnalyzing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val assistant = com.example.ai.ROMAssistant()
                        val response = assistant.analyzeLog(result.lines.joinToString("\n"), "Samsung Galaxy J2 Prime (SM-G532F)")
                        withContext(Dispatchers.Main) {
                            aiAnalysis = response
                            isAiAnalyzing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAiAnalyzing
            ) {
                Text(if (isAiAnalyzing) "Analyzing with AI..." else "Explain with AI")
            }

            if (aiAnalysis != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AI Analysis", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(aiAnalysis!!)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Filtered Error Lines:", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(result.lines) { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    Divider()
                }
            }
        }
    }
}
