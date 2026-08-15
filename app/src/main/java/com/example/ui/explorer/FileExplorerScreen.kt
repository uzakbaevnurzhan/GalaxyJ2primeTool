package com.example.ui.explorer

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

class FileExplorerViewModel : ViewModel() {
    private val _currentDir = MutableStateFlow<DocumentFile?>(null)
    val currentDir: StateFlow<DocumentFile?> = _currentDir

    private val _files = MutableStateFlow<List<DocumentFile>>(emptyList())
    val files: StateFlow<List<DocumentFile>> = _files

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val stack = mutableListOf<DocumentFile>()

    fun setRootDirectory(uri: Uri, context: android.content.Context) {
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root != null && root.isDirectory) {
            stack.clear()
            stack.add(root)
            loadDirectory(root)
        }
    }

    private fun loadDirectory(dir: DocumentFile) {
        viewModelScope.launch {
            _isLoading.value = true
            _currentDir.value = dir
            _files.value = withContext(Dispatchers.IO) {
                dir.listFiles().sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
            }
            _isLoading.value = false
        }
    }

    fun navigateTo(dir: DocumentFile) {
        if (dir.isDirectory) {
            stack.add(dir)
            loadDirectory(dir)
        }
    }

    fun navigateUp(): Boolean {
        if (stack.size > 1) {
            stack.removeLast()
            loadDirectory(stack.last())
            return true
        }
        return false
    }
}

@Composable
fun FileExplorerScreen(navController: NavController, viewModel: FileExplorerViewModel = viewModel()) {
    val context = LocalContext.current
    val currentDir by viewModel.currentDir.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.setRootDirectory(it, context) }
    }

    BackHandler(enabled = currentDir != null) {
        if (!viewModel.navigateUp()) {
            navController.popBackStack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Workspace Explorer", style = MaterialTheme.typography.headlineMedium)
        Text("Browse extracted ROMs and workspaces.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        if (currentDir == null) {
            Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Select Workspace Directory")
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { launcher.launch(null) }) {
                    Text("Change Workspace")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Current Path: ${currentDir?.name ?: "Unknown"}", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(files) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (file.isDirectory) {
                                        viewModel.navigateTo(file)
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(file.name ?: "Unknown", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    if (!file.isDirectory) {
                                        Text(
                                            text = Formatter.formatFileSize(context, file.length()),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
