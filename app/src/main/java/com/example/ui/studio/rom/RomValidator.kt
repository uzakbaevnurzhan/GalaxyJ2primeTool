package com.example.ui.studio.rom

import com.example.ui.studio.workspace.RomProject
import java.io.File

object RomValidator {
    fun validatePreRepack(project: RomProject): RomValidationResult {
        val workspaceDir = File(project.rootPath, "workspace")
        if (!workspaceDir.exists() || workspaceDir.listFiles()?.isEmpty() == true) {
            return RomValidationResult(ValidationStatus.ERROR, listOf("Workspace is empty"))
        }

        val messages = mutableListOf<String>()
        var status = ValidationStatus.PASS

        // Check for common broken configurations
        val bootDir = File(workspaceDir, "boot")
        if (bootDir.exists()) {
            val kernelFile = File(bootDir, "kernel")
            if (!kernelFile.exists()) {
                messages.add("Missing kernel in boot workspace")
                status = ValidationStatus.ERROR
            }
        }

        // Just a basic implementation for now.
        // It will be integrated with existing ELf, SELinux analyzers later.
        
        return RomValidationResult(status, messages)
    }
}

enum class ValidationStatus {
    PASS, WARNING, ERROR
}

data class RomValidationResult(
    val status: ValidationStatus,
    val messages: List<String>
)
