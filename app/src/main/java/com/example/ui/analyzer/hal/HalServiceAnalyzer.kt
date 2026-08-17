package com.example.ui.analyzer.hal

import com.example.ui.analyzer.boot.InitRcParser
import com.example.ui.analyzer.boot.InitServiceBlock
import com.example.ui.analyzer.vendor.models.*
import java.io.File

object HalServiceAnalyzer {

    data class DiscoveredHalBinary(
        val binaryName: String,
        val relativePath: String,
        val fullPath: String,
        val architecture: String? = null,
        val is64Bit: Boolean = false,
        val neededLibraries: List<String> = emptyList(),
        val missingLibraries: List<String> = emptyList()
    )

    fun scanHwBinaries(rootDirectory: File?, vendorBinaries: List<VendorBinary>): List<DiscoveredHalBinary> {
        val list = mutableListOf<DiscoveredHalBinary>()
        
        // Match from already parsed vendor binaries
        for (vb in vendorBinaries) {
            if (vb.isHwService || vb.relativePath.contains("bin/hw/") || vb.name.contains("@") || vb.name.contains("-service")) {
                list.add(
                    DiscoveredHalBinary(
                        binaryName = vb.name,
                        relativePath = vb.relativePath,
                        fullPath = vb.relativePath,
                        architecture = vb.architecture,
                        is64Bit = vb.is64Bit,
                        neededLibraries = vb.neededLibraries,
                        missingLibraries = vb.missingLibraries
                    )
                )
            }
        }

        return list
    }

    fun parseInitServices(rootDirectory: File?): List<InitServiceBlock> {
        val services = mutableListOf<InitServiceBlock>()
        if (rootDirectory == null || !rootDirectory.exists()) return services

        val initFiles = mutableListOf<File>()
        rootDirectory.walkTopDown().maxDepth(4).forEach { f ->
            if (f.isFile && (f.name.endsWith(".rc") || f.name.startsWith("init."))) {
                initFiles.add(f)
            }
        }

        for (f in initFiles) {
            try {
                val content = f.readText(Charsets.UTF_8)
                val parsed = InitRcParser.parse(content, f.name)
                services.addAll(parsed.services)
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        return services
    }
}
