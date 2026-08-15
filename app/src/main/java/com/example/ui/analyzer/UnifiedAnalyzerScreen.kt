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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class UnifiedAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var fileName by mutableStateOf<String?>(null)

    fun analyzeFile(uri: Uri, context: android.content.Context, toolType: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _result.value = null

            try {
                val output = withContext(Dispatchers.IO) {
                    var fName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fName = cursor.getString(nameIndex)
                        }
                    }
                    fileName = fName

                    when (toolType) {
                        "text_viewer" -> readText(uri, context)
                        "hex_viewer" -> readHex(uri, context)
                        "sparse_analyzer" -> parseSparse(uri, context)
                        "elf_analyzer" -> parseElf(uri, context)
                        "dat_analyzer" -> parseDat(uri, context)
                        "selinux_analyzer" -> parseTextFileMetadata(uri, context, "SELinux Contexts/Policy")
                        "kernel_crash" -> parseTextFileMetadata(uri, context, "Kernel Crash Log")
                        "getprop_analyzer" -> readGetprop()
                        else -> "Tool type '$toolType' is not fully implemented yet. Reading basic metadata:\nFile: $fName\nSize: ${getFileSize(uri, context)} bytes"
                    }
                }
                _result.value = output
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    private fun parseTextFileMetadata(uri: Uri, context: android.content.Context, label: String): String {
        return "[$label]\n" + readText(uri, context)
    }

    private fun readGetprop(): String {
        fileName = "Live System Properties"
        return com.example.utils.RootShell.executeCommand("getprop").getOrNull() ?: "Failed to read getprop. Root required."
    }

    private fun getFileSize(uri: Uri, context: android.content.Context): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    private fun readText(uri: Uri, context: android.content.Context): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var linesRead = 0
            var line = reader.readLine()
            while (line != null && linesRead < 5000) { // Limit to 5000 lines
                sb.append(line).append("\n")
                line = reader.readLine()
                linesRead++
            }
            if (line != null) sb.append("\n... [TRUNCATED - File too large] ...")
            return sb.toString()
        }
        return "Failed to read text."
    }

    private fun readHex(uri: Uri, context: android.content.Context): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = ByteArray(512) // Read first 512 bytes
            val read = inputStream.read(bytes)
            if (read <= 0) return "Empty file."
            
            val sb = StringBuilder()
            for (i in 0 until read) {
                sb.append(String.format("%02X ", bytes[i]))
                if ((i + 1) % 16 == 0) sb.append("\n")
            }
            sb.append("\n\n[Displaying first 512 bytes]")
            return sb.toString()
        }
        return "Failed to read hex."
    }

    private fun parseSparse(uri: Uri, context: android.content.Context): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val header = ByteArray(28)
            val read = inputStream.read(header)
            if (read < 28) return "Invalid Sparse Image: Too small."
            
            // Check Magic: 0xED26FF3A (Little Endian) -> 3A FF 26 ED
            if (header[0] == 0x3A.toByte() && header[1] == 0xFF.toByte() && header[2] == 0x26.toByte() && header[3] == 0xED.toByte()) {
                return "Valid Sparse Image Detected!\nMagic: 0xED26FF3A\nVersion: ${header[4]}.${header[5]}\nBlock Size: 4096 (Standard Android)\n" +
                        "Note: Full chunk parsing requires loading the entire block map."
            }
            return "Not a valid Android Sparse Image. Magic mismatch."
        }
        return "Failed to read."
    }

    private fun parseElf(uri: Uri, context: android.content.Context): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val header = ByteArray(64)
            val read = inputStream.read(header)
            if (read < 64) return "Invalid ELF File: Too small."
            
            // Check Magic: 7F 45 4C 46 (\x7fELF)
            if (header[0] == 0x7F.toByte() && header[1] == 0x45.toByte() && header[2] == 0x4C.toByte() && header[3] == 0x46.toByte()) {
                val bitClass = if (header[4] == 1.toByte()) "32-bit" else if (header[4] == 2.toByte()) "64-bit" else "Unknown"
                val endianness = if (header[5] == 1.toByte()) "Little Endian" else "Big Endian"
                val machine = header[18].toInt() // Simplified check
                val arch = when(machine) {
                    3 -> "x86"
                    40 -> "ARM"
                    62 -> "x86_64"
                    183 -> "ARM64 (AArch64)"
                    else -> "Unknown ($machine)"
                }
                return "Valid ELF Detected!\nClass: $bitClass\nData: $endianness\nArchitecture: $arch\n\n" +
                        "Note: Full Symbol and Section mapping requires NDK/Readelf equivalent."
            }
            return "Not a valid ELF file. Magic mismatch."
        }
        return "Failed to read."
    }

    private fun parseDat(uri: Uri, context: android.content.Context): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = ByteArray(4)
            inputStream.read(bytes)
            // check brotli magic or dat transfer list text
            val text = String(bytes)
            if (text.startsWith("1\n") || text.startsWith("2\n") || text.startsWith("3\n") || text.startsWith("4\n")) {
                 return "Android Sparse Transfer List Detected.\nVersion: ${text.trim()}\nThis file contains chunk instructions for block recovery."
            }
            return "File is likely a raw sparse chunk or brotli compressed (system.new.dat.br)."
        }
        return "Failed to read."
    }
}

@Composable
fun UnifiedAnalyzerScreen(navController: NavController, toolType: String, viewModel: UnifiedAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val error by viewModel.error.collectAsState()

    val toolName = toolType.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeFile(it, context, toolType) }
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
                Text(if (isAnalyzing) "Analyzing..." else "Select File to Analyze")
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else if (isAnalyzing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        result?.let { content ->
            Text("File: ${viewModel.fileName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SelectionContainer {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        item {
                            Text(content, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        }
                    }
                }
            }
        }
    }
}
