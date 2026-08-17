package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object BluetoothAnalyzer {

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

        val btHals = hals.filter { it.category == "Bluetooth" || it.name.contains("bluetooth") }
        val btServices = services.filter { it.category == "Bluetooth" }
        val btBinaries = binaries.filter { it.name.contains("bluetooth") || it.name.contains("bt_") || it.name.contains("hci") }
        val btLibraries = libraries.filter { it.name.contains("bluetooth") || it.name.contains("bt_") || it.name.contains("audio.bluetooth") }
        val btProps = properties.filter { it.category == "Bluetooth" }

        val candidateConfigs = listOf(
            "vendor/etc/bluetooth/bt_stack.conf",
            "system/etc/bluetooth/bt_stack.conf",
            "vendor/etc/bluetooth/interop_database.conf"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = btHals.isNotEmpty()
        val hasLibs = btLibraries.isNotEmpty()
        val hasMissingLibs = btLibraries.any { it.missingLibraries.isNotEmpty() } || btBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && (hasLibs || btServices.isNotEmpty()) && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasLibs -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "Bluetooth Subsystem Integration",
                evidence = "HALs: ${btHals.size}, Services: ${btServices.size}, Libs: ${btLibraries.size}, Config files: ${configFiles.size}",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "BluetoothAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "Bluetooth",
            halEntries = btHals,
            serviceItems = btServices,
            binaries = btBinaries,
            libraries = btLibraries,
            configFiles = configFiles,
            properties = btProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "HCI UART / VFS: /dev/ttyHS*, /dev/stpbt, HAL: android.hardware.bluetooth"
        )
    }
}
