package com.example.ui.analyzer.kernel.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class PstoreEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long
)

sealed class RootCollectResult<out T> {
    data class Success<out T>(val data: T) : RootCollectResult<T>()
    data class Error(val message: String) : RootCollectResult<Nothing>()
}

object RootKernelCollector {

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun collectDmesg(): RootCollectResult<String> = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext RootCollectResult.Error("Root unavailable — select a log file manually.")
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "dmesg"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) {
                RootCollectResult.Success(output)
            } else {
                RootCollectResult.Error("dmesg returned exit code $exitCode or was empty.")
            }
        } catch (e: Exception) {
            RootCollectResult.Error("Failed to execute dmesg: ${e.message}")
        }
    }

    suspend fun listPstoreFiles(): RootCollectResult<List<PstoreEntry>> = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext RootCollectResult.Error("Root unavailable — select a log file manually.")
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "ls -l /sys/fs/pstore /proc/last_kmsg 2>/dev/null"))
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            process.waitFor()

            val entries = mutableListOf<PstoreEntry>()
            for (line in lines) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val size = parts[4].toLongOrNull() ?: 0L
                    val path = parts.last()
                    if (path.contains("ramoops") || path.contains("pmsg") || path.contains("last_kmsg")) {
                        entries.add(PstoreEntry(path, File(path).name, size))
                    }
                }
            }

            if (entries.isNotEmpty()) {
                RootCollectResult.Success(entries)
            } else {
                // If listing output didn't catch, try checking common paths directly
                val commonPaths = listOf(
                    "/sys/fs/pstore/console-ramoops",
                    "/sys/fs/pstore/console-ramoops-0",
                    "/sys/fs/pstore/dmesg-ramoops-0",
                    "/sys/fs/pstore/pmsg-ramoops-0",
                    "/proc/last_kmsg"
                )
                val directEntries = mutableListOf<PstoreEntry>()
                for (p in commonPaths) {
                    val checkProc = Runtime.getRuntime().exec(arrayOf("su", "-c", "[ -f $p ] && echo 1"))
                    val res = checkProc.inputStream.bufferedReader().readLine()
                    checkProc.waitFor()
                    if (res == "1") {
                        directEntries.add(PstoreEntry(p, File(p).name, 0L))
                    }
                }
                if (directEntries.isNotEmpty()) {
                    RootCollectResult.Success(directEntries)
                } else {
                    RootCollectResult.Error("No pstore/ramoops/last_kmsg files found on device.")
                }
            }
        } catch (e: Exception) {
            RootCollectResult.Error("Failed to list pstore: ${e.message}")
        }
    }

    suspend fun readRootFile(filePath: String): RootCollectResult<String> = withContext(Dispatchers.IO) {
        if (!isRootAvailable()) {
            return@withContext RootCollectResult.Error("Root unavailable — select a log file manually.")
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $filePath"))
            val content = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && content.isNotBlank()) {
                RootCollectResult.Success(content)
            } else {
                RootCollectResult.Error("Failed to read $filePath (exit code $exitCode)")
            }
        } catch (e: Exception) {
            RootCollectResult.Error("Failed to read $filePath: ${e.message}")
        }
    }
}
