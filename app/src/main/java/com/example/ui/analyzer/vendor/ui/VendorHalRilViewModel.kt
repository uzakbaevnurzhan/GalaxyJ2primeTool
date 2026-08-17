package com.example.ui.analyzer.vendor.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.analyzer.vendor.VendorHalRilEngine
import com.example.ui.analyzer.vendor.models.VendorHalRilAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VendorHalRilViewModel : ViewModel() {

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisResult = MutableStateFlow<VendorHalRilAnalysisResult?>(null)
    val analysisResult: StateFlow<VendorHalRilAnalysisResult?> = _analysisResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun analyzeDirectory(directory: File, targetName: String = directory.name) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    // Extract properties if build.prop exists
                    val props = mutableMapOf<String, String>()
                    val buildPropFiles = listOf(
                        File(directory, "build.prop"),
                        File(directory, "vendor/build.prop"),
                        File(directory, "system/build.prop"),
                        File(directory, "default.prop")
                    )
                    for (f in buildPropFiles) {
                        if (f.exists()) {
                            f.readLines(Charsets.UTF_8).forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                                    val key = trimmed.substringBefore("=").trim()
                                    val value = trimmed.substringAfter("=").trim()
                                    props[key] = value
                                }
                            }
                        }
                    }

                    // Extract log files if present
                    val logs = mutableListOf<String>()
                    val logFiles = directory.walkTopDown().maxDepth(3).filter {
                        it.isFile && (it.name.endsWith(".log") || it.name.endsWith(".txt") || it.name.contains("logcat") || it.name.contains("dmesg"))
                    }.take(5).toList()

                    for (lf in logFiles) {
                        try {
                            logs.addAll(lf.readLines().take(2000))
                        } catch (_: Exception) {}
                    }

                    VendorHalRilEngine.runFullAnalysis(
                        rootDirectory = directory,
                        properties = props,
                        logLines = logs,
                        targetName = targetName
                    )
                }
                _analysisResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Analysis failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun analyzeDeviceLive(context: Context) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    // Gather system properties from getprop or live files
                    val props = mutableMapOf<String, String>()
                    try {
                        val process = Runtime.getRuntime().exec("getprop")
                        process.inputStream.bufferedReader().useLines { lines ->
                            val regex = Regex("""\[(.*?)\]:\s*\[(.*?)\]""")
                            lines.forEach { line ->
                                val match = regex.find(line)
                                if (match != null) {
                                    props[match.groupValues[1]] = match.groupValues[2]
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // Check standard vendor dirs
                    val vendorDir = File("/vendor").takeIf { it.exists() && it.canRead() }
                        ?: File("/system/vendor").takeIf { it.exists() && it.canRead() }

                    VendorHalRilEngine.runFullAnalysis(
                        rootDirectory = vendorDir,
                        properties = props,
                        targetName = "Live Android Device (${android.os.Build.MODEL})"
                    )
                }
                _analysisResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Live analysis failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun runSampleAnalysis(sampleType: String = "mtk_legacy") {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val props = mutableMapOf<String, String>()
                    val files = mutableListOf<String>()
                    val logs = mutableListOf<String>()

                    when (sampleType) {
                        "mtk_legacy" -> {
                            props["ro.board.platform"] = "mt6737t"
                            props["ro.hardware"] = "mt6735"
                            props["ro.mediatek.platform"] = "MT6737T"
                            props["ro.build.version.release"] = "6.0.1"
                            props["ro.build.version.sdk"] = "23"
                            props["ro.treble.enabled"] = "false"
                            props["rild.libpath"] = "/vendor/lib/libmtk-ril.so"
                            props["rild.libargs"] = "-d /dev/ttyC0"
                            props["ro.telephony.default_network"] = "9,9"
                            props["ro.mtk_gemini_support"] = "1"
                            props["ro.mtk_bip_scws"] = "1"
                            props["ro.vendor.mtk_telephony_add_on_policy"] = "0"
                            props["ro.product.cpu.abi"] = "armeabi-v7a"

                            files.addAll(
                                listOf(
                                    "vendor/bin/rild",
                                    "vendor/bin/mtk_agpsd",
                                    "vendor/bin/wpa_supplicant",
                                    "vendor/lib/libmtk-ril.so",
                                    "vendor/lib/librilmtk.so",
                                    "vendor/lib/hw/audio.primary.mt6735.so",
                                    "vendor/lib/hw/camera.mt6735.so",
                                    "vendor/lib/hw/gralloc.mt6735.so",
                                    "vendor/lib/hw/sensors.mt6735.so",
                                    "vendor/lib/hw/bluetooth.default.so",
                                    "vendor/etc/audio_policy.conf",
                                    "vendor/etc/permissions/android.hardware.telephony.gsm.xml",
                                    "vendor/etc/permissions/android.hardware.camera.xml",
                                    "vendor/etc/permissions/android.hardware.wifi.xml",
                                    "vendor/etc/permissions/android.hardware.bluetooth.xml",
                                    "vendor/etc/permissions/android.hardware.sensor.accelerometer.xml"
                                )
                            )

                            logs.add("I/rild    (  245): RILD MTK Qualcomm/MTK dual SIM initialized")
                            logs.add("E/RILC    (  245): RIL_register: RIL version 11")
                            logs.add("type=1400 audit(0.0:42): avc: denied { read write } for pid=245 comm=\"rild\" name=\"ccci_c1\" dev=\"tmpfs\" ino=12345 scontext=u:r:rild:s0 tcontext=u:object_r:device:s0 tclass=chr_file permissive=0")
                        }
                        "treble_arm64" -> {
                            props["ro.board.platform"] = "universal7870"
                            props["ro.hardware"] = "samsungexynos7870"
                            props["ro.build.version.release"] = "9.0"
                            props["ro.build.version.sdk"] = "28"
                            props["ro.treble.enabled"] = "true"
                            props["ro.vndk.version"] = "28"
                            props["ro.product.cpu.abi"] = "arm64-v8a"
                            props["rild.libpath"] = "/vendor/lib64/libsec-ril.so"

                            files.addAll(
                                listOf(
                                    "vendor/etc/vintf/manifest.xml",
                                    "vendor/bin/hw/android.hardware.radio@1.2-service",
                                    "vendor/bin/hw/android.hardware.audio@4.0-service",
                                    "vendor/bin/hw/android.hardware.camera.provider@2.4-service",
                                    "vendor/bin/hw/android.hardware.wifi@1.0-service",
                                    "vendor/bin/hw/android.hardware.bluetooth@1.0-service",
                                    "vendor/bin/hw/android.hardware.sensors@1.0-service",
                                    "vendor/lib64/libsec-ril.so",
                                    "vendor/lib64/hw/audio.primary.exynos7870.so",
                                    "vendor/lib64/hw/camera.exynos7870.so",
                                    "vendor/etc/audio_policy_configuration.xml",
                                    "vendor/etc/permissions/android.hardware.telephony.gsm.xml",
                                    "vendor/etc/permissions/android.hardware.camera.xml",
                                    "vendor/etc/permissions/android.hardware.wifi.xml"
                                )
                            )
                        }
                    }

                    VendorHalRilEngine.runFullAnalysis(
                        rootDirectory = null,
                        properties = props,
                        filePaths = files,
                        logLines = logs,
                        targetName = if (sampleType == "mtk_legacy") "MediaTek MT6737 (ARM32 Legacy HAL)" else "Samsung Exynos (Treble ARM64 HAL)"
                    )
                }
                _analysisResult.value = result
            } catch (e: Exception) {
                _errorMessage.value = "Sample analysis failed: ${e.localizedMessage ?: e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun exportReport(): String {
        val result = _analysisResult.value ?: return "No analysis performed."
        val sb = StringBuilder()
        sb.appendLine("# Android Vendor, HAL & RIL Analysis Report")
        sb.appendLine("Generated on: ${java.util.Date(result.timestamp)}")
        sb.appendLine("Target: ${result.targetName}")
        sb.appendLine()
        sb.appendLine("## 1. System & Architecture Overview")
        sb.appendLine("- **Chipset / Platform:** ${result.vendorInfo.chipsetPlatform}")
        sb.appendLine("- **Vendor Manufacturer:** ${result.vendorInfo.vendorManufacturer}")
        sb.appendLine("- **Primary Architecture:** ${result.vendorInfo.primaryArch}")
        sb.appendLine("- **Android Target Version:** ${result.vendorInfo.androidTargetVersion}")
        sb.appendLine("- **Treble Architecture:** ${result.vendorInfo.trebleStatus}")
        sb.appendLine()
        sb.appendLine("## 2. Hardware Subsystem Matrix")
        sb.appendLine(result.hardwareMatrix.summary)
        sb.appendLine("| Subsystem | Files | HAL | Service | Libs | Overall Status | Notes |")
        sb.appendLine("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |")
        for (item in result.hardwareMatrix.items) {
            sb.appendLine("| ${item.displayName} | ${item.filesStatus} | ${item.halStatus} | ${item.serviceStatus} | ${item.librariesStatus} | ${item.overallStatus} | ${item.notes} |")
        }
        sb.appendLine()
        sb.appendLine("## 3. RIL & Telephony Diagnostics")
        sb.appendLine("- **Readiness Level:** ${result.rilInfo.readinessScore.overallReadiness} (${result.rilInfo.readinessScore.readinessPercentage}%)")
        sb.appendLine("- **Summary:** ${result.rilInfo.readinessScore.diagnosticSummary}")
        sb.appendLine("- **Daemons:** ${result.rilInfo.daemons.joinToString { it.name }}")
        sb.appendLine("- **Libraries:** ${result.rilInfo.libraries.joinToString { "${it.name} (${it.vendorFlavor})" }}")
        sb.appendLine("- **Init Services:** ${result.rilInfo.initServices.joinToString { it.serviceName }}")
        sb.appendLine()
        sb.appendLine("## 4. Issues & Recommendations (${result.allIssues.size} total)")
        for ((idx, issue) in result.allIssues.withIndex()) {
            sb.appendLine("### ${idx + 1}. [${issue.severity}] ${issue.message}")
            sb.appendLine("- **Type:** ${issue.type}")
            sb.appendLine("- **Source:** ${issue.source}")
            sb.appendLine("- **Evidence:** ${issue.evidence}")
            sb.appendLine("- **Recommendation:** ${issue.recommendation}")
            sb.appendLine()
        }
        return sb.toString()
    }
}
