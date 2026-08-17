package com.example.ui.studio.workspace

import java.io.File
import java.security.MessageDigest

object RomFileManager {
    fun hashFile(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (it.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun scanWorkspace(project: RomProject): List<WorkspaceFile> {
        val workspaceDir = File(project.rootPath, "workspace")
        val result = mutableListOf<WorkspaceFile>()
        
        fun scanDir(dir: File) {
            dir.listFiles()?.forEach { file ->
                val relativePath = file.absolutePath.removePrefix(workspaceDir.absolutePath + "/")
                result.add(
                    WorkspaceFile(
                        name = file.name,
                        path = relativePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0L,
                        modifiedTime = file.lastModified()
                    )
                )
                if (file.isDirectory) scanDir(file)
            }
        }
        
        if (workspaceDir.exists()) scanDir(workspaceDir)
        return result
    }
}
