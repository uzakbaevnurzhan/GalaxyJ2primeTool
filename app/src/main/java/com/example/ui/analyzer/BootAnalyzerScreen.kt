package com.example.ui.analyzer

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class BootImageInfo(
    val fileName: String,
    val magic: String,
    val kernelSize: Int,
    val kernelAddr: String,
    val ramdiskSize: Int,
    val ramdiskAddr: String,
    val pageSize: Int,
    val cmdline: String
)

class BootAnalyzerViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<BootImageInfo?>(null)
    val result: StateFlow<BootImageInfo?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun analyzeBootImg(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _result.value = null

            try {
                val res = withContext(Dispatchers.IO) {
                    var fileName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                        }
                    }

                    // Android boot.img header structure (v0 / standard)
                    // 8 bytes: ANDROID!
                    // 4 bytes: kernel size
                    // 4 bytes: kernel address
                    // 4 bytes: ramdisk size
                    // 4 bytes: ramdisk address
                    // ...
                    // 4 bytes: page size (at offset 36)
                    // ...
                    // 512 bytes: cmdline (at offset 64)

                    val headerSize = 1024
                    val headerBytes = ByteArray(headerSize)
                    var bytesRead = 0

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        bytesRead = inputStream.read(headerBytes, 0, headerSize)
                    }

                    if (bytesRead < headerSize) {
                        throw Exception("File is too small to be a valid boot image.")
                    }

                    val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    
                    val magicBytes = ByteArray(8)
                    buffer.get(magicBytes, 0, 8)
                    val magic = String(magicBytes)

                    if (!magic.startsWith("ANDROID!")) {
                        throw Exception("Invalid magic number: '$magic'. Not a standard Android boot.img")
                    }

                    val kernelSize = buffer.getInt()
                    val kernelAddr = "0x" + Integer.toHexString(buffer.getInt())
                    val ramdiskSize = buffer.getInt()
                    val ramdiskAddr = "0x" + Integer.toHexString(buffer.getInt())
                    
                    buffer.position(36)
                    val pageSize = buffer.getInt()

                    buffer.position(64)
                    val cmdlineBytes = ByteArray(512)
                    buffer.get(cmdlineBytes, 0, 512)
                    val cmdline = String(cmdlineBytes).trimEnd('\u0000')

                    BootImageInfo(fileName, magic, kernelSize, kernelAddr, ramdiskSize, ramdiskAddr, pageSize, cmdline)
                }
                _result.value = res
            } catch (e: Exception) {
                _error.value = "Analysis failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun BootAnalyzerScreen(navController: NavController, viewModel: BootAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeBootImg(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Boot Image Analyzer", style = MaterialTheme.typography.headlineMedium)
        Text("Parses standard boot.img header structure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { launcher.launch("application/octet-stream") }, modifier = Modifier.fillMaxWidth()) {
            Text("Select boot.img")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Analyzing boot.img...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        result?.let { info ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File: ${info.fileName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Magic Number: ${info.magic}", color = com.example.ui.theme.ColorGood)
                    Text("Kernel Size: ${info.kernelSize} bytes")
                    Text("Kernel Addr: ${info.kernelAddr}", style = MaterialTheme.typography.bodySmall)
                    Text("Ramdisk Size: ${info.ramdiskSize} bytes")
                    Text("Ramdisk Addr: ${info.ramdiskAddr}", style = MaterialTheme.typography.bodySmall)
                    Text("Page Size: ${info.pageSize} bytes")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Command Line (cmdline):", fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(info.cmdline.ifEmpty { "No cmdline parameters found." }, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

