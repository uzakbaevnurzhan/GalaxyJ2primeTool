package com.example.ui.analyzer.rom

import com.example.ui.analyzer.boot.BootPartitionInfo
import com.example.ui.analyzer.boot.TrebleStatusInfo
import com.example.ui.analyzer.boot.AbSlotStatusInfo
import com.example.ui.analyzer.boot.ArchitectureCheckInfo
import com.example.ui.analyzer.boot.AndroidVersionAnalysisInfo
import com.example.ui.analyzer.boot.VendorDetailsInfo
import com.example.ui.analyzer.boot.VintfDetailsInfo
import com.example.ui.analyzer.boot.VintfHalInfo
import com.example.ui.analyzer.boot.PortingCheckRuleResult
import com.example.ui.analyzer.boot.BootStageStatus
import com.example.ui.analyzer.boot.GalaxyJ2PrimeProfileCheck
import java.io.File

object RomStructureAnalyzer {

    fun scanPartitions(files: List<File>): List<BootPartitionInfo> {
        val list = mutableListOf<BootPartitionInfo>()

        for (f in files) {
            val name = f.name.lowercase()
            val size = f.length()

            val (partName, format) = when {
                name == "boot.img" || name.startsWith("boot") && name.endsWith(".img") -> Pair("boot", "Android Boot Image")
                name == "recovery.img" || name.startsWith("recovery") && name.endsWith(".img") -> Pair("recovery", "Android Boot Image (Recovery)")
                name == "system.img" || name.startsWith("system") && name.endsWith(".img") -> Pair("system", "EXT4 / EROFS Image")
                name == "vendor.img" || name.startsWith("vendor") && name.endsWith(".img") -> Pair("vendor", "EXT4 / EROFS Image")
                name == "product.img" || name.startsWith("product") && name.endsWith(".img") -> Pair("product", "EXT4 Image")
                name == "odm.img" || name.startsWith("odm") && name.endsWith(".img") -> Pair("odm", "EXT4 Image")
                name == "dtbo.img" -> Pair("dtbo", "Device Tree Blob Overlay")
                name == "vbmeta.img" || name.startsWith("vbmeta") && name.endsWith(".img") -> Pair("vbmeta", "AVB 2.0 Hash Tree")
                name == "userdata.img" || name == "data.img" -> Pair("data", "EXT4 / F2FS Image")
                name == "cache.img" -> Pair("cache", "EXT4 Image")
                name.endsWith(".new.dat.br") -> Pair(name.substringBefore(".new.dat.br"), "Brotli Compressed Sparse DAT")
                name.endsWith(".new.dat") -> Pair(name.substringBefore(".new.dat"), "Sparse Block DAT")
                name.endsWith(".transfer.list") -> Pair(name.substringBefore(".transfer.list"), "Block Transfer List")
                else -> Pair(name.substringBeforeLast('.'), "Binary / Raw")
            }

            list.add(
                BootPartitionInfo(
                    partitionName = partName,
                    fileName = f.name,
                    fileSize = size,
                    format = format,
                    mountPoint = if (partName in listOf("system", "vendor", "product", "odm", "data", "cache")) "/$partName" else null,
                    detected = true
                )
            )
        }

        return list
    }

    fun detectTreble(
        hasVendorPartition: Boolean,
        properties: Map<String, String>,
        filesList: List<String>
    ): TrebleStatusInfo {
        val roTreble = properties["ro.treble.enabled"]
        val vndkVer = properties["ro.vndk.version"]
        val hasVndkProps = properties.keys.any { it.contains("vndk", ignoreCase = true) }
        val hasVintfManifest = filesList.any { it.contains("manifest.xml") || it.contains("vintf") }
        val hasSeparateVendor = hasVendorPartition || filesList.any { it.startsWith("vendor/") || it.contains("/vendor/") }

        val indicators = mutableListOf<String>()
        if (roTreble != null) indicators.add("ro.treble.enabled = $roTreble")
        if (vndkVer != null) indicators.add("ro.vndk.version = $vndkVer")
        if (hasSeparateVendor) indicators.add("Separate /vendor directory or image detected")
        if (hasVintfManifest) indicators.add("VINTF manifest files detected")

        val isTreble = (roTreble == "true") || (hasSeparateVendor && hasVndkProps) || (hasVintfManifest && hasSeparateVendor)

        val confidence = when {
            roTreble != null -> "HIGH"
            hasSeparateVendor && hasVndkProps -> "HIGH"
            hasSeparateVendor -> "MEDIUM"
            else -> "LOW"
        }

        return TrebleStatusInfo(
            isTreble = isTreble,
            hasVendorPartition = hasSeparateVendor,
            roTrebleProperty = roTreble,
            hasVndkProps = hasVndkProps,
            frameworkVendorSeparation = hasSeparateVendor && isTreble,
            confidence = confidence,
            indicators = indicators
        )
    }

    fun detectAbSlots(
        properties: Map<String, String>,
        partitionNames: List<String>,
        filesList: List<String>
    ): AbSlotStatusInfo {
        val slotSuffix = properties["ro.boot.slot_suffix"] ?: properties["ro.build.ab_update"]
        val hasAbProp = properties["ro.build.ab_update"] == "true"
        val hasAbPartitions = partitionNames.any { it.endsWith("_a") || it.endsWith("_b") }
        val hasUpdateEngine = filesList.any { it.contains("update_engine") }

        val indicators = mutableListOf<String>()
        if (slotSuffix != null) indicators.add("Slot suffix: $slotSuffix")
        if (hasAbProp) indicators.add("ro.build.ab_update = true")
        if (hasAbPartitions) indicators.add("A/B slotted partition names detected (_a/_b)")
        if (hasUpdateEngine) indicators.add("update_engine binary present")

        val isAb = hasAbProp || hasAbPartitions || slotSuffix != null

        return AbSlotStatusInfo(
            isAb = isAb,
            slotSuffixDetected = slotSuffix,
            updateEngineFound = hasUpdateEngine,
            bootSlotsFound = hasAbPartitions,
            indicators = indicators
        )
    }

    fun analyzeArchitecture(
        kernelArch: String?,
        initArch: String?,
        systemArch: String?,
        vendorArch: String?
    ): ArchitectureCheckInfo {
        val archSet = setOfNotNull(kernelArch, initArch, systemArch, vendorArch)
            .filter { it != "unknown" && it.isNotBlank() }

        val isPure32 = archSet.all { it == "ARM" || it == "ARM32" || it == "armv7" }
        val isPure64 = archSet.all { it == "ARM64" || it == "AArch64" || it == "arm64-v8a" }

        val overall = when {
            isPure32 && archSet.isNotEmpty() -> "ARM32"
            isPure64 && archSet.isNotEmpty() -> "ARM64"
            archSet.size > 1 -> "MIXED"
            else -> "UNKNOWN"
        }

        val notes = mutableListOf<String>()
        if (overall == "ARM32") {
            notes.add("Complete 32-bit (armv7-a) ROM environment detected.")
        } else if (overall == "ARM64") {
            notes.add("Complete 64-bit (aarch64) ROM environment detected.")
        } else if (overall == "MIXED") {
            notes.add("WARNING: Mixed 32-bit and 64-bit binaries detected across layers.")
        }

        return ArchitectureCheckInfo(
            kernelArch = kernelArch,
            initArch = initArch,
            systemArch = systemArch,
            vendorArch = vendorArch,
            overallArch = overall,
            isConsistent = overall != "MIXED",
            notes = notes
        )
    }

    fun analyzeVersions(
        bootHeaderVer: String?,
        buildProps: Map<String, String>,
        defaultProps: Map<String, String>
    ): AndroidVersionAnalysisInfo {
        val buildPropVer = buildProps["ro.build.version.release"] ?: buildProps["ro.system.build.version.release"]
        val defaultPropVer = defaultProps["ro.build.version.release"]

        val candidates = listOfNotNull(
            bootHeaderVer?.takeIf { it != "Unknown" && it != "None" },
            buildPropVer,
            defaultPropVer
        ).distinct()

        val hasConflict = candidates.size > 1
        val resolved = buildPropVer ?: defaultPropVer ?: bootHeaderVer ?: "Unknown"

        val conflictDetails = if (hasConflict) {
            "Version mismatch across sources: Boot Header ($bootHeaderVer), build.prop ($buildPropVer), default.prop ($defaultPropVer)"
        } else null

        return AndroidVersionAnalysisInfo(
            bootHeaderVersion = bootHeaderVer,
            buildPropVersion = buildPropVer,
            defaultPropVersion = defaultPropVer,
            resolvedVersion = resolved,
            hasConflict = hasConflict,
            conflictDetails = conflictDetails
        )
    }

    fun analyzeVintf(manifestXmlContent: String?): VintfDetailsInfo {
        if (manifestXmlContent.isNullOrBlank()) {
            return VintfDetailsInfo(
                hasManifest = false,
                hasMatrix = false,
                hals = emptyList(),
                parsedSummary = "No VINTF manifest.xml found",
                isPartiallyValidated = false
            )
        }

        val hals = mutableListOf<VintfHalInfo>()
        val halRegex = Regex("""<hal[\s\S]*?format="([^"]*)"[\s\S]*?<name>([^<]+)</name>[\s\S]*?<transport>([^<]*)</transport>[\s\S]*?<version>([^<]*)</version>[\s\S]*?</hal>""")
        val matches = halRegex.findAll(manifestXmlContent)

        for (m in matches) {
            val format = m.groupValues[1]
            val name = m.groupValues[2]
            val transport = m.groupValues[3]
            val version = m.groupValues[4]
            hals.add(
                VintfHalInfo(
                    name = name,
                    transport = transport,
                    format = format,
                    versions = listOf(version),
                    interfaces = emptyList()
                )
            )
        }

        return VintfDetailsInfo(
            hasManifest = true,
            hasMatrix = false,
            hals = hals,
            parsedSummary = "Parsed ${hals.size} HAL definitions from manifest.xml",
            isPartiallyValidated = true
        )
    }
}
