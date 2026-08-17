package com.example.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
            "Root & Device Management",
            listOf(
                ToolItem("Root Center & Capabilities", "Verify root privileges, capability matrix, by-name partitions & Magisk modules", "root_center", Icons.Filled.Security),
                ToolItem("Device Center (Audit)", "Comprehensive 16-category hardware, thermal, sensor & kernel specifications", "device_info", Icons.Filled.PhoneAndroid),
                ToolItem("ADB & Fastboot Studio", "Built-in interactive terminal with safe command presets and execution logs", "adb_fastboot", Icons.Filled.Terminal),
                ToolItem("USB Host & Samsung Odin", "OTG USB device probe, Samsung TAR/MD5 analyzer & Safe Flash Plan", "usb_host_center", Icons.Filled.Usb),
                ToolItem("Boot Diagnostic Pipeline", "12-stage boot sequence verifier from bootloader to launcher", "boot_diagnostic", Icons.Filled.CheckCircle),
                ToolItem("Build Tool Registry", "Local toolchain verifier for simg2img, mkbootimg, brotli, and lz4", "build_tool_registry", Icons.Filled.Handyman)
            )
        ),
        ToolCategory(
            "ROM Unpack / Repack Studio",
            listOf(
                ToolItem("ROM Patcher & Configuration", "Offline patcher for modifying build.prop, init.rc, XMLs, and binaries with rollback support", "rom_patcher", Icons.Filled.BuildCircle),
                ToolItem("ROM Build & Repack Studio", "Complete pipeline for validating, compiling partitions, and assembling flashable ROMs", "rom_builder", Icons.Filled.BuildCircle),
                ToolItem("ROM Studio", "Offline environment for unpacking, modifying and repacking ROMs", "rom_studio", Icons.Filled.Build)
            )
        ),
        ToolCategory(
            "ROM Tools",
            listOf(
                ToolItem("ROM Image Analyzer", "Deep scan RAW, Sparse, EXT4, EROFS, Super images", "image_analyzer", Icons.Filled.Image),
                ToolItem("Vendor / HAL / RIL Analyzer", "Deep scan Vendor partition, VINTF HALs & RIL telephony", "vendor_analyzer", Icons.Filled.Memory),
                ToolItem("ROM Analyzer", "Deep dive into system and vendor images", "rom_analyzer", Icons.Filled.Archive),
                ToolItem("ROM Integrity Checker", "Check ROM file hashes and signatures", "hash_calculator", Icons.Filled.Verified),
                ToolItem("DAT/DAT.BR Analyzer", "Analyze Android dat transfer lists", "dat_analyzer", Icons.Filled.DataArray),
            )
        ),
        ToolCategory(
            "Partition & Flashing Tools",
            listOf(
                ToolItem("Partition Table Analyzer", "Analyze GPT/MBR tables, MTK scatter files & gaps", "partition_analyzer", Icons.Filled.Storage),
                ToolItem("Safe Flash Pre-Check", "Check partition limits, image sizes & brick risks", "flash_precheck", Icons.Filled.Shield),
            )
        ),
        ToolCategory(
            "Boot & Ramdisk Tools",
            listOf(
                ToolItem("Kernel & DTB Studio", "Analyze kernel, DTB/DTBO, CONFIG_*, cmdline & porting signals", "kernel_studio", Icons.Filled.DeveloperBoard),
                ToolItem("Boot Analyzer", "Analyze boot.img, extract kernel & ramdisk", "boot_analyzer", Icons.Filled.Memory),
                ToolItem("Init Script Analyzer", "Parse init.rc flow and security", "init_analyzer", Icons.Filled.Code),
                ToolItem("Fstab Analyzer", "Parse partition mounts and encryption", "fstab_analyzer", Icons.Filled.Storage),
            )
        ),
        ToolCategory(
            "System & Library Tools",
            listOf(
                ToolItem("Build.prop Analyzer", "Analyze device properties", "buildprop_analyzer", Icons.AutoMirrored.Filled.List),
                ToolItem("APK Inspector", "Analyze APK manifest and permissions", "apk_inspector", Icons.Filled.Android),
                ToolItem("ELF Library Analyzer", "Analyze .so and ELF binaries", "elf_analyzer", Icons.Filled.SettingsSystemDaydream),
                ToolItem("SELinux Policy Analyzer", "Analyze sepolicy, contexts and AVC denials", "selinux_analyzer", Icons.Filled.Security),
            )
        ),
        ToolCategory(
            "File & Conversion Tools",
            listOf(
                ToolItem("Workspace Explorer", "Browse and modify extracted ROMs", "file_explorer", Icons.Filled.FolderOpen),
                ToolItem("Hex Viewer", "View binary files in hex format", "unified_analyzer/hex_viewer", Icons.Filled.DataArray),
                ToolItem("Text Viewer", "View raw text files", "unified_analyzer/text_viewer", Icons.AutoMirrored.Filled.TextSnippet),
                ToolItem("Hash Calculator", "Calculate MD5/SHA hashes", "hash_calculator", Icons.Filled.Tag),
            )
        ),
        ToolCategory(
            "Log Tools",
            listOf(
                ToolItem("System Log Analyzer", "Analyze Logcat, dmesg, and pstore logs", "log_analyzer", Icons.Filled.Monitor),
                ToolItem("Kernel Crash Analyzer", "Parse last_kmsg and pstore ram-oops", "kernel_crash_analyzer", Icons.Filled.BugReport),
            )
        ),
        ToolCategory(
            "Build & Compare Tools",
            listOf(
                ToolItem("ROM Deep Merge", "Perform file & partition merges with conflict resolution", "rom_merge", Icons.Filled.CallMerge),
                ToolItem("Device & Project Snapshots", "Capture and diff system states before and after patches", "snapshot_manager", Icons.Filled.CameraAlt),
                ToolItem("ROM Compare", "Compare added/removed files between ROMs", "rom_compare", Icons.AutoMirrored.Filled.CompareArrows),
                ToolItem("Compatibility Check", "Check Treble/ABI compatibility", "compatibility_check", Icons.Filled.DeveloperBoard),
                ToolItem("Report Generator", "Generate markdown technical reports", "report_generator", Icons.Filled.Description),
                ToolItem("ROM Builder & Repack", "Repack system and boot into flashable ZIP", "rom_builder", Icons.Filled.Build),
            )
        ),
        ToolCategory(
            "Diagnostics & Management",
            listOf(
                ToolItem("Global Search", "Instant search across all tools, projects, and logs", "global_search", Icons.Filled.Search),
                ToolItem("Task Center", "Monitor active and background operations", "task_center", Icons.Filled.ListAlt),
                ToolItem("Error Diagnostic Center", "Diagnose and resolve tool and extraction errors", "error_center", Icons.Filled.Warning),
            )
        )
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Development Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Complete mobile toolchain for Samsung Galaxy J2 Prime & Android ROM engineering", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
        }

        categories.forEach { category ->
            item {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(category.tools) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { navController.navigate(tool.route) },
                    shape = RoundedCornerShape(12.dp)
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
