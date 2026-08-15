package com.example.ui.analyzer.getprop

object GetpropExporter {

    fun exportToMarkdown(result: GetpropAnalysisResult): String {
        val s = result.snapshot.deviceSummary
        val snap = result.snapshot

        return buildString {
            appendLine("# Android System Properties Analysis Report")
            appendLine()
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(snap.timestamp))}")
            appendLine("Snapshot Name: `${snap.name}`")
            appendLine()
            
            appendLine("## 1. Device & OS Summary")
            appendLine("| Parameter | Value |")
            appendLine("|---|---|")
            appendLine("| **Model** | ${s.model} |")
            appendLine("| **Brand / Manufacturer** | ${s.brand} / ${s.manufacturer} |")
            appendLine("| **Device / Board** | ${s.device} / ${s.board} |")
            appendLine("| **Android OS** | ${s.androidVersion} (SDK ${s.sdk}, ${s.codename}) |")
            appendLine("| **Build ID** | ${s.buildId} (`${s.buildDisplayId}`) |")
            appendLine("| **Security Patch** | ${s.securityPatch} |")
            appendLine("| **Primary ABI** | `${s.primaryAbi}` (${s.abiType}) |")
            appendLine("| **ABI List** | ${s.abiList.joinToString(", ")} |")
            appendLine("| **SoC / Platform** | ${s.platform} (Hardware: `${s.hardware}`, Chip: `${s.socModel}`) |")
            appendLine("| **SELinux Mode** | ${s.selinuxMode} |")
            appendLine("| **Build Tags** | ${s.buildTags} |")
            appendLine("| **Debuggable / Secure** | debuggable=${s.isDebuggable}, secure=${s.isSecure}, adbSecure=${s.isAdbSecure} |")
            appendLine()

            appendLine("## 2. Input Sources & Hashes")
            appendLine("| Source File | Size (Bytes) | SHA-256 | Parsed Lines | Skipped |")
            appendLine("|---|---|---|---|---|")
            snap.sources.forEach { src ->
                appendLine("| `${src.fileName}` | ${src.sizeBytes} | `${src.sha256}` | ${src.parsedCount} | ${src.skippedCount} |")
            }
            appendLine()

            appendLine("## 3. Subsystem Breakdown")
            appendLine("### Graphics & Display")
            appendLine("- **EGL Hardware**: `${result.graphics.eglHardware}`")
            appendLine("- **OpenGL ES Version**: ${result.graphics.glesVersionFormatted}")
            appendLine("- **HWUI Renderer**: `${result.graphics.hwuiRenderer}`")
            appendLine("- **LCD Density**: `${result.display.lcdDensity}`")
            appendLine()

            appendLine("### Runtime & ART / Dalvik VM")
            appendLine("- **Heap Start Size**: `${result.runtimeArt.heapStartSize}`")
            appendLine("- **Heap Growth Limit**: `${result.runtimeArt.heapGrowthLimit}`")
            appendLine("- **Heap Max Size**: `${result.runtimeArt.heapSize}`")
            appendLine("- **Heap Target Utilization**: `${result.runtimeArt.heapTargetUtilization}`")
            appendLine("- **JIT Compiler**: `${result.runtimeArt.useJit}`")
            appendLine("- **Dex2oat Filter**: `${result.runtimeArt.dex2oatFilter}`")
            appendLine()

            appendLine("### Telephony & RIL")
            appendLine("- **Status**: ${if (result.telephonyRil.isRilDetected) "RIL-related properties detected" else "No RIL properties detected"}")
            appendLine("- **RIL Implementation**: `${result.telephonyRil.rilImplementation}`")
            appendLine("- **RILD Library Path**: `${result.telephonyRil.rildLibPath}`")
            appendLine("- *Note*: ${result.telephonyRil.note}")
            appendLine()

            appendLine("### Camera & Audio")
            appendLine("- **Camera Properties Found**: ${result.media.cameraProperties.size}")
            appendLine("- **Audio Properties Found**: ${result.media.audioProperties.size}")
            appendLine("- **Media Properties Found**: ${result.media.mediaProperties.size}")
            appendLine("- *Note*: ${result.media.note}")
            appendLine()

            if (result.warnings.isNotEmpty() || result.conflictsList.isNotEmpty()) {
                appendLine("## 4. Warnings & Property Conflicts")
                result.warnings.forEach { warn ->
                    appendLine("- ⚠️ **Warning**: $warn")
                }
                result.conflictsList.forEach { conf ->
                    appendLine("- ⚠️ **Conflict on `${conf.key}`**:")
                    conf.occurrences.forEach { occ ->
                        appendLine("  - Source `${occ.source}` (line ${occ.lineNumber}): `${occ.value}`")
                    }
                }
                appendLine()
            }

            appendLine("## 5. Category Distribution")
            appendLine("| Category | Property Count |")
            appendLine("|---|---|")
            result.categoryCounts.entries.sortedByDescending { it.value }.forEach { (cat, count) ->
                appendLine("| **${cat.displayName}** | $count |")
            }
            appendLine("| **Total Unique Properties** | **${snap.totalPropertiesCount}** |")
            appendLine()

            appendLine("## 6. All Properties (${snap.totalPropertiesCount})")
            appendLine("| Key | Value | Category | Type | Source | Line |")
            appendLine("|---|---|---|---|---|---|")
            snap.properties.toSortedMap().forEach { (k, entry) ->
                val escapedVal = entry.value.replace("|", "\\|").replace("\n", " ")
                appendLine("| `$k` | `$escapedVal` | ${entry.category.displayName} | ${entry.valueType} | `${entry.source}` | ${entry.lineNumber} |")
            }
        }
    }

    fun exportToJson(result: GetpropAnalysisResult): String {
        val s = result.snapshot.deviceSummary
        val snap = result.snapshot

        fun escape(str: String): String = str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return buildString {
            appendLine("{")
            appendLine("  \"snapshot\": {")
            appendLine("    \"id\": \"${snap.id}\",")
            appendLine("    \"name\": \"${escape(snap.name)}\",")
            appendLine("    \"timestamp\": ${snap.timestamp},")
            appendLine("    \"totalPropertiesCount\": ${snap.totalPropertiesCount},")
            appendLine("    \"duplicateCount\": ${snap.duplicateCount},")
            appendLine("    \"conflictCount\": ${snap.conflictCount},")
            appendLine("    \"sources\": [")
            snap.sources.forEachIndexed { index, src ->
                appendLine("      {")
                appendLine("        \"fileName\": \"${escape(src.fileName)}\",")
                appendLine("        \"path\": \"${escape(src.path)}\",")
                appendLine("        \"sizeBytes\": ${src.sizeBytes},")
                appendLine("        \"sha256\": \"${src.sha256}\",")
                appendLine("        \"parsedCount\": ${src.parsedCount},")
                appendLine("        \"skippedCount\": ${src.skippedCount}")
                append("      }${if (index < snap.sources.size - 1) "," else ""}\n")
            }
            appendLine("    ],")
            appendLine("    \"deviceSummary\": {")
            appendLine("      \"model\": \"${escape(s.model)}\",")
            appendLine("      \"name\": \"${escape(s.name)}\",")
            appendLine("      \"device\": \"${escape(s.device)}\",")
            appendLine("      \"board\": \"${escape(s.board)}\",")
            appendLine("      \"brand\": \"${escape(s.brand)}\",")
            appendLine("      \"manufacturer\": \"${escape(s.manufacturer)}\",")
            appendLine("      \"hardware\": \"${escape(s.hardware)}\",")
            appendLine("      \"platform\": \"${escape(s.platform)}\",")
            appendLine("      \"socModel\": \"${escape(s.socModel)}\",")
            appendLine("      \"androidVersion\": \"${escape(s.androidVersion)}\",")
            appendLine("      \"sdk\": ${s.sdk},")
            appendLine("      \"codename\": \"${escape(s.codename)}\",")
            appendLine("      \"buildId\": \"${escape(s.buildId)}\",")
            appendLine("      \"buildDisplayId\": \"${escape(s.buildDisplayId)}\",")
            appendLine("      \"securityPatch\": \"${escape(s.securityPatch)}\",")
            appendLine("      \"incremental\": \"${escape(s.incremental)}\",")
            appendLine("      \"primaryAbi\": \"${escape(s.primaryAbi)}\",")
            appendLine("      \"abiType\": \"${escape(s.abiType)}\",")
            appendLine("      \"abiList\": [${s.abiList.joinToString(", ") { "\"${escape(it)}\"" }}],")
            appendLine("      \"selinuxMode\": \"${escape(s.selinuxMode)}\",")
            appendLine("      \"isDebuggable\": ${s.isDebuggable},")
            appendLine("      \"isSecure\": ${s.isSecure},")
            appendLine("      \"isAdbSecure\": ${s.isAdbSecure},")
            appendLine("      \"buildTags\": \"${escape(s.buildTags)}\"")
            appendLine("    }")
            appendLine("  },")
            appendLine("  \"properties\": [")
            val propList = snap.properties.values.toList()
            propList.forEachIndexed { index, prop ->
                appendLine("    {")
                appendLine("      \"key\": \"${escape(prop.key)}\",")
                appendLine("      \"value\": \"${escape(prop.value)}\",")
                appendLine("      \"source\": \"${escape(prop.source)}\",")
                appendLine("      \"lineNumber\": ${prop.lineNumber},")
                appendLine("      \"category\": \"${prop.category.name}\",")
                appendLine("      \"valueType\": \"${prop.valueType.name}\",")
                appendLine("      \"isDuplicate\": ${prop.isDuplicate},")
                appendLine("      \"conflictStatus\": \"${prop.conflictStatus.name}\"")
                append("    }${if (index < propList.size - 1) "," else ""}\n")
            }
            appendLine("  ]")
            append("}")
        }
    }

    fun exportToCsv(result: GetpropAnalysisResult): String {
        return buildString {
            appendLine("key,value,source,category,type,line")
            result.snapshot.properties.toSortedMap().forEach { (k, prop) ->
                val escapedKey = "\"${prop.key.replace("\"", "\"\"")}\""
                val escapedValue = "\"${prop.value.replace("\"", "\"\"")}\""
                val escapedSource = "\"${prop.source.replace("\"", "\"\"")}\""
                appendLine("$escapedKey,$escapedValue,$escapedSource,${prop.category.name},${prop.valueType.name},${prop.lineNumber}")
            }
        }
    }

    fun exportToTxt(result: GetpropAnalysisResult): String {
        return buildString {
            appendLine("--- ANDROID SYSTEM PROPERTIES REPORT ---")
            appendLine("Generated: ${java.util.Date(result.snapshot.timestamp)}")
            appendLine("Snapshot: ${result.snapshot.name}")
            appendLine()
            appendLine(result.rawSummary)
            appendLine()
            appendLine("--- ALL PROPERTIES (${result.snapshot.totalPropertiesCount}) ---")
            result.snapshot.properties.toSortedMap().forEach { (k, v) ->
                appendLine("$k=${v.value}")
            }
        }
    }

    fun exportDiffToMarkdown(diff: GetpropDiffResult): String {
        return buildString {
            appendLine("# ROM / Properties Snapshot Diff Report")
            appendLine()
            appendLine("Snapshot A: **${diff.snapshotAName}**")
            appendLine("Snapshot B: **${diff.snapshotBName}**")
            appendLine()
            appendLine("## Summary of Differences")
            appendLine("- **Added properties**: ${diff.addedCount}")
            appendLine("- **Removed properties**: ${diff.removedCount}")
            appendLine("- **Changed values**: ${diff.changedCount}")
            appendLine("- **Conflicts**: ${diff.conflictCount}")
            appendLine("- **Unchanged**: ${diff.unchangedCount}")
            appendLine()

            appendLine("## Category Diff Breakdown")
            appendLine("| Category | Changes Count |")
            appendLine("|---|---|")
            diff.categoryChangeCounts.entries.sortedByDescending { it.value }.forEach { (cat, count) ->
                appendLine("| **${cat.displayName}** | $count |")
            }
            appendLine()

            appendLine("## Detailed Differences")
            appendLine("| Status | Key | Value in A | Value in B | Category |")
            appendLine("|---|---|---|---|---|")
            diff.entries.filter { it.status != DiffStatus.UNCHANGED }.forEach { entry ->
                val valA = entry.valueA?.replace("|", "\\|") ?: "*(none)*"
                val valB = entry.valueB?.replace("|", "\\|") ?: "*(none)*"
                appendLine("| **${entry.status.displayName}** | `${entry.key}` | `$valA` | `$valB` | ${entry.category.displayName} |")
            }
        }
    }

    fun exportPortingCheckToMarkdown(check: GetpropPortingCheckResult): String {
        return buildString {
            appendLine("# Android ROM Porting Compatibility Check")
            appendLine()
            appendLine("Base ROM: **${check.baseSnapshotName}**")
            appendLine("Port ROM: **${check.portSnapshotName}**")
            appendLine("Overall Status: **${check.overallLevel.displayName.uppercase()}**")
            appendLine()
            appendLine("## Results Overview")
            appendLine("- Passed checks: ${check.passedCount}")
            appendLine("- Warnings (Potential Mismatches): ${check.warningCount}")
            appendLine("- Errors (Critical Blockers): ${check.errorCount}")
            appendLine()
            appendLine("> **Disclaimer**: ${check.disclaimer}")
            appendLine()

            appendLine("## Detailed Compatibility Items")
            check.items.forEach { item ->
                val icon = when (item.level) {
                    PortingCheckLevel.PASS -> "✅"
                    PortingCheckLevel.WARNING -> "⚠️"
                    PortingCheckLevel.ERROR -> "❌"
                }
                appendLine("### $icon [${item.level.displayName.uppercase()}] ${item.title} (${item.category})")
                appendLine("- **Finding**: ${item.message}")
                if (item.details != null) {
                    appendLine("- **Recommendation**: ${item.details}")
                }
                if (item.propKeyA != null || item.valueA != null) {
                    appendLine("- **Base Value**: `${item.propKeyA} = ${item.valueA}`")
                }
                if (item.propKeyB != null || item.valueB != null) {
                    appendLine("- **Port Value**: `${item.propKeyB} = ${item.valueB}`")
                }
                appendLine()
            }
        }
    }
}
