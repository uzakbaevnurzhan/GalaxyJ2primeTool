package com.example.ui.analyzer.kernel.studio.analyzer

import android.content.Context
import com.example.ui.analyzer.boot.BootHeaderParser
import com.example.ui.analyzer.kernel.studio.compatibility.KernelCompatibilityAnalyzer
import com.example.ui.analyzer.kernel.studio.compatibility.KernelHardwareAnalyzer
import com.example.ui.analyzer.kernel.studio.dtb.DtbAnalyzer
import com.example.ui.analyzer.kernel.studio.models.CmdlineComparisonItem
import com.example.ui.analyzer.kernel.studio.models.KernelAnalysisResult
import com.example.ui.analyzer.kernel.studio.models.KernelCmdlineEntry
import com.example.ui.analyzer.kernel.studio.models.KernelConfig
import com.example.ui.analyzer.kernel.studio.models.KernelInfo
import com.example.ui.analyzer.kernel.studio.models.KernelIssue
import com.example.ui.analyzer.kernel.studio.models.KernelIssueSeverity
import com.example.ui.analyzer.kernel.studio.models.KernelIssueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

object KernelStudioAnalyzer {

    suspend fun analyzeKernelOrBootImage(
        bytes: ByteArray,
        fileName: String = "kernel_or_boot.img"
    ): KernelAnalysisResult = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val issues = mutableListOf<KernelIssue>()

        logs.add("Starting analysis of $fileName (${bytes.size} bytes)...")

        var kernelBytes = bytes
        var dtbBytes: ByteArray? = null
        var bootCmdline = ""

        // 1. Check if input is a boot.img (magic ANDROID!)
        if (bytes.size >= 64 && bytes[0] == 'A'.code.toByte() && bytes[1] == 'N'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'R'.code.toByte() &&
            bytes[4] == 'O'.code.toByte() && bytes[5] == 'I'.code.toByte() &&
            bytes[6] == 'D'.code.toByte() && bytes[7] == '!'.code.toByte()
        ) {
            logs.add("Detected Android Boot Image header format")
            val bootHeader = BootHeaderParser.parseHeaderBytes(bytes, bytes.size.toLong())
            bootCmdline = (bootHeader.cmdline + " " + bootHeader.extraCmdline).trim()

            // Extract kernel slice
            val kOffset = bootHeader.kernelOffset.toInt()
            val kSize = bootHeader.kernelSize.toInt()
            if (kOffset > 0 && kSize > 0 && kOffset + kSize <= bytes.size) {
                kernelBytes = ByteArray(kSize)
                System.arraycopy(bytes, kOffset, kernelBytes, 0, kSize)
                logs.add("Extracted kernel payload: $kSize bytes at offset $kOffset")
            }

            // Check if separate DTB in boot header v2
            if (bootHeader.dtbSize > 0 && bootHeader.dtbLoadAddr > 0) {
                // In boot v2, dtb is after second stage
                val pageCount = { size: Long -> ((size + bootHeader.pageSize - 1) / bootHeader.pageSize) * bootHeader.pageSize }
                val dtbOffset = (bootHeader.secondOffset + pageCount(bootHeader.secondStageSize)).toInt()
                val dtbSize = bootHeader.dtbSize.toInt()
                if (dtbOffset + dtbSize <= bytes.size) {
                    dtbBytes = ByteArray(dtbSize)
                    System.arraycopy(bytes, dtbOffset, dtbBytes, 0, dtbSize)
                    logs.add("Extracted standalone DTB payload from boot image: $dtbSize bytes")
                }
            }
        }

        // 2. Format & Compression Detection
        val formatInfo = KernelFormatDetector.detect(kernelBytes)
        logs.add("Format detected: ${formatInfo.format}, Compression: ${formatInfo.compression}")

        val decompressedBytes = if (formatInfo.compression != "none") {
            KernelFormatDetector.decompressIfPossible(kernelBytes, formatInfo.compression) ?: kernelBytes
        } else {
            kernelBytes
        }

        // 3. String & Version Analysis
        val stringAnalysis = KernelStringAnalyzer.analyze(decompressedBytes)
        logs.add("Extracted strings: ${stringAnalysis.rawStrings.size}, Linux version: ${stringAnalysis.versionInfo.fullString}")

        // 4. Architecture Detection
        val (arch, archConflictReason) = KernelArchitectureDetector.detect(
            decompressedBytes,
            stringAnalysis.rawStrings,
            formatInfo.architecture
        )
        logs.add("Architecture detected: $arch")

        if (archConflictReason != null) {
            issues.add(
                KernelIssue(
                    type = KernelIssueType.ARCHITECTURE_MISMATCH,
                    severity = KernelIssueSeverity.ERROR,
                    message = "Architecture contradiction detected in kernel binary",
                    evidence = archConflictReason,
                    source = "Kernel Architecture Detector"
                )
            )
        }

        // 5. Config Analysis
        val configs = KernelConfigAnalyzer.parseConfigs(decompressedBytes, stringAnalysis.rawStrings)
        logs.add("Extracted ${configs.size} kernel configuration items")

        // 6. Cmdline Analysis
        val cmdlineEntries = if (bootCmdline.isNotBlank()) {
            KernelCmdlineAnalyzer.parse(bootCmdline, "boot.img")
        } else emptyList()

        // 7. Device Tree (DTB/DTBO) Analysis
        val dtbTargetBytes = dtbBytes ?: decompressedBytes
        val dtbResult = DtbAnalyzer.analyze(dtbTargetBytes)
        logs.add("Device tree analysis completed: ${dtbResult.detectedDtbCount} DTB(s) found, ${dtbResult.hardwareNodes.size} hardware nodes")

        // 8. Module Analysis
        val kernelInfo = KernelInfo(
            formatInfo = formatInfo.copy(architecture = arch),
            versionInfo = stringAnalysis.versionInfo,
            architecture = arch,
            rawSize = kernelBytes.size.toLong(),
            decompressedSize = decompressedBytes.size.toLong(),
            detectedStringsCount = stringAnalysis.rawStrings.size,
            notes = listOf(
                "Build host/date: ${stringAnalysis.buildDate}",
                "Compiler: ${stringAnalysis.compiler} ${stringAnalysis.compilerVersion}",
                "SMP: ${stringAnalysis.isSmp}, PREEMPT: ${stringAnalysis.isPreempt}"
            )
        )

        // 9. Android 11 Signals & Hardware Profile Checks
        val portingSignals = KernelCompatibilityAnalyzer.analyzeAndroid11Signals(
            kernelInfo,
            configs,
            dtbResult.compatibleStrings
        )

        val j2PrimeEval = KernelHardwareAnalyzer.evaluateGalaxyJ2PrimeProfile(
            kernelInfo,
            dtbResult.compatibleStrings,
            dtbResult.hardwareNodes
        )

        val hardwareIssues = KernelHardwareAnalyzer.generateHardwareIssues(
            kernelInfo,
            configs,
            dtbResult.hardwareNodes,
            dtbResult.compatibleStrings
        )
        issues.addAll(hardwareIssues)

        KernelAnalysisResult(
            kernelInfo = kernelInfo,
            configs = configs,
            cmdlineEntries = cmdlineEntries,
            cmdlineComparisons = emptyList(),
            rootDtbNode = dtbResult.rootNode,
            dtbCompatibleStrings = dtbResult.compatibleStrings,
            dtbHardwareNodes = dtbResult.hardwareNodes,
            dtboInfo = dtbResult.dtboInfo,
            issues = issues,
            portingSignals = portingSignals,
            isGalaxyJ2PrimeMatch = j2PrimeEval.isMatch,
            j2PrimeNotes = j2PrimeEval.notes,
            liveDeviceImported = false,
            logMessages = logs
        )
    }

    suspend fun importLiveDeviceData(): KernelAnalysisResult = withContext(Dispatchers.IO) {
        val logs = mutableListOf<String>()
        val issues = mutableListOf<KernelIssue>()
        var configs = listOf<KernelConfig>()
        var cmdlines = listOf<KernelCmdlineEntry>()
        var versionStr = "UNKNOWN"

        logs.add("Reading live device kernel data from /proc and /sys (READ-ONLY)...")

        // 1. Read /proc/version
        try {
            val procVersionFile = File("/proc/version")
            if (procVersionFile.exists() && procVersionFile.canRead()) {
                versionStr = procVersionFile.readText().trim()
                logs.add("Read /proc/version: $versionStr")
            } else {
                logs.add("/proc/version is not accessible")
            }
        } catch (e: Exception) {
            logs.add("Failed to read /proc/version: ${e.message}")
        }

        // 2. Read /proc/cmdline
        try {
            val procCmdlineFile = File("/proc/cmdline")
            if (procCmdlineFile.exists() && procCmdlineFile.canRead()) {
                val text = procCmdlineFile.readText().trim()
                cmdlines = KernelCmdlineAnalyzer.parse(text, "/proc/cmdline")
                logs.add("Read /proc/cmdline (${cmdlines.size} parameters)")
            }
        } catch (e: Exception) {
            logs.add("Failed to read /proc/cmdline: ${e.message}")
        }

        // 3. Read /proc/config.gz
        try {
            val configGz = File("/proc/config.gz")
            if (configGz.exists() && configGz.canRead()) {
                val decompressed = GZIPInputStream(FileInputStream(configGz)).use { it.readBytes() }
                configs = KernelConfigAnalyzer.parseConfigs(decompressed)
                logs.add("Extracted ${configs.size} configs from /proc/config.gz")
            }
        } catch (e: Exception) {
            logs.add("/proc/config.gz not available or unreadable")
        }

        val versionInfo = KernelVersionParser.parse(versionStr)
        val kernelInfo = KernelInfo(
            formatInfo = com.example.ui.analyzer.kernel.studio.models.KernelFormatInfo(
                format = "live",
                compression = "none",
                architecture = System.getProperty("os.arch") ?: "unknown"
            ),
            versionInfo = versionInfo,
            architecture = if (System.getProperty("os.arch")?.contains("64") == true) "ARM64" else "ARM32",
            rawSize = 0L,
            decompressedSize = 0L,
            detectedStringsCount = 0,
            notes = listOf("Imported directly from live running kernel")
        )

        val portingSignals = KernelCompatibilityAnalyzer.analyzeAndroid11Signals(
            kernelInfo,
            configs,
            emptyList()
        )

        KernelAnalysisResult(
            kernelInfo = kernelInfo,
            configs = configs,
            cmdlineEntries = cmdlines,
            cmdlineComparisons = emptyList(),
            rootDtbNode = null,
            dtbCompatibleStrings = emptyList(),
            dtbHardwareNodes = emptyList(),
            dtboInfo = null,
            issues = issues,
            portingSignals = portingSignals,
            isGalaxyJ2PrimeMatch = null,
            j2PrimeNotes = listOf("Live device mode"),
            liveDeviceImported = true,
            logMessages = logs
        )
    }

    fun generateMarkdownReport(result: KernelAnalysisResult): String {
        val sb = StringBuilder()
        sb.append("# Kernel & Device Tree Studio Analysis Report\n\n")

        sb.append("## 1. Kernel Binary & Architecture\n")
        val kInfo = result.kernelInfo
        if (kInfo != null) {
            sb.append("- **Format**: ${kInfo.formatInfo.format} (${kInfo.formatInfo.compression})\n")
            sb.append("- **Architecture**: ${kInfo.architecture}\n")
            sb.append("- **Version**: ${kInfo.versionInfo.fullString}\n")
            sb.append("- **Compiler**: ${kInfo.versionInfo.compiler} ${kInfo.versionInfo.compilerVersion}\n")
            sb.append("- **Raw Size**: ${kInfo.rawSize} bytes\n")
            sb.append("- **SMP / PREEMPT**: SMP=${kInfo.versionInfo.isSmp}, PREEMPT=${kInfo.versionInfo.isPreempt}\n\n")
        } else {
            sb.append("*No kernel binary analyzed.*\n\n")
        }

        sb.append("## 2. Device Tree (DTB/DTBO) & Hardware Nodes\n")
        sb.append("- **Detected Hardware Nodes**: ${result.dtbHardwareNodes.size}\n")
        sb.append("- **Compatible Strings**: ${result.dtbCompatibleStrings.size}\n")
        if (result.dtboInfo != null) {
            sb.append("- **DTBO Table**: ${result.dtboInfo.entries.size} entries (Magic: ${result.dtboInfo.magic})\n")
        }
        sb.append("\n### Hardware Peripherals Summary:\n")
        result.dtbHardwareNodes.take(20).forEach { hw ->
            sb.append("- [${hw.category}] `${hw.path}` (Compatible: ${hw.compatible.joinToString(", ")})\n")
        }
        sb.append("\n")

        sb.append("## 3. Kernel Configuration (CONFIG_*)\n")
        sb.append("- **Total Configs**: ${result.configs.size}\n")
        sb.append("- **Enabled**: ${result.configs.count { it.state == com.example.ui.analyzer.kernel.studio.models.ConfigState.ENABLED }}\n")
        sb.append("- **Modules**: ${result.configs.count { it.state == com.example.ui.analyzer.kernel.studio.models.ConfigState.MODULE }}\n\n")

        sb.append("## 4. Android 11 Porting Compatibility Signals\n")
        result.portingSignals.forEach { sig ->
            sb.append("- **[${sig.category}] ${sig.title}**: ${sig.description} *(Evidence: ${sig.evidence})*\n")
        }
        sb.append("\n")

        sb.append("## 5. Detected Issues & Warnings\n")
        if (result.issues.isEmpty()) {
            sb.append("No critical kernel or device tree issues detected.\n")
        } else {
            result.issues.forEach { issue ->
                sb.append("- **[${issue.severity}] ${issue.type}**: ${issue.message}\n  *Evidence: ${issue.evidence} (${issue.source})*\n")
            }
        }

        return sb.toString()
    }
}
