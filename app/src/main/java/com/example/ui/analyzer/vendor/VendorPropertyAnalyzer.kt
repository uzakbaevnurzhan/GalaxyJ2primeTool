package com.example.ui.analyzer.vendor

import com.example.ui.analyzer.vendor.models.*

object VendorPropertyAnalyzer {

    fun categorizeProperty(key: String): String {
        val lower = key.lowercase()
        return when {
            lower.contains("radio") || lower.contains("ril") || lower.contains("telephony") || lower.startsWith("gsm.") -> "Radio"
            lower.contains("audio") || lower.contains("sound") || lower.contains("volume") || lower.contains("alsa") -> "Audio"
            lower.contains("camera") || lower.contains("cam.") || lower.contains("sensor.camera") -> "Camera"
            lower.contains("display") || lower.contains("surface_flinger") || lower.contains("sf.") || lower.contains("gpu") || lower.contains("egl") || lower.contains("gralloc") -> "Display"
            lower.contains("bluetooth") || lower.contains("bt.") || lower.contains("bcm.") -> "Bluetooth"
            lower.contains("wifi") || lower.contains("wlan") || lower.contains("wpa") -> "Wi-Fi"
            lower.contains("sensor") || lower.contains("gyro") || lower.contains("accel") || lower.contains("mag") -> "Sensors"
            lower.contains("gps") || lower.contains("gnss") || lower.contains("location") -> "GNSS/GPS"
            lower.contains("usb") || lower.contains("mtp") || lower.contains("adb") -> "USB"
            lower.startsWith("ro.hardware") || lower.startsWith("ro.board") || lower.startsWith("ro.boot.hardware") || lower.startsWith("ro.chipname") || lower.startsWith("ro.arch") -> "Hardware"
            lower.startsWith("ro.vendor") || lower.startsWith("persist.vendor") -> "Vendor"
            else -> "General"
        }
    }

    fun analyzeProperties(
        rawProperties: Map<String, String>,
        sourceFileName: String = "build.prop"
    ): Pair<List<VendorProperty>, Map<String, List<VendorProperty>>> {
        val list = mutableListOf<VendorProperty>()
        for ((k, v) in rawProperties) {
            val cat = categorizeProperty(k)
            list.add(VendorProperty(k, v, cat, sourceFileName))
        }
        val grouped = list.groupBy { it.category }
        return Pair(list, grouped)
    }

    fun findPropertyConflicts(
        properties: Map<String, String>,
        trebleStatus: TrebleStatus
    ): List<VendorIssue> {
        val issues = mutableListOf<VendorIssue>()

        val trebleEnabledProp = properties["ro.treble.enabled"]?.equals("true", ignoreCase = true) == true
        if (trebleEnabledProp && trebleStatus == TrebleStatus.NON_TREBLE) {
            issues.add(
                VendorIssue(
                    type = VendorIssueType.PROPERTY_CONFLICT,
                    severity = Severity.WARNING,
                    message = "Treble mismatch: ro.treble.enabled=true, but non-treble partition structure was detected.",
                    evidence = "ro.treble.enabled=${properties["ro.treble.enabled"]}, detected structure non-treble",
                    source = "VendorPropertyAnalyzer",
                    confidence = Confidence.HIGH,
                    recommendation = "Verify if system image expects a dedicated vendor partition or legacy system-as-root."
                )
            )
        }

        val hwProp = properties["ro.hardware"] ?: properties["ro.boot.hardware"]
        val boardPlatform = properties["ro.board.platform"]
        if (hwProp != null && boardPlatform != null && hwProp.isNotEmpty() && boardPlatform.isNotEmpty()) {
            if (!hwProp.contains(boardPlatform, ignoreCase = true) && !boardPlatform.contains(hwProp, ignoreCase = true)) {
                issues.add(
                    VendorIssue(
                        type = VendorIssueType.PROPERTY_CONFLICT,
                        severity = Severity.INFO,
                        message = "Hardware definition mismatch between ro.hardware and ro.board.platform.",
                        evidence = "ro.hardware=$hwProp, ro.board.platform=$boardPlatform",
                        source = "VendorPropertyAnalyzer",
                        confidence = Confidence.MEDIUM,
                        recommendation = "Check if HAL search paths expect '$hwProp' or '$boardPlatform' suffixes."
                    )
                )
            }
        }

        return issues
    }
}
