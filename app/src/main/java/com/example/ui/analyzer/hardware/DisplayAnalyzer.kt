package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object DisplayAnalyzer {

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

        val displayHals = hals.filter { it.category == "Display" || it.name.contains("graphics") || it.name.contains("composer") || it.name.contains("allocator") || it.name.contains("mapper") }
        val displayServices = services.filter { it.category == "Display" }
        val displayBinaries = binaries.filter { it.name.contains("hwcomposer") || it.name.contains("allocator") || it.name.contains("composer") }
        val displayLibraries = libraries.filter { it.name.contains("gralloc") || it.name.contains("hwcomposer") || it.name.contains("egl") || it.name.contains("gles") || it.name.contains("vulkan") || it.name.contains("mali") || it.name.contains("adreno") }
        val displayProps = properties.filter { it.category == "Display" }

        val candidateConfigs = listOf(
            "vendor/etc/egl/egl.cfg",
            "system/etc/egl.cfg",
            "vendor/etc/display_config.xml"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = displayHals.isNotEmpty()
        val hasLibs = displayLibraries.isNotEmpty()
        val hasMissingLibs = displayLibraries.any { it.missingLibraries.isNotEmpty() } || displayBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && (hasLibs || displayServices.isNotEmpty()) && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasLibs -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "Display / GPU Subsystem Integration",
                evidence = "HALs: ${displayHals.size}, Services: ${displayServices.size}, Libs: ${displayLibraries.size} (Gralloc/HWC/GPU)",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "DisplayAnalyzer"
            )
        )

        return SpecificHardwareAnalysis(
            categoryName = "Display/Graphics",
            halEntries = displayHals,
            serviceItems = displayServices,
            binaries = displayBinaries,
            libraries = displayLibraries,
            configFiles = configFiles,
            properties = displayProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "GPU Nodes: /dev/kgsl-3d0, /dev/mali0, /dev/dri/card*, /dev/graphics/fb0"
        )
    }
}
