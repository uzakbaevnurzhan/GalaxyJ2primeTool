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

data class BuildPropEntry(val key: String, val value: String, val category: String)

class BuildPropAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _entries = MutableStateFlow<List<BuildPropEntry>>(emptyList())
    val entries: StateFlow<List<BuildPropEntry>> = _entries

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var searchQuery by mutableStateOf("")

    fun analyzeBuildProp(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _entries.value = emptyList()

            try {
                val list = withContext(Dispatchers.IO) {
                    val temp = mutableListOf<BuildPropEntry>()
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        var line = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                                val parts = trimmed.split("=", limit = 2)
                                if (parts.size == 2) {
                                    val key = parts[0].trim()
                                    val value = parts[1].trim()
                                    temp.add(BuildPropEntry(key, value, getCategory(key)))
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                    temp.toList()
                }
                _entries.value = list
            } catch (e: Exception) {
                _error.value = "Failed to parse build.prop: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun getCategory(key: String): String {
        return when {
            key.startsWith("ro.product") -> "Product"
            key.startsWith("ro.build") -> "Build"
            key.startsWith("ro.boot") -> "Boot"
            key.startsWith("ro.vendor") -> "Vendor"
            key.startsWith("ro.hardware") -> "Hardware"
            key.startsWith("dalvik") -> "Dalvik"
            key.startsWith("persist") -> "Persist"
            key.startsWith("telephony") || key.startsWith("ro.telephony") -> "Telephony"
            key.startsWith("debug") -> "Debug"
            else -> "Other"
        }
    }
}

@Composable
fun BuildPropAnalyzerScreen(navController: NavController, viewModel: BuildPropAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val error by viewModel.error.collectAsState()

    val filteredEntries = if (viewModel.searchQuery.isEmpty()) {
        entries
    } else {
        entries.filter {
            it.key.contains(viewModel.searchQuery, ignoreCase = true) || 
            it.value.contains(viewModel.searchQuery, ignoreCase = true)
        }
    }

    val groupedEntries = filteredEntries.groupBy { it.category }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeBuildProp(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Build.prop Analyzer", style = MaterialTheme.typography.headlineMedium)
        Text("Parse and categorize Android properties.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.weight(1f),
                enabled = !isAnalyzing
            ) {
                Text(if (isAnalyzing) "Reading..." else "Select build.prop")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (entries.isNotEmpty()) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.searchQuery = it },
                label = { Text("Search properties") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            groupedEntries.forEach { (category, props) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(props) { prop ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(prop.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(prop.value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                        }
                    }
                }
            }
        }
    }
}
