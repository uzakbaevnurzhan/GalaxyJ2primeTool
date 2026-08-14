package com.example.ui.analyzer

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
import java.util.zip.ZipInputStream

data class RomAnalysisResult(
    val fileName: String,
    val entries: List<String>,
    val buildPropFound: Boolean,
    val bootFound: Boolean,
    val systemFound: Boolean,
    val vendorFound: Boolean,
    val buildPropContent: String?
)

class ROMAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _analysisResult = MutableStateFlow<RomAnalysisResult?>(null)
    val analysisResult: StateFlow<RomAnalysisResult?> = _analysisResult

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun analyzeRom(uri: Uri, context: android.content.Context) {
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

                    val entries = mutableListOf<String>()
                    var buildPropFound = false
                    var bootFound = false
                    var systemFound = false
                    var vendorFound = false
                    var propContent: String? = null

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zip ->
                            var entry = zip.nextEntry
                            var count = 0
                            while (entry != null && count < 2000) { // Limit for preview
                                val name = entry.name
                                entries.add(name)
                                if (name.contains("boot.img")) bootFound = true
                                if (name.contains("system")) systemFound = true
                                if (name.contains("vendor")) vendorFound = true
                                
                                if (name.endsWith("build.prop") && propContent == null) {
                                    buildPropFound = true
                                    val reader = java.io.BufferedReader(java.io.InputStreamReader(zip))
                                    var line = reader.readLine()
                                    var linesRead = 0
                                    var content = ""
                                    while (line != null && linesRead < 50) { // read top 50 lines
                                        content += "$line\n"
                                        line = reader.readLine()
                                        linesRead++
                                    }
                                    if (line != null) content += "...\n"
                                    propContent = content
                                }
                                
                                entry = zip.nextEntry
                                count++
                            }
                        }
                    }
                    RomAnalysisResult(fileName, entries, buildPropFound, bootFound, systemFound, vendorFound, propContent)
                }
                _analysisResult.value = result
            } catch (e: Exception) {
                _error.value = "Failed to analyze ROM: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun ROMAnalyzerScreen(navController: NavController, viewModel: ROMAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisResult by viewModel.analysisResult.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeRom(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ROM Analyzer", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { launcher.launch("application/zip") }, modifier = Modifier.fillMaxWidth()) {
            Text("Select ROM ZIP")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Analyzing ROM...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        analysisResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File: ${result.fileName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Contains build.prop: ${result.buildPropFound}")
                    Text("Contains boot.img: ${result.bootFound}")
                    Text("Contains system: ${result.systemFound}")
                    Text("Contains vendor: ${result.vendorFound}")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            if (result.buildPropContent != null) {
                Text("build.prop Preview:", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(text = result.buildPropContent, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Text("Files Preview (Top 2000):", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(result.entries) { entry ->
                    Text(text = entry, style = MaterialTheme.typography.bodySmall)
                    Divider()
                }
            }
        }
    }
}
