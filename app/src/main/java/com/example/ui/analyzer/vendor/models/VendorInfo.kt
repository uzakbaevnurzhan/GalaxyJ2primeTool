package com.example.ui.analyzer.vendor.models

import kotlinx.serialization.Serializable

@Serializable
data class VendorStructure(
    val hasVendorDir: Boolean = false,
    val hasBuildProp: Boolean = false,
    val hasBin: Boolean = false,
    val hasBinHw: Boolean = false,
    val hasLib: Boolean = false,
    val hasLib64: Boolean = false,
    val hasEtc: Boolean = false,
    val hasEtcInit: Boolean = false,
    val hasVintf: Boolean = false,
    val hasPermissions: Boolean = false,
    val hasSelinux: Boolean = false,
    val vendorPath: String = "",
    val detectedPaths: List<String> = emptyList()
)

@Serializable
data class VendorProperty(
    val key: String,
    val value: String,
    val category: String,
    val sourceFile: String = "build.prop",
    val description: String = ""
)

@Serializable
data class VendorBinary(
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val architecture: String,
    val is64Bit: Boolean,
    val soname: String? = null,
    val neededLibraries: List<String> = emptyList(),
    val missingLibraries: List<String> = emptyList(),
    val isHwService: Boolean = false
) {
    val arch: String get() = architecture
    val path: String get() = relativePath
    val dependencies: List<String> get() = neededLibraries
}

@Serializable
data class VendorLibrary(
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val architecture: String,
    val is64Bit: Boolean,
    val soname: String? = null,
    val neededLibraries: List<String> = emptyList(),
    val missingLibraries: List<String> = emptyList(),
    val dependents: List<String> = emptyList()
) {
    val arch: String get() = architecture
    val path: String get() = relativePath
    val dependencies: List<String> get() = neededLibraries
}

@Serializable
data class VendorFeaturePermission(
    val featureName: String,
    val sourceFile: String,
    val isRequired: Boolean = true,
    val isAvailableInVendor: Boolean = true
)

@Serializable
data class VendorInfo(
    val structure: VendorStructure = VendorStructure(),
    val trebleStatus: TrebleStatus = TrebleStatus.UNKNOWN,
    val trebleEvidence: String = "",
    val properties: List<VendorProperty> = emptyList(),
    val propertyGroups: Map<String, List<VendorProperty>> = emptyMap(),
    val binaries: List<VendorBinary> = emptyList(),
    val libraries: List<VendorLibrary> = emptyList(),
    val missingLibrariesMap: Map<String, List<String>> = emptyMap(),
    val permissions: List<VendorFeaturePermission> = emptyList(),
    val findings: List<EvidenceFinding> = emptyList(),
    val issues: List<VendorIssue> = emptyList()
) {
    val chipsetPlatform: String
        get() = properties.firstOrNull { it.key == "ro.board.platform" || it.key == "ro.hardware" || it.key == "ro.mediatek.platform" }?.value ?: "Unknown Platform"

    val vendorManufacturer: String
        get() = properties.firstOrNull { it.key == "ro.product.vendor.manufacturer" || it.key == "ro.product.manufacturer" }?.value ?: "Generic / AOSP"

    val primaryArch: String
        get() = properties.firstOrNull { it.key == "ro.product.cpu.abi" || it.key == "ro.vendor.product.cpu.abilist" }?.value
            ?: if (libraries.any { it.is64Bit }) "arm64-v8a" else if (libraries.isNotEmpty()) "armeabi-v7a" else "Unknown"

    val androidTargetVersion: String
        get() = properties.firstOrNull { it.key == "ro.build.version.release" }?.value ?: "Unknown"
}
