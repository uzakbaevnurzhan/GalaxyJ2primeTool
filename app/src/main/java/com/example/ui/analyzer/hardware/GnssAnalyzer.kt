package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object GnssAnalyzer {

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

        val gnssHals = hals.filter { it.category == "GNSS/GPS" || it.name.contains("gnss") || it.name.contains("gps") }
        val gnssServices = services.filter { it.category == "GNSS/GPS" }
        val gnssBinaries = binaries.filter { it.name.contains("gnss") || it.name.contains("gps") || it.name.contains("loc_launcher") }
        val gnssLibraries = libraries.filter { it.name.contains("gnss") || it.name.contains("gps") || it.name.contains("loc_") }
        val gnssProps = properties.filter { it.category == "GNSS/GPS" }

        val candidateConfigs = listOf(
            "vendor/etc/gps.conf",
            "system/etc/gps.conf",
            "vendor/etc/gnss/gps.conf",
            "vendor/etc/flp.conf",
            "vendor/etc/izat.conf"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = gnssHals.isNotEmpty()
        val hasLibs = gnssLibraries.isNotEmpty()
        val hasMissingLibs = gnssLibraries.any { it.missingLibraries.isNotEmpty() } || gnssBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && (hasLibs || gnssServices.isNotEmpty()) && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasLibs -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "GNSS / GPS Subsystem Integration",
                evidence = "HALs: ${gnssHals.size}, Services: ${gnssServices.size}, Libs: ${gnssLibraries.size}, Config files: ${configFiles.size}",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "GnssAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "GNSS/GPS",
            halEntries = gnssHals,
            serviceItems = gnssServices,
            binaries = gnssBinaries,
            libraries = gnssLibraries,
            configFiles = configFiles,
            properties = gnssProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "Interfaces: /dev/stpgps, /dev/ttyGPS*, Config: ${configFiles.joinToString()}"
        )
    }
}
