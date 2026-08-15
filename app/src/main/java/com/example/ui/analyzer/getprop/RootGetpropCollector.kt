package com.example.ui.analyzer.getprop

import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class LiveCollectionResult(
    val isSuccess: Boolean,
    val isRootUsed: Boolean,
    val rawProperties: RawParsedProperties?,
    val errorMessage: String? = null
)

object RootGetpropCollector {

    suspend fun collectLiveProperties(): LiveCollectionResult = withContext(Dispatchers.IO) {
        val isRoot = RootShell.isRootAvailable()
        
        if (isRoot) {
            val rootRes = RootShell.executeCommand("getprop")
            if (rootRes.isSuccess) {
                val output = rootRes.getOrNull() ?: ""
                val parsed = GetpropParser.parseString(output, "Live System Properties (root)")
                return@withContext LiveCollectionResult(
                    isSuccess = true,
                    isRootUsed = true,
                    rawProperties = parsed
                )
            }
        }

        // Non-root fallback: run standard getprop process
        try {
            val process = Runtime.getRuntime().exec("getprop")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            
            val errSb = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errSb.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            val output = sb.toString().trim()
            
            if (exitCode == 0 && output.isNotEmpty()) {
                val parsed = GetpropParser.parseString(output, "Live System Properties (non-root)")
                LiveCollectionResult(
                    isSuccess = true,
                    isRootUsed = false,
                    rawProperties = parsed
                )
            } else {
                LiveCollectionResult(
                    isSuccess = false,
                    isRootUsed = false,
                    rawProperties = null,
                    errorMessage = errSb.toString().ifBlank { "Exit code $exitCode from getprop" }
                )
            }
        } catch (e: Exception) {
            LiveCollectionResult(
                isSuccess = false,
                isRootUsed = false,
                rawProperties = null,
                errorMessage = e.message ?: "Failed to execute live getprop command"
            )
        }
    }
}
