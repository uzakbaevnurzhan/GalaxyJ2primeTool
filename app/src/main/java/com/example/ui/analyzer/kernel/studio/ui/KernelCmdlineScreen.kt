package com.example.ui.analyzer.kernel.studio.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.analyzer.kernel.studio.models.CmdlineCategory
import com.example.ui.analyzer.kernel.studio.models.CmdlineComparisonItem
import com.example.ui.analyzer.kernel.studio.models.KernelCmdlineEntry

@Composable
fun KernelCmdlineScreen(
    cmdlines: List<KernelCmdlineEntry>,
    comparisons: List<CmdlineComparisonItem> = emptyList()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CmdlineCategory?>(null) }
    var showComparison by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    if (cmdlines.isEmpty() && comparisons.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Kernel Cmdline Parameters Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Load a boot.img or import /proc/cmdline from live device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        return
    }

    val filteredEntries = remember(cmdlines, searchQuery, selectedCategory) {
        cmdlines.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.key.contains(searchQuery, ignoreCase = true) ||
                    (item.value?.contains(searchQuery, ignoreCase = true) == true) ||
                    item.description.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == null || item.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (comparisons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compare Mode (boot.img vs /proc/cmdline)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showComparison, onCheckedChange = { showComparison = it })
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search cmdline parameter...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )

        if (!showComparison) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${cmdlines.size})") }
                )
                FilterChip(
                    selected = selectedCategory == CmdlineCategory.ANDROIDBOOT,
                    onClick = { selectedCategory = if (selectedCategory == CmdlineCategory.ANDROIDBOOT) null else CmdlineCategory.ANDROIDBOOT },
                    label = { Text("androidboot.*") }
                )
                FilterChip(
                    selected = selectedCategory == CmdlineCategory.CONSOLE,
                    onClick = { selectedCategory = if (selectedCategory == CmdlineCategory.CONSOLE) null else CmdlineCategory.CONSOLE },
                    label = { Text("Console / Earlycon") }
                )
                FilterChip(
                    selected = selectedCategory == CmdlineCategory.SELINUX,
                    onClick = { selectedCategory = if (selectedCategory == CmdlineCategory.SELINUX) null else CmdlineCategory.SELINUX },
                    label = { Text("SELinux") }
                )
                FilterChip(
                    selected = selectedCategory == CmdlineCategory.SECURITY,
                    onClick = { selectedCategory = if (selectedCategory == CmdlineCategory.SECURITY) null else CmdlineCategory.SECURITY },
                    label = { Text("Security / AVB") }
                )
                FilterChip(
                    selected = selectedCategory == CmdlineCategory.DEBUG,
                    onClick = { selectedCategory = if (selectedCategory == CmdlineCategory.DEBUG) null else CmdlineCategory.DEBUG },
                    label = { Text("Debug / Logs") }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (showComparison) {
                items(comparisons) { comp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (comp.status) {
                                "MATCH" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                "DIFFERENCE" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    comp.key,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Badge(
                                    containerColor = when (comp.status) {
                                        "MATCH" -> MaterialTheme.colorScheme.primaryContainer
                                        "DIFFERENCE" -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(comp.status, fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "boot.img: ${comp.bootValue ?: "<none>"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "live /proc: ${comp.liveValue ?: "<none>"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            } else {
                items(filteredEntries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(entry.raw))
                            snackbarMessage = "Copied ${entry.key}"
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.key,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        entry.category.name,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (entry.value != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "= ${entry.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (entry.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
