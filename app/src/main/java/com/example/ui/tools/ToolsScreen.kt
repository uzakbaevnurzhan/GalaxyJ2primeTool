package com.example.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class ToolItem(val title: String, val description: String, val route: String, val icon: ImageVector)
data class ToolCategory(val name: String, val tools: List<ToolItem>)

@Composable
fun ToolsScreen(navController: NavController) {
    val categories = listOf(
        ToolCategory(
            "ROM Tools",
            listOf(
                ToolItem("ROM Analyzer", "Deep dive into system and vendor images", "rom_analyzer", Icons.Filled.Archive),
                ToolItem("ROM Integrity Checker", "Check ROM file hashes and signatures", "hash_calculator", Icons.Filled.Verified),
                ToolItem("Sparse Image Analyzer", "Analyze Android Sparse Images (simg)", "unified_analyzer/sparse_analyzer", Icons.Filled.Image),
                ToolItem("DAT/DAT.BR Analyzer", "Analyze Android dat transfer lists", "unified_analyzer/dat_analyzer", Icons.Filled.DataArray),
            )
        ),
        ToolCategory(
            "Boot & Ramdisk Tools",
            listOf(
                ToolItem("Boot Analyzer", "Analyze boot.img, extract kernel & ramdisk", "boot_analyzer", Icons.Filled.Memory),
                ToolItem("Init Script Analyzer", "Parse init.rc flow and security", "init_analyzer", Icons.Filled.Code),
                ToolItem("Fstab Analyzer", "Parse partition mounts and encryption", "fstab_analyzer", Icons.Filled.Storage),
            )
        ),
        ToolCategory(
            "System & Library Tools",
            listOf(
                ToolItem("Build.prop Analyzer", "Analyze device properties", "buildprop_analyzer", Icons.Filled.List),
                ToolItem("APK Inspector", "Analyze APK manifest and permissions", "apk_inspector", Icons.Filled.Android),
                ToolItem("ELF Library Analyzer", "Analyze .so and ELF binaries", "unified_analyzer/elf_analyzer", Icons.Filled.SettingsSystemDaydream),
                ToolItem("SELinux Policy Analyzer", "Analyze sepolicy and contexts", "unified_analyzer/selinux_analyzer", Icons.Filled.Security),
            )
        ),
        ToolCategory(
            "File & Conversion Tools",
            listOf(
                ToolItem("Workspace Explorer", "Browse and modify extracted ROMs", "file_explorer", Icons.Filled.FolderOpen),
                ToolItem("Hex Viewer", "View binary files in hex format", "unified_analyzer/hex_viewer", Icons.Filled.DataArray),
                ToolItem("Text Viewer", "View raw text files", "unified_analyzer/text_viewer", Icons.Filled.TextSnippet),
                ToolItem("Hash Calculator", "Calculate MD5/SHA hashes", "hash_calculator", Icons.Filled.Tag),
            )
        ),
        ToolCategory(
            "Log Tools",
            listOf(
                ToolItem("System Log Analyzer", "Analyze Logcat, dmesg, and pstore logs", "log_analyzer", Icons.Filled.Monitor),
                ToolItem("Kernel Crash Analyzer", "Parse last_kmsg and pstore ram-oops", "unified_analyzer/kernel_crash", Icons.Filled.BugReport),
            )
        ),
        ToolCategory(
            "Build & Compare Tools",
            listOf(
                ToolItem("ROM Compare", "Compare added/removed files between ROMs", "rom_compare", Icons.Filled.CompareArrows),
                ToolItem("Compatibility Check", "Check Treble/ABI compatibility", "compatibility_check", Icons.Filled.DeveloperBoard),
                ToolItem("Report Generator", "Generate markdown technical reports", "report_generator", Icons.Filled.Description),
                ToolItem("ROM Builder", "Repack system and boot into flashable ZIP", "rom_builder", Icons.Filled.Build),
            )
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Development Tools", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }

        categories.forEach { category ->
            item {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(category.tools) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { navController.navigate(tool.route) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(tool.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
