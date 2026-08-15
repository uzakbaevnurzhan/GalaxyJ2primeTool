package com.example.ui.analyzer.selinux.engine

import com.example.ui.analyzer.selinux.model.SelinuxAnalysisResult
import org.json.JSONArray
import org.json.JSONObject

object SelinuxExporter {

    fun exportToJson(result: SelinuxAnalysisResult): String {
        val root = JSONObject()
        root.put("detectedType", result.detectedType.name)
        root.put("totalLinesParsed", result.totalLinesParsed)
        root.put("skippedLines", result.skippedLinesCount)

        result.detectedStatus?.let { status ->
            val statusObj = JSONObject()
            statusObj.put("mode", status.mode.name)
            statusObj.put("evidence", JSONArray(status.sourceEvidence))
            statusObj.put("hasConflict", status.hasConflict)
            root.put("status", statusObj)
        }

        val stats = result.avcStatistics
        if (stats != null) {
            val statsObj = JSONObject()
            statsObj.put("totalDenials", stats.totalDenials)
            statsObj.put("uniqueDenials", stats.uniqueDenials)
            statsObj.put("permissiveCount", stats.permissiveCount)
            statsObj.put("enforcingCount", stats.enforcingCount)
            statsObj.put("mostFrequentSource", stats.mostFrequentSource?.first ?: "none")
            statsObj.put("mostFrequentTarget", stats.mostFrequentTarget?.first ?: "none")
            statsObj.put("mostFrequentPermission", stats.mostFrequentPermission?.first ?: "none")
            statsObj.put("mostFrequentClass", stats.mostFrequentClass?.first ?: "none")
            root.put("statistics", statsObj)
        }

        val groupsArray = JSONArray()
        for (g in result.avcGroups) {
            val gObj = JSONObject()
            gObj.put("source", g.sourceDomain)
            gObj.put("target", g.targetDomain)
            gObj.put("class", g.tclass)
            gObj.put("permission", g.permission)
            gObj.put("count", g.count)
            gObj.put("isPermissive", g.isPermissive)
            gObj.put("suggestedRule", g.suggestedRule)
            groupsArray.put(gObj)
        }
        root.put("groups", groupsArray)

        val denialsArray = JSONArray()
        for (d in result.avcDenials.take(500)) { // limit export to first 500 for compact json if huge
            val dObj = JSONObject()
            dObj.put("comm", d.comm)
            dObj.put("pid", d.pid)
            dObj.put("source", d.sourceDomain)
            dObj.put("target", d.targetDomain)
            dObj.put("class", d.tclass)
            dObj.put("permissions", JSONArray(d.permissions))
            dObj.put("path", d.path)
            dObj.put("isPermissive", d.isPermissive)
            denialsArray.put(dObj)
        }
        root.put("denials", denialsArray)
        root.put("warnings", JSONArray(result.warnings))
        root.put("bootDiagnosis", JSONArray(result.bootDiagnosis))

        return root.toString(2)
    }

    fun exportToMarkdown(result: SelinuxAnalysisResult): String {
        val sb = StringBuilder()
        sb.appendLine("# SELinux Analysis Report")
        sb.appendLine()
        sb.appendLine("**Detected File Type:** `${result.detectedType.name}`")
        if (result.detectedStatus != null) {
            sb.appendLine("**SELinux Mode:** `${result.detectedStatus.mode.name}` (Conflict: ${result.detectedStatus.hasConflict})")
        }
        sb.appendLine("**Total Lines Analyzed:** ${result.totalLinesParsed}")
        sb.appendLine()

        if (result.bootDiagnosis.isNotEmpty()) {
            sb.appendLine("## ⚠️ Boot Diagnostics")
            for (diag in result.bootDiagnosis) {
                sb.appendLine("- $diag")
            }
            sb.appendLine()
        }

        val stats = result.avcStatistics
        if (stats != null) {
            sb.appendLine("## 📊 Denial Statistics")
            sb.appendLine("| Metric | Value |")
            sb.appendLine("| --- | --- |")
            sb.appendLine("| Total Denials | ${stats.totalDenials} |")
            sb.appendLine("| Unique Rules | ${stats.uniqueDenials} |")
            sb.appendLine("| Enforcing Denials | ${stats.enforcingCount} |")
            sb.appendLine("| Permissive Denials | ${stats.permissiveCount} |")
            sb.appendLine("| Top Source | `${stats.mostFrequentSource?.first ?: "N/A"}` (${stats.mostFrequentSource?.second ?: 0}) |")
            sb.appendLine("| Top Target | `${stats.mostFrequentTarget?.first ?: "N/A"}` (${stats.mostFrequentTarget?.second ?: 0}) |")
            sb.appendLine("| Top Permission | `${stats.mostFrequentPermission?.first ?: "N/A"}` (${stats.mostFrequentPermission?.second ?: 0}) |")
            sb.appendLine("| Top Class | `${stats.mostFrequentClass?.first ?: "N/A"}` (${stats.mostFrequentClass?.second ?: 0}) |")
            sb.appendLine()
        }

        if (result.avcGroups.isNotEmpty()) {
            sb.appendLine("## 🛠️ Required SELinux Policy Rules")
            sb.appendLine("```te")
            for (g in result.avcGroups) {
                sb.appendLine("${g.suggestedRule} # count: ${g.count}${if (g.isPermissive) " (permissive)" else ""}")
            }
            sb.appendLine("```")
            sb.appendLine()
        }

        if (result.fileContexts.isNotEmpty()) {
            sb.appendLine("## 📂 File Contexts (${result.fileContexts.size} entries)")
            sb.appendLine("| Path Pattern | Type | Context |")
            sb.appendLine("| --- | --- | --- |")
            for (fc in result.fileContexts.take(100)) {
                sb.appendLine("| `${fc.pathRegex}` | ${fc.fileTypeDescription} | `${fc.context?.raw ?: "<<none>>"}` |")
            }
            sb.appendLine()
        }

        if (result.propertyContexts.isNotEmpty()) {
            sb.appendLine("## 🏷️ Property Contexts (${result.propertyContexts.size} entries)")
            sb.appendLine("| Property Pattern | Context | Type |")
            sb.appendLine("| --- | --- | --- |")
            for (pc in result.propertyContexts.take(100)) {
                sb.appendLine("| `${pc.propertyPattern}` | `${pc.context?.raw ?: "none"}` | ${pc.typeClass ?: "-"} |")
            }
            sb.appendLine()
        }

        if (result.serviceContexts.isNotEmpty()) {
            sb.appendLine("## 🔌 Service Contexts (${result.serviceContexts.size} entries)")
            sb.appendLine("| Service Name | Context |")
            sb.appendLine("| --- | --- |")
            for (sc in result.serviceContexts.take(100)) {
                sb.appendLine("| `${sc.serviceName}` | `${sc.context?.raw ?: "none"}` |")
            }
            sb.appendLine()
        }

        if (result.seappContexts.isNotEmpty()) {
            sb.appendLine("## 📱 Seapp Contexts (${result.seappContexts.size} entries)")
            sb.appendLine("| User | Domain | Type | Name / Level |")
            sb.appendLine("| --- | --- | --- | --- |")
            for (sa in result.seappContexts.take(100)) {
                sb.appendLine("| `${sa.user ?: "-"}` | `${sa.domain ?: "-"}` | `${sa.type ?: "-"}` | ${sa.name ?: sa.levelFrom ?: "-"} |")
            }
            sb.appendLine()
        }

        if (result.warnings.isNotEmpty()) {
            sb.appendLine("## ⚠️ Warnings")
            for (w in result.warnings) {
                sb.appendLine("- $w")
            }
        }

        return sb.toString()
    }

    fun exportToText(result: SelinuxAnalysisResult): String {
        val sb = StringBuilder()
        sb.appendLine("=== SELINUX ANALYSIS REPORT ===")
        sb.appendLine("Detected Type: ${result.detectedType.name}")
        if (result.detectedStatus != null) {
            sb.appendLine("SELinux Mode: ${result.detectedStatus.mode.name}")
            sb.appendLine("Evidence: ${result.detectedStatus.sourceEvidence.joinToString("; ")}")
        }
        sb.appendLine("Total lines analyzed: ${result.totalLinesParsed}")
        sb.appendLine()

        val stats = result.avcStatistics
        if (stats != null) {
            sb.appendLine("--- AVC DENIAL STATISTICS ---")
            sb.appendLine("Total Denials: ${stats.totalDenials}")
            sb.appendLine("Unique Rules: ${stats.uniqueDenials}")
            sb.appendLine("Enforcing Denials: ${stats.enforcingCount}")
            sb.appendLine("Permissive Denials: ${stats.permissiveCount}")
            sb.appendLine("Top Source: ${stats.mostFrequentSource?.first} (${stats.mostFrequentSource?.second})")
            sb.appendLine("Top Target: ${stats.mostFrequentTarget?.first} (${stats.mostFrequentTarget?.second})")
            sb.appendLine("Top Permission: ${stats.mostFrequentPermission?.first} (${stats.mostFrequentPermission?.second})")
            sb.appendLine("Top Class: ${stats.mostFrequentClass?.first} (${stats.mostFrequentClass?.second})")
            sb.appendLine()
        }

        if (result.avcGroups.isNotEmpty()) {
            sb.appendLine("--- RECOMMENDED POLICY ALLOW RULES ---")
            for (g in result.avcGroups) {
                sb.appendLine("${g.suggestedRule} // count: ${g.count}")
            }
            sb.appendLine()
        }

        if (result.bootDiagnosis.isNotEmpty()) {
            sb.appendLine("--- BOOT DIAGNOSIS ---")
            for (d in result.bootDiagnosis) {
                sb.appendLine("• $d")
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}
