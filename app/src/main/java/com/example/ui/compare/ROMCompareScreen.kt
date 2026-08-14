package com.example.ui.compare

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
import java.util.zip.ZipInputStream

data class CompareResult(
    val added: List<String>,
    val removed: List<String>,
    val modified: List<String>
)

class ROMCompareViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<CompareResult?>(null)
    val result: StateFlow<CompareResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var uri1: Uri? by mutableStateOf(null)
    var uri2: Uri? by mutableStateOf(null)
    var name1: String by mutableStateOf("Select ROM 1 (Base)")
    var name2: String by mutableStateOf("Select ROM 2 (Target)")

    fun getFileName(uri: Uri, context: android.content.Context): String {
        var fileName = "Unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    fun compareRoms(context: android.content.Context) {
        val u1 = uri1 ?: return
        val u2 = uri2 ?: return

        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _result.value = null

            try {
                val res = withContext(Dispatchers.IO) {
                    val map1 = mutableMapOf<String, Long>()
                    context.contentResolver.openInputStream(u1)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    map1[entry.name] = entry.crc
                                }
                                entry = zip.nextEntry
                            }
                        }
                    }

                    val map2 = mutableMapOf<String, Long>()
                    context.contentResolver.openInputStream(u2)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    map2[entry.name] = entry.crc
                                }
                                entry = zip.nextEntry
                            }
                        }
                    }

                    val added = mutableListOf<String>()
                    val removed = mutableListOf<String>()
                    val modified = mutableListOf<String>()

                    for ((file, crc) in map2) {
                        if (!map1.containsKey(file)) {
                            added.add(file)
                        } else if (map1[file] != crc) {
                            modified.add(file)
                        }
                    }

                    for (file in map1.keys) {
                        if (!map2.containsKey(file)) {
                            removed.add(file)
                        }
                    }

                    CompareResult(added, removed, modified)
                }
                _result.value = res
            } catch (e: Exception) {
                _error.value = "Comparison failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun ROMCompareScreen(navController: NavController, viewModel: ROMCompareViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher1 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uri1 = it
            viewModel.name1 = viewModel.getFileName(it, context)
        }
    }

    val launcher2 = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uri2 = it
            viewModel.name2 = viewModel.getFileName(it, context)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ROM Compare Tool", style = MaterialTheme.typography.headlineMedium)
        Text("Find added, removed, and modified files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { launcher1.launch("application/zip") }, modifier = Modifier.weight(1f)) {
                Text(viewModel.name1, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { launcher2.launch("application/zip") }, modifier = Modifier.weight(1f)) {
                Text(viewModel.name2, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.compareRoms(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.uri1 != null && viewModel.uri2 != null && !isAnalyzing
        ) {
            Text(if (isAnalyzing) "Comparing..." else "Compare ROMs")
        }

        Spacer(modifier = Modifier.height(16.dp))

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        result?.let { res ->
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                item { Text("Summary:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Added", color = com.example.ui.theme.ColorGood)
                                Text("${res.added.size}", style = MaterialTheme.typography.headlineSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Removed", color = MaterialTheme.colorScheme.error)
                                Text("${res.removed.size}", style = MaterialTheme.typography.headlineSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Modified", color = com.example.ui.theme.ColorWarning)
                                Text("${res.modified.size}", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }

                if (res.added.isNotEmpty()) {
                    item { Text("Added Files (${res.added.size}):", color = com.example.ui.theme.ColorGood, modifier = Modifier.padding(top = 16.dp)) }
                    items(res.added.take(50)) { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (res.added.size > 50) item { Text("...and ${res.added.size - 50} more") }
                }

                if (res.removed.isNotEmpty()) {
                    item { Text("Removed Files (${res.removed.size}):", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp)) }
                    items(res.removed.take(50)) { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (res.removed.size > 50) item { Text("...and ${res.removed.size - 50} more") }
                }

                if (res.modified.isNotEmpty()) {
                    item { Text("Modified Files (${res.modified.size}):", color = com.example.ui.theme.ColorWarning, modifier = Modifier.padding(top = 16.dp)) }
                    items(res.modified.take(50)) { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (res.modified.size > 50) item { Text("...and ${res.modified.size - 50} more") }
                }
            }
        }
    }
}
