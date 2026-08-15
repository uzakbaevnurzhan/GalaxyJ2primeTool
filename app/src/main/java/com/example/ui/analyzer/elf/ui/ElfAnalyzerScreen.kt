package com.example.ui.analyzer.elf.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.ui.analyzer.elf.engine.ElfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElfAnalyzerScreen(navController: NavController, viewModel: ElfAnalyzerViewModel = viewModel()) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.selectFile(context, it) }
    }
    val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.selectDirectory(context, it) }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Header", "Program Headers", "Sections", "Dynamic", "Dependencies", "Symbols", "Compatibility")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ELF / .so Analyzer") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header Selection
            Card(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = { launcher.launch(arrayOf("*/*")) }) {
                            Text("Select ELF / .so")
                        }
                        Text(viewModel.elfName ?: "No file selected", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(onClick = { dirLauncher.launch(null) }) {
                            Text("Scan ROM Directory")
                        }
                    }
                }
            }

            if (viewModel.isAnalyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(viewModel.statusText ?: "Working...", modifier = Modifier.padding(16.dp))
                Button(onClick = { viewModel.cancelScan() }, modifier = Modifier.padding(start = 16.dp)) {
                    Text("Cancel")
                }
            } else if (viewModel.errorMsg != null) {
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(viewModel.errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            } else if (viewModel.scannedNodes != null) {
                Text("Scanned Libraries: ${viewModel.scannedNodes!!.size}", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewModel.scannedNodes!!) { node ->
                        Card(onClick = { viewModel.selectFile(context, node.uri) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(node.name, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("Arch: ${node.architecture}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                Text("SONAME: ${node.soname ?: "<none>"}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Text("Dependencies: ${node.dependencies.size}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else if (viewModel.elfFile != null) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> HeaderTab(viewModel.elfFile!!)
                        1 -> ProgramHeadersTab(viewModel.elfFile!!)
                        2 -> SectionsTab(viewModel.elfFile!!)
                        3 -> DynamicTab(viewModel.elfFile!!)
                        4 -> DependenciesTab(viewModel.elfFile!!)
                        5 -> SymbolsTab(viewModel.elfFile!!)
                        6 -> CompatibilityTab(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderTab(elf: ElfFile) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyValueRow("Class", elf.header.elfClass.name)
        KeyValueRow("Data Encoding", elf.header.endian.name)
        KeyValueRow("Machine", "${elf.header.architectureName} (0x${elf.header.machine.toString(16)})")
        KeyValueRow("OS ABI", "0x${elf.header.osAbi.toString(16)}")
        KeyValueRow("ABI Version", elf.header.abiVersion.toString())
        KeyValueRow("Type", "0x${elf.header.type.toString(16)}")
        KeyValueRow("Entry Point Address", "0x${elf.header.entryPoint.toString(16)}")
        KeyValueRow("Program Headers Offset", "${elf.header.phOff} (bytes)")
        KeyValueRow("Section Headers Offset", "${elf.header.shOff} (bytes)")
        KeyValueRow("Flags", "0x${elf.header.flags.toString(16)}")
        KeyValueRow("Header Size", "${elf.header.ehSize} bytes")
        KeyValueRow("Program Headers", "${elf.header.phNum} entries")
        KeyValueRow("Section Headers", "${elf.header.shNum} entries")
    }
}

@Composable
fun ProgramHeadersTab(elf: ElfFile) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(elf.programHeaders) { ph ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(ph.typeName, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Offset: 0x${ph.offset.toString(16)} | VAddr: 0x${ph.vaddr.toString(16)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("FileSz: ${ph.fileSize} | MemSz: ${ph.memSize}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Flags: ${ph.flagsString} | Align: ${ph.align}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SectionsTab(elf: ElfFile) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(elf.sections) { sh ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(sh.name.ifEmpty { "<unnamed>" }, fontWeight = FontWeight.Bold)
                    Text(sh.typeName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Addr: 0x${sh.addr.toString(16)} | Offset: 0x${sh.offset.toString(16)} | Size: ${sh.size}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("EntSize: ${sh.entSize} | Align: ${sh.align}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DynamicTab(elf: ElfFile) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(elf.dynamicTable) { dyn ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text(dyn.tagName, fontWeight = FontWeight.Bold)
                    Text("Value: 0x${dyn.value.toString(16)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    if (dyn.stringValue != null) {
                        Text("String: ${dyn.stringValue}", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DependenciesTab(elf: ElfFile) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("SONAME", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(elf.soname ?: "<Not defined>", color = MaterialTheme.colorScheme.primary)
        
        Spacer(Modifier.height(16.dp))
        
        Text("Needed Libraries (${elf.neededLibraries.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        elf.neededLibraries.forEach { lib ->
            Text("• $lib", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun SymbolsTab(elf: ElfFile) {
    val imports = elf.imports
    val exports = elf.exports
    
    Column {
        var showExports by remember { mutableStateOf(true) }
        TabRow(selectedTabIndex = if (showExports) 0 else 1) {
            Tab(selected = showExports, onClick = { showExports = true }, text = { Text("Exports (${exports.size})") })
            Tab(selected = !showExports, onClick = { showExports = false }, text = { Text("Imports (${imports.size})") })
        }
        
        val list = if (showExports) exports else imports
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list) { sym ->
                Card {
                    Column(Modifier.padding(8.dp)) {
                        Text(sym.name.ifEmpty { "<unnamed>" }, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("${sym.binding} | ${sym.type} | Value: 0x${sym.value.toString(16)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun CompatibilityTab(viewModel: ElfAnalyzerViewModel) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val res = viewModel.validationResult
        if (res != null) {
            val color = when (res.status) {
                com.example.ui.analyzer.elf.engine.ElfStatus.VALID -> MaterialTheme.colorScheme.primaryContainer
                com.example.ui.analyzer.elf.engine.ElfStatus.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
            Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(res.status.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    res.messages.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}
