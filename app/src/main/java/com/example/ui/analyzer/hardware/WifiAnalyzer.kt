package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object WifiAnalyzer {

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

        val wifiHals = hals.filter { it.category == "Wi-Fi" || it.name.contains("wifi") || it.name.contains("wlan") }
        val wifiServices = services.filter { it.category == "Wi-Fi" }
        val wifiBinaries = binaries.filter { it.name.contains("wpa_supplicant") || it.name.contains("hostapd") || it.name.contains("wifi") }
        val wifiLibraries = libraries.filter { it.name.contains("wifi") || it.name.contains("wpa") || it.name.contains("wlan") }
        val wifiProps = properties.filter { it.category == "Wi-Fi" }

        val candidateConfigs = listOf(
            "vendor/etc/wifi/wpa_supplicant.conf",
            "vendor/etc/wifi/p2p_supplicant_overlay.conf",
            "vendor/etc/wifi/wpa_supplicant_overlay.conf",
            "system/etc/wifi/wpa_supplicant.conf"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = wifiHals.isNotEmpty()
        val hasBin = wifiBinaries.any { it.name.contains("wpa_supplicant") } || wifiServices.isNotEmpty()
        val hasMissingLibs = wifiLibraries.any { it.missingLibraries.isNotEmpty() } || wifiBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && (hasBin || wifiLibraries.isNotEmpty()) && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasBin || wifiLibraries.isNotEmpty() -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "Wi-Fi Subsystem Integration",
                evidence = "HALs: ${wifiHals.size}, Services: ${wifiServices.size}, Binaries: ${wifiBinaries.size}, Config files: ${configFiles.size}",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "WifiAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "Wi-Fi",
            halEntries = wifiHals,
            serviceItems = wifiServices,
            binaries = wifiBinaries,
            libraries = wifiLibraries,
            configFiles = configFiles,
            properties = wifiProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "Interfaces: nl80211, wlan0, Daemons: wpa_supplicant, hostapd"
        )
    }
}
