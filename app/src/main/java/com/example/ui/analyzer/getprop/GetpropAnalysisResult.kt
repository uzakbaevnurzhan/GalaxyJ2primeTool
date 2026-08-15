package com.example.ui.analyzer.getprop

import com.example.ui.analyzer.core.AnalyzerStatus

data class HardwareSocDetails(
    val hardware: String = "Unknown",
    val board: String = "Unknown",
    val platform: String = "Unknown",
    val socModel: String = "Unknown",
    val bootHardware: String = "Unknown",
    val bootPlatform: String = "Unknown",
    val chipname: String = "Unknown",
    val hasConflict: Boolean = false,
    val warnings: List<String> = emptyList()
)

data class GraphicsDetails(
    val eglHardware: String = "Unknown",
    val glesVersionRaw: String = "Unknown",
    val glesVersionFormatted: String = "Unknown",
    val hwuiRenderer: String = "Unknown",
    val isHwuiDetected: Boolean = false,
    val graphicsProperties: List<GetpropEntry> = emptyList()
)

data class RuntimeArtDetails(
    val heapStartSize: String = "Unknown",
    val heapGrowthLimit: String = "Unknown",
    val heapSize: String = "Unknown",
    val heapMinFree: String = "Unknown",
    val heapMaxFree: String = "Unknown",
    val heapTargetUtilization: String = "Unknown",
    val useJit: String = "Unknown",
    val dex2oatFilter: String = "Unknown",
    val imageDex2oatXms: String = "Unknown",
    val imageDex2oatXmx: String = "Unknown",
    val dalvikProperties: List<GetpropEntry> = emptyList()
)

data class DisplayDetails(
    val lcdDensity: String = "Unknown",
    val surfaceFlingerProperties: List<GetpropEntry> = emptyList()
)

data class TelephonyRilDetails(
    val rilImplementation: String = "Unknown",
    val rildLibPath: String = "Unknown",
    val telephonyProperties: List<GetpropEntry> = emptyList(),
    val isRilDetected: Boolean = false,
    val note: String = "RIL configuration detected. Does not confirm runtime telephony hardware readiness."
)

data class MediaDetails(
    val cameraProperties: List<GetpropEntry> = emptyList(),
    val audioProperties: List<GetpropEntry> = emptyList(),
    val mediaProperties: List<GetpropEntry> = emptyList(),
    val note: String = "Subsystem properties detected. Hardware state can only be validated at runtime."
)

data class SecuritySelinuxDetails(
    val selinuxBoot: String = "Unknown",
    val selinuxMode: String = "Unknown",
    val buildTags: String = "Unknown",
    val debuggable: String = "Unknown",
    val secure: String = "Unknown",
    val adbSecure: String = "Unknown",
    val cryptoState: String = "Unknown",
    val warnings: List<String> = emptyList(),
    val securityProperties: List<GetpropEntry> = emptyList()
)

/**
 * Complete result of the getprop/build.prop analysis.
 */
data class GetpropAnalysisResult(
    val status: AnalyzerStatus = AnalyzerStatus.SUCCESS,
    val snapshot: GetpropSnapshot,
    val hardwareSoc: HardwareSocDetails = HardwareSocDetails(),
    val graphics: GraphicsDetails = GraphicsDetails(),
    val runtimeArt: RuntimeArtDetails = RuntimeArtDetails(),
    val display: DisplayDetails = DisplayDetails(),
    val telephonyRil: TelephonyRilDetails = TelephonyRilDetails(),
    val media: MediaDetails = MediaDetails(),
    val securitySelinux: SecuritySelinuxDetails = SecuritySelinuxDetails(),
    val categoryCounts: Map<GetpropCategory, Int> = emptyMap(),
    val typeCounts: Map<PropertyValueType, Int> = emptyMap(),
    val duplicatesList: List<GetpropEntry> = emptyList(),
    val conflictsList: List<GetpropEntry> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val rawSummary: String = "",
    val rawDetails: String = ""
)
