package com.example.ui.tools

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ui.common.AppTopBar

data class ToolCapability(
    val label: String
)

data class ToolItem(
    val title: String,
    val description: String,
    val route: String,
    val icon: ImageVector,
    val category: String,
    val status: String = "READY",
    val rootRequired: Boolean = false,
    val inputType: String = "System / File",
    val capabilities: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val allTools = remember {
        listOf(
            // SYSTEM
            ToolItem(
                title = "Full System Analyzer",
                description = "Master orchestrator: Hardware, Kernel 3.18, HALs, RIL, SELinux, Partitions & Boot audit",
                route = "full_system_analyzer",
                icon = Icons.Filled.Troubleshoot,
                category = "SYSTEM",
                status = "ACTIVE",
                rootRequired = false,
                inputType = "Live Device / Specs",
                capabilities = listOf("Master Audit", "Root Cause Analysis", "Export Reports")
            ),
            ToolItem(
                title = "Compatibility Checker",
                description = "Verify Treble, ABI arch, and partition budget compatibility matrix",
                route = "compatibility_check",
                icon = Icons.Filled.CheckCircle,
                category = "SYSTEM",
                status = "READY",
                rootRequired = false,
                inputType = "Target Device / Spec",
                capabilities = listOf("Treble Check", "32-bit ABI Validation", "Partition Budget")
            ),
            ToolItem(
                title = "Safe Flash Pre-Check",
                description = "Assess partition size limits, overflow detection & brick risk",
                route = "flash_precheck",
                icon = Icons.Filled.Shield,
                category = "SYSTEM",
                status = "READY",
                rootRequired = false,
                inputType = "Firmware Image / Target",
                capabilities = listOf("Size Overflow", "Signature Verify", "Brick Risk Assessment")
            ),

            // ROM
            ToolItem(
                title = "ROM Analyzer",
                description = "Deep dive into system and vendor image structures and file tree",
                route = "rom_analyzer",
                icon = Icons.Filled.Archive,
                category = "ROM",
                status = "READY",
                rootRequired = false,
                inputType = "system.img / vendor.img / ZIP",
                capabilities = listOf("Tree Extraction", "File Inspection", "Hash Signature")
            ),

            // BOOT
            ToolItem(
                title = "Boot Analyzer",
                description = "Analyze boot.img, extract kernel, dtb, ramdisk, cmdline & page offsets",
                route = "boot_analyzer",
                icon = Icons.Filled.Memory,
                category = "BOOT",
                status = "READY",
                rootRequired = false,
                inputType = "boot.img / recovery.img",
                capabilities = listOf("Header Parsing", "Ramdisk Unpack", "MTK Header Check")
            ),

            // KERNEL
            ToolItem(
                title = "Kernel & DTB Studio",
                description = "Analyze kernel, DTB/DTBO, CONFIG_*, cmdline & porting signals",
                route = "kernel_studio",
                icon = Icons.Filled.DeveloperBoard,
                category = "KERNEL",
                status = "READY",
                rootRequired = false,
                inputType = "zImage / Image.gz-dtb / Live",
                capabilities = listOf("Symbol Scan", "Config Dump", "Cmdline Auditor")
            ),
            ToolItem(
                title = "Kernel Crash Analyzer",
                description = "Parse last_kmsg, pstore ram-oops, panic traces & console logs",
                route = "kernel_crash_analyzer",
                icon = Icons.Filled.BugReport,
                category = "KERNEL",
                status = "READY",
                rootRequired = false,
                inputType = "pstore / last_kmsg / log",
                capabilities = listOf("Panic Triage", "Oops Trace Parsing", "Call Stack Decode")
            ),

            // DTB
            ToolItem(
                title = "DTB / DTBO Inspector",
                description = "Device Tree Blob node explorer, compatible hardware & memory map",
                route = "kernel_studio",
                icon = Icons.Filled.AccountTree,
                category = "DTB",
                status = "READY",
                rootRequired = false,
                inputType = "dtb.img / kernel-dtb",
                capabilities = listOf("FDT Tree Parse", "Compatible Probe", "Memory Nodes")
            ),

            // IMAGE
            ToolItem(
                title = "ROM Image Analyzer",
                description = "Deep scan RAW, Sparse, EXT4, EROFS, F2FS & Super images",
                route = "image_analyzer",
                icon = Icons.Filled.Image,
                category = "IMAGE",
                status = "READY",
                rootRequired = false,
                inputType = ".img / .sparse / .raw",
                capabilities = listOf("Magic Header Detection", "Super Image Unpack", "Corruption Scan")
            ),
            ToolItem(
                title = "DAT / DAT.BR Analyzer",
                description = "Analyze Android sparse data transfer lists (system.new.dat.br) & Brotli",
                route = "dat_analyzer",
                icon = Icons.Filled.DataArray,
                category = "IMAGE",
                status = "READY",
                rootRequired = false,
                inputType = "system.new.dat[.br] / transfer.list",
                capabilities = listOf("Transfer List Parse", "Brotli Decompression", "Block Map")
            ),

            // PARTITIONS
            ToolItem(
                title = "Partition Table Analyzer",
                description = "Analyze GPT/MBR tables, Scatter files, offsets & mount flags",
                route = "partition_analyzer",
                icon = Icons.Filled.Storage,
                category = "PARTITIONS",
                status = "READY",
                rootRequired = false,
                inputType = "Scatter / GPT / /proc/partitions",
                capabilities = listOf("Scatter Table Map", "Boundary Validation", "Mount Flag Check")
            ),
            ToolItem(
                title = "Fstab Analyzer",
                description = "Parse partition mounts, flags, wait/check options and encryption parameters",
                route = "fstab_analyzer",
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                category = "PARTITIONS",
                status = "READY",
                rootRequired = false,
                inputType = "fstab.* / vendor/etc/fstab",
                capabilities = listOf("Mount Option Audit", "Encryption Type", "Flag Extraction")
            ),

            // ELF
            ToolItem(
                title = "ELF Library Analyzer",
                description = "Analyze .so shared libraries & binaries, DT_NEEDED, symbols & ABI",
                route = "elf_analyzer",
                icon = Icons.Filled.SettingsSystemDaydream,
                category = "ELF",
                status = "READY",
                rootRequired = false,
                inputType = ".so / ELF binary",
                capabilities = listOf("32/64-bit ABI Match", "DT_NEEDED Dependencies", "Missing Symbol Scan")
            ),

            // HAL & VENDOR
            ToolItem(
                title = "Vendor & HAL Analyzer",
                description = "Deep scan Vendor partition, VINTF HALs, manifests and binder services",
                route = "vendor_analyzer",
                icon = Icons.Filled.Hub,
                category = "HAL",
                status = "READY",
                rootRequired = false,
                inputType = "/vendor / vendor.img",
                capabilities = listOf("VINTF Manifest", "HIDL/AIDL Service Check", "Driver Dependency")
            ),

            // RIL
            ToolItem(
                title = "RIL Telephony Analyzer",
                description = "Audit MTK rild, libmtk-ril.so, baseband firmware and radio HAL",
                route = "vendor_analyzer",
                icon = Icons.Filled.SignalCellularAlt,
                category = "RIL",
                status = "READY",
                rootRequired = false,
                inputType = "/system/vendor/lib/libmtk-ril.so",
                capabilities = listOf("Radio Binary Check", "Telephony Properties", "Baseband Match")
            ),

            // SELINUX
            ToolItem(
                title = "SELinux Policy Analyzer",
                description = "Analyze sepolicy, file_contexts, types, permissions and AVC denials",
                route = "selinux_analyzer",
                icon = Icons.Filled.Security,
                category = "SELINUX",
                status = "READY",
                rootRequired = false,
                inputType = "sepolicy / file_contexts",
                capabilities = listOf("AVC Denials Parse", "Domain Rule Query", "Context Mapper")
            ),

            // LOGS
            ToolItem(
                title = "System Log Analyzer",
                description = "Live streaming and parsing for Logcat, dmesg & pstore with filters",
                route = "log_analyzer",
                icon = Icons.Filled.Monitor,
                category = "LOGS",
                status = "ACTIVE",
                rootRequired = false,
                inputType = "Live Stream / Log File",
                capabilities = listOf("PID / Tag Filter", "Fatal Crash Highlighting", "Export to TXT")
            ),

            // HARDWARE
            ToolItem(
                title = "Hardware Diagnostics",
                description = "Interactive test suite: Display, Touch, Sensors, Audio, Camera, Vibration",
                route = "boot_diagnostic",
                icon = Icons.Filled.Sensors,
                category = "HARDWARE",
                status = "READY",
                rootRequired = false,
                inputType = "SensorManager / CameraManager",
                capabilities = listOf("Real-time Sensor Test", "Multi-touch Grid", "Audio Probe")
            ),
            ToolItem(
                title = "Device Center (Audit)",
                description = "15-category hardware, thermal, sensor & kernel specifications audit",
                route = "device_info",
                icon = Icons.Filled.PhoneAndroid,
                category = "HARDWARE",
                status = "ACTIVE",
                rootRequired = false,
                inputType = "Live Hardware Telemetry",
                capabilities = listOf("15-Category Specs", "Thermal Monitor", "Sensor Registry")
            ),

            // ADB & FASTBOOT
            ToolItem(
                title = "ADB & Fastboot Studio",
                description = "Multi-mode terminal with safe presets, log inspection & execution history",
                route = "adb_fastboot",
                icon = Icons.Filled.Terminal,
                category = "ADB",
                status = "READY",
                rootRequired = false,
                inputType = "Command String / Shell",
                capabilities = listOf("Local Shell Execution", "Quick Presets", "Output Logging")
            ),

            // SAMSUNG
            ToolItem(
                title = "Samsung Odin & Firmware",
                description = "TAR / TAR.MD5 unpacker, MD5 checksum verifier & partition mapper (BL/AP/CP/CSC)",
                route = "samsung_firmware",
                icon = Icons.Filled.CloudDownload,
                category = "SAMSUNG",
                status = "READY",
                rootRequired = false,
                inputType = "TAR / TAR.MD5 package",
                capabilities = listOf("MD5 Verification", "AP/BL/CP Split", "MTK Scatter Extraction")
            ),
            ToolItem(
                title = "Boot Modes & Reboot Tool",
                description = "Hardware reboot triggers: System, TWRP Recovery, Download Mode, Bootloader",
                route = "boot_modes",
                icon = Icons.Filled.RestartAlt,
                category = "SAMSUNG",
                status = "READY",
                rootRequired = true,
                inputType = "System Trigger",
                capabilities = listOf("Reboot Recovery", "Reboot Download", "Reboot Bootloader")
            ),

            // ROOT
            ToolItem(
                title = "Root Center & Privileges",
                description = "Privilege matrix, root shell validation & Magisk module inspector",
                route = "root_center",
                icon = Icons.Filled.Security,
                category = "ROOT",
                status = "READY",
                rootRequired = false,
                inputType = "su binary / RootShell",
                capabilities = listOf("Magisk Detection", "UID 0 Test", "Mount RW Check")
            ),

            // FILES & UTILITIES
            ToolItem(
                title = "File Explorer & Manager",
                description = "Standalone browser, hex/text viewer, hash calculation & file operations",
                route = "file_explorer",
                icon = Icons.Filled.Folder,
                category = "FILES",
                status = "READY",
                rootRequired = false,
                inputType = "Storage / System Paths",
                capabilities = listOf("Hex Viewer", "Hash Calculator", "Analyzer Dispatch")
            ),
            ToolItem(
                title = "ROM Hash Calculator",
                description = "Calculate and verify MD5, SHA-1, SHA-256 signatures for images",
                route = "hash_calculator",
                icon = Icons.Filled.Verified,
                category = "FILES",
                status = "READY",
                rootRequired = false,
                inputType = "File / Image",
                capabilities = listOf("MD5", "SHA-1", "SHA-256")
            ),
            ToolItem(
                title = "Report Generator",
                description = "Export comprehensive technical audit reports in Markdown, JSON, and TXT",
                route = "report_generator",
                icon = Icons.Filled.Description,
                category = "SYSTEM",
                status = "READY",
                rootRequired = false,
                inputType = "Diagnostic Data",
                capabilities = listOf("Markdown", "JSON", "Plain Text")
            ),
            ToolItem(
                title = "Build Tool Registry",
                description = "Toolchain checker for simg2img, mkbootimg, brotli, and lz4 binaries",
                route = "build_tool_registry",
                icon = Icons.Filled.Handyman,
                category = "SYSTEM",
                status = "READY",
                rootRequired = false,
                inputType = "System Binaries",
                capabilities = listOf("Binary Presence", "ABI Compatibility", "Path Checker")
            ),
            ToolItem(
                title = "Build.prop & Getprop Analyzer",
                description = "Analyze system properties, porting flags, conflicts and values",
                route = "getprop_analyzer",
                icon = Icons.AutoMirrored.Filled.List,
                category = "SYSTEM",
                status = "READY",
                rootRequired = false,
                inputType = "getprop / build.prop",
                capabilities = listOf("Property Search", "Conflict Detection", "Export Props")
            ),
            ToolItem(
                title = "APK Inspector",
                description = "Analyze APK manifest, permissions, ABI binaries & package components",
                route = "apk_inspector",
                icon = Icons.Filled.Android,
                category = "FILES",
                status = "READY",
                rootRequired = false,
                inputType = ".apk file",
                capabilities = listOf("Manifest XML", "ABI Libs Check", "Permission Audit")
            ),
            ToolItem(
                title = "Init Script Analyzer",
                description = "Parse init.rc execution flows, trigger conditions, services and actions",
                route = "init_analyzer",
                icon = Icons.Filled.Code,
                category = "SYSTEM",
                status = "READY",
                rootRequired = false,
                inputType = "init.rc / *.rc",
                capabilities = listOf("Service Discovery", "Trigger Parsing", "On-Boot Sequences")
            )
        )
    }

    val categories = remember(allTools) {
        listOf("ALL") + allTools.map { it.category }.distinct()
    }

    val filteredTools = remember(searchQuery, selectedCategoryFilter, allTools) {
        allTools.filter { tool ->
            val matchesCategory = selectedCategoryFilter == "ALL" || tool.category == selectedCategoryFilter
            val matchesSearch = searchQuery.isBlank() ||
                    tool.title.contains(searchQuery, ignoreCase = true) ||
                    tool.description.contains(searchQuery, ignoreCase = true) ||
                    tool.category.contains(searchQuery, ignoreCase = true) ||
                    tool.capabilities.any { it.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Development Tools",
                subtitle = "${filteredTools.size} Active Tools • J2 Prime MT6737T",
                actions = {
                    IconButton(onClick = { navController.navigate("global_search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Global Search")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar & Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search tools, formats, capabilities...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Horizontal Category Chips
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategoryFilter).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    categories.forEach { cat ->
                        Tab(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            text = { Text(cat, fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }

            // Tools List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTools, key = { it.title + it.route }) { tool ->
                    ToolCardItem(
                        tool = tool,
                        onOpen = { navController.navigate(tool.route) }
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun ToolCardItem(
    tool: ToolItem,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Category: ${tool.category} • Input: ${tool.inputType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Badges
                if (tool.rootRequired) {
                    AssistChip(
                        onClick = {},
                        label = { Text("ROOT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        border = null
                    )
                }
            }

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Open Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("OPEN", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
