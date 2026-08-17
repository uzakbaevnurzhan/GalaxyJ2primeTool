package com.example.ui.analyzer.vendor

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object VendorStructureAnalyzer {

    fun analyzeStructure(
        filePaths: List<String>,
        rootPath: String = ""
    ): Pair<VendorStructure, TrebleStatus> {
        val normalized = filePaths.map { it.replace("\\", "/").trimStart('/') }
        
        val hasVendorDir = normalized.any { it.startsWith("vendor/") || it == "vendor" } ||
                normalized.any { it.contains("/vendor/") }
        val hasBuildProp = normalized.any { it == "vendor/build.prop" || it == "vendor/default.prop" || it.endsWith("vendor/build.prop") }
        val hasBin = normalized.any { it.startsWith("vendor/bin/") || it.contains("/vendor/bin/") }
        val hasBinHw = normalized.any { it.startsWith("vendor/bin/hw/") || it.contains("/vendor/bin/hw/") }
        val hasLib = normalized.any { it.startsWith("vendor/lib/") || it.contains("/vendor/lib/") }
        val hasLib64 = normalized.any { it.startsWith("vendor/lib64/") || it.contains("/vendor/lib64/") }
        val hasEtc = normalized.any { it.startsWith("vendor/etc/") || it.contains("/vendor/etc/") }
        val hasEtcInit = normalized.any { it.startsWith("vendor/etc/init/") || it.contains("/vendor/etc/init/") }
        val hasVintf = normalized.any { it.contains("vendor/etc/vintf") || it.contains("manifest.xml") || it.contains("vendor/manifest.xml") }
        val hasPermissions = normalized.any { it.contains("vendor/etc/permissions") || it.contains("system/etc/permissions") }
        val hasSelinux = normalized.any { it.contains("vendor/etc/selinux") || it.contains("plat_sepolicy") || it.contains("vendor_sepolicy") || it.contains("nonplat_sepolicy") }

        val structure = VendorStructure(
            hasVendorDir = hasVendorDir,
            hasBuildProp = hasBuildProp,
            hasBin = hasBin,
            hasBinHw = hasBinHw,
            hasLib = hasLib,
            hasLib64 = hasLib64,
            hasEtc = hasEtc,
            hasEtcInit = hasEtcInit,
            hasVintf = hasVintf,
            hasPermissions = hasPermissions,
            hasSelinux = hasSelinux,
            vendorPath = if (hasVendorDir) "vendor" else if (rootPath.isNotEmpty()) rootPath else "",
            detectedPaths = normalized.filter { it.startsWith("vendor") || it.startsWith("system") }.take(100)
        )

        // Treble Status detection from multiple multi-source indicators
        val trebleStatus = when {
            hasVintf || hasBinHw || hasEtcInit -> TrebleStatus.TREBLE
            hasVendorDir && !hasVintf && !hasBinHw -> TrebleStatus.NON_TREBLE
            !hasVendorDir && normalized.any { it.startsWith("system") } -> TrebleStatus.NON_TREBLE
            else -> TrebleStatus.UNKNOWN
        }

        return Pair(structure, trebleStatus)
    }

    fun analyzeDirectory(dir: File): Pair<VendorStructure, TrebleStatus> {
        val filePaths = mutableListOf<String>()
        fun scan(current: File, depth: Int = 0) {
            if (depth > 6) return
            val children = current.listFiles() ?: return
            for (child in children) {
                val rel = child.relativeTo(dir).path.replace("\\", "/")
                filePaths.add(rel)
                if (child.isDirectory) {
                    scan(child, depth + 1)
                }
            }
        }
        scan(dir)
        return analyzeStructure(filePaths, dir.absolutePath)
    }
}
