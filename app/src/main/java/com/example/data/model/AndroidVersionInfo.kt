package com.example.data.model

import android.os.Build

enum class AndroidVersionSource(val displayName: String) {
    LIVE_DEVICE("Live Device getprop"),
    BUILD_PROP("Extracted build.prop"),
    BOOT_METADATA("Boot Image Header / cmdline"),
    PROJECT_TARGET("Project Port Target"),
    REFERENCE_PROFILE("Reference Target Profile"),
    UNKNOWN("Unknown Source")
}

data class AndroidVersionInfo(
    val version: String,
    val apiLevel: Int,
    val codeName: String,
    val source: AndroidVersionSource,
    val isConflict: Boolean = false,
    val conflictDetails: String? = null
) {
    val formattedDisplay: String
        get() = if (version.isBlank() || version.equals("unknown", ignoreCase = true)) {
            "UNKNOWN"
        } else if (isConflict) {
            "VERSION CONFLICT: $version (${source.displayName})"
        } else {
            "Android $version (API $apiLevel) • [${source.displayName}]"
        }

    companion object {
        fun getLiveDeviceVersion(): AndroidVersionInfo {
            val rel = Build.VERSION.RELEASE ?: "Unknown"
            val sdk = Build.VERSION.SDK_INT
            val codeName = when (sdk) {
                23 -> "Marshmallow"
                24, 25 -> "Nougat"
                26, 27 -> "Oreo"
                28 -> "Pie"
                29 -> "Android 10 (Q)"
                30 -> "Android 11 (R)"
                31, 32 -> "Android 12 (S)"
                33 -> "Android 13 (Tiramisu)"
                34 -> "Android 14 (Upside Down Cake)"
                35, 36 -> "Android 15 / 16"
                else -> "API $sdk"
            }
            return AndroidVersionInfo(
                version = rel,
                apiLevel = sdk,
                codeName = codeName,
                source = AndroidVersionSource.LIVE_DEVICE
            )
        }

        fun fromProjectTarget(targetVersion: String, targetApi: Int = 30): AndroidVersionInfo {
            val cleaned = targetVersion.trim()
            if (cleaned.isBlank()) {
                return AndroidVersionInfo("Unknown", 0, "Unknown", AndroidVersionSource.UNKNOWN)
            }
            val codeName = when {
                cleaned.contains("11") -> "Android 11 (Red Velvet Cake)"
                cleaned.contains("10") -> "Android 10 (Quince Tart)"
                cleaned.contains("9") -> "Android 9 (Pie)"
                cleaned.contains("8.1") || cleaned.contains("8") -> "Android 8.1 (Oreo)"
                cleaned.contains("7") -> "Android 7.1 (Nougat)"
                cleaned.contains("6") -> "Android 6.0.1 (Marshmallow)"
                else -> "Android $cleaned"
            }
            return AndroidVersionInfo(
                version = cleaned,
                apiLevel = targetApi,
                codeName = codeName,
                source = AndroidVersionSource.PROJECT_TARGET
            )
        }

        fun evaluateComparison(live: AndroidVersionInfo, target: AndroidVersionInfo): Pair<String, String> {
            val liveStr = "Live Device: ${live.version} (${live.codeName}) [${live.source.displayName}]"
            val targetStr = "Target ROM: ${target.version} (${target.codeName}) [${target.source.displayName}]"
            return liveStr to targetStr
        }
    }
}
