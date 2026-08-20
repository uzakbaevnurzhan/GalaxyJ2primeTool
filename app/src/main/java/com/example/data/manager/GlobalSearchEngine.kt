package com.example.data.manager

import android.content.Context
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GlobalSearchEngine {

    data class SearchResult(
        val category: String,
        val title: String,
        val subtitle: String,
        val targetRoute: String,
        val matchSnippet: String? = null
    )

    suspend fun searchAll(
        query: String,
        context: Context
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        val results = mutableListOf<SearchResult>()

        // 1. Search Tools
        val allTools = listOf(
            SearchResult("Analyzers", "Full System Analyzer", "Master orchestrator: Hardware, Kernel 3.18, HALs, RIL, SELinux", "full_system_analyzer"),
            SearchResult("Analyzers", "Boot Analyzer", "Analyze boot.img, extract kernel, dtb & ramdisk", "boot_analyzer"),
            SearchResult("Analyzers", "ROM Analyzer", "Deep dive into system and vendor image structures", "rom_analyzer"),
            SearchResult("Analyzers", "ROM Image Analyzer", "Deep scan RAW, Sparse, EXT4, EROFS, Super images", "image_analyzer"),
            SearchResult("Analyzers", "Partition Table Analyzer", "Analyze GPT/MBR tables, MTK scatter files & offsets", "partition_analyzer"),
            SearchResult("Analyzers", "Kernel & DTB Studio", "Analyze kernel, DTB/DTBO, CONFIG_*, cmdline & porting signals", "kernel_studio"),
            SearchResult("Analyzers", "ELF Library Analyzer", "Analyze .so and ELF binaries, symbols, ABI & dependencies", "elf_analyzer"),
            SearchResult("Analyzers", "DAT / DAT.BR Analyzer", "Analyze Android sparse data transfer lists & Brotli", "dat_analyzer"),
            SearchResult("Analyzers", "SELinux Policy Analyzer", "Analyze sepolicy, contexts and AVC denials", "selinux_analyzer"),
            SearchResult("Analyzers", "Build.prop & Getprop Analyzer", "Analyze system properties & porting flags", "getprop_analyzer"),
            SearchResult("Analyzers", "Fstab Analyzer", "Parse partition mounts, flags and encryption parameters", "fstab_analyzer"),
            SearchResult("Analyzers", "Init Script Analyzer", "Parse init.rc execution flows and trigger conditions", "init_analyzer"),
            SearchResult("Analyzers", "APK Inspector", "Analyze APK manifest, permissions, ABI & components", "apk_inspector"),

            SearchResult("Porting", "ROM Port Assistant", "8-stage compatibility matrix, ABI blockers & port plan", "rom_port_assistant"),
            SearchResult("Porting", "Compatibility Check", "Verify Treble, ABI, and architecture compatibility matrix", "compatibility_check"),
            SearchResult("Porting", "Safe Flash Pre-Check", "Assess partition size limits, overflow detection & brick risk", "flash_precheck"),
            SearchResult("Porting", "Vendor / HAL / RIL Analyzer", "Deep scan Vendor partition, VINTF HALs & RIL telephony", "vendor_analyzer"),

            SearchResult("Diagnostics", "Boot Diagnostic Pipeline", "12-stage boot sequence verifier from bootloader to launcher", "boot_diagnostic"),
            SearchResult("Diagnostics", "Kernel Crash Analyzer", "Parse last_kmsg, pstore ram-oops & panics", "kernel_crash_analyzer"),
            SearchResult("Diagnostics", "System Log Analyzer", "Live streaming and parsing for Logcat, dmesg & pstore", "log_analyzer"),

            SearchResult("Device Control", "Device Center", "Comprehensive hardware, thermal, sensor & kernel specifications", "device_info"),
            SearchResult("Device Control", "Root Center & Privileges", "Capability matrix, privileged shell & Magisk module inspector", "root_center"),
            SearchResult("Device Control", "Boot Modes & Reboot Tool", "Hardware reboot triggers: System, TWRP Recovery, Download, Bootloader", "boot_modes"),
            SearchResult("Device Control", "Samsung Odin & Firmware Service", "Tar/Tar.md5 unpacker, MD5 checksum verifier & partition mapper", "samsung_firmware"),

            SearchResult("Bridges", "ADB & Fastboot Studio", "Multi-mode terminal with safe presets & execution history", "adb_fastboot"),
            SearchResult("Bridges", "USB Host Center", "OTG probe, device enumeration, Samsung VID/PID scanner & descriptors", "usb_host_center"),

            SearchResult("Utilities", "File Explorer & Manager", "Standalone browser, hex/text viewer, hash calculation & file ops", "file_explorer"),
            SearchResult("Utilities", "Build Tool Registry", "Local toolchain verifier for simg2img, mkbootimg, brotli, and lz4", "build_tool_registry"),
            SearchResult("Utilities", "ROM Integrity & Hash Calculator", "Calculate and verify MD5, SHA-1, SHA-256 signatures", "hash_calculator"),
            SearchResult("Utilities", "Report Generator", "Export detailed markdown technical audit reports", "report_generator"),
            SearchResult("Utilities", "Task Center", "Monitor active and background operations", "task_center"),
            SearchResult("Utilities", "Error Diagnostic Center", "Diagnose and resolve tool and extraction errors", "error_center")
        )
        results.addAll(allTools.filter { it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) || it.category.lowercase().contains(q) })

        // 2. Search Tasks
        TaskManager.tasks.value.forEach { task ->
            if (task.title.lowercase().contains(q) || task.description.lowercase().contains(q) || task.type.lowercase().contains(q)) {
                results.add(
                    SearchResult("Tasks", task.title, "Status: ${task.status.name} | Stage: ${task.currentStage}", "task_center")
                )
            }
        }

        // 3. Search Errors
        ErrorCenterManager.errors.value.forEach { err ->
            if (err.message.lowercase().contains(q) || err.module.lowercase().contains(q) || err.operation.lowercase().contains(q)) {
                results.add(
                    SearchResult("Errors", "[${err.module}] ${err.operation}", err.message, "error_center", matchSnippet = err.suggestedAction)
                )
            }
        }

        results.take(40)
    }
}

