package com.example.data.manager

import android.content.Context

object AdbToolProvider {
    suspend fun getStatus(context: Context): ToolMetadata {
        return ToolRegistry.probeTool(context, "adb")
    }

    suspend fun listDevices(context: Context): ToolExecutionResult {
        return ToolRegistry.executeCommand(context, "adb", listOf("devices", "-l"), useRootIfAvailable = true)
    }

    suspend fun getState(context: Context): ToolExecutionResult {
        return ToolRegistry.executeCommand(context, "adb", listOf("get-state"), useRootIfAvailable = true)
    }

    suspend fun runShell(context: Context, shellCmd: String): ToolExecutionResult {
        return ToolRegistry.executeCommand(context, "adb", listOf("shell", shellCmd), useRootIfAvailable = true)
    }

    suspend fun reboot(context: Context, targetMode: String = ""): ToolExecutionResult {
        val args = if (targetMode.isBlank()) listOf("reboot") else listOf("reboot", targetMode)
        return ToolRegistry.executeCommand(context, "adb", args, useRootIfAvailable = true)
    }
}

object FastbootToolProvider {
    suspend fun getStatus(context: Context): ToolMetadata {
        return ToolRegistry.probeTool(context, "fastboot")
    }

    suspend fun listDevices(context: Context): ToolExecutionResult {
        return ToolRegistry.executeCommand(context, "fastboot", listOf("devices"), useRootIfAvailable = true)
    }

    suspend fun getVar(context: Context, variable: String = "all"): ToolExecutionResult {
        return ToolRegistry.executeCommand(context, "fastboot", listOf("getvar", variable), useRootIfAvailable = true)
    }

    suspend fun reboot(context: Context, targetMode: String = ""): ToolExecutionResult {
        val args = if (targetMode.isBlank()) listOf("reboot") else listOf("reboot", targetMode)
        return ToolRegistry.executeCommand(context, "fastboot", args, useRootIfAvailable = true)
    }
}
