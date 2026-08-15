package com.example.utils

import java.io.BufferedReader
import java.io.InputStreamReader

object RootShell {
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output?.contains("uid=0(root)") == true
        } catch (e: Exception) {
            false
        }
    }

    fun executeCommand(command: String): Result<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }
            
            process.waitFor()
            if (process.exitValue() == 0) {
                Result.success(output.toString().trimEnd())
            } else {
                Result.failure(Exception(errorOutput.toString().trimEnd()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
