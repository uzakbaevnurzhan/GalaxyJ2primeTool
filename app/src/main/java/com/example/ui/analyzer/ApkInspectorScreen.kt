package com.example.ui.analyzer

import android.content.pm.PackageManager
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
import java.io.File

data class ApkInfoResult(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>
)

class ApkInspectorViewModel : ViewModel() {
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _result = MutableStateFlow<ApkInfoResult?>(null)
    val result: StateFlow<ApkInfoResult?> = _result

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    var fileName by mutableStateOf<String?>(null)

    fun analyzeApk(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _error.value = null
            _result.value = null

            var tempFile: File? = null

            try {
                val apkInfo = withContext(Dispatchers.IO) {
                    var fName = "Unknown"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) fName = cursor.getString(nameIndex)
                        }
                    }
                    fileName = fName

                    tempFile = File(context.cacheDir, "temp_inspector_${System.currentTimeMillis()}.apk")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile?.outputStream()?.use { output ->
                            input.copyTo(output)
                        }
                    }

                    val pm = context.packageManager
                    val flags = PackageManager.GET_PERMISSIONS or 
                                PackageManager.GET_ACTIVITIES or 
                                PackageManager.GET_SERVICES or 
                                PackageManager.GET_RECEIVERS or 
                                PackageManager.GET_PROVIDERS
                    
                    val packageInfo = pm.getPackageArchiveInfo(tempFile!!.absolutePath, flags)
                        ?: throw Exception("Could not parse APK information (is it a valid APK?)")
                    
                    ApkInfoResult(
                        packageName = packageInfo.packageName,
                        versionName = packageInfo.versionName ?: "Unknown",
                        versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong(),
                        permissions = packageInfo.requestedPermissions?.toList() ?: emptyList(),
                        activities = packageInfo.activities?.map { it.name } ?: emptyList(),
                        services = packageInfo.services?.map { it.name } ?: emptyList(),
                        receivers = packageInfo.receivers?.map { it.name } ?: emptyList(),
                        providers = packageInfo.providers?.map { it.name } ?: emptyList()
                    )
                }
                _result.value = apkInfo
            } catch (e: Exception) {
                _error.value = "Failed to parse APK: ${e.message}"
            } finally {
                withContext(Dispatchers.IO) {
                    try {
                        tempFile?.delete()
                    } catch (e: Exception) {
                        // ignore deletion errors
                    }
                }
                _isAnalyzing.value = false
            }
        }
    }
}

@Composable
fun ApkInspectorScreen(navController: NavController, viewModel: ApkInspectorViewModel = viewModel()) {
    val context = LocalContext.current
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val result by viewModel.result.collectAsState()
    val error by viewModel.error.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.analyzeApk(it, context) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("APK Inspector", style = MaterialTheme.typography.headlineMedium)
        Text("Analyze internal structure of Android applications.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { launcher.launch("application/vnd.android.package-archive") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isAnalyzing
        ) {
            Text(if (isAnalyzing) "Analyzing..." else "Select APK File")
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (isAnalyzing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        result?.let { apk ->
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Package: ${apk.packageName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Version Name: ${apk.versionName}", style = MaterialTheme.typography.bodyMedium)
                            Text("Version Code: ${apk.versionCode}", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Permissions: ${apk.permissions.size}")
                            Text("Activities: ${apk.activities.size}")
                            Text("Services: ${apk.services.size}")
                            Text("Receivers: ${apk.receivers.size}")
                            Text("Providers: ${apk.providers.size}")
                        }
                    }
                }

                if (apk.permissions.isNotEmpty()) {
                    item { Text("Permissions:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                    items(apk.permissions) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }

                if (apk.activities.isNotEmpty()) {
                    item { Text("Activities:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                    items(apk.activities) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }

                if (apk.services.isNotEmpty()) {
                    item { Text("Services:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                    items(apk.services) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                
                if (apk.receivers.isNotEmpty()) {
                    item { Text("Receivers:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                    items(apk.receivers) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                
                if (apk.providers.isNotEmpty()) {
                    item { Text("Providers:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                    items(apk.providers) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
