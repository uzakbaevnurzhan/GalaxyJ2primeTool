package com.example.ui.analyzer.ril

import com.example.ui.analyzer.boot.InitServiceBlock
import com.example.ui.analyzer.vendor.models.RilDaemonInfo
import com.example.ui.analyzer.vendor.models.RilInitService
import com.example.ui.analyzer.vendor.models.VendorBinary

object RilServiceAnalyzer {

    fun isRilRelatedBinary(name: String, path: String): Boolean {
        val lower = "$name $path".lowercase()
        return lower.contains("rild") || lower.contains("ril-daemon") || lower.contains("mtkrild") ||
                lower.contains("sec-ril") || lower.contains("cbd") || lower.contains("ccci") ||
                lower.contains("gsm0710") || lower.contains("muxd") || lower.contains("radio")
    }

    fun analyzeRilDaemons(vendorBinaries: List<VendorBinary>): List<RilDaemonInfo> {
        val daemons = mutableListOf<RilDaemonInfo>()
        for (bin in vendorBinaries) {
            if (isRilRelatedBinary(bin.name, bin.relativePath)) {
                daemons.add(
                    RilDaemonInfo(
                        name = bin.name,
                        path = bin.relativePath,
                        exists = true,
                        architecture = bin.architecture,
                        neededLibraries = bin.neededLibraries,
                        missingLibraries = bin.missingLibraries
                    )
                )
            }
        }
        return daemons
    }

    fun analyzeRilInitServices(
        initServices: List<InitServiceBlock>,
        discoveredDaemons: List<RilDaemonInfo>
    ): List<RilInitService> {
        val rilServices = mutableListOf<RilInitService>()

        for (s in initServices) {
            val isRilService = isRilRelatedBinary(s.name, s.binaryPath)
            if (isRilService) {
                val binName = s.binaryPath.substringAfterLast("/")
                val binExists = discoveredDaemons.any { it.name == binName || s.binaryPath.contains(it.name) }

                rilServices.add(
                    RilInitService(
                        serviceName = s.name,
                        binaryPath = s.binaryPath,
                        isBinaryFound = binExists,
                        className = s.className,
                        user = s.user,
                        group = s.group,
                        seclabel = s.seclabel,
                        isDisabled = s.isDisabled
                    )
                )
            }
        }

        return rilServices
    }
}
