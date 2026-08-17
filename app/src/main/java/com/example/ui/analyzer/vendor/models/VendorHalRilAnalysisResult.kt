package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

@Serializable
data class SpecificHardwareAnalysis(
    val categoryName: String,
    val halEntries: List<HalEntry> = emptyList(),
    val serviceItems: List<HalServiceMapItem> = emptyList(),
    val binaries: List<VendorBinary> = emptyList(),
    val libraries: List<VendorLibrary> = emptyList(),
    val configFiles: List<String> = emptyList(),
    val properties: List<VendorProperty> = emptyList(),
    val presenceStatus: HardwarePresenceStatus = HardwarePresenceStatus.UNKNOWN,
    val findings: List<EvidenceFinding> = emptyList(),
    val issues: List<VendorIssue> = emptyList(),
    val technicalDetails: String = ""
)

@Serializable
data class DependencyGraphNode(
    val id: String,
    val label: String,
    val type: String, // SERVICE, BINARY, LIBRARY, HAL, KERNEL
    val architecture: String? = null,
    val exists: Boolean = true,
    val dependencies: List<String> = emptyList(),
    val dependents: List<String> = emptyList(),
    val evidence: String = ""
)

@Serializable
data class DependencyGraphData(
    val nodes: List<DependencyGraphNode> = emptyList(),
    val missingDependenciesCount: Int = 0,
    val totalLibrariesCount: Int = 0,
    val totalBinariesCount: Int = 0
)

@Serializable
data class VendorHalRilAnalysisResult(
    val timestamp: Long = System.currentTimeMillis(),
    val targetName: String = "",
    val vendorInfo: VendorInfo = VendorInfo(),
    val halInfo: HalInfo = HalInfo(),
    val rilInfo: RilInfo = RilInfo(),
    val audioAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("Audio"),
    val cameraAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("Camera"),
    val wifiAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("Wi-Fi"),
    val bluetoothAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("Bluetooth"),
    val sensorsAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("Sensors"),
    val gnssAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("GNSS/GPS"),
    val displayAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("Display/Graphics"),
    val usbAnalysis: SpecificHardwareAnalysis = SpecificHardwareAnalysis("USB"),
    val hardwareMatrix: HardwareFunctionMatrix = HardwareFunctionMatrix(),
    val dependencyGraph: DependencyGraphData = DependencyGraphData(),
    val allIssues: List<VendorIssue> = emptyList(),
    val allFindings: List<EvidenceFinding> = emptyList(),
    val logClassification: Map<String, List<String>> = emptyMap()
)
