package com.example.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ToolsScreen(navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Development Tools", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            ToolCard(
                title = "ROM Analyzer",
                description = "Deep dive into system, vendor, and boot partitions. Find errors and warnings.",
                icon = Icons.Filled.Archive,
                onClick = { navController.navigate("rom_analyzer") }
            )
        }
        item {
            ToolCard(
                title = "Boot Analyzer",
                description = "Analyze boot.img structure, kernel, ramdisk, and init scripts.",
                icon = Icons.Filled.Memory,
                onClick = { navController.navigate("boot_analyzer") }
            )
        }
        item {
            ToolCard(
                title = "ROM Compare",
                description = "Compare two ROMs to find added, removed, or modified files.",
                icon = Icons.Filled.CompareArrows,
                onClick = { navController.navigate("rom_compare") }
            )
        }
        item {
            ToolCard(
                title = "Compatibility Check",
                description = "Check ROM against J2 Prime MT6737T profile.",
                icon = Icons.Filled.DeveloperBoard,
                onClick = { navController.navigate("compatibility_check") }
            )
        }
        item {
            ToolCard(
                title = "ROM ZIP Builder",
                description = "Safely package system, boot, and META-INF into a flashable ZIP.",
                icon = Icons.Filled.Build,
                onClick = { navController.navigate("rom_builder") }
            )
        }
    }
}

@Composable
fun ToolCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
