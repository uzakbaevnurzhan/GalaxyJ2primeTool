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
import androidx.compose.ui.text.font.FontWeight
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

data class InitInstruction(val type: String, val content: String, val isWarning: Boolean = false)

class InitScriptAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _instructions = MutableStateFlow<List<InitInstruction>>(emptyList())
    val instructions: StateFlow<List<InitInstruction>> = _instructions

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var fileName by mutableStateOf<String?>(null)

    fun analyzeInitScript(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _instructions.value = emptyList()

            try {
                val list = withContext(Dispatchers.IO) {
                    var fName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fName = cursor.getString(nameIndex)
                        }
                    }
                    fileName = fName

                    val temp = mutableListOf<InitInstruction>()
                    var currentBlock = ""
                    var currentType = "Global"

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        var line = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                if (trimmed.startsWith("on ") || trimmed.startsWith("service ")) {
                                    if (currentBlock.isNotEmpty()) {
                                        temp.add(InitInstruction(currentType, currentBlock.trimEnd()))
                                    }
                                    currentType = if (trimmed.startsWith("on ")) "Action (${trimmed.substring(3)})" else "Service"
                                    currentBlock = trimmed + "\n"
                                } else {
                                    currentBlock += "    $trimmed\n"
                                }
                            }
                            line = reader.readLine()
                        }
                        if (currentBlock.isNotEmpty()) {
                            temp.add(InitInstruction(currentType, currentBlock.trimEnd()))
                        }
                    }

                    // Post-process warnings
                    temp.map {
                        val isWarning = it.content.contains("chmod 0777") || 
                                        it.content.contains("setenforce 0") || 
                                        it.content.contains("disabled") ||
                                        it.content.contains("seclabel u:r:su:s0")
                        it.copy(isWarning = isWarning)
                    }
                }
                _instructions.value = list
            } catch (e: Exception) {
                _error.value = "Failed to parse init script: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun InitScriptAnalyzerScreen(navController: NavController, viewModel: InitScriptAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val instructions by viewModel.instructions.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeInitScript(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Init Script Analyzer", style = MaterialTheme.typography.headlineMedium)
        Text("Parse Android init.rc and detect misconfigurations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { launcher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAnalyzing
        ) {
            Text(if (isAnalyzing) "Reading..." else "Select init.rc File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (instructions.isNotEmpty()) {
            Text("File: ${viewModel.fileName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            val warningCount = instructions.count { it.isWarning }
            if (warningCount > 0) {
                Text("Detected $warningCount potential security warnings (e.g. chmod 777, setenforce 0).", color = com.example.ui.theme.ColorWarning)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(instructions) { instruction ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = instruction.type, 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (instruction.type == "Service") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                if (instruction.isWarning) {
                                    Text("WARNING", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(instruction.content, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                        }
                    }
                }
            }
        }
    }
}
