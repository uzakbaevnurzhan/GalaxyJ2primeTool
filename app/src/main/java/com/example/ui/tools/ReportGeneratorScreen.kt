package com.example.ui.tools

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportGeneratorViewModel : ViewModel() {
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _reportContent = MutableStateFlow<String?>(null)
    val reportContent: StateFlow<String?> = _reportContent

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun generateReport(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _reportContent.value = null

            try {
                val report = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(context, uri) ?: throw Exception("Invalid Directory")
                    val sb = java.lang.StringBuilder()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                    sb.append("# ROM Workspace Report\n")
                    sb.append("**Generated:** ${dateFormat.format(Date())}\n\n")
                    sb.append("## Workspace Summary\n")
                    sb.append("- **Location:** ${root.name}\n")
                    
                    var totalSize = 0L
                    var fileCount = 0
                    var buildPropContent: String? = null
                    val partitionFiles = mutableListOf<DocumentFile>()

                    // Scan first level of directory
                    val files = root.listFiles()
                    for (file in files) {
                        if (file.isDirectory) continue
                        totalSize += file.length()
                        fileCount++
                        
                        val name = file.name ?: ""
                        if (name.endsWith(".img") || name.endsWith(".dat") || name.endsWith(".dat.br")) {
                            partitionFiles.add(file)
                        } else if (name == "build.prop" || name == "system/build.prop") {
                            // Find build.prop to extract version info
                            try {
                                context.contentResolver.openInputStream(file.uri)?.use { fis ->
                                    buildPropContent = BufferedReader(InputStreamReader(fis)).readText()
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }

                    sb.append("- **Total Files (Root):** $fileCount\n")
                    sb.append("- **Total Size (Root):** ${Formatter.formatFileSize(context, totalSize)}\n\n")

                    if (buildPropContent != null) {
                        sb.append("## Build Properties (Summary)\n")
                        val props = buildPropContent!!.lines()
                        val keysOfInterest = listOf("ro.build.display.id", "ro.product.model", "ro.build.version.release", "ro.build.version.sdk")
                        for (line in props) {
                            val parts = line.split("=")
                            if (parts.size == 2 && keysOfInterest.contains(parts[0].trim())) {
                                sb.append("- **${parts[0].trim()}:** ${parts[1].trim()}\n")
                            }
                        }
                        sb.append("\n")
                    }

                    sb.append("## Partitions\n")
                    if (partitionFiles.isEmpty()) {
                        sb.append("*No .img or .dat files found in root.*\n")
                    } else {
                        partitionFiles.sortedBy { it.name }.forEach { file ->
                            sb.append("- **${file.name}**: ${Formatter.formatFileSize(context, file.length())}\n")
                        }
                    }

                    sb.toString()
                }
                _reportContent.value = report
            } catch (e: Exception) {
                _error.value = "Failed to generate report: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }
}

@Composable
fun ReportGeneratorScreen(navController: NavController, viewModel: ReportGeneratorViewModel = viewModel()) {
    val context = LocalContext.current
    val isGenerating by viewModel.isGenerating.collectAsState()
    val reportContent by viewModel.reportContent.collectAsState()
    val error by viewModel.error.collectAsState()

    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.generateReport(it, context) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(reportContent?.toByteArray() ?: ByteArray(0))
                }
                android.widget.Toast.makeText(context, "Report Exported", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Export Failed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ROM Report Generator", style = MaterialTheme.typography.headlineMedium)
        Text("Scan a ROM workspace and generate a Markdown report.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { dirLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating
        ) {
            Text(if (isGenerating) "Scanning..." else "Select ROM Workspace Folder")
        }

        Spacer(modifier = Modifier.height(16.dp))

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        reportContent?.let { content ->
            Button(onClick = { exportLauncher.launch("ROM_Report.md") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export Markdown Report")
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = content,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            )
        }
    }
}
