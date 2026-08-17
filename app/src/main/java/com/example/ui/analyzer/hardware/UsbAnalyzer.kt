package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object UsbAnalyzer {

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

        val usbHals = hals.filter { it.category == "USB" || it.name.contains("usb") }
        val usbServices = services.filter { it.category == "USB" }
        val usbBinaries = binaries.filter { it.name.contains("usb") || it.name.contains("gadget") }
        val usbLibraries = libraries.filter { it.name.contains("usb") || it.name.contains("gadget") }
        val usbProps = properties.filter { it.category == "USB" }

        val candidateConfigs = listOf(
            "vendor/etc/init/hw/init.usb.rc",
            "vendor/etc/init/hw/init.usb.configfs.rc",
            "system/etc/init/hw/init.usb.rc"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = usbHals.isNotEmpty()
        val hasLibs = usbLibraries.isNotEmpty() || usbBinaries.isNotEmpty()
        val hasMissingLibs = usbLibraries.any { it.missingLibraries.isNotEmpty() } || usbBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal || (hasLibs && configFiles.isNotEmpty()) && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasLibs || configFiles.isNotEmpty() -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "USB & Gadget Subsystem Integration",
                evidence = "HALs: ${usbHals.size}, Services: ${usbServices.size}, Config files: ${configFiles.size}",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "UsbAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "USB",
            halEntries = usbHals,
            serviceItems = usbServices,
            binaries = usbBinaries,
            libraries = usbLibraries,
            configFiles = configFiles,
            properties = usbProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "ConfigFS Gadget: /config/usb_gadget/g1, Legacy: /sys/class/android_usb"
        )
    }
}
