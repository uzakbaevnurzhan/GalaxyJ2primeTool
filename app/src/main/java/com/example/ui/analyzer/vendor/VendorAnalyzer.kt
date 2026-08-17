package com.example.ui.analyzer.vendor

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object VendorAnalyzer {

    fun analyzeVendor(
        rootDirectory: File?,
        properties: Map<String, String> = emptyMap(),
        filePaths: List<String> = emptyList(),
        deviceArchHint: String? = null
    ): VendorInfo {
        val findings = mutableListOf<EvidenceFinding>()
        val issues = mutableListOf<VendorIssue>()

        // 1. Structure Analysis
        val (structure, detectedTreble) = if (rootDirectory != null && rootDirectory.exists()) {
            VendorStructureAnalyzer.analyzeDirectory(rootDirectory)
        } else {
            VendorStructureAnalyzer.analyzeStructure(filePaths)
        }

        // Treble Determination with multi-source fallback
        val trebleProp = properties["ro.treble.enabled"]
        val trebleStatus = when {
            trebleProp.equals("true", ignoreCase = true) -> TrebleStatus.TREBLE
            trebleProp.equals("false", ignoreCase = true) -> TrebleStatus.NON_TREBLE
            detectedTreble != TrebleStatus.UNKNOWN -> detectedTreble
            else -> TrebleStatus.UNKNOWN
        }

        val trebleEvidence = when (trebleStatus) {
            TrebleStatus.TREBLE -> "Treble enabled (VINTF/HW-services/Property: ro.treble.enabled=$trebleProp)"
            TrebleStatus.NON_TREBLE -> "Non-Treble structure detected (Missing /vendor partition or legacy monolithic system structure)"
            TrebleStatus.UNKNOWN -> "Treble status unknown (No VINTF manifests or ro.treble.enabled property found)"
        }

        findings.add(
            EvidenceFinding(
                fact = "Vendor Partition Structure",
                evidence = if (structure.hasVendorDir) "Vendor directory/files detected (hasBinHw=${structure.hasBinHw}, hasVintf=${structure.hasVintf})"
                else "No dedicated /vendor directory found. Monolithic system structure assumed.",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "VendorStructureAnalyzer"
            )
        )

        findings.add(
            EvidenceFinding(
                fact = "Treble Architecture Status",
                evidence = trebleEvidence,
                severity = if (trebleStatus == TrebleStatus.NON_TREBLE) Severity.INFO else Severity.INFO,
                confidence = Confidence.HIGH,
                source = "VendorStructureAnalyzer"
            )
        )

        if (!structure.hasVendorDir && trebleStatus == TrebleStatus.TREBLE) {
            issues.add(
                VendorIssue(
                    type = VendorIssueType.MISSING_VENDOR,
                    severity = Severity.ERROR,
                    message = "Treble configuration declared, but vendor directory/partition is missing.",
                    evidence = "Treble status: $trebleStatus, hasVendorDir=false",
                    source = "VendorAnalyzer",
                    confidence = Confidence.HIGH,
                    recommendation = "Check if vendor.img was extracted into the workspace."
                )
            )
        }

        // 2. Properties
        val (propList, propGroups) = VendorPropertyAnalyzer.analyzeProperties(properties)
        val propConflicts = VendorPropertyAnalyzer.findPropertyConflicts(properties, trebleStatus)
        issues.addAll(propConflicts)

        // 3. Binaries and Libraries
        val (binaries, libraries) = VendorBinaryAnalyzer.analyzeBinariesAndLibraries(rootDirectory, emptyMap(), deviceArchHint)
        val abiIssues = VendorBinaryAnalyzer.detectAbiMismatches(binaries, libraries, deviceArchHint)
        issues.addAll(abiIssues)

        val missingLibsMap = mutableMapOf<String, List<String>>()
        binaries.filter { it.missingLibraries.isNotEmpty() }.forEach {
            missingLibsMap[it.name] = it.missingLibraries
        }
        libraries.filter { it.missingLibraries.isNotEmpty() }.forEach {
            missingLibsMap[it.name] = it.missingLibraries
        }

        // 4. Permissions
        val permissions = mutableListOf<VendorFeaturePermission>()
        if (rootDirectory != null && rootDirectory.exists()) {
            val permDir = File(rootDirectory, "vendor/etc/permissions")
            if (permDir.exists()) {
                permissions.addAll(VendorPermissionAnalyzer.scanPermissionsDirectory(permDir))
            }
            val sysPermDir = File(rootDirectory, "system/etc/permissions")
            if (sysPermDir.exists()) {
                permissions.addAll(VendorPermissionAnalyzer.scanPermissionsDirectory(sysPermDir))
            }
        }

        return VendorInfo(
            structure = structure,
            trebleStatus = trebleStatus,
            trebleEvidence = trebleEvidence,
            properties = propList,
            propertyGroups = propGroups,
            binaries = binaries,
            libraries = libraries,
            missingLibrariesMap = missingLibsMap,
            permissions = permissions,
            findings = findings,
            issues = issues
        )
    }
}
