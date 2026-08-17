package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object CameraAnalyzer {

    fun analyze(
        rootDirectory: File?,
        hals: List<HalEntry>,
        services: List<HalServiceMapItem>,
        binaries: List<VendorBinary>,
        libraries: List<VendorLibrary>,
        properties: List<VendorProperty>
    ): SpecificHardwareAnalysis {
        val findings = mutableListOf<EvidenceFinding>()
        val issues = mutableListOf<VendorIssue>()
        val configFiles = mutableListOf<String>()

        val cameraHals = hals.filter { it.category == "Camera" || it.name.contains("camera") }
        val cameraServices = services.filter { it.category == "Camera" }
        val cameraBinaries = binaries.filter { it.name.contains("camera") || it.name.contains("cameraserver") }
        val cameraLibraries = libraries.filter { it.name.contains("camera") || it.name.contains("cam.") || it.name.contains("sensor.camera") || it.name.contains("chromatix") }
        val cameraProps = properties.filter { it.category == "Camera" }

        val candidateConfigs = listOf(
            "vendor/etc/camera/camera_config.xml",
            "vendor/etc/camera_config.xml",
            "vendor/etc/media_profiles.xml",
            "vendor/etc/media_profiles_V1_0.xml",
            "system/etc/media_profiles.xml"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = cameraHals.isNotEmpty()
        val hasLibs = cameraLibraries.isNotEmpty()
        val hasMissingLibs = cameraLibraries.any { it.missingLibraries.isNotEmpty() } || cameraBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && hasLibs && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasLibs -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "Camera Subsystem Integration",
                evidence = "HALs: ${cameraHals.size}, Services: ${cameraServices.size}, Vendor Libs: ${cameraLibraries.size}, Config files: ${configFiles.size}",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "CameraAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "Camera",
            halEntries = cameraHals,
            serviceItems = cameraServices,
            binaries = cameraBinaries,
            libraries = cameraLibraries,
            configFiles = configFiles,
            properties = cameraProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "V4L2 Kernel Nodes: /dev/video*, /dev/media*, HAL: android.hardware.camera.provider"
        )
    }
}
