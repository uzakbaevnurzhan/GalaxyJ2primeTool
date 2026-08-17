package com.example.ui.analyzer.hal

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object HalAnalyzer {

    fun analyzeHal(
        rootDirectory: File?,
        vendorBinaries: List<VendorBinary> = emptyList(),
        vendorLibraries: List<VendorLibrary> = emptyList(),
        trebleStatus: TrebleStatus = TrebleStatus.UNKNOWN
    ): HalInfo {
        val findings = mutableListOf<EvidenceFinding>()
        val issues = mutableListOf<VendorIssue>()

        // 1. Scan and parse manifests
        val manifestPairs = if (rootDirectory != null && rootDirectory.exists()) {
            HalManifestParser.scanManifestsInDir(rootDirectory)
        } else emptyList()

        val parsedManifestNames = manifestPairs.map { it.first }
        val allHals = manifestPairs.flatMap { it.second }
        val categoryHals = allHals.groupBy { it.category }

        findings.add(
            EvidenceFinding(
                fact = "VINTF Manifests",
                evidence = if (parsedManifestNames.isNotEmpty()) "Parsed ${parsedManifestNames.size} manifests (${parsedManifestNames.joinToString()}), total ${allHals.size} HAL entries."
                else "No VINTF manifest.xml found.",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "HalManifestParser"
            )
        )

        // 2. Discover HW binaries and Init services
        val hwBinaries = HalServiceAnalyzer.scanHwBinaries(rootDirectory, vendorBinaries)
        val initServices = HalServiceAnalyzer.parseInitServices(rootDirectory)

        // 3. Build service map
        val serviceMapItems = HalDependencyAnalyzer.buildHalServiceMap(
            manifestHals = allHals,
            hwBinaries = hwBinaries,
            initServices = initServices,
            libraries = vendorLibraries
        )

        // 4. Check compatibility issues
        val halIssues = HalCompatibilityAnalyzer.validateHalCompatibility(serviceMapItems, trebleStatus)
        issues.addAll(halIssues)

        val likelyCount = serviceMapItems.count { it.status == HardwarePresenceStatus.LIKELY_PRESENT }
        val partialCount = serviceMapItems.count { it.status == HardwarePresenceStatus.PARTIALLY_PRESENT }
        val missingCount = serviceMapItems.count { it.status == HardwarePresenceStatus.MISSING }
        val conflictCount = serviceMapItems.count { it.status == HardwarePresenceStatus.CONFLICT }

        findings.add(
            EvidenceFinding(
                fact = "HAL Service Integration Summary",
                evidence = "Total mapped HAL services: ${serviceMapItems.size}. Likely Present: $likelyCount, Partial: $partialCount, Missing Binaries: $missingCount, Conflicts: $conflictCount.",
                severity = if (conflictCount > 0) Severity.ERROR else if (missingCount > 0) Severity.WARNING else Severity.INFO,
                confidence = Confidence.HIGH,
                source = "HalAnalyzer"
            )
        )

        return HalInfo(
            manifestsParsed = parsedManifestNames,
            hals = allHals,
            categoryHals = categoryHals,
            services = serviceMapItems,
            findings = findings,
            issues = issues
        )
    }
}
