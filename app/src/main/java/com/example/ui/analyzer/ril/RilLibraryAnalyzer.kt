package com.example.ui.analyzer.ril

import com.example.ui.analyzer.vendor.models.RilLibraryInfo
import com.example.ui.analyzer.vendor.models.VendorLibrary

object RilLibraryAnalyzer {

    fun identifyRilVendorFlavor(name: String, path: String): String {
        val lower = "$name $path".lowercase()
        return when {
            lower.contains("mtk") || lower.contains("mediatek") || lower.contains("libmtkril") || lower.contains("libmtk-ril") -> "MediaTek (MTK RIL)"
            lower.contains("sec") || lower.contains("samsung") || lower.contains("libsec-ril") || lower.contains("libsecril") -> "Samsung (SecRIL)"
            lower.contains("qc") || lower.contains("qmi") || lower.contains("qualcomm") || lower.contains("libqc-opt-ril") -> "Qualcomm (QMI RIL)"
            lower.contains("sprd") || lower.contains("spreadtrum") || lower.contains("unisoc") -> "Unisoc / Spreadtrum"
            lower.contains("reference") || lower.contains("libreference-ril") -> "AOSP Reference RIL"
            else -> "Generic OEM RIL"
        }
    }

    fun isRilRelatedLibrary(name: String, path: String): Boolean {
        val lower = "$name $path".lowercase()
        return lower.contains("ril") || lower.contains("radio") || lower.contains("telephony") ||
                lower.contains("atci") || lower.contains("ccci") || lower.contains("modem") ||
                lower.contains("netdagent") || lower.contains("gsm")
    }

    fun analyzeRilLibraries(
        vendorLibraries: List<VendorLibrary>
    ): List<RilLibraryInfo> {
        val results = mutableListOf<RilLibraryInfo>()

        for (lib in vendorLibraries) {
            if (isRilRelatedLibrary(lib.name, lib.relativePath)) {
                val flavor = identifyRilVendorFlavor(lib.name, lib.relativePath)
                results.add(
                    RilLibraryInfo(
                        name = lib.name,
                        path = lib.relativePath,
                        exists = true,
                        architecture = lib.architecture,
                        soname = lib.soname,
                        neededLibraries = lib.neededLibraries,
                        missingLibraries = lib.missingLibraries,
                        isVendorSpecific = !flavor.contains("AOSP"),
                        vendorFlavor = flavor
                    )
                )
            }
        }

        return results
    }
}
