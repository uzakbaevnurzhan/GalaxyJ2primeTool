package com.example.ui.analyzer.selinux.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class RootCollectionResult {
    data class Success(val logs: String, val getenforce: String, val lineCount: Int) : RootCollectionResult()
    data class Error(val message: String) : RootCollectionResult()
}

object RootLogCollector {

    /**
     * Checks if root ('su') is available and accessible.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = withTimeoutOrNull(3000L) {
                process.waitFor()
            }
            if (exitCode == null) {
                process.destroy()
                return@withContext false
            }
            if (exitCode == 0) {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                return@withContext output.contains("uid=0")
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Collects live SELinux status and AVC denial logs using root commands.
     */
    suspend fun collectLiveLogs(): RootCollectionResult = withContext(Dispatchers.IO) {
        try {
            if (!isRootAvailable()) {
                return@withContext RootCollectionResult.Error("Root (su) permission is not granted or not available on this device.")
            }

            // 1. Get SELinux enforce status
            val getenforceProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "getenforce"))
            withTimeoutOrNull(3000L) { getenforceProcess.waitFor() }
            val enforceStatus = getenforceProcess.inputStream.bufferedReader().use { it.readText().trim() }

            // 2. Collect dmesg and logcat AVC logs
            val command = "dmesg | grep -i avc; logcat -d -b all | grep -i avc"
            val logProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            
            val logBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(logProcess.inputStream))
            var line = reader.readLine()
            var count = 0

            while (line != null) {
                logBuilder.appendLine(line)
                count++
                line = reader.readLine()
            }

            val exitCode = withTimeoutOrNull(10000L) { logProcess.waitFor() } ?: -1

            if (exitCode != 0 && count == 0) {
                val errorOutput = logProcess.errorStream.bufferedReader().use { it.readText() }
                return@withContext RootCollectionResult.Error("Failed to collect logs (exit code $exitCode): $errorOutput")
            }

            if (count == 0) {
                return@withContext RootCollectionResult.Success(
                    logs = "# No active AVC denials found in dmesg or logcat.",
                    getenforce = enforceStatus.ifBlank { "Unknown" },
                    lineCount = 0
                )
            }

            RootCollectionResult.Success(
                logs = logBuilder.toString(),
                getenforce = enforceStatus.ifBlank { "Unknown" },
                lineCount = count
            )
        } catch (e: Exception) {
            RootCollectionResult.Error("Exception while executing root collection: ${e.message}")
        }
    }
}
