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
import com.example.ui.analyzer.kernel.studio.models.ConfigCategory
import com.example.ui.analyzer.kernel.studio.models.ConfigState
import com.example.ui.analyzer.kernel.studio.models.KernelConfig

@Composable
fun KernelConfigScreen(configs: List<KernelConfig>) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ConfigCategory?>(null) }
    var selectedState by remember { mutableStateOf<ConfigState?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    if (configs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Kernel Configurations Found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Kernel image may not have IKCONFIG or embedded symbols enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        return
    }

    val filteredConfigs = remember(configs, searchQuery, selectedCategory, selectedState) {
        configs.filter { config ->
            val matchesSearch = searchQuery.isBlank() ||
                    config.name.contains(searchQuery, ignoreCase = true) ||
                    config.value.contains(searchQuery, ignoreCase = true) ||
                    config.description.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == null || config.category == selectedCategory
            val matchesState = selectedState == null || config.state == selectedState

            matchesSearch && matchesCategory && matchesState
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search CONFIG_* or description...") },
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

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null && selectedState == null,
                onClick = {
                    selectedCategory = null
                    selectedState = null
                },
                label = { Text("All (${configs.size})") }
            )
            FilterChip(
                selected = selectedState == ConfigState.ENABLED,
                onClick = { selectedState = if (selectedState == ConfigState.ENABLED) null else ConfigState.ENABLED },
                label = { Text("Enabled") }
            )
            FilterChip(
                selected = selectedState == ConfigState.MODULE,
                onClick = { selectedState = if (selectedState == ConfigState.MODULE) null else ConfigState.MODULE },
                label = { Text("Module (=m)") }
            )
            FilterChip(
                selected = selectedCategory == ConfigCategory.ANDROID,
                onClick = { selectedCategory = if (selectedCategory == ConfigCategory.ANDROID) null else ConfigCategory.ANDROID },
                label = { Text("Android") }
            )
            FilterChip(
                selected = selectedCategory == ConfigCategory.FILESYSTEM,
                onClick = { selectedCategory = if (selectedCategory == ConfigCategory.FILESYSTEM) null else ConfigCategory.FILESYSTEM },
                label = { Text("Filesystem") }
            )
            FilterChip(
                selected = selectedCategory == ConfigCategory.SECURITY,
                onClick = { selectedCategory = if (selectedCategory == ConfigCategory.SECURITY) null else ConfigCategory.SECURITY },
                label = { Text("Security") }
            )
            FilterChip(
                selected = selectedCategory == ConfigCategory.NETWORK,
                onClick = { selectedCategory = if (selectedCategory == ConfigCategory.NETWORK) null else ConfigCategory.NETWORK },
                label = { Text("Network") }
            )
            FilterChip(
                selected = selectedCategory == ConfigCategory.HARDWARE,
                onClick = { selectedCategory = if (selectedCategory == ConfigCategory.HARDWARE) null else ConfigCategory.HARDWARE },
                label = { Text("Hardware") }
            )
        }

        Text(
            text = "Showing ${filteredConfigs.size} of ${configs.size} items",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredConfigs) { config ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    onClick = {
                        clipboardManager.setText(AnnotatedString("${config.name}=${config.value}"))
                        snackbarMessage = "Copied ${config.name}"
                    }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                config.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Badge(
                                containerColor = when (config.state) {
                                    ConfigState.ENABLED -> MaterialTheme.colorScheme.primaryContainer
                                    ConfigState.MODULE -> MaterialTheme.colorScheme.secondaryContainer
                                    ConfigState.DISABLED -> MaterialTheme.colorScheme.errorContainer
                                    ConfigState.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    config.value,
                                    color = when (config.state) {
                                        ConfigState.ENABLED -> MaterialTheme.colorScheme.onPrimaryContainer
                                        ConfigState.MODULE -> MaterialTheme.colorScheme.onSecondaryContainer
                                        ConfigState.DISABLED -> MaterialTheme.colorScheme.onErrorContainer
                                        ConfigState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (config.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                config.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Category: ${config.category.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                "Type: ${config.type.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
