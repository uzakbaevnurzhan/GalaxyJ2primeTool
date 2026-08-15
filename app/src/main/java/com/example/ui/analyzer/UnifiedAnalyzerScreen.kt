package com.example.ui.analyzer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.core.AnalyzerResult
import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.getprop.GetpropAnalyzer
import com.example.ui.analyzer.kernel.KernelCrashAnalyzer
import com.example.ui.analyzer.selinux.SelinuxAnalyzer
import com.example.ui.analyzer.sparse.SparseImageParser
import com.example.ui.analyzer.text.TextHexViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnifiedAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<AnalyzerResult?>(null)
    val result: StateFlow<AnalyzerResult?> = _result

    var fileName by mutableStateOf<String?>(null)
    var currentUri by mutableStateOf<Uri?>(null)
    
    // Pagination for text/hex viewers
    var currentOffset by mutableStateOf(0L)
    val chunkSizeText = 1024L * 64L
    val chunkSizeHex = 1024L * 4L

    fun analyzeFile(uri: Uri, context: android.content.Context, toolType: String, offset: Long = 0L) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            
            if (offset == 0L) {
                _result.value = null
                currentUri = uri
                currentOffset = 0L
            }

            try {
                if (uri != Uri.EMPTY) {
                    var fName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fName = cursor.getString(nameIndex)
                        }
                    }
                    fileName = fName
                }

                val output = when (toolType) {
                    "text_viewer" -> TextHexViewer.readTextChunk(context, uri, offset, chunkSizeText.toInt())
                    "hex_viewer" -> TextHexViewer.readHexChunk(context, uri, offset, chunkSizeHex.toInt())
                    "sparse_analyzer" -> SparseImageParser.parse(context, uri)
                    "selinux_analyzer" -> SelinuxAnalyzer.parse(context, uri)
                    "kernel_crash" -> KernelCrashAnalyzer.parse(context, uri)
                    "getprop_analyzer" -> {
                        fileName = "Live System Properties"
                        GetpropAnalyzer.parseLive()
                    }
                    else -> AnalyzerResult(AnalyzerStatus.UNSUPPORTED, "Unknown Tool", "Tool type '$toolType' is not fully implemented.")
                }
                
                _result.value = output
                currentOffset = offset
                
            } catch (e: Exception) {
                _result.value = AnalyzerResult(AnalyzerStatus.ERROR, "Critical Error", e.message ?: "Unknown error")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun UnifiedAnalyzerScreen(navController: NavController, toolType: String, viewModel: UnifiedAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()

    val toolName = toolType.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeFile(it, context, toolType, 0L) }
    }
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let { destUri ->
            result?.let { res ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            out.write("--- SUMMARY ---\n${res.summary}\n\n--- DETAILS ---\n${res.details}".toByteArray())
                        }
                        withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "Exported Successfully", android.widget.Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { android.widget.Toast.makeText(context, "Export Failed", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        }
    }

    LaunchedEffect(toolType) {
        if (toolType == "getprop_analyzer") {
            viewModel.analyzeFile(Uri.EMPTY, context, toolType)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(toolName, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (toolType != "getprop_analyzer") {
            Button(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAnalyzing
            ) {
                Text(if (isAnalyzing) "Processing..." else "Select File")
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else if (isAnalyzing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        result?.let { res ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Target: ${viewModel.fileName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (res.status != AnalyzerStatus.ERROR) {
                    OutlinedButton(onClick = { exportLauncher.launch("${toolType}_report.txt") }) {
                        Text("Export")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Status: ${res.status.name}", color = when(res.status) {
                AnalyzerStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                AnalyzerStatus.WARNING -> androidx.compose.ui.graphics.Color(0xFFFFA000)
                AnalyzerStatus.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SelectionContainer {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        item {
                            Text(res.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(res.details, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
            }
            
            // Pagination controls for viewers
            if (toolType == "text_viewer" || toolType == "hex_viewer") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { 
                            val step = if (toolType == "text_viewer") viewModel.chunkSizeText else viewModel.chunkSizeHex
                            val newOffset = maxOf(0L, viewModel.currentOffset - step)
                            viewModel.currentUri?.let { viewModel.analyzeFile(it, context, toolType, newOffset) }
                        },
                        enabled = viewModel.currentOffset > 0 && !isAnalyzing
                    ) {
                        Text("Previous")
                    }
                    Button(
                        onClick = { 
                            val step = if (toolType == "text_viewer") viewModel.chunkSizeText else viewModel.chunkSizeHex
                            val newOffset = viewModel.currentOffset + step
                            viewModel.currentUri?.let { viewModel.analyzeFile(it, context, toolType, newOffset) }
                        },
                        enabled = !isAnalyzing && res.status == AnalyzerStatus.SUCCESS && res.details.contains("TRUNCATED")
                    ) {
                        Text("Next Page")
                    }
                }
            }
        }
    }
}
