package com.example.ui.analyzer.hal

import com.example.ui.analyzer.vendor.models.*

object HalCompatibilityAnalyzer {

    fun validateHalCompatibility(
        serviceItems: List<HalServiceMapItem>,
        trebleStatus: TrebleStatus
    ): List<VendorIssue> {
        val issues = mutableListOf<VendorIssue>()

        for (item in serviceItems) {
            // 1. Manifest declared, but binary missing in Treble ROM
            if (trebleStatus == TrebleStatus.TREBLE && item.manifestStatus == StageStatus.FOUND && item.binaryStatus == StageStatus.MISSING) {
                // If it's passthrough, binary is a .so instead of executable, so lower severity
                val isPassthrough = item.evidence.contains("PASSTHROUGH", ignoreCase = true)
                issues.add(
                    VendorIssue(
                        type = VendorIssueType.MISSING_BINARY,
                        severity = if (isPassthrough) Severity.INFO else Severity.ERROR,
                        message = "HAL '${item.halName}' declared in manifest, but matching binary was not found.",
                        evidence = item.evidence,
                        source = "HalCompatibilityAnalyzer",
                        confidence = Confidence.HIGH,
                        recommendation = if (isPassthrough) "Passthrough HALs load dynamic libraries (.so) into client process directly." else "Ensure service binary is placed in /vendor/bin/hw/ or /system/bin/hw/."
                    )
                )
            }

            // 2. Binary present but has missing dependencies
            if (item.binaryStatus == StageStatus.FOUND && item.missingLibraries.isNotEmpty()) {
                issues.add(
                    VendorIssue(
                        type = VendorIssueType.DEPENDENCY_MISSING,
                        severity = Severity.ERROR,
                        message = "HAL binary for '${item.halName}' fails library dependency resolution.",
                        evidence = "Binary: ${item.binaryPath}, Missing: ${item.missingLibraries.joinToString(", ")}",
                        source = "HalCompatibilityAnalyzer",
                        confidence = Confidence.HIGH,
                        recommendation = "Provide missing libraries in /vendor/lib or /system/lib."
                    )
                )
            }

            // 3. Init service missing for standalone hwbinder service
            if (trebleStatus == TrebleStatus.TREBLE && item.binaryStatus == StageStatus.FOUND && item.initServiceStatus == StageStatus.MISSING && !item.evidence.contains("PASSTHROUGH")) {
                issues.add(
                    VendorIssue(
                        type = VendorIssueType.INIT_SERVICE_MISSING,
                        severity = Severity.WARNING,
                        message = "HAL service '${item.halName}' binary exists, but no corresponding init.rc service declaration was found.",
                        evidence = "Binary: ${item.binaryPath}",
                        source = "HalCompatibilityAnalyzer",
                        confidence = Confidence.MEDIUM,
                        recommendation = "Declare 'service ... ${item.binaryPath}' in vendor/etc/init/*.rc so hwservicemanager or init starts it."
                    )
                )
            }
        }

        return issues
    }
}
