package com.example.ui.compare

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

enum class CheckStatus { GOOD, WARNING, ERROR, UNKNOWN }

data class CompatibilityCheckItem(
    val category: String,
    val status: CheckStatus,
    val details: String
)

data class CompatibilityResult(
    val romName: String,
    val overallStatus: String,
    val checks: List<CompatibilityCheckItem>
)

class CompatibilityViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<CompatibilityResult?>(null)
    val result: StateFlow<CompatibilityResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun checkRom(uri: Uri, context: android.content.Context) {
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

                    var buildPropContent = ""
                    var hasBoot = false
                    var hasSystem = false

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zip ->
                            var entry = zip.nextEntry
                            var foundProps = false
                            while (entry != null && !foundProps) {
                                val name = entry.name
                                if (name.contains("boot.img")) hasBoot = true
                                if (name.contains("system")) hasSystem = true
                                
                                if (name.endsWith("build.prop")) {
                                    val reader = BufferedReader(InputStreamReader(zip))
                                    var line = reader.readLine()
                                    while (line != null) {
                                        buildPropContent += "$line\n"
                                        line = reader.readLine()
                                    }
                                    foundProps = true
                                }
                                entry = zip.nextEntry
                            }
                        }
                    }

                    val checks = mutableListOf<CompatibilityCheckItem>()
                    var errors = 0
                    var warnings = 0

                    // Target Profile: SM-G532F / MT6737T / ARM32 / non-Treble

                    if (buildPropContent.isNotEmpty()) {
                        // Check Architecture
                        if (buildPropContent.contains("ro.product.cpu.abi=armeabi-v7a")) {
                            checks.add(CompatibilityCheckItem("Architecture (ARM32)", CheckStatus.GOOD, "Found armeabi-v7a"))
                        } else if (buildPropContent.contains("ro.product.cpu.abi=arm64")) {
                            checks.add(CompatibilityCheckItem("Architecture", CheckStatus.ERROR, "64-bit ROMs are NOT compatible with J2 Prime stock bootloader."))
                            errors++
                        } else {
                            checks.add(CompatibilityCheckItem("Architecture", CheckStatus.UNKNOWN, "Could not identify ABI in build.prop"))
                            warnings++
                        }

                        // Check Platform
                        if (buildPropContent.contains("mt6737t") || buildPropContent.contains("MT6737T")) {
                            checks.add(CompatibilityCheckItem("Platform (MT6737T)", CheckStatus.GOOD, "Platform matches MediaTek MT6737T"))
                        } else {
                            checks.add(CompatibilityCheckItem("Platform", CheckStatus.WARNING, "Platform string does not explicitly match MT6737T. Verify kernel."))
                            warnings++
                        }

                        // Check Treble
                        if (buildPropContent.contains("ro.treble.enabled=true")) {
                            checks.add(CompatibilityCheckItem("Treble Status", CheckStatus.ERROR, "Project Treble is enabled. J2 Prime is non-Treble. This requires a custom vendor partition layout."))
                            errors++
                        } else {
                            checks.add(CompatibilityCheckItem("Treble Status", CheckStatus.GOOD, "non-Treble configuration detected."))
                        }

                    } else {
                        checks.add(CompatibilityCheckItem("build.prop", CheckStatus.UNKNOWN, "Could not find or read build.prop in the ZIP root/system."))
                        warnings++
                    }

                    if (!hasBoot) {
                        checks.add(CompatibilityCheckItem("Boot Image", CheckStatus.WARNING, "boot.img is missing. Ensure you are flashing a compatible kernel separately."))
                        warnings++
                    } else {
                        checks.add(CompatibilityCheckItem("Boot Image", CheckStatus.GOOD, "boot.img found."))
                    }

                    val overall = when {
                        errors > 0 -> "HIGH RISK / INCOMPATIBLE"
                        warnings > 1 -> "PARTIALLY COMPATIBLE"
                        checks.all { it.status == CheckStatus.UNKNOWN } -> "INSUFFICIENT DATA"
                        else -> "LIKELY COMPATIBLE"
                    }

                    CompatibilityResult(fileName, overall, checks)
                }
                _result.value = res
            } catch (e: Exception) {
                _error.value = "Failed to check compatibility: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun CompatibilityCheckScreen(navController: NavController, viewModel: CompatibilityViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.checkRom(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Compatibility Check", style = MaterialTheme.typography.headlineMedium)
        Text("Target Profile: Galaxy J2 Prime (SM-G532F, MT6737T, ARM32, non-Treble)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { launcher.launch("application/zip") }, modifier = Modifier.fillMaxWidth()) {
            Text("Select ROM ZIP")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Analyzing compatibility...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        result?.let { res ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File: ${res.romName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Overall Status: ${res.overallStatus}",
                        style = MaterialTheme.typography.titleLarge,
                        color = when (res.overallStatus) {
                            "HIGH RISK / INCOMPATIBLE" -> MaterialTheme.colorScheme.error
                            "LIKELY COMPATIBLE" -> com.example.ui.theme.ColorGood
                            else -> com.example.ui.theme.ColorWarning
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(res.checks) { check ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (check.status) {
                                CheckStatus.GOOD -> Icons.Filled.CheckCircle
                                CheckStatus.WARNING -> Icons.Filled.Warning
                                CheckStatus.ERROR -> Icons.Filled.Error
                                CheckStatus.UNKNOWN -> Icons.AutoMirrored.Filled.Help
                            },
                            contentDescription = null,
                            tint = when (check.status) {
                                CheckStatus.GOOD -> com.example.ui.theme.ColorGood
                                CheckStatus.WARNING -> com.example.ui.theme.ColorWarning
                                CheckStatus.ERROR -> com.example.ui.theme.ColorError
                                CheckStatus.UNKNOWN -> com.example.ui.theme.ColorUnknown
                            },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(check.category, style = MaterialTheme.typography.titleMedium)
                            Text(check.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

