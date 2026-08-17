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
import com.example.ui.common.AppTopBar

data class ToolItem(val title: String, val description: String, val route: String, val icon: ImageVector)
data class ToolCategory(val name: String, val tools: List<ToolItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavController) {
    val categories = listOf(
        ToolCategory(
            "Flashing & Hardware Services",
            listOf(
                ToolItem("Samsung Odin & Firmware Service", "Tar/Tar.md5 unpacker, MD5 checksum verifier, partition mapper & Safe Flash Plan", "samsung_firmware", Icons.Filled.CloudDownload),
                ToolItem("Safe Flash Pre-Check", "Assess partition size limits, overflow detection & brick risk analysis", "flash_precheck", Icons.Filled.Shield),
                ToolItem("Boot Modes & Reboot Tool", "Hardware reboot triggers: System, Recovery (TWRP), Odin Download, Bootloader & Soft Reboot", "boot_modes", Icons.Filled.RestartAlt),
                ToolItem("ADB & Fastboot Studio", "Integrated multi-mode terminal with safe presets, execution history & Error 127 diagnostics", "adb_fastboot", Icons.Filled.Terminal),
                ToolItem("USB Host Center", "OTG probe, device enumeration, Samsung VID/PID scanner & descriptor dump", "usb_host_center", Icons.Filled.Usb)
            )
        ),
        ToolCategory(
            "Root & Device Management",
            listOf(
                ToolItem("Root Center & Privileges", "Capability matrix, privileged shell, mount tables & Magisk module inspector", "root_center", Icons.Filled.Security),
                ToolItem("Device Center (Audit)", "Comprehensive 16-category hardware, thermal, sensor & kernel specifications", "device_info", Icons.Filled.PhoneAndroid),
                ToolItem("Boot Diagnostic Pipeline", "12-stage boot sequence verifier from bootloader to Android launcher", "boot_diagnostic", Icons.Filled.CheckCircle),
                ToolItem("Build Tool Registry", "Local toolchain verifier for simg2img, mkbootimg, brotli, and lz4", "build_tool_registry", Icons.Filled.Handyman)
            )
        ),
        ToolCategory(
            "ROM Studio & Patcher",
            listOf(
                ToolItem("ROM Patcher & Configuration", "Offline patcher for modifying build.prop, init.rc, XMLs, and binaries with rollback", "rom_patcher", Icons.Filled.BuildCircle),
                ToolItem("ROM Build & Repack Studio", "Complete pipeline for validating, compiling partitions, and assembling flashable ROMs", "rom_builder", Icons.Filled.BuildCircle),
                ToolItem("ROM Studio Workspace", "Offline environment for unpacking, modifying and repacking ROMs", "rom_studio", Icons.Filled.Build)
            )
        ),
        ToolCategory(
            "Image & Partition Analyzers",
            listOf(
                ToolItem("ROM Image Analyzer", "Deep scan RAW, Sparse, EXT4, EROFS, Super images", "image_analyzer", Icons.Filled.Image),
                ToolItem("Partition Table Analyzer", "Analyze GPT/MBR tables, MTK scatter files & gaps", "partition_analyzer", Icons.Filled.Storage),
                ToolItem("Vendor / HAL / RIL Analyzer", "Deep scan Vendor partition, VINTF HALs & RIL telephony", "vendor_analyzer", Icons.Filled.Memory),
                ToolItem("ROM Analyzer", "Deep dive into system and vendor images", "rom_analyzer", Icons.Filled.Archive),
                ToolItem("DAT/DAT.BR Analyzer", "Analyze Android sparse data transfer lists", "dat_analyzer", Icons.Filled.DataArray),
                ToolItem("ROM Integrity & Hash Calculator", "Calculate MD5, SHA-1, SHA-256 signatures", "hash_calculator", Icons.Filled.Verified)
            )
        ),
        ToolCategory(
            "Kernel, Boot & Low-Level",
            listOf(
                ToolItem("Kernel & DTB Studio", "Analyze kernel, DTB/DTBO, CONFIG_*, cmdline & porting signals", "kernel_studio", Icons.Filled.DeveloperBoard),
                ToolItem("Boot Analyzer", "Analyze boot.img, extract kernel & ramdisk", "boot_analyzer", Icons.Filled.Memory),
                ToolItem("Init Script Analyzer", "Parse init.rc flow and security", "init_analyzer", Icons.Filled.Code),
                ToolItem("Fstab Analyzer", "Parse partition mounts and encryption", "fstab_analyzer", Icons.Filled.Storage),
                ToolItem("Kernel Crash Analyzer", "Parse last_kmsg and pstore ram-oops", "kernel_crash_analyzer", Icons.Filled.BugReport)
            )
        ),
        ToolCategory(
            "System & Library Tools",
            listOf(
                ToolItem("Build.prop Analyzer", "Analyze device properties & porting flags", "buildprop_analyzer", Icons.AutoMirrored.Filled.List),
                ToolItem("APK Inspector", "Analyze APK manifest, permissions and components", "apk_inspector", Icons.Filled.Android),
                ToolItem("ELF Library Analyzer", "Analyze .so and ELF binaries, symbols, and dependencies", "elf_analyzer", Icons.Filled.SettingsSystemDaydream),
                ToolItem("SELinux Policy Analyzer", "Analyze sepolicy, contexts and AVC denials", "selinux_analyzer", Icons.Filled.Security)
            )
        ),
        ToolCategory(
            "Comparison & Reporting",
            listOf(
                ToolItem("ROM Deep Merge", "Perform file & partition merges with conflict resolution", "rom_merge", Icons.Filled.CallMerge),
                ToolItem("Device & Project Snapshots", "Capture and diff system states before and after patches", "snapshot_manager", Icons.Filled.CameraAlt),
                ToolItem("ROM Compare", "Compare added/removed files between ROMs", "rom_compare", Icons.AutoMirrored.Filled.CompareArrows),
                ToolItem("Compatibility Check", "Check Treble/ABI compatibility", "compatibility_check", Icons.Filled.DeveloperBoard),
                ToolItem("Report Generator", "Generate markdown technical reports", "report_generator", Icons.Filled.Description)
            )
        ),
        ToolCategory(
            "Diagnostics & Management",
            listOf(
                ToolItem("Global Search", "Instant search across all tools, projects, and logs", "global_search", Icons.Filled.Search),
                ToolItem("Task Center", "Monitor active and background operations", "task_center", Icons.Filled.ListAlt),
                ToolItem("Error Diagnostic Center", "Diagnose and resolve tool and extraction errors", "error_center", Icons.Filled.Warning),
                ToolItem("System Log Analyzer", "Analyze Logcat, dmesg, and pstore logs", "log_analyzer", Icons.Filled.Monitor)
            )
        )
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Development Tools",
                subtitle = "Galaxy J2 Prime Toolchain • ${categories.sumOf { it.tools.size }} Tools",
                actions = {
                    IconButton(onClick = { navController.navigate("global_search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search Tools")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { category ->
                item {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                items(category.tools) { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(tool.route) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tool.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
