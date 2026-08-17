package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object SensorAnalyzer {

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

        val sensorHals = hals.filter { it.category == "Sensors" || it.name.contains("sensor") }
        val sensorServices = services.filter { it.category == "Sensors" }
        val sensorBinaries = binaries.filter { it.name.contains("sensor") }
        val sensorLibraries = libraries.filter { it.name.contains("sensor") || it.name.contains("sensors.") || it.name.contains("invensense") || it.name.contains("bosch") }
        val sensorProps = properties.filter { it.category == "Sensors" }

        val candidateConfigs = listOf(
            "vendor/etc/sensors/sensor_def_qcomdev.conf",
            "vendor/etc/sensors/hals.conf",
            "vendor/etc/sensors/proto/sns_reg_config",
            "system/etc/sensors/hals.conf"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = sensorHals.isNotEmpty()
        val hasLibs = sensorLibraries.isNotEmpty()
        val hasMissingLibs = sensorLibraries.any { it.missingLibraries.isNotEmpty() } || sensorBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && (hasLibs || sensorServices.isNotEmpty()) && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasLibs -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "Sensor Subsystem Integration",
                evidence = "HALs: ${sensorHals.size}, Services: ${sensorServices.size}, Libs: ${sensorLibraries.size}, Config files: ${configFiles.size}",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "SensorAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "Sensors",
            halEntries = sensorHals,
            serviceItems = sensorServices,
            binaries = sensorBinaries,
            libraries = sensorLibraries,
            configFiles = configFiles,
            properties = sensorProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "Kernel Interfaces: /dev/input/event*, /sys/class/sensors/*, /dev/i2c-*"
        )
    }
}
