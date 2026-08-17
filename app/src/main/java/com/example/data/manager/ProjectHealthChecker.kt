package com.example.data.manager

import android.content.Context
import com.example.data.model.HealthCheckItem
import com.example.data.model.ProjectHealthReport
import com.example.data.model.ProjectHealthStatus
import com.example.ui.studio.workspace.RomProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ProjectHealthChecker {

    suspend fun evaluateProjectHealth(
        context: Context,
        project: RomProject
    ): ProjectHealthReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<HealthCheckItem>()
        val projectDir = File(project.rootPath)
        val workspaceDir = File(projectDir, "workspace")
        val metadataDir = File(projectDir, "metadata")
        val snapshotsDir = File(projectDir, "snapshots")
        val outputDir = File(projectDir, "output")

        // 1. Workspace Files Check
        val workspaceFiles = if (workspaceDir.exists()) workspaceDir.walkTopDown().filter { it.isFile }.toList() else emptyList()
        val hasFiles = workspaceFiles.isNotEmpty()
        checks.add(
            HealthCheckItem(
                category = "Files",
                checkName = "Workspace File Presence",
                passed = hasFiles,
                isCritical = true,
                message = if (hasFiles) "Found ${workspaceFiles.size} files in workspace." else "Workspace directory is empty.",
                recommendation = if (!hasFiles) "Import a ROM image or device partition dump into workspace." else null
            )
        )

        // 2. ROM & System Partition Check
        val hasSystem = workspaceFiles.any { it.name.contains("system") || it.path.contains("system") }
        checks.add(
            HealthCheckItem(
                category = "ROM",
                checkName = "System Partition / Rootfs",
                passed = hasSystem,
                isCritical = false,
                message = if (hasSystem) "System tree detected in workspace." else "No system partition found.",
                recommendation = if (!hasSystem) "Unpack system.img or system.new.dat into workspace." else null
            )
        )

        // 3. Boot & Kernel Check
        val hasBoot = workspaceFiles.any { it.name.contains("boot") || it.path.contains("boot") || it.name == "zImage" || it.name == "Image.gz" }
        checks.add(
            HealthCheckItem(
                category = "Boot & Kernel",
                checkName = "Boot Image / Kernel Artifact",
                passed = hasBoot,
                isCritical = false,
                message = if (hasBoot) "Boot.img / Kernel payload detected." else "No kernel or boot image present.",
                recommendation = if (!hasBoot) "Ensure a compatible boot.img is provided for repacking." else null
            )
        )

        // 4. Properties Check
        val hasProps = workspaceFiles.any { it.name == "build.prop" || it.name.endsWith(".prop") } || File(metadataDir, "build.prop.dump").exists()
        checks.add(
            HealthCheckItem(
                category = "Properties",
                checkName = "Device Properties & Fingerprint",
                passed = hasProps,
                isCritical = false,
                message = if (hasProps) "build.prop or metadata properties available." else "No property manifests found.",
                recommendation = if (!hasProps) "Extract build.prop from ROM or capture device props." else null
            )
        )

        // 5. Vendor & HAL Check
        val hasVendor = workspaceFiles.any { it.name.contains("vendor") || it.path.contains("/hw/") || it.name.contains("manifest.xml") }
        checks.add(
            HealthCheckItem(
                category = "Vendor & HAL",
                checkName = "Vendor & Hardware HALs",
                passed = hasVendor,
                isCritical = false,
                message = if (hasVendor) "Vendor/HAL interfaces present." else "No vendor HAL trees detected.",
                recommendation = if (!hasVendor) "Include proprietary vendor HALs if porting to new Android version." else null
            )
        )

        // 6. SELinux Policy Check
        val hasSepolicy = workspaceFiles.any { it.name.contains("sepolicy") || it.name.contains("file_contexts") }
        checks.add(
            HealthCheckItem(
                category = "SELinux",
                checkName = "SELinux Contexts & Policies",
                passed = hasSepolicy,
                isCritical = false,
                message = if (hasSepolicy) "SELinux policy files present." else "SELinux contexts not detected.",
                recommendation = if (!hasSepolicy) "Verify file_contexts before flashing to avoid bootloop." else null
            )
        )

        // 7. Snapshots Check
        val snapshots = if (snapshotsDir.exists()) snapshotsDir.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList() else emptyList()
        val hasSnapshots = snapshots.isNotEmpty()
        checks.add(
            HealthCheckItem(
                category = "Snapshots",
                checkName = "Safety Snapshots",
                passed = hasSnapshots,
                isCritical = false,
                message = if (hasSnapshots) "${snapshots.size} safety snapshots available." else "No snapshots recorded yet.",
                recommendation = if (!hasSnapshots) "Create a device/project snapshot before applying heavy patches." else null
            )
        )

        // 8. Build readiness
        val hasOutput = if (outputDir.exists()) outputDir.listFiles()?.isNotEmpty() == true else false
        checks.add(
            HealthCheckItem(
                category = "Build",
                checkName = "ROM Output Status",
                passed = hasOutput || hasFiles,
                isCritical = false,
                message = if (hasOutput) "Built packages available in output directory." else "Workspace ready for compilation.",
                recommendation = null
            )
        )

        // Calculate score & status
        val totalScore = (checks.count { it.passed }.toFloat() / checks.size * 100).toInt()
        val criticalFailed = checks.any { it.isCritical && !it.passed }
        val nonCriticalFailed = checks.count { !it.passed }

        val status = when {
            criticalFailed -> ProjectHealthStatus.BLOCKED
            nonCriticalFailed == 0 -> ProjectHealthStatus.READY
            nonCriticalFailed <= 2 -> ProjectHealthStatus.READY_WITH_WARNINGS
            else -> ProjectHealthStatus.NOT_READY
        }

        ProjectHealthReport(
            status = status,
            score = totalScore,
            checks = checks
        )
    }
}
