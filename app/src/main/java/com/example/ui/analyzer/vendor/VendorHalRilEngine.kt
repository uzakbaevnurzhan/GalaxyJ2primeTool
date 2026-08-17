package com.example.ui.analyzer.vendor

import com.example.ui.analyzer.hal.HalAnalyzer
import com.example.ui.analyzer.hardware.*
import com.example.ui.analyzer.ril.RilAnalyzer
import com.example.ui.analyzer.vendor.models.*
import java.io.File

object VendorHalRilEngine {

    fun runFullAnalysis(
        rootDirectory: File?,
        properties: Map<String, String> = emptyMap(),
        filePaths: List<String> = emptyList(),
        selinuxLogs: List<String> = emptyList(),
        logLines: List<String> = emptyList(),
        targetName: String = "Extracted Image",
        deviceArchHint: String? = null
    ): VendorHalRilAnalysisResult {
        // 1. Run Vendor Analyzer
        val vendorInfo = VendorAnalyzer.analyzeVendor(
            rootDirectory = rootDirectory,
            properties = properties,
            filePaths = filePaths,
            deviceArchHint = deviceArchHint
        )

        // 2. Run HAL Analyzer
        val halInfo = HalAnalyzer.analyzeHal(
            rootDirectory = rootDirectory,
            vendorBinaries = vendorInfo.binaries,
            vendorLibraries = vendorInfo.libraries,
            trebleStatus = vendorInfo.trebleStatus
        )

        // 3. Run RIL Analyzer
        val rilInfo = RilAnalyzer.analyzeRil(
            rootDirectory = rootDirectory,
            vendorBinaries = vendorInfo.binaries,
            vendorLibraries = vendorInfo.libraries,
            properties = properties,
            selinuxLogs = selinuxLogs,
            logLines = logLines,
            trebleStatus = vendorInfo.trebleStatus
        )

        // 4. Run Individual Hardware Analyzers
        val audioAnalysis = AudioAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val cameraAnalysis = CameraAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val wifiAnalysis = WifiAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val btAnalysis = BluetoothAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val sensorAnalysis = SensorAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val gnssAnalysis = GnssAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val displayAnalysis = DisplayAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        val usbAnalysis = UsbAnalyzer.analyze(
            rootDirectory = rootDirectory,
            hals = halInfo.hals,
            services = halInfo.services,
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries,
            properties = vendorInfo.properties
        )

        // 5. Generate Hardware Matrix
        val hwMatrix = HardwareMatrixAnalyzer.generateMatrix(
            vendorInfo = vendorInfo,
            halInfo = halInfo,
            rilInfo = rilInfo,
            audio = audioAnalysis,
            camera = cameraAnalysis,
            wifi = wifiAnalysis,
            bluetooth = btAnalysis,
            sensors = sensorAnalysis,
            gnss = gnssAnalysis,
            display = displayAnalysis,
            usb = usbAnalysis
        )

        // 6. Build Dependency Graph
        val depGraph = VendorBinaryAnalyzer.buildDependencyGraph(
            binaries = vendorInfo.binaries,
            libraries = vendorInfo.libraries
        )

        // 7. Aggregate all issues and findings
        val allIssues = mutableListOf<VendorIssue>()
        allIssues.addAll(vendorInfo.issues)
        allIssues.addAll(halInfo.issues)
        allIssues.addAll(rilInfo.issues)
        allIssues.addAll(audioAnalysis.issues)
        allIssues.addAll(cameraAnalysis.issues)
        allIssues.addAll(wifiAnalysis.issues)
        allIssues.addAll(btAnalysis.issues)
        allIssues.addAll(sensorAnalysis.issues)
        allIssues.addAll(gnssAnalysis.issues)
        allIssues.addAll(displayAnalysis.issues)
        allIssues.addAll(usbAnalysis.issues)

        val allFindings = mutableListOf<EvidenceFinding>()
        allFindings.addAll(vendorInfo.findings)
        allFindings.addAll(halInfo.findings)
        allFindings.addAll(rilInfo.findings)
        allFindings.addAll(audioAnalysis.findings)
        allFindings.addAll(cameraAnalysis.findings)
        allFindings.addAll(wifiAnalysis.findings)
        allFindings.addAll(btAnalysis.findings)
        allFindings.addAll(sensorAnalysis.findings)
        allFindings.addAll(gnssAnalysis.findings)
        allFindings.addAll(displayAnalysis.findings)
        allFindings.addAll(usbAnalysis.findings)

        // 8. Classify Log lines by subsystem
        val logClassification = mutableMapOf<String, MutableList<String>>()
        for (line in logLines + selinuxLogs) {
            val lower = line.lowercase()
            when {
                lower.contains("ril") || lower.contains("radio") || lower.contains("telephony") -> logClassification.getOrPut("RIL / Radio") { mutableListOf() }.add(line)
                lower.contains("audio") || lower.contains("sound") || lower.contains("alsa") -> logClassification.getOrPut("Audio") { mutableListOf() }.add(line)
                lower.contains("camera") -> logClassification.getOrPut("Camera") { mutableListOf() }.add(line)
                lower.contains("wifi") || lower.contains("wlan") || lower.contains("wpa") -> logClassification.getOrPut("Wi-Fi") { mutableListOf() }.add(line)
                lower.contains("bluetooth") || lower.contains("bt") -> logClassification.getOrPut("Bluetooth") { mutableListOf() }.add(line)
                lower.contains("sensor") -> logClassification.getOrPut("Sensors") { mutableListOf() }.add(line)
                lower.contains("gps") || lower.contains("gnss") -> logClassification.getOrPut("GNSS/GPS") { mutableListOf() }.add(line)
                lower.contains("surface_flinger") || lower.contains("hwcomposer") || lower.contains("gralloc") -> logClassification.getOrPut("Display/GPU") { mutableListOf() }.add(line)
                lower.contains("avc: denied") -> logClassification.getOrPut("SELinux Denials") { mutableListOf() }.add(line)
            }
        }

        return VendorHalRilAnalysisResult(
            timestamp = System.currentTimeMillis(),
            targetName = targetName,
            vendorInfo = vendorInfo,
            halInfo = halInfo,
            rilInfo = rilInfo,
            audioAnalysis = audioAnalysis,
            cameraAnalysis = cameraAnalysis,
            wifiAnalysis = wifiAnalysis,
            bluetoothAnalysis = btAnalysis,
            sensorsAnalysis = sensorAnalysis,
            gnssAnalysis = gnssAnalysis,
            displayAnalysis = displayAnalysis,
            usbAnalysis = usbAnalysis,
            hardwareMatrix = hwMatrix,
            dependencyGraph = depGraph,
            allIssues = allIssues.distinctBy { "${it.type}_${it.message}" },
            allFindings = allFindings.distinctBy { "${it.fact}_${it.evidence}" },
            logClassification = logClassification
        )
    }
}
