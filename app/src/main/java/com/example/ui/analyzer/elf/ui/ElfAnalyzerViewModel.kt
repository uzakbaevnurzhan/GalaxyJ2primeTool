package com.example.ui.analyzer.elf.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.analyzer.elf.engine.ElfFile
import com.example.ui.analyzer.elf.engine.ElfNode
import com.example.ui.analyzer.elf.engine.ElfParserEngine
import com.example.ui.analyzer.elf.engine.ElfDependencyScanner
import com.example.ui.analyzer.elf.engine.ElfValidationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ElfAnalyzerViewModel : ViewModel() {
    var elfUri by mutableStateOf<Uri?>(null)
    var elfName by mutableStateOf<String?>(null)
    
    var isAnalyzing by mutableStateOf(false)
    var statusText by mutableStateOf<String?>(null)
    var errorMsg by mutableStateOf<String?>(null)
    
    var elfFile by mutableStateOf<ElfFile?>(null)
    var validationResult by mutableStateOf<ElfValidationResult?>(null)
    
    var scannedNodes by mutableStateOf<List<ElfNode>?>(null)
    private var scanJob: Job? = null

    fun selectFile(context: Context, uri: Uri) {
        elfUri = uri
        elfName = getFileName(context, uri)
        analyze(context, uri)
    }
    
    fun selectDirectory(context: Context, uri: Uri) {
        scanJob?.cancel()
        elfUri = null
        elfName = null
        elfFile = null
        validationResult = null
        scannedNodes = null
        
        isAnalyzing = true
        errorMsg = null
        
        scanJob = viewModelScope.launch {
            try {
                scannedNodes = ElfDependencyScanner.scanDirectory(context, uri) { progress ->
                    statusText = progress
                }
            } catch (e: Exception) {
                errorMsg = "Directory Scan Failed: ${e.message}"
            } finally {
                isAnalyzing = false
                statusText = null
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        isAnalyzing = false
        statusText = null
    }

    private fun analyze(context: Context, uri: Uri) {
        isAnalyzing = true
        errorMsg = null
        elfFile = null
        validationResult = null
        scannedNodes = null
        statusText = "Parsing ELF structures..."

        viewModelScope.launch {
            try {
                val parsed = ElfParserEngine.parse(context, uri)
                val validation = ElfParserEngine.validateAndroidCompatibility(parsed)
                elfFile = parsed
                validationResult = validation
            } catch (e: Exception) {
                errorMsg = "Analysis Failed: ${e.message}"
            } finally {
                isAnalyzing = false
                statusText = null
            }
        }
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
