package com.example.ui.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.common.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

enum class FileSortOption {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC
}

data class FileItemModel(
    val file: File,
    val isDirectory: Boolean,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isSelected: Boolean = false
)

class FileExplorerViewModel : ViewModel() {
    private val _currentDir = MutableStateFlow<File?>(null)
    val currentDir: StateFlow<File?> = _currentDir

    private val _files = MutableStateFlow<List<FileItemModel>>(emptyList())
    val files: StateFlow<List<FileItemModel>> = _files

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOption = MutableStateFlow(FileSortOption.NAME_ASC)
    val sortOption: StateFlow<FileSortOption> = _sortOption

    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles: StateFlow<Set<File>> = _selectedFiles

    private val _clipboardOperation = MutableStateFlow<Pair<String, List<File>>?>(null) // "copy" or "cut"
    val clipboardOperation: StateFlow<Pair<String, List<File>>?> = _clipboardOperation

    private val dirStack = mutableListOf<File>()

    fun initDirectory(context: Context, customPath: String? = null) {
        val initial = if (customPath != null && File(customPath).exists()) {
            File(customPath)
        } else {
            Environment.getExternalStorageDirectory() ?: context.filesDir
        }
        dirStack.clear()
        dirStack.add(initial)
        loadDirectory(initial)
    }

    fun loadDirectory(dir: File) {
        viewModelScope.launch {
            _isLoading.value = true
            _currentDir.value = dir
            _selectedFiles.value = emptySet()
            val list = withContext(Dispatchers.IO) {
                try {
                    val rawFiles = dir.listFiles() ?: emptyArray()
                    rawFiles.map { f ->
                        FileItemModel(
                            file = f,
                            isDirectory = f.isDirectory,
                            name = f.name,
                            sizeBytes = if (f.isFile) f.length() else 0L,
                            lastModified = f.lastModified()
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _files.value = list
            _isLoading.value = false
        }
    }

    fun navigateTo(dir: File) {
        if (dir.isDirectory) {
            dirStack.add(dir)
            loadDirectory(dir)
        }
    }

    fun navigateUp(): Boolean {
        if (dirStack.size > 1) {
            dirStack.removeAt(dirStack.lastIndex)
            loadDirectory(dirStack.last())
            return true
        }
        return false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: FileSortOption) {
        _sortOption.value = option
    }

    fun toggleSelection(file: File) {
        val set = _selectedFiles.value.toMutableSet()
        if (set.contains(file)) set.remove(file) else set.add(file)
        _selectedFiles.value = set
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun copySelected() {
        if (_selectedFiles.value.isNotEmpty()) {
            _clipboardOperation.value = "copy" to _selectedFiles.value.toList()
        }
    }

    fun cutSelected() {
        if (_selectedFiles.value.isNotEmpty()) {
            _clipboardOperation.value = "cut" to _selectedFiles.value.toList()
        }
    }

    fun paste(onFinished: (Boolean, String) -> Unit) {
        val op = _clipboardOperation.value ?: return
        val destDir = _currentDir.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isCut = op.first == "cut"
                for (src in op.second) {
                    val dest = File(destDir, src.name)
                    if (src.isDirectory) {
                        src.copyRecursively(dest, overwrite = true)
                        if (isCut) src.deleteRecursively()
                    } else {
                        src.copyTo(dest, overwrite = true)
                        if (isCut) src.delete()
                    }
                }
                _clipboardOperation.value = null
                withContext(Dispatchers.Main) {
                    loadDirectory(destDir)
                    onFinished(true, "Files pasted successfully.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFinished(false, "Paste failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun deleteFiles(filesToDelete: List<File>, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var success = true
            for (f in filesToDelete) {
                val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                if (!ok) success = false
            }
            withContext(Dispatchers.Main) {
                _currentDir.value?.let { loadDirectory(it) }
                onFinished(success)
            }
        }
    }

    fun createFolder(name: String, onFinished: (Boolean) -> Unit) {
        val cur = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newDir = File(cur, name)
            val created = newDir.mkdirs()
            withContext(Dispatchers.Main) {
                if (created) loadDirectory(cur)
                onFinished(created)
            }
        }
    }

    fun createFile(name: String, content: String = "", onFinished: (Boolean) -> Unit) {
        val cur = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newFile = File(cur, name)
            val created = try {
                newFile.writeText(content)
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                if (created) loadDirectory(cur)
                onFinished(created)
            }
        }
    }

    fun renameFile(target: File, newName: String, onFinished: (Boolean) -> Unit) {
        val cur = _currentDir.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dest = File(target.parentFile, newName)
            val ok = target.renameTo(dest)
            withContext(Dispatchers.Main) {
                if (ok) loadDirectory(cur)
                onFinished(ok)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    navController: NavController,
    initialPath: String? = null,
    viewModel: FileExplorerViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val currentDir by viewModel.currentDir.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val clipboardOp by viewModel.clipboardOperation.collectAsState()

    var showCreateDialog by remember { mutableStateOf<String?>(null) } // "folder" or "file"
    var createName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var propertiesFile by remember { mutableStateOf<File?>(null) }
    var viewerFile by remember { mutableStateOf<Pair<File, String>?>(null) } // File to mode: "text" or "hex"
    var hashFile by remember { mutableStateOf<File?>(null) }
    var isCalculatingHash by remember { mutableStateOf(false) }
    var calculatedHashes by remember { mutableStateOf<Map<String, String>?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(initialPath) {
        viewModel.initDirectory(context, initialPath)
    }

    BackHandler(enabled = currentDir != null) {
        if (!viewModel.navigateUp()) {
            navController.popBackStack()
        }
    }

    // Filter and Sort files
    val displayedFiles = remember(files, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) files else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
        when (sortOption) {
            FileSortOption.NAME_ASC -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            FileSortOption.NAME_DESC -> filtered.sortedWith(compareBy<FileItemModel> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
            FileSortOption.SIZE_ASC -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.sizeBytes }))
            FileSortOption.SIZE_DESC -> filtered.sortedWith(compareBy<FileItemModel> { !it.isDirectory }.thenByDescending { it.sizeBytes })
            FileSortOption.DATE_ASC -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            FileSortOption.DATE_DESC -> filtered.sortedWith(compareBy<FileItemModel> { !it.isDirectory }.thenByDescending { it.lastModified })
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "File Manager",
                subtitle = currentDir?.name ?: "Root",
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateUp()) navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Sort button
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("Name (A to Z)") }, onClick = { viewModel.setSortOption(FileSortOption.NAME_ASC); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Name (Z to A)") }, onClick = { viewModel.setSortOption(FileSortOption.NAME_DESC); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Size (Smallest first)") }, onClick = { viewModel.setSortOption(FileSortOption.SIZE_ASC); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Size (Largest first)") }, onClick = { viewModel.setSortOption(FileSortOption.SIZE_DESC); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Date (Oldest first)") }, onClick = { viewModel.setSortOption(FileSortOption.DATE_ASC); showSortMenu = false })
                        DropdownMenuItem(text = { Text("Date (Newest first)") }, onClick = { viewModel.setSortOption(FileSortOption.DATE_DESC); showSortMenu = false })
                    }

                    // Create Menu
                    IconButton(onClick = { showCreateDialog = "folder"; createName = "" }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                    }
                    IconButton(onClick = { showCreateDialog = "file"; createName = "" }) {
                        Icon(Icons.Filled.Add, contentDescription = "New File")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedFiles.isNotEmpty() || clipboardOp != null) {
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedFiles.isNotEmpty()) {
                            Text("${selectedFiles.size} selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { viewModel.copySelected() }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { viewModel.cutSelected() }) {
                                Icon(Icons.Filled.ContentCut, contentDescription = "Cut")
                            }
                            IconButton(onClick = {
                                viewModel.deleteFiles(selectedFiles.toList()) { ok ->
                                    Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear Selection")
                            }
                        } else if (clipboardOp != null) {
                            Text("${clipboardOp!!.second.size} items in clipboard (${clipboardOp!!.first})", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                viewModel.paste { ok, msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                            }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Paste Here")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search current folder...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Current Path Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentDir?.absolutePath ?: "Root",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (displayedFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No files or directories found.", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayedFiles, key = { it.file.absolutePath }) { item ->
                        val isSelected = selectedFiles.contains(item.file)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedFiles.isNotEmpty()) {
                                            viewModel.toggleSelection(item.file)
                                        } else if (item.isDirectory) {
                                            viewModel.navigateTo(item.file)
                                        } else {
                                            // Open file or show action dialog
                                            viewerFile = item.file to "text"
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelection(item.file)
                                    }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (item.isDirectory) Icons.Filled.Folder else getFileIcon(item.name),
                                    contentDescription = null,
                                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Row {
                                        if (!item.isDirectory) {
                                            Text(Formatter.formatFileSize(context, item.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            Text(" • ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Text(
                                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.lastModified)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                // Context quick menu for item
                                var showItemMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showItemMenu = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(expanded = showItemMenu, onDismissRequest = { showItemMenu = false }) {
                                        if (!item.isDirectory) {
                                            DropdownMenuItem(text = { Text("View as Text") }, onClick = { viewerFile = item.file to "text"; showItemMenu = false })
                                            DropdownMenuItem(text = { Text("View as Hex") }, onClick = { viewerFile = item.file to "hex"; showItemMenu = false })
                                            DropdownMenuItem(text = { Text("Calculate Hashes") }, onClick = { hashFile = item.file; showItemMenu = false })
                                            DropdownMenuItem(text = { Text("Open with Analyzer") }, onClick = {
                                                routeToAnalyzer(item.name, navController)
                                                showItemMenu = false
                                            })
                                        }
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { renameTarget = item.file; renameInput = item.name; showItemMenu = false })
                                        DropdownMenuItem(text = { Text("Properties") }, onClick = { propertiesFile = item.file; showItemMenu = false })
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                viewModel.deleteFiles(listOf(item.file)) { ok ->
                                                    Toast.makeText(context, if (ok) "Deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
                                                }
                                                showItemMenu = false
                                            }
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

    // Dialog: Create Folder / File
    if (showCreateDialog != null) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = null },
            title = { Text(if (showCreateDialog == "folder") "Create New Folder" else "Create New File") },
            text = {
                OutlinedTextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (createName.isNotBlank()) {
                            if (showCreateDialog == "folder") {
                                viewModel.createFolder(createName.trim()) { ok ->
                                    Toast.makeText(context, if (ok) "Folder created" else "Failed to create", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.createFile(createName.trim()) { ok ->
                                    Toast.makeText(context, if (ok) "File created" else "Failed to create", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showCreateDialog = null
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = { OutlinedButton(onClick = { showCreateDialog = null }) { Text("Cancel") } }
        )
    }

    // Dialog: Rename
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameInput.isNotBlank()) {
                        viewModel.renameFile(renameTarget!!, renameInput.trim()) { ok ->
                            Toast.makeText(context, if (ok) "Renamed" else "Rename failed", Toast.LENGTH_SHORT).show()
                        }
                        renameTarget = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = { OutlinedButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    // Dialog: File Properties
    if (propertiesFile != null) {
        val f = propertiesFile!!
        AlertDialog(
            onDismissRequest = { propertiesFile = null },
            title = { Text("File Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${f.name}", fontWeight = FontWeight.Bold)
                    Text("Path: ${f.absolutePath}", style = MaterialTheme.typography.bodySmall)
                    Text("Type: ${if (f.isDirectory) "Directory" else "File"}")
                    if (f.isFile) Text("Size: ${Formatter.formatFileSize(context, f.length())} (${f.length()} bytes)")
                    Text("Last Modified: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(f.lastModified()))}")
                    Text("Readable: ${f.canRead()} • Writable: ${f.canWrite()} • Executable: ${f.canExecute()}")
                }
            },
            confirmButton = { Button(onClick = { propertiesFile = null }) { Text("OK") } }
        )
    }

    // Dialog: Hash Calculator
    if (hashFile != null) {
        val target = hashFile!!
        LaunchedEffect(target) {
            isCalculatingHash = true
            calculatedHashes = null
            withContext(Dispatchers.IO) {
                try {
                    val md5 = computeDigest(target, "MD5")
                    val sha1 = computeDigest(target, "SHA-1")
                    val sha256 = computeDigest(target, "SHA-256")
                    withContext(Dispatchers.Main) {
                        calculatedHashes = mapOf("MD5" to md5, "SHA-1" to sha1, "SHA-256" to sha256)
                        isCalculatingHash = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        calculatedHashes = mapOf("Error" to (e.message ?: "Failed"))
                        isCalculatingHash = false
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { hashFile = null },
            title = { Text("Hashes: ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCalculatingHash) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Calculating cryptographic signatures...")
                    } else if (calculatedHashes != null) {
                        calculatedHashes!!.forEach { (algo, hash) ->
                            Column {
                                Text(algo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(
                                    hash,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.combinedClickable(onClick = {
                                        clipboard.setText(AnnotatedString(hash))
                                        Toast.makeText(context, "$algo copied to clipboard", Toast.LENGTH_SHORT).show()
                                    })
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { hashFile = null }) { Text("Close") } }
        )
    }

    // Modal: Text / Hex Viewer
    if (viewerFile != null) {
        val (file, mode) = viewerFile!!
        var fileContent by remember { mutableStateOf("Loading...") }

        LaunchedEffect(file, mode) {
            withContext(Dispatchers.IO) {
                fileContent = try {
                    if (mode == "text") {
                        if (file.length() > 2 * 1024 * 1024) {
                            "File too large for live preview (> 2MB). Showing first 64KB:\n\n" +
                                    file.inputStream().bufferedReader().use { String(it.readText().take(65536).toCharArray()) }
                        } else {
                            file.readText()
                        }
                    } else {
                        // Hex dump
                        val bytes = file.inputStream().use { it.readNBytes(16 * 128) } // 2KB hex view
                        formatHexDump(bytes)
                    }
                } catch (e: Exception) {
                    "Error loading content: ${e.localizedMessage}"
                }
            }
        }

        Dialog(onDismissRequest = { viewerFile = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("Mode: ${mode.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = { viewerFile = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = fileContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

private fun getFileIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".img") || lower.endsWith(".bin") -> Icons.Filled.Memory
        lower.endsWith(".apk") -> Icons.Filled.Android
        lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".br") -> Icons.Filled.Archive
        lower.endsWith(".so") || lower.endsWith(".elf") -> Icons.Filled.SettingsSystemDaydream
        lower.endsWith(".prop") || lower.endsWith(".rc") || lower.endsWith(".fstab") || lower.endsWith(".xml") || lower.endsWith(".txt") || lower.endsWith(".log") -> Icons.Filled.Description
        else -> Icons.Filled.Description
    }
}

private fun routeToAnalyzer(fileName: String, navController: NavController) {
    val lower = fileName.lowercase()
    when {
        lower.endsWith("boot.img") || lower.endsWith("recovery.img") -> navController.navigate("boot_analyzer")
        lower.endsWith(".apk") -> navController.navigate("apk_inspector")
        lower.endsWith(".so") || lower.endsWith(".elf") -> navController.navigate("elf_analyzer")
        lower.endsWith(".dat") || lower.endsWith(".dat.br") -> navController.navigate("dat_analyzer")
        lower.endsWith(".prop") -> navController.navigate("buildprop_analyzer")
        lower.endsWith(".rc") -> navController.navigate("init_analyzer")
        lower.endsWith(".fstab") || lower.contains("fstab") -> navController.navigate("fstab_analyzer")
        lower.endsWith(".img") -> navController.navigate("image_analyzer")
        else -> navController.navigate("hash_calculator")
    }
}

private fun computeDigest(file: File, algorithm: String): String {
    val md = MessageDigest.getInstance(algorithm)
    FileInputStream(file).use { fis ->
        val buffer = ByteArray(8192)
        var read: Int
        while (fis.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

private fun formatHexDump(bytes: ByteArray): String {
    val sb = StringBuilder()
    for (i in bytes.indices step 16) {
        sb.append("%08X: ".format(i))
        val chunk = bytes.copyOfRange(i, Math.min(i + 16, bytes.size))
        for (b in chunk) {
            sb.append("%02X ".format(b))
        }
        for (k in chunk.size until 16) {
            sb.append("   ")
        }
        sb.append(" | ")
        for (b in chunk) {
            val c = b.toInt().toChar()
            if (c in ' '..'~') sb.append(c) else sb.append('.')
        }
        sb.append("\n")
    }
    return sb.toString()
}
