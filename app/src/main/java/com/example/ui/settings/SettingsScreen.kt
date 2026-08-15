package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    var isDarkTheme by remember { mutableStateOf(true) }
    var askBeforeModify by remember { mutableStateOf(true) }
    var maxArchiveSize by remember { mutableStateOf("2048") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item { SettingsSectionTitle("Appearance") }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dark Theme")
                Switch(checked = isDarkTheme, onCheckedChange = { isDarkTheme = it })
            }
        }

        item { SettingsSectionTitle("Analysis") }
        item {
            OutlinedTextField(
                value = maxArchiveSize,
                onValueChange = { maxArchiveSize = it },
                label = { Text("Maximum archive size (MB)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        item { SettingsSectionTitle("Safety") }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ask before modifying files")
                Switch(checked = askBeforeModify, onCheckedChange = { askBeforeModify = it })
            }
        }

        item { SettingsSectionTitle("Storage") }
        item {
            Button(onClick = { /* Clear Cache */ }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Clear Cache & History")
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}
