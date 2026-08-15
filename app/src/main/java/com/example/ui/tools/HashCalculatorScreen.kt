package com.example.ui.tools

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import java.security.MessageDigest
import java.util.zip.CRC32

class HashCalculatorViewModel : ViewModel() {
    private val _isCalculating = MutableStateFlow(false)
    val isCalculating: StateFlow<Boolean> = _isCalculating

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _results = MutableStateFlow<Map<String, String>?>(null)
    val results: StateFlow<Map<String, String>?> = _results

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    var fileName by mutableStateOf<String?>(null)

    fun calculateHashes(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isCalculating.value = true
            _error.value = null
            _results.value = null
            _progress.value = 0f

            try {
                val hashes = withContext(Dispatchers.IO) {
                    var fName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fName = cursor.getString(nameIndex)
                        }
                    }
                    fileName = fName

                    val md5 = MessageDigest.getInstance("MD5")
                    val sha1 = MessageDigest.getInstance("SHA-1")
                    val sha256 = MessageDigest.getInstance("SHA-256")
                    val sha512 = MessageDigest.getInstance("SHA-512")
                    val crc32 = CRC32()

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val totalBytes = inputStream.available().toLong().takeIf { it > 0 } ?: 1024L
                        val buffer = ByteArray(1024 * 64)
                        var length: Int
                        var readBytes = 0L
                        while (inputStream.read(buffer).also { length = it } >= 0) {
                            md5.update(buffer, 0, length)
                            sha1.update(buffer, 0, length)
                            sha256.update(buffer, 0, length)
                            sha512.update(buffer, 0, length)
                            crc32.update(buffer, 0, length)
                            readBytes += length
                            _progress.value = (readBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        }
                    }

                    mapOf(
                        "MD5" to md5.digest().joinToString("") { "%02x".format(it) },
                        "SHA-1" to sha1.digest().joinToString("") { "%02x".format(it) },
                        "SHA-256" to sha256.digest().joinToString("") { "%02x".format(it) },
                        "SHA-512" to sha512.digest().joinToString("") { "%02x".format(it) },
                        "CRC32" to crc32.value.toString(16).padStart(8, '0')
                    )
                }
                _results.value = hashes
            } catch (e: Exception) {
                _error.value = "Failed to calculate hashes: ${e.message}"
            } finally {
                _isCalculating.value = false
            }
        }
    }
}

@Composable
fun HashCalculatorScreen(navController: NavController, viewModel: HashCalculatorViewModel = viewModel()) {
    val context = LocalContext.current
    val isCalculating by viewModel.isCalculating.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val results by viewModel.results.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.calculateHashes(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Hash Calculator", style = MaterialTheme.typography.headlineMedium)
        Text("Calculate MD5, SHA-1, SHA-256, SHA-512 and CRC32.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { launcher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCalculating
        ) {
            Text(if (isCalculating) "Calculating..." else "Select File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isCalculating) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text("Processing...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        results?.let { hashes ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File: ${viewModel.fileName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    hashes.forEach { (alg, hash) ->
                        Text(alg, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(hash, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
