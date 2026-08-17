package com.example.ui.studio.rom

import android.content.Context
import android.net.Uri
import com.example.ui.analyzer.boot.BootHeaderParser
import com.example.ui.studio.workspace.RomProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

object RomUnpackEngine {
    
    suspend fun unpack(
        context: Context,
        project: RomProject,
        sourceUri: Uri,
        fileName: String,
        onProgress: suspend (String, Float) -> Unit
    ): RomOperationResult = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
                ?: return@withContext RomOperationResult.Error("Could not open source URI")
            
            val inputFile = File(project.rootPath, "input/$fileName")
            pfd.use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { src ->
                    FileOutputStream(inputFile).channel.use { dst ->
                        dst.transferFrom(src, 0, src.size())
                    }
                }
            }
            
            val format = RomFormatDetector.detect(inputFile)
            onProgress("Detected format: $format", 0.1f)
            
            when (format) {
                RomFormat.BOOT_IMG -> unpackBootImg(inputFile, project, onProgress)
                RomFormat.SPARSE -> unpackSparse(inputFile, project, onProgress)
                RomFormat.DAT -> unpackDat(inputFile, project, onProgress)
                RomFormat.DAT_BR -> unpackDatBr(inputFile, project, onProgress)
                RomFormat.EXT4 -> unpackExt4(inputFile, project, onProgress)
                RomFormat.EROFS -> unpackErofs(inputFile, project, onProgress)
                RomFormat.SUPER -> unpackSuper(inputFile, project, onProgress)
                RomFormat.CPIO -> unpackCpio(inputFile, project, onProgress)
                else -> RomOperationResult.Error("Unsupported format: $format")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RomOperationResult.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun unpackBootImg(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult = withContext(Dispatchers.IO) {
        onProgress("Unpacking Boot Image...", 0.2f)
        try {
            val header = BootHeaderParser.parse(file)
            if (!header.isValid) return@withContext RomOperationResult.Error("Invalid boot image header")

            val workspaceDir = File(project.rootPath, "workspace/${file.nameWithoutExtension}")
            workspaceDir.mkdirs()

            RandomAccessFile(file, "r").use { raf ->
                // Extract Kernel
                if (header.kernelSize > 0) {
                    onProgress("Extracting Kernel...", 0.4f)
                    val kernelFile = File(workspaceDir, "kernel")
                    extractPart(raf, header.kernelOffset, header.kernelSize, kernelFile)
                }

                // Extract Ramdisk
                if (header.ramdiskSize > 0) {
                    onProgress("Extracting Ramdisk...", 0.6f)
                    val ramdiskFile = File(workspaceDir, "ramdisk.img")
                    extractPart(raf, header.ramdiskOffset, header.ramdiskSize, ramdiskFile)
                    
                    onProgress("Decompressing Ramdisk...", 0.7f)
                    val rawRamdisk = File(workspaceDir, "ramdisk_raw")
                    if (com.example.ui.studio.formats.CompressionHandler.decompress(ramdiskFile, rawRamdisk)) {
                        onProgress("Unpacking Ramdisk CPIO...", 0.8f)
                        val ramdiskOutDir = File(workspaceDir, "ramdisk")
                        com.example.ui.studio.formats.CpioHandler.unpack(rawRamdisk, ramdiskOutDir)
                    }
                }
            }
            
            onProgress("Boot Image unpacked successfully", 1.0f)
            com.example.ui.studio.workspace.WorkspaceTracker.createSnapshot(project)
            RomOperationResult.Success("Unpacked Boot Image successfully", workspaceDir.absolutePath)
        } catch (e: Exception) {
            RomOperationResult.Error("Failed to unpack boot image: ${e.message}")
        }
    }

    private suspend fun unpackSparse(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult = withContext(Dispatchers.IO) {
        onProgress("Unpacking Sparse Image...", 0.1f)
        val workspaceDir = File(project.rootPath, "workspace")
        workspaceDir.mkdirs()
        val rawFile = File(workspaceDir, file.name.replace(".sparse", "").replace(".img", "_raw.img"))
        val success = com.example.ui.studio.formats.SparseImageHandler.sparseToRaw(file, rawFile) { progress ->
            // Coroutines can launch this callback, but we need to pass progress
        }
        if (success) {
            com.example.ui.studio.workspace.WorkspaceTracker.createSnapshot(project)
            RomOperationResult.Success("Unpacked Sparse Image successfully", rawFile.absolutePath)
        } else {
            RomOperationResult.Error("Failed to unpack sparse image")
        }
    }

    private suspend fun unpackDat(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult = withContext(Dispatchers.IO) {
        onProgress("Unpacking DAT image...", 0.1f)
        val workspaceDir = File(project.rootPath, "workspace")
        workspaceDir.mkdirs()
        
        val transferList = File(file.parentFile, file.name.replace(".new.dat", ".transfer.list").replace(".dat", ".transfer.list"))
        if (!transferList.exists()) return@withContext RomOperationResult.Error("Missing transfer.list file for ${file.name}")
        
        val rawFile = File(workspaceDir, file.name.replace(".new.dat", ".img").replace(".dat", ".img"))
        val success = com.example.ui.studio.formats.DatHandler.datToRaw(transferList, file, rawFile)
        
        if (success) {
            com.example.ui.studio.workspace.WorkspaceTracker.createSnapshot(project)
            RomOperationResult.Success("Unpacked DAT to raw image successfully", rawFile.absolutePath)
        } else {
            RomOperationResult.Error("Failed to unpack DAT image")
        }
    }

    private suspend fun unpackDatBr(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult {
        return RomOperationResult.Error("UNSUPPORTED")
    }
    
    private suspend fun unpackExt4(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult {
        return RomOperationResult.Error("UNSUPPORTED")
    }

    private suspend fun unpackErofs(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult {
        return RomOperationResult.Error("EROFS extraction backend unavailable")
    }

    private suspend fun unpackSuper(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult {
        return RomOperationResult.Error("UNSUPPORTED")
    }
    
    private suspend fun unpackCpio(file: File, project: RomProject, onProgress: suspend (String, Float) -> Unit): RomOperationResult {
        return RomOperationResult.Error("UNSUPPORTED")
    }

    private fun extractPart(raf: RandomAccessFile, offset: Long, size: Long, outFile: File) {
        raf.seek(offset)
        FileOutputStream(outFile).use { fos ->
            val buf = ByteArray(4096)
            var bytesRead: Int
            var totalRead: Long = 0
            while (totalRead < size) {
                val toRead = (size - totalRead).coerceAtMost(buf.size.toLong()).toInt()
                bytesRead = raf.read(buf, 0, toRead)
                if (bytesRead == -1) break
                fos.write(buf, 0, bytesRead)
                totalRead += bytesRead
            }
        }
    }
}
