package com.example.ui.analyzer.elf.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ElfNode(
    val name: String,
    val uri: Uri,
    val size: Long,
    val architecture: String,
    val soname: String?,
    val dependencies: List<String>
)

object ElfDependencyScanner {
    suspend fun scanDirectory(context: Context, treeUri: Uri, onProgress: (String) -> Unit): List<ElfNode> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val results = mutableListOf<ElfNode>()
        
        suspend fun traverse(dir: DocumentFile) {
            ensureActive()
            for (file in dir.listFiles()) {
                ensureActive()
                if (file.isDirectory) {
                    traverse(file)
                } else {
                    val name = file.name ?: ""
                    if (name.endsWith(".so") || name.endsWith(".elf") || name.endsWith(".ko")) {
                        onProgress("Analyzing $name...")
                        try {
                            val elf = ElfParserEngine.parse(context, file.uri)
                            results.add(
                                ElfNode(
                                    name = name,
                                    uri = file.uri,
                                    size = file.length(),
                                    architecture = elf.header.architectureName,
                                    soname = elf.soname,
                                    dependencies = elf.neededLibraries
                                )
                            )
                        } catch (e: Exception) {
                            // Skip invalid or non-ELF files gracefully
                        }
                    }
                }
            }
        }
        
        traverse(root)
        results
    }
}
