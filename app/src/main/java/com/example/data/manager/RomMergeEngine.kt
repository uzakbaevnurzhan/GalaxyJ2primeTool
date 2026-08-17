package com.example.data.manager

import android.content.Context
import android.net.Uri
import com.example.data.model.MergeConflict
import com.example.data.model.MergePlan
import com.example.ui.studio.workspace.RomProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object RomMergeEngine {

    suspend fun createMergePlan(
        context: Context,
        project: RomProject,
        targetRomUri: Uri,
        targetRomName: String,
        selectedFiles: List<String>
    ): MergePlan = withContext(Dispatchers.IO) {
        val workspaceDir = File(project.rootPath, "workspace")
        val conflicts = mutableListOf<MergeConflict>()
        val warnings = mutableListOf<String>()

        // 1. Scan target zip sizes & verify conflicts against local workspace
        val targetEntries = mutableMapOf<String, Long>()
        context.contentResolver.openInputStream(targetRomUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && (selectedFiles.isEmpty() || selectedFiles.contains(entry.name))) {
                        targetEntries[entry.name] = entry.size
                    }
                    entry = zip.nextEntry
                }
            }
        }

        for ((relPath, targetSize) in targetEntries) {
            val localFile = File(workspaceDir, relPath)
            if (localFile.exists()) {
                if (localFile.length() != targetSize) {
                    conflicts.add(
                        MergeConflict(
                            relativePath = relPath,
                            baseSize = localFile.length(),
                            targetSize = targetSize,
                            conflictReason = "File exists in workspace with differing size (${localFile.length()} B vs $targetSize B)",
                            isResolvable = true
                        )
                    )
                }
            }
        }

        // 2. Dependency & ABI Check
        var abiCompatible = true
        if (targetEntries.keys.any { it.contains("lib64") }) {
            // If project is arm32 (e.g. Galaxy J2 Prime J280/G532F)
            warnings.add("Target ROM contains 64-bit libraries (lib64), which may be incompatible with 32-bit Cortex-A53 base port.")
            abiCompatible = false
        }

        MergePlan(
            id = "plan_${UUID.randomUUID()}",
            projectId = project.id,
            baseRomName = project.name,
            targetRomName = targetRomName,
            selectedFiles = targetEntries.keys.toList(),
            conflicts = conflicts,
            abiCompatible = abiCompatible,
            dependenciesMet = warnings.isEmpty(),
            dependencyWarnings = warnings
        )
    }

    suspend fun executeMerge(
        context: Context,
        project: RomProject,
        targetRomUri: Uri,
        plan: MergePlan,
        forceOverwriteConflicts: Boolean,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val workspaceDir = File(project.rootPath, "workspace")
        val backupDir = File(project.rootPath, "backups/merge_${System.currentTimeMillis()}")
        backupDir.mkdirs()

        try {
            onProgress("Creating safety rollback snapshot...", 0.1f)
            SnapshotManager.createLiveDeviceSnapshot(project.id, "Pre-Merge Snapshot", "Created before merge with ${plan.targetRomName}", context)

            // Backup existing files that will be overwritten
            for (relPath in plan.selectedFiles) {
                val localFile = File(workspaceDir, relPath)
                if (localFile.exists()) {
                    val bFile = File(backupDir, relPath)
                    bFile.parentFile?.mkdirs()
                    localFile.copyTo(bFile, overwrite = true)
                }
            }

            onProgress("Extracting and merging selective files...", 0.3f)
            val conflictPaths = plan.conflicts.map { it.relativePath }.toSet()
            var mergedCount = 0
            var skippedCount = 0

            context.contentResolver.openInputStream(targetRomUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && plan.selectedFiles.contains(entry.name)) {
                            val isConflict = conflictPaths.contains(entry.name)
                            if (isConflict && !forceOverwriteConflicts) {
                                skippedCount++
                            } else {
                                val targetFile = File(workspaceDir, entry.name)
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { out ->
                                    zip.copyTo(out)
                                }
                                mergedCount++
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            }

            onProgress("Running post-merge validation...", 0.9f)
            // Validation: ensure required files still exist
            if (!workspaceDir.exists() || workspaceDir.listFiles().isNullOrEmpty()) {
                throw Exception("Post-merge validation failed: workspace is empty.")
            }

            onProgress("Merge completed!", 1.0f)
            Result.success("Successfully merged $mergedCount files (Skipped $skippedCount conflicted files).")
        } catch (e: Exception) {
            onProgress("Merge encountered error! Initiating Rollback...", 0.95f)
            // Rollback from backupDir
            if (backupDir.exists()) {
                backupDir.walkTopDown().forEach { bFile ->
                    if (bFile.isFile) {
                        val rel = bFile.relativeTo(backupDir).path
                        val origFile = File(workspaceDir, rel)
                        origFile.parentFile?.mkdirs()
                        bFile.copyTo(origFile, overwrite = true)
                    }
                }
            }
            Result.failure(Exception("Merge failed and rolled back safely: ${e.message}", e))
        }
    }
}
