package com.example.ui.builder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ROMBuilderViewModel : ViewModel() {
    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress
    
    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success

    var sourceDirUri: Uri? by mutableStateOf(null)
    var destFileUri: Uri? by mutableStateOf(null)

    private suspend fun zipDirectory(
        context: android.content.Context, 
        sourceDir: DocumentFile, 
        zipOut: ZipOutputStream, 
        basePath: String = ""
    ) {
        val files = sourceDir.listFiles()
        for (file in files) {
            val entryName = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
            _statusText.value = "Compressing: $entryName"
            
            if (file.isDirectory) {
                val entry = ZipEntry("$entryName/")
                zipOut.putNextEntry(entry)
                zipOut.closeEntry()
                zipDirectory(context, file, zipOut, entryName ?: "")
            } else {
                context.contentResolver.openInputStream(file.uri)?.use { fis ->
                    val entry = ZipEntry(entryName)
                    zipOut.putNextEntry(entry)
                    val buffer = ByteArray(1024 * 64) // 64KB buffer
                    var length: Int
                    while (fis.read(buffer).also { length = it } >= 0) {
                        zipOut.write(buffer, 0, length)
                    }
                    zipOut.closeEntry()
                }
            }
        }
    }

    fun buildRom(context: android.content.Context) {
        val srcUri = sourceDirUri ?: return
        val dstUri = destFileUri ?: return

        viewModelScope.launch {
            _isBuilding.value = true
            _error.value = null
            _success.value = false
            _statusText.value = "Initializing..."
            _progress.value = 0f

            try {
                withContext(Dispatchers.IO) {
                    val srcDir = DocumentFile.fromTreeUri(context, srcUri) ?: throw Exception("Invalid source directory")
                    
                    context.contentResolver.openOutputStream(dstUri)?.use { fos ->
                        ZipOutputStream(fos).use { zipOut ->
                            zipOut.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                            zipDirectory(context, srcDir, zipOut)
                        }
                    }
                }
                _success.value = true
                _statusText.value = "Build complete!"
            } catch (e: Exception) {
                _error.value = "Build failed: ${e.message}"
                _statusText.value = "Error"
            } finally {
                _isBuilding.value = false
            }
        }
    }
}

@Composable
fun ROMBuilderScreen(navController: NavController, viewModel: ROMBuilderViewModel = viewModel()) {
    val context = LocalContext.current
    val isBuilding by viewModel.isBuilding.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()

    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.sourceDirUri = it }
    }

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let { viewModel.destFileUri = it }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ROM Builder", style = MaterialTheme.typography.headlineMedium)
        Text("Package a ROM directory into a flashable ZIP archive locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = { dirLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (viewModel.sourceDirUri != null) "Source Selected" else "Select Source Directory")
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(onClick = { createDocLauncher.launch("Custom_ROM.zip") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (viewModel.destFileUri != null) "Destination Selected" else "Select Output ZIP Location")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.buildRom(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.sourceDirUri != null && viewModel.destFileUri != null && !isBuilding
        ) {
            Text(if (isBuilding) "Building..." else "Build ZIP")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isBuilding) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
        
        if (success) {
            Text("ROM ZIP successfully built!", color = com.example.ui.theme.ColorGood)
        }
    }
}
