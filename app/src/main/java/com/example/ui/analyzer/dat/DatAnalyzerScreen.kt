package com.example.ui.analyzer.dat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.ui.analyzer.dat.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class DatAnalyzerViewModel : ViewModel() {
    var datUri by mutableStateOf<Uri?>(null)
    var datName by mutableStateOf<String?>(null)
    var isBrotli by mutableStateOf(false)

    var listUri by mutableStateOf<Uri?>(null)
    var listName by mutableStateOf<String?>(null)
    var transferList by mutableStateOf<DatTransferList?>(null)
    var validationResult by mutableStateOf<DatValidator.Result?>(null)

    var isConverting by mutableStateOf(false)
    var conversionProgress by mutableStateOf(0f)
    var conversionStatus by mutableStateOf("")
    var errorMsg by mutableStateOf<String?>(null)

    private var conversionJob: Job? = null

    fun selectDatFile(context: Context, uri: Uri) {
        datUri = uri
        datName = getFileName(context, uri)
        isBrotli = datName?.endsWith(".br") == true
    }

    fun selectListFile(context: Context, uri: Uri) {
        listUri = uri
        listName = getFileName(context, uri)
        parseTransferList(context, uri)
    }

    private fun parseTransferList(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
                val parsed = DatTransferList.parse(BufferedReader(InputStreamReader(inputStream)))
                withContext(Dispatchers.Main) {
                    transferList = parsed
                    validationResult = DatValidator.validate(parsed)
                    errorMsg = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = "Failed to parse transfer list: ${e.message}"
                    transferList = null
                    validationResult = null
                }
            }
        }
    }

    fun convertToRaw(context: Context, outputUri: Uri) {
        val dUri = datUri ?: return
        val tList = transferList ?: return
        val brotli = isBrotli

        isConverting = true
        conversionProgress = 0f
        conversionStatus = "Initializing..."
        errorMsg = null

        conversionJob = viewModelScope.launch {
            try {
                DatConverter.convert(
                    context = context,
                    datUri = dUri,
                    isBrotli = brotli,
                    transferList = tList,
                    outputUri = outputUri,
                    onProgress = { p, s ->
                        conversionProgress = p
                        conversionStatus = s
                    }
                )
                conversionStatus = "Conversion complete! File saved."
                conversionProgress = 100f
            } catch (e: Exception) {
                errorMsg = "Conversion failed: ${e.message}"
                conversionStatus = "Failed"
            } finally {
                isConverting = false
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        isConverting = false
        conversionStatus = "Cancelled"
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) result = cursor.getString(idx)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/') ?: "unknown"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatAnalyzerScreen(navController: NavController, viewModel: DatAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current

    val datLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectDatFile(context, it) }
    }

    val listLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectListFile(context, it) }
    }

    val saveRawLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.convertToRaw(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DAT/DAT.BR Engine") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Input Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { datLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                            Text("Select DAT/DAT.BR")
                        }
                        Text(viewModel.datName ?: "No file selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { listLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                            Text("Select transfer.list")
                        }
                        Text(viewModel.listName ?: "No file selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            viewModel.errorMsg?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            }

            viewModel.transferList?.let { list ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Version: ${list.version}")
                        Text("Total Blocks: ${list.totalBlocks}")
                        Text("Stash Entries: ${list.stashEntries}")
                        HorizontalDivider()
                        Text("Commands: ${list.commands.size}")
                        Text("New Blocks: ${list.newBlocks} (${(list.newBlocks * 4096) / 1024 / 1024} MB)")
                        Text("Zero Blocks: ${list.zeroBlocks}")
                        Text("Erase Blocks: ${list.eraseBlocks}")
                        Text("Is Incremental OTA: ${if (list.isIncremental) "Yes" else "No"}")
                    }
                }
            }

            viewModel.validationResult?.let { result ->
                val color = when (result.status) {
                    DatValidator.Status.VALID -> MaterialTheme.colorScheme.primaryContainer
                    DatValidator.Status.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                    DatValidator.Status.INVALID -> MaterialTheme.colorScheme.errorContainer
                }
                Card(colors = CardDefaults.cardColors(containerColor = color)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("3. Validation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        result.messages.forEach { msg ->
                            Text("• $msg")
                        }
                    }
                }
            }

            if (viewModel.datUri != null && viewModel.transferList != null) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("4. Convert to RAW (.img)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        
                        if (viewModel.isConverting) {
                            LinearProgressIndicator(
                                progress = { viewModel.conversionProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(viewModel.conversionStatus)
                            Button(onClick = { viewModel.cancelConversion() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("Cancel")
                            }
                        } else {
                            val isInvalid = viewModel.validationResult?.status == DatValidator.Status.INVALID
                            Button(
                                onClick = { saveRawLauncher.launch("system.img") },
                                enabled = !isInvalid,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Convert & Save as RAW Image")
                            }
                            if (viewModel.conversionStatus.isNotEmpty()) {
                                Text(viewModel.conversionStatus)
                            }
                        }
                    }
                }
            }
        }
    }
}
