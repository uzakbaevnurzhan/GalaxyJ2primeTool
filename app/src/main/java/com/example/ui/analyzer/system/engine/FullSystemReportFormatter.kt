package com.example.ui.analyzer.system.engine

import com.example.data.model.ReportFormat
import com.example.ui.analyzer.system.models.FullSystemAnalysisResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FullSystemReportFormatter {

    fun formatReport(result: FullSystemAnalysisResult, format: ReportFormat): String {
        return when (format) {
            ReportFormat.MARKDOWN -> formatMarkdown(result)
            ReportFormat.JSON -> formatJson(result)
            ReportFormat.TXT -> formatText(result)
            ReportFormat.CSV -> formatCsv(result)
        }
    }

    private fun formatMarkdown(r: FullSystemAnalysisResult): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateStr = sdf.format(Date(r.timestamp))

        val sb = StringBuilder()
        sb.append("# 📱 Galaxy J2 Prime Tool — Full System Analysis Report\n")
        sb.append("**Version:** ${r.appVersion}  \n")
        sb.append("**Generated:** $dateStr  \n")
        sb.append("**Analysis Mode:** ${r.analysisMode.title}  \n")
        sb.append("**Elapsed Time:** ${r.elapsedMillis} ms  \n\n")

        sb.append("---\n\n")

        // Executive Summary
        sb.append("## 📊 1. System Health & Executive Summary\n\n")
        sb.append("| Metric | Status / Value |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **Overall System Health** | **${r.healthStatus.label}** |\n")
        sb.append("| **Last Confirmed Working Stage** | `${r.lastConfirmedWorkingStage}` |\n")
        sb.append("| **Suspected Failure Stage** | `${r.suspectedFailureStage ?: "None"}` |\n")
        sb.append("| **Working Components** | `${r.workingCount}` |\n")
        sb.append("| **Failed Components** | `${r.failedCount}` |\n")
        sb.append("| **Partial Components** | `${r.partialCount}` |\n")
        sb.append("| **Unknown / Unverified** | `${r.unknownCount}` |\n")
        sb.append("| **Total Distinct Errors** | `${r.totalErrorsCount}` (Blockers: `${r.blockersCount}`, Critical: `${r.criticalCount}`) |\n\n")

        // Capabilities
        sb.append("### 🔌 Capabilities Matrix\n\n")
        sb.append("- **Root Privileges:** ${if (r.capabilities.rootAvailable) "✅ AVAILABLE" else "⚠️ UNAVAILABLE (Non-root mode)"}\n")
        sb.append("- **Procfs (/proc):** ${if (r.capabilities.procAvailable) "✅ READABLE" else "❌ UNAVAILABLE"}\n")
        sb.append("- **Sysfs (/sys):** ${if (r.capabilities.sysAvailable) "✅ READABLE" else "❌ UNAVAILABLE"}\n")
        sb.append("- **Partitions (/dev/block):** ${if (r.capabilities.partitionsAvailable) "✅ READABLE" else "⚠️ RESTRICTED"}\n")
        sb.append("- **Pstore / Ram-Oops:** ${if (r.capabilities.pstoreAvailable) "✅ AVAILABLE" else "⚠️ UNAVAILABLE"}\n")
        sb.append("- **USB / ADB:** USB Connected: `${r.capabilities.usbConnected}`, ADB Enabled: `${r.capabilities.adbEnabled}`\n\n")

        // Android Version & Conflict Check
        sb.append("## 🤖 2. Android Version & Conflict Audit\n\n")
        if (r.androidVersionAudit.hasConflict) {
            sb.append("> ⚠️ **ANDROID VERSION CONFLICT DETECTED**  \n")
            sb.append("> ${r.androidVersionAudit.conflictSummary}\n\n")
        }
        sb.append("| Source | Release | SDK |\n")
        sb.append("| :--- | :--- | :--- |\n")
        sb.append("| **Live Android API** | `${r.androidVersionAudit.liveRelease}` | `${r.androidVersionAudit.liveSdk}` |\n")
        sb.append("| **Getprop System** | `${r.androidVersionAudit.getpropRelease}` | `${r.androidVersionAudit.liveSdk}` |\n")
        if (r.androidVersionAudit.buildPropRelease != null) {
            sb.append("| **build.prop File** | `${r.androidVersionAudit.buildPropRelease}` | - |\n")
        }
        if (r.androidVersionAudit.projectRelease != null) {
            sb.append("| **Project Metadata** | `${r.androidVersionAudit.projectRelease}` | - |\n")
        }
        sb.append("| **Treble Architecture** | `${if (r.androidVersionAudit.isTreble) "TREBLE" else "NON-TREBLE"}` | ${r.androidVersionAudit.trebleDetails} |\n\n")

        // Device Specs
        sb.append("## 📋 3. Device Identification & Hardware Specs\n\n")
        sb.append("| Specification | Value | Source | Status |\n")
        sb.append("| :--- | :--- | :--- | :--- |\n")
        r.deviceSummary.forEach { (key, item) ->
            sb.append("| **$key** | `${item.value}` | ${item.source.label} | `${item.status.label}` |\n")
        }
        sb.append("\n")

        // Security & SELinux
        sb.append("## 🛡️ 4. Security & SELinux Audit\n\n")
        sb.append("- **SELinux Enforce Mode:** `${r.securityAudit.selinuxMode}` (${r.securityAudit.selinuxStatus.label})\n")
        sb.append("- **Root State:** `${r.securityAudit.rootStatus.label}` — Evidence: `${r.securityAudit.rootEvidence}`\n")
        sb.append("- **Verified Boot / AVB:** `${r.securityAudit.verifiedBootState}` (${r.securityAudit.avbStatus.label})\n")
        sb.append("- **Device Encryption:** `${r.securityAudit.encryptionState}`\n")
        sb.append("- **Debuggable Build:** `${r.securityAudit.debuggable}` (Tags: `${r.securityAudit.buildTags}`)\n")
        sb.append("- **Total SELinux Denials Found:** `${r.selinuxAudit.totalDenialsCount}`\n\n")

        if (r.selinuxAudit.topDenialsList.isNotEmpty()) {
            sb.append("#### Top AVC Denials:\n```text\n")
            r.selinuxAudit.topDenialsList.take(5).forEach { sb.append("$it\n") }
            sb.append("```\n\n")
        }

        // CPU, ABI, Memory & Storage
        sb.append("## ⚙️ 5. CPU, ABI, RAM & Storage Audit\n\n")
        if (r.cpuAbiAudit.hasAbiMismatch) {
            sb.append("> 🔴 **ABI MISMATCH DETECTED:** ${r.cpuAbiAudit.mismatchDetails}\n\n")
        }
        sb.append("- **CPU Arch:** `${r.cpuAbiAudit.cpuArchitecture}` | **Kernel Arch:** `${r.cpuAbiAudit.kernelArchitecture}`\n")
        sb.append("- **Primary System ABI:** `${r.cpuAbiAudit.systemAbi}` | **Vendor ABI:** `${r.cpuAbiAudit.vendorAbi}`\n")
        sb.append("- **RAM Total / Avail:** `${r.ramAudit.totalMemKb / 1024} MB` / `${r.ramAudit.availMemKb / 1024} MB` (Status: `${r.ramAudit.ramHealthStatus.label}`)\n")
        sb.append("- **Swap / ZRAM:** `${r.ramAudit.swapTotalKb / 1024} MB` / `${r.ramAudit.zramSizeKb / 1024} MB`\n")
        sb.append("- **Internal Storage Free:** `${r.storageAudit.internalFreeBytes / (1024 * 1024)} MB` / `${r.storageAudit.internalTotalBytes / (1024 * 1024)} MB`\n\n")

        // Partitions
        sb.append("## 💾 6. Partition Inspection\n\n")
        sb.append("| Partition | Path | Size (MB) | FS | Mounted | Status |\n")
        sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n")
        r.partitionAudit.take(15).forEach { part ->
            val sizeMb = if (part.sizeBytes > 0) "${part.sizeBytes / (1024 * 1024)} MB" else "-"
            sb.append("| **${part.name}** | `${part.path}` | $sizeMb | `${part.filesystem}` | ${if (part.isMounted) "YES (${part.mountPoint})" else "NO"} | `${part.status.label}` |\n")
        }
        sb.append("\n")

        // Kernel & Boot & DTB
        sb.append("## 🐧 7. Kernel, Boot Image & DTB Audit\n\n")
        sb.append("- **Kernel Release:** `${r.kernelAudit.linuxVersion}`\n")
        sb.append("- **Kernel Compiler:** `${r.kernelAudit.compiler}`\n")
        sb.append("- **Kernel Cmdline:** `${r.kernelAudit.cmdline}`\n")
        sb.append("- **Loaded Modules:** `${r.kernelAudit.loadedModulesCount}` modules in `/proc/modules`\n")
        sb.append("- **Boot Image Status:** `${r.bootAudit.status.label}` (${r.bootAudit.evidence})\n")
        sb.append("- **DTB Status:** `${r.dtbAudit.status.label}` (Detected SoC: `${r.dtbAudit.detectedSoC ?: "Unknown"}`)\n\n")

        // System Component Status Matrix
        sb.append("## 🧩 8. Subsystem & Component Status Matrix\n\n")
        sb.append("| Component | Category | Status | Primary Error / Evidence | Source |\n")
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n")
        r.halComponentMatrix.forEach { comp ->
            val errOrEv = comp.primaryError ?: comp.evidence.take(60)
            sb.append("| **${comp.componentName}** | ${comp.category} | **`${comp.status.label}`** | $errOrEv | ${comp.source.label} |\n")
        }
        sb.append("\n")

        // Root Cause Candidates
        if (r.rootCauses.isNotEmpty()) {
            sb.append("## 🔍 9. Root Cause Candidates & Failure Chains\n\n")
            r.rootCauses.forEachIndexed { idx, rc ->
                sb.append("### Candidate #${idx + 1}: ${rc.problem} (${rc.severity.label} — Confidence: ${rc.confidence}%)\n")
                sb.append("- **Component:** `${rc.component}`\n")
                sb.append("- **Evidence:** ${rc.evidence}\n")
                sb.append("- **Causation Chain:**\n")
                rc.causeChain.forEach { step ->
                    sb.append("  - ➔ `$step`\n")
                }
                sb.append("- **Actionable Next Step:** Run **${rc.nextTool}** ➔ *${rc.nextAction}*\n\n")
            }
        }

        // Deduplicated Errors
        if (r.deduplicatedErrors.isNotEmpty()) {
            sb.append("## ⚠️ 10. Deduplicated Error Registry (${r.deduplicatedErrors.size} distinct signatures)\n\n")
            sb.append("| Subsystem | Severity | Repeats | Error Message & Evidence | Recommended Tool |\n")
            sb.append("| :--- | :--- | :--- | :--- | :--- |\n")
            r.deduplicatedErrors.take(20).forEach { err ->
                sb.append("| **${err.subsystem.label}** | `${err.severity.label}` | `x${err.repeatCount}` | **${err.message.take(60)}** <br> `${err.rawEvidence.take(70)}` | `${err.relatedTool}` |\n")
            }
            sb.append("\n")
        }

        // Fix Suggestions
        if (r.fixSuggestions.isNotEmpty()) {
            sb.append("## 🛠️ 11. Actionable Recommendations & Next Steps\n\n")
            r.fixSuggestions.forEachIndexed { i, fix ->
                sb.append("${i + 1}. **${fix.problem}**\n")
                sb.append("   - Evidence: `${fix.evidence}`\n")
                sb.append("   - Next Tool: **${fix.nextTool}** (`${fix.nextToolRoute}`)\n")
                sb.append("   - Action: ${fix.nextAction}\n\n")
            }
        }

        sb.append("---\n")
        sb.append("*Generated by Galaxy J2 Prime Tool (SM-G532F/G/M Suite) • ${com.example.config.AppVersionConfig.RELEASE_NAME} Engine*\n")
        return sb.toString()
    }

    private fun formatJson(r: FullSystemAnalysisResult): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"id\": \"${r.id}\",\n")
        sb.append("  \"appVersion\": \"${r.appVersion}\",\n")
        sb.append("  \"timestamp\": ${r.timestamp},\n")
        sb.append("  \"healthStatus\": \"${r.healthStatus.name}\",\n")
        sb.append("  \"lastConfirmedWorkingStage\": \"${r.lastConfirmedWorkingStage}\",\n")
        sb.append("  \"suspectedFailureStage\": ${if (r.suspectedFailureStage != null) "\"${r.suspectedFailureStage}\"" else "null"},\n")
        sb.append("  \"counts\": {\n")
        sb.append("    \"working\": ${r.workingCount},\n")
        sb.append("    \"failed\": ${r.failedCount},\n")
        sb.append("    \"partial\": ${r.partialCount},\n")
        sb.append("    \"unknown\": ${r.unknownCount},\n")
        sb.append("    \"totalErrors\": ${r.totalErrorsCount},\n")
        sb.append("    \"blockers\": ${r.blockersCount},\n")
        sb.append("    \"critical\": ${r.criticalCount}\n")
        sb.append("  },\n")
        sb.append("  \"androidVersion\": {\n")
        sb.append("    \"liveRelease\": \"${r.androidVersionAudit.liveRelease}\",\n")
        sb.append("    \"liveSdk\": ${r.androidVersionAudit.liveSdk},\n")
        sb.append("    \"hasConflict\": ${r.androidVersionAudit.hasConflict},\n")
        sb.append("    \"isTreble\": ${r.androidVersionAudit.isTreble}\n")
        sb.append("  },\n")
        sb.append("  \"components\": [\n")
        r.halComponentMatrix.forEachIndexed { i, comp ->
            sb.append("    {\n")
            sb.append("      \"key\": \"${comp.componentKey}\",\n")
            sb.append("      \"name\": \"${comp.componentName}\",\n")
            sb.append("      \"category\": \"${comp.category}\",\n")
            sb.append("      \"status\": \"${comp.status.name}\",\n")
            sb.append("      \"source\": \"${comp.source.name}\",\n")
            sb.append("      \"evidence\": \"${escapeJson(comp.evidence)}\"\n")
            sb.append("    }${if (i < r.halComponentMatrix.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")
        sb.append("  \"errors\": [\n")
        r.deduplicatedErrors.forEachIndexed { i, err ->
            sb.append("    {\n")
            sb.append("      \"subsystem\": \"${err.subsystem.name}\",\n")
            sb.append("      \"severity\": \"${err.severity.name}\",\n")
            sb.append("      \"message\": \"${escapeJson(err.message)}\",\n")
            sb.append("      \"repeatCount\": ${err.repeatCount},\n")
            sb.append("      \"relatedTool\": \"${err.relatedTool}\"\n")
            sb.append("    }${if (i < r.deduplicatedErrors.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun formatText(r: FullSystemAnalysisResult): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("============================================================\n")
        sb.append("GALAXY J2 PRIME TOOL — FULL SYSTEM ANALYZER REPORT\n")
        sb.append("VERSION: ${r.appVersion}\n")
        sb.append("DATE: ${sdf.format(Date(r.timestamp))}\n")
        sb.append("============================================================\n\n")

        sb.append("1. SYSTEM HEALTH\n")
        sb.append("Health: ${r.healthStatus.label}\n")
        sb.append("Last Confirmed Working: ${r.lastConfirmedWorkingStage}\n")
        sb.append("Suspected Failure: ${r.suspectedFailureStage ?: "None"}\n")
        sb.append("Working: ${r.workingCount} | Failed: ${r.failedCount} | Partial: ${r.partialCount} | Unknown: ${r.unknownCount}\n")
        sb.append("Errors: ${r.totalErrorsCount} (Blockers: ${r.blockersCount}, Critical: ${r.criticalCount})\n\n")

        sb.append("2. ANDROID & SECURITY\n")
        sb.append("Live Release: ${r.androidVersionAudit.liveRelease} (SDK ${r.androidVersionAudit.liveSdk})\n")
        sb.append("SELinux: ${r.securityAudit.selinuxMode} (${r.securityAudit.selinuxStatus.label})\n")
        sb.append("Root: ${r.securityAudit.rootStatus.label} (${r.securityAudit.rootEvidence})\n\n")

        sb.append("3. COMPONENT STATUS MATRIX\n")
        r.halComponentMatrix.forEach { comp ->
            sb.append(String.format("%-25s | %-12s | %s\n", comp.componentName, comp.status.label, comp.primaryError ?: comp.evidence.take(50)))
        }
        sb.append("\n")

        sb.append("4. ROOT CAUSES & NEXT ACTIONS\n")
        r.rootCauses.forEachIndexed { i, rc ->
            sb.append("#${i + 1}: ${rc.problem} [${rc.severity.label}]\n")
            sb.append("  Evidence: ${rc.evidence}\n")
            sb.append("  Chain: ${rc.causeChain.joinToString(" -> ")}\n")
            sb.append("  Next Tool: ${rc.nextTool} -> ${rc.nextAction}\n\n")
        }

        return sb.toString()
    }

    private fun formatCsv(r: FullSystemAnalysisResult): String {
        val sb = StringBuilder()
        sb.append("Component,Category,Status,Source,PrimaryError,Evidence\n")
        r.halComponentMatrix.forEach { comp ->
            sb.append("\"${comp.componentName}\",\"${comp.category}\",\"${comp.status.name}\",\"${comp.source.name}\",\"${escapeCsv(comp.primaryError ?: "")}\",\"${escapeCsv(comp.evidence)}\"\n")
        }
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", "")
            .replace("\t", " ")
    }

    private fun escapeCsv(str: String): String {
        return str.replace("\"", "\"\"")
    }
}
