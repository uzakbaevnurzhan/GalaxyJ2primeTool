package com.example.data.manager

import android.content.Context
import android.os.Build
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ToolStatus {
    AVAILABLE,
    SYSTEM_AVAILABLE,
    BUNDLED_AVAILABLE,
    MISSING_BACKEND,
    PERMISSION_DENIED,
    ABI_INCOMPATIBLE
}

data class ToolMetadata(
    val id: String,
    val name: String,
    val executableName: String,
    val status: ToolStatus,
    val resolvedPath: String?,
    val version: String?,
    val abi: String?,
    val isBundled: Boolean,
    val capabilities: List<String>,
    val errorMessage: String? = null
)

data class ToolExecutionResult(
    val command: String,
    val args: List<String>,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val isSuccess: Boolean,
    val diagnosticDetails: String? = null,
    val suggestedAction: String? = null
)

object ToolRegistry {

    private val commonSearchPaths = listOf(
        "/system/bin",
        "/system/xbin",
        "/vendor/bin",
        "/sbin",
        "/system/sbin",
        "/apex/com.android.runtime/bin"
    )

    suspend fun probeTool(context: Context, toolName: String): ToolMetadata = withContext(Dispatchers.IO) {
        val appBinDir = File(context.filesDir, "bin")
        val bundledFile = File(appBinDir, toolName)
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeLibFile = File(nativeLibDir, "lib$toolName.so")

        var resolvedPath: String? = null
        var isBundled = false

        if (bundledFile.exists() && bundledFile.canExecute()) {
            resolvedPath = bundledFile.absolutePath
            isBundled = true
        } else if (nativeLibFile.exists() && nativeLibFile.canExecute()) {
            resolvedPath = nativeLibFile.absolutePath
            isBundled = true
        } else {
            for (dir in commonSearchPaths) {
                val f = File(dir, toolName)
                if (f.exists()) {
                    resolvedPath = f.absolutePath
                    break
                }
            }
        }

        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        if (resolvedPath == null) {
            val envPath = System.getenv("PATH") ?: "unknown"
            return@withContext ToolMetadata(
                id = toolName,
                name = toolName.uppercase(),
                executableName = toolName,
                status = ToolStatus.MISSING_BACKEND,
                resolvedPath = null,
                version = null,
                abi = deviceAbi,
                isBundled = false,
                capabilities = emptyList(),
                errorMessage = "Executable '$toolName' was not found in bundled app directory (${appBinDir.path}) or system PATH ($envPath)."
            )
        }

        // Try getting version
        var versionStr: String? = null
        var status = if (isBundled) ToolStatus.BUNDLED_AVAILABLE else ToolStatus.SYSTEM_AVAILABLE

        try {
            val pb = ProcessBuilder(resolvedPath, "version")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (proc.exitValue() == 0 && output.isNotBlank()) {
                versionStr = output.lines().firstOrNull()
            }
        } catch (e: Exception) {
            // Version probing might differ per tool
            try {
                val pb = ProcessBuilder(resolvedPath, "--version")
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val output = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (output.isNotBlank()) {
                    versionStr = output.lines().firstOrNull()
                }
            } catch (e2: Exception) {
                // Ignore
            }
        }

        val caps = when (toolName.lowercase()) {
            "adb" -> listOf("Device enumeration", "Shell execution", "Logcat stream", "File Push/Pull", "Reboot trigger")
            "fastboot" -> listOf("Bootloader query", "Fastboot getvar", "Partition unlock check", "Flash readiness")
            "toybox", "busybox" -> listOf("POSIX utilities", "tar", "gzip", "dd", "sha256sum")
            else -> listOf("Command execution")
        }

        ToolMetadata(
            id = toolName,
            name = toolName.uppercase(),
            executableName = toolName,
            status = status,
            resolvedPath = resolvedPath,
            version = versionStr ?: "Detected ($resolvedPath)",
            abi = deviceAbi,
            isBundled = isBundled,
            capabilities = caps
        )
    }

    suspend fun executeCommand(
        context: Context,
        toolName: String,
        arguments: List<String>,
        useRootIfAvailable: Boolean = false
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val toolMeta = probeTool(context, toolName)
        val escapedArgs = arguments.joinToString(" ") { com.example.utils.SecurityUtil.escapeShellArg(it) }
        val fullCmdStr = "$toolName $escapedArgs"

        if (toolMeta.status == ToolStatus.MISSING_BACKEND) {
            // Check if root shell can execute it or if it's a shell built-in
            if (useRootIfAvailable && RootShell.isRootAvailable()) {
                val rootRes = RootShell.executeCommand(fullCmdStr)
                val dur = System.currentTimeMillis() - startTime
                if (rootRes.isSuccess) {
                    return@withContext ToolExecutionResult(
                        command = fullCmdStr,
                        args = arguments,
                        stdout = rootRes.getOrNull() ?: "",
                        stderr = "",
                        exitCode = 0,
                        durationMs = dur,
                        isSuccess = true
                    )
                }
            }

            val envPath = System.getenv("PATH") ?: "N/A"
            val abis = Build.SUPPORTED_ABIS.joinToString(", ")
            val diag = "Technical Details:\n" +
                    "• Exit Code: 127 (Command Not Found)\n" +
                    "• Tool: $toolName\n" +
                    "• Device ABIs: $abis\n" +
                    "• Searched Local: ${context.filesDir}/bin/$toolName\n" +
                    "• System PATH: $envPath\n" +
                    "• Status: Backend binary is not present in container or system environment."

            val suggestion = "Suggested Action: Use Root Shell fallback, connect via USB Host, or configure backend binary in Settings -> Tools."

            return@withContext ToolExecutionResult(
                command = fullCmdStr,
                args = arguments,
                stdout = "",
                stderr = "$toolName backend not found (Exit Code 127)",
                exitCode = 127,
                durationMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                diagnosticDetails = diag,
                suggestedAction = suggestion
            )
        }

        val execPath = toolMeta.resolvedPath ?: toolName
        val commandList = mutableListOf(execPath).apply { addAll(arguments) }

        var stdout = ""
        var stderr = ""
        var exitCode = 0

        try {
            val pb = ProcessBuilder(commandList)
            pb.directory(context.filesDir)
            val process = pb.start()

            val outStream = process.inputStream.bufferedReader()
            val errStream = process.errorStream.bufferedReader()

            stdout = outStream.readText()
            stderr = errStream.readText()

            process.waitFor()
            exitCode = process.exitValue()
        } catch (e: Exception) {
            stderr = "Execution Exception: ${e.message}"
            exitCode = 127
        }

        val duration = System.currentTimeMillis() - startTime
        val isSuccess = exitCode == 0

        var diag: String? = null
        var suggestion: String? = null

        if (exitCode == 127) {
            diag = "Technical Details:\n" +
                    "• Binary Path: $execPath\n" +
                    "• Exit Code: 127\n" +
                    "• Stderr: $stderr"
            suggestion = "Verify binary execution permissions (chmod +x) or ensure dependent shared libraries match device ABI."
        }

        ToolExecutionResult(
            command = fullCmdStr,
            args = arguments,
            stdout = stdout.trim(),
            stderr = stderr.trim(),
            exitCode = exitCode,
            durationMs = duration,
            isSuccess = isSuccess,
            diagnosticDetails = diag,
            suggestedAction = suggestion
        )
    }

    suspend fun executeRawShell(
        command: String,
        useRoot: Boolean = false
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var stdout = ""
        var stderr = ""
        var exitCode = 0

        try {
            val process = if (useRoot && RootShell.isRootAvailable()) {
                ProcessBuilder("su", "-c", command).start()
            } else {
                ProcessBuilder("sh", "-c", command).start()
            }

            val outReader = process.inputStream.bufferedReader()
            val errReader = process.errorStream.bufferedReader()

            stdout = outReader.readText()
            stderr = errReader.readText()

            process.waitFor()
            exitCode = process.exitValue()
        } catch (e: Exception) {
            stderr = "Shell Execution Exception: ${e.message}"
            exitCode = -1
        }

        val duration = System.currentTimeMillis() - startTime
        ToolExecutionResult(
            command = command,
            args = listOf(command),
            stdout = stdout.trim(),
            stderr = stderr.trim(),
            exitCode = exitCode,
            durationMs = duration,
            isSuccess = exitCode == 0,
            diagnosticDetails = if (exitCode != 0) "Exit code $exitCode from shell: $stderr" else null
        )
    }
}
