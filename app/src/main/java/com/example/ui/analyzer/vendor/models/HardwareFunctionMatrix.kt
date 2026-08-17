package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

@Serializable
data class HardwareFunctionItem(
    val functionKey: String, // e.g. "ril", "calls", "sms", "data", "wifi", "bluetooth", "gps", "camera", "audio", "mic", "sensors", "display", "usb", "vibrator", "torch", "headset"
    val displayName: String,
    val category: String, // Radio & Cellular, Connectivity, Multimedia, Sensors & Input, System & Power
    val filesStatus: StageStatus = StageStatus.UNKNOWN,
    val halStatus: StageStatus = StageStatus.UNKNOWN,
    val serviceStatus: StageStatus = StageStatus.UNKNOWN,
    val librariesStatus: StageStatus = StageStatus.UNKNOWN,
    val kernelStatus: StageStatus = StageStatus.UNKNOWN,
    val logsStatus: LogStatus = LogStatus.UNKNOWN,
    val overallStatus: HardwarePresenceStatus = HardwarePresenceStatus.UNKNOWN,
    val evidenceList: List<String> = emptyList(),
    val notes: String = ""
)

@Serializable
data class HardwareFunctionMatrix(
    val items: List<HardwareFunctionItem> = emptyList(),
    val summary: String = "",
    val totalCount: Int = 0,
    val likelyPresentCount: Int = 0,
    val partialCount: Int = 0,
    val missingCount: Int = 0,
    val conflictCount: Int = 0,
    val unknownCount: Int = 0
)
