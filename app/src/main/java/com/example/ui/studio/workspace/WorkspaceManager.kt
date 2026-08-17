package com.example.ui.studio.workspace

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object WorkspaceManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun getProjectDir(context: Context, projectId: String): File {
        return File(context.filesDir, "rom_studio/$projectId")
    }

    suspend fun createProject(
        context: Context,
        projectName: String,
        device: String = "Samsung Galaxy J2 Prime (SM-G532F)",
        androidVersion: String = "6.0.1",
        architecture: String = "arm32 (armv7-a)"
    ): RomProject = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "rom_studio/${UUID.randomUUID()}")
        root.mkdirs()
        File(root, "input").mkdirs()
        File(root, "workspace").mkdirs()
        File(root, "output").mkdirs()
        File(root, "backups").mkdirs()
        File(root, "reports").mkdirs()
        File(root, "metadata").mkdirs()
        File(root, "logs").mkdirs()
        File(root, "snapshots").mkdirs()
        File(root, "patches").mkdirs()
        File(root, "comparisons").mkdirs()

        val project = RomProject(
            id = root.name,
            name = projectName,
            createdAt = System.currentTimeMillis(),
            rootPath = root.absolutePath,
            device = device,
            androidVersion = androidVersion,
            architecture = architecture
        )
        saveProjectMetadata(project)
        project
    }

    suspend fun saveProjectMetadata(project: RomProject) = withContext(Dispatchers.IO) {
        val metadataFile = File(project.rootPath, "metadata/project.json")
        metadataFile.parentFile?.mkdirs()
        metadataFile.writeText(json.encodeToString(project))
    }

    suspend fun loadProject(rootPath: String): RomProject? = withContext(Dispatchers.IO) {
        val metadataFile = File(rootPath, "metadata/project.json")
        if (metadataFile.exists()) {
            try {
                json.decodeFromString<RomProject>(metadataFile.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun loadAllProjects(context: Context): List<RomProject> = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "rom_studio")
        if (!root.exists()) return@withContext emptyList()
        root.listFiles()?.mapNotNull { loadProject(it.absolutePath) }?.sortedByDescending { it.createdAt } ?: emptyList()
    }

    suspend fun renameProject(context: Context, project: RomProject, newName: String): RomProject = withContext(Dispatchers.IO) {
        val updated = project.copy(name = newName)
        saveProjectMetadata(updated)
        updated
    }

    suspend fun duplicateProject(context: Context, original: RomProject, newName: String): RomProject = withContext(Dispatchers.IO) {
        val newProject = createProject(context, newName, original.device, original.androidVersion, original.architecture)
        val origDir = File(original.rootPath)
        val newDir = File(newProject.rootPath)
        
        // Copy workspace and metadata contents
        origDir.copyRecursively(newDir, overwrite = true)
        val finalizedProject = newProject.copy(
            id = newDir.name,
            name = newName,
            createdAt = System.currentTimeMillis(),
            rootPath = newDir.absolutePath
        )
        saveProjectMetadata(finalizedProject)
        finalizedProject
    }

    suspend fun deleteProject(context: Context, project: RomProject) = withContext(Dispatchers.IO) {
        val root = File(project.rootPath)
        if (root.exists()) {
            root.deleteRecursively()
        }
    }

    suspend fun exportProjectAsZip(
        context: Context,
        project: RomProject,
        outputUri: Uri,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress("Indexing project files...", 0.1f)
        val projectDir = File(project.rootPath)
        val allFiles = projectDir.walkTopDown().filter { it.isFile }.toList()
        val total = allFiles.size.coerceAtLeast(1)

        context.contentResolver.openOutputStream(outputUri)?.use { fos ->
            ZipOutputStream(fos.buffered()).use { zos ->
                val buffer = ByteArray(64 * 1024)
                allFiles.forEachIndexed { index, file ->
                    val relPath = file.relativeTo(projectDir).path.replace("\\", "/")
                    val entry = ZipEntry(relPath)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)

                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                        }
                    }
                    zos.closeEntry()

                    if (index % 10 == 0) {
                        onProgress("Exporting: $relPath", 0.1f + 0.85f * (index.toFloat() / total))
                    }
                }
            }
        }
        onProgress("Export complete!", 1.0f)
    }

    suspend fun importProjectFromZip(
        context: Context,
        inputUri: Uri,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): RomProject = withContext(Dispatchers.IO) {
        onProgress("Creating destination directory...", 0.1f)
        val newId = UUID.randomUUID().toString()
        val targetDir = File(context.filesDir, "rom_studio/$newId")
        targetDir.mkdirs()

        onProgress("Extracting project archive...", 0.3f)
        context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val destFile = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out ->
                            zip.copyTo(out)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }

        onProgress("Validating imported metadata...", 0.8f)
        val loaded = loadProject(targetDir.absolutePath)
        val finalProject = if (loaded != null) {
            val updated = loaded.copy(id = newId, rootPath = targetDir.absolutePath)
            saveProjectMetadata(updated)
            updated
        } else {
            val created = RomProject(
                id = newId,
                name = "Imported_${newId.take(6)}",
                createdAt = System.currentTimeMillis(),
                rootPath = targetDir.absolutePath
            )
            saveProjectMetadata(created)
            created
        }

        onProgress("Import complete!", 1.0f)
        finalProject
    }
}
