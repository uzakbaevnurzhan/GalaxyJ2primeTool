package com.example.data.manager

import android.content.Context
import com.example.ui.studio.workspace.RomProject
import com.example.ui.studio.workspace.WorkspaceManager
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
            SearchResult("Tools", "Root Center & Capabilities", "Verify root privileges & Magisk", "root_center"),
            SearchResult("Tools", "Device Center (Audit)", "16-category hardware & kernel specs", "device_info"),
            SearchResult("Tools", "ADB & Fastboot Studio", "Interactive terminal console", "adb_fastboot"),
            SearchResult("Tools", "USB Host & Samsung Odin", "Samsung TAR/MD5 & Safe Flash", "usb_host_center"),
            SearchResult("Tools", "Boot Diagnostic Pipeline", "12-stage boot sequence verifier", "boot_diagnostic"),
            SearchResult("Tools", "ROM Patcher & Configuration", "Offline patcher with rollback", "rom_patcher"),
            SearchResult("Tools", "ROM Build & Repack Studio", "Complete packaging pipeline", "rom_build"),
            SearchResult("Tools", "ROM Studio", "Workspace environment for ROMs", "rom_studio"),
            SearchResult("Tools", "ROM Image Analyzer", "RAW, Sparse, EXT4, EROFS scan", "image_analyzer"),
            SearchResult("Tools", "Vendor / HAL / RIL Analyzer", "Vendor partition & HALs", "vendor_analyzer"),
            SearchResult("Tools", "ROM Analyzer", "System and vendor images", "rom_analyzer"),
            SearchResult("Tools", "ROM Compare", "Diff files between ROMs", "rom_compare"),
            SearchResult("Tools", "Compatibility Check", "Treble / ABI checks", "compatibility_check"),
            SearchResult("Tools", "Report Generator", "Multi-format markdown/json reports", "report_generator"),
            SearchResult("Tools", "Task Center", "Active & historic background tasks", "task_center"),
            SearchResult("Tools", "Error Center", "System errors & diagnostics", "error_center"),
            SearchResult("Tools", "Build Tool Registry", "Local toolchain checker", "build_tool_registry"),
            SearchResult("Tools", "Hash Calculator", "MD5 & SHA hashes", "hash_calculator")
        )
        results.addAll(allTools.filter { it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) })

        // 2. Search Projects & Workspace files
        val rootDir = File(context.filesDir, "rom_studio")
        if (rootDir.exists()) {
            rootDir.listFiles()?.forEach { projDir ->
                val project = WorkspaceManager.loadProject(projDir.absolutePath)
                if (project != null) {
                    if (project.name.lowercase().contains(q) || project.id.lowercase().contains(q)) {
                        results.add(
                            SearchResult("Projects", project.name, "ROM Project Workspace", "rom_workspace/${project.id}")
                        )
                    }

                    // Search workspace files
                    val wsDir = File(projDir, "workspace")
                    if (wsDir.exists()) {
                        wsDir.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { file ->
                            if (file.name.lowercase().contains(q)) {
                                results.add(
                                    SearchResult(
                                        "Files",
                                        file.name,
                                        "In project: ${project.name} (${file.length()} B)",
                                        "file_explorer/${project.id}",
                                        matchSnippet = file.path
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Search Tasks
        TaskManager.tasks.value.forEach { task ->
            if (task.title.lowercase().contains(q) || task.description.lowercase().contains(q) || task.type.lowercase().contains(q)) {
                results.add(
                    SearchResult("Tasks", task.title, "Status: ${task.status.name} | Stage: ${task.currentStage}", "task_center")
                )
            }
        }

        // 4. Search Errors
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
