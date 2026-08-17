package com.example.data.manager

import android.content.Context
import android.os.Build
import com.example.data.model.ReportFormat
import com.example.data.model.ReportType
import com.example.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGeneratorEngine {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    data class ReportData(
        val reportType: String,
        val generatedAt: String,
        val projectName: String,
        val device: String,
        val brand: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdkInt: Int,
        val securityPatch: String,
        val fingerprint: String,
        val kernelVersion: String,
        val primaryAbi: String,
        val rootAvailable: Boolean,
        val selinuxStatus: String,
        val sections: Map<String, String>,
        val warnings: List<String>,
        val errors: List<String>,
        val hashes: Map<String, String>
    )

    suspend fun generateReport(
        context: Context,
        type: ReportType,
        format: ReportFormat,
        projectName: String = "Global Workspace",
        customDetails: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        val isRooted = RootShell.isRootAvailable()
        val selinux = RootShell.executeCommand("getenforce").getOrNull() ?: "Enforcing"
        val kernel = RootShell.executeCommand("cat /proc/version").getOrNull() ?: (System.getProperty("os.version") ?: "Linux Unknown")

        val sections = mutableMapOf<String, String>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val hashes = mutableMapOf<String, String>()

        when (type) {
            ReportType.DEVICE_REPORT -> {
                sections["Hardware"] = "Board: ${Build.BOARD}, Hardware: ${Build.HARDWARE}, Cores: ${Runtime.getRuntime().availableProcessors()}"
                sections["Memory"] = "Total RAM: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB"
                val partitionsOut = RootShell.executeCommand("cat /proc/partitions").getOrNull() ?: "Standard user partitions"
                sections["Partitions"] = partitionsOut
            }
            ReportType.ROM_REPORT -> {
                sections["ROM Environment"] = "Target device: Samsung Galaxy J2 Prime (SM-G532F / MT6737T)\nArchitecture: ARM32 / armv7-a-neon\nTreble: Legacy non-Treble"
                sections["Filesystem Support"] = "EXT4, RAW Sparse, EROFS, Super Sparse"
            }
            ReportType.BUILD_REPORT -> {
                sections["Build Pipeline"] = "Pipeline stages: Prepare, Validate, Build, Package, Post-Validate, Hash, Certify"
                sections["Packaging Format"] = "Flashable ZIP with Edify update-binary or standard meta-inf"
            }
            ReportType.PATCH_REPORT -> {
                sections["Patch Engine"] = "Supported Operations: build.prop, XML, init.rc, permissions, symlinks, binary replacements"
                sections["Rollback Guard"] = "Active transaction safety with pre-patch snapshots"
            }
            ReportType.DIAGNOSTIC_REPORT -> {
                sections["12-Stage Boot Audit"] = "Bootloader -> Kernel -> Ramdisk -> Init -> SELinux -> Vendor HAL -> Zygote -> System Server -> Framework -> SurfaceFlinger -> Telephony -> Launcher"
                val uptime = RootShell.executeCommand("uptime").getOrNull() ?: "N/A"
                sections["System Uptime"] = uptime
            }
            ReportType.ROOT_REPORT -> {
                sections["Root Status"] = if (isRooted) "Privileged root access GRANTED (UID=0, su binary responsive)" else "Root access NOT available"
                val suVersion = RootShell.executeCommand("su -v").getOrNull() ?: "N/A"
                sections["SU Version"] = suVersion
            }
            ReportType.USB_REPORT -> {
                sections["USB Host Controller"] = "Android USB Host API subsystem (android.hardware.usb.host)"
                sections["OTG Support"] = "Direct USB bulk transfer with Samsung Galaxy Download Mode VID:04E8"
            }
            ReportType.ADB_REPORT -> {
                sections["ADB Environment"] = "ADB Terminal & Fastboot protocol bridge with safe command execution guards"
            }
            ReportType.SAMSUNG_REPORT -> {
                sections["Samsung Odin Specification"] = "TAR/MD5 unpack & repack validator with PIT partition table alignment check"
                sections["Odin Protocols"] = "Loke / Odin Download mode flashing parameters"
            }
        }

        // Add any custom details
        sections.putAll(customDetails)

        val reportData = ReportData(
            reportType = type.name,
            generatedAt = dateStr,
            projectName = projectName,
            device = Build.MODEL,
            brand = Build.BRAND,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            fingerprint = Build.FINGERPRINT,
            kernelVersion = kernel,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm32",
            rootAvailable = isRooted,
            selinuxStatus = selinux,
            sections = sections,
            warnings = warnings,
            errors = errors,
            hashes = hashes
        )

        when (format) {
            ReportFormat.JSON -> json.encodeToString(reportData)
            ReportFormat.TXT -> formatAsTxt(reportData)
            ReportFormat.MARKDOWN -> formatAsMarkdown(reportData)
        }
    }

    private fun formatAsMarkdown(data: ReportData): String = buildString {
        appendLine("# ${data.reportType.replace("_", " ")}")
        appendLine()
        appendLine("**Project:** ${data.projectName}  ")
        appendLine("**Date:** ${data.generatedAt}  ")
        appendLine("**Device:** ${data.manufacturer} ${data.modelName(data.device)} (${data.brand})  ")
        appendLine("**Android Version:** ${data.androidVersion} (API ${data.sdkInt})  ")
        appendLine("**Security Patch:** ${data.securityPatch}  ")
        appendLine("**Kernel:** ${data.kernelVersion}  ")
        appendLine("**Primary ABI:** ${data.primaryAbi}  ")
        appendLine("**Root Privilege:** ${if (data.rootAvailable) "GRANTED (UID=0)" else "NO ROOT"}  ")
        appendLine("**SELinux:** ${data.selinuxStatus}  ")
        appendLine("**Fingerprint:** `${data.fingerprint}`  ")
        appendLine()
        appendLine("---")
        appendLine()
        data.sections.forEach { (title, content) ->
            appendLine("## $title")
            appendLine("```")
            appendLine(content.trim())
            appendLine("```")
            appendLine()
        }
        if (data.warnings.isNotEmpty()) {
            appendLine("## Warnings")
            data.warnings.forEach { appendLine("- ⚠️ $it") }
            appendLine()
        }
        if (data.errors.isNotEmpty()) {
            appendLine("## Errors")
            data.errors.forEach { appendLine("- ❌ $it") }
            appendLine()
        }
    }

    private fun formatAsTxt(data: ReportData): String = buildString {
        appendLine("==================================================")
        appendLine(" ${data.reportType.replace("_", " ")}")
        appendLine("==================================================")
        appendLine("Project:         ${data.projectName}")
        appendLine("Date:            ${data.generatedAt}")
        appendLine("Device:          ${data.manufacturer} ${data.device} (${data.brand})")
        appendLine("Android:         ${data.androidVersion} (API ${data.sdkInt})")
        appendLine("Security Patch:  ${data.securityPatch}")
        appendLine("Kernel:          ${data.kernelVersion}")
        appendLine("ABI:             ${data.primaryAbi}")
        appendLine("Root Access:     ${if (data.rootAvailable) "GRANTED" else "NO ROOT"}")
        appendLine("SELinux:         ${data.selinuxStatus}")
        appendLine("Fingerprint:     ${data.fingerprint}")
        appendLine("--------------------------------------------------")
        data.sections.forEach { (title, content) ->
            appendLine()
            appendLine("[$title]")
            appendLine(content.trim())
        }
        appendLine("==================================================")
    }

    private fun ReportData.modelName(device: String): String = device
}
