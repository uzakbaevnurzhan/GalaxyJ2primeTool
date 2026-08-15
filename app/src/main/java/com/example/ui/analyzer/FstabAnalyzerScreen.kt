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

data class FstabEntry(
    val device: String,
    val mountPoint: String,
    val fileSystem: String,
    val flags: String,
    val options: String,
    val isWarning: Boolean
)

class FstabAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _entries = MutableStateFlow<List<FstabEntry>>(emptyList())
    val entries: StateFlow<List<FstabEntry>> = _entries

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var fileName by mutableStateOf<String?>(null)

    fun analyzeFstab(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _entries.value = emptyList()

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

                    val temp = mutableListOf<FstabEntry>()

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        var line = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                val parts = trimmed.split("\\s+".toRegex())
                                if (parts.size >= 5) {
                                    val flags = parts[3]
                                    val isWarning = flags.contains("encryptable") || flags.contains("forceencrypt") == false
                                    temp.add(
                                        FstabEntry(
                                            device = parts[0],
                                            mountPoint = parts[1],
                                            fileSystem = parts[2],
                                            flags = flags,
                                            options = parts[4],
                                            isWarning = isWarning
                                        )
                                    )
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                    temp.toList()
                }
                _entries.value = list
            } catch (e: Exception) {
                _error.value = "Failed to parse fstab: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun FstabAnalyzerScreen(navController: NavController, viewModel: FstabAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeFstab(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fstab Analyzer", style = MaterialTheme.typography.headlineMedium)
        Text("Analyze fstab partitions, flags, and encryption.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { launcher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAnalyzing
        ) {
            Text(if (isAnalyzing) "Reading..." else "Select fstab File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (entries.isNotEmpty()) {
            Text("File: ${viewModel.fileName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(entries) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Mount Point: ${entry.mountPoint}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Device: ${entry.device}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                            Text("File System: ${entry.fileSystem}", style = MaterialTheme.typography.bodySmall)
                            Text("Flags: ${entry.flags}", style = MaterialTheme.typography.bodySmall, color = if (entry.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Text("Options: ${entry.options}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
