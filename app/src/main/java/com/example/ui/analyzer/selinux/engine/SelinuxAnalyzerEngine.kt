package com.example.ui.analyzer.selinux.engine

import android.content.Context
import android.net.Uri
import com.example.ui.analyzer.selinux.model.*
import com.example.ui.analyzer.selinux.parser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object SelinuxAnalyzerEngine {

    /**
     * Binary sepolicy magic header in little endian: 0xF97CFF8C -> bytes 0x8C, 0xFF, 0x7C, 0xF9 (or 0xF9, 0x7C, 0xFF, 0x8C depending on byte order)
     */
    private fun isBinarySepolicy(firstBytes: ByteArray): Boolean {
        if (firstBytes.size < 4) return false
        val b0 = firstBytes[0].toInt() and 0xFF
        val b1 = firstBytes[1].toInt() and 0xFF
        val b2 = firstBytes[2].toInt() and 0xFF
        val b3 = firstBytes[3].toInt() and 0xFF

        return (b0 == 0xF9 && b1 == 0x7C && b2 == 0xFF && b3 == 0x8C) ||
               (b0 == 0x8C && b1 == 0xFF && b2 == 0x7C && b3 == 0xF9)
    }

    /**
     * Parses an input stream of SELinux logs or context configuration files.
     * Supports streaming for arbitrary size logs (1GB+) without high memory allocation.
     */
    suspend fun analyzeStream(
        inputStream: InputStream,
        totalBytes: Long = 0L,
        fileName: String? = null,
        props: Map<String, String> = emptyMap(),
        onProgress: (bytesRead: Long, totalBytes: Long, progressPercent: Float, status: String) -> Unit = { _, _, _, _ -> }
    ): SelinuxAnalysisResult = withContext(Dispatchers.IO) {
        val bufferedInput = BufferedInputStream(inputStream)
        bufferedInput.mark(8)
        val headerBytes = ByteArray(4)
        val readHeader = bufferedInput.read(headerBytes)
        bufferedInput.reset()

        if (readHeader == 4 && isBinarySepolicy(headerBytes)) {
            return@withContext SelinuxAnalysisResult(
                detectedType = SelinuxFileType.BINARY_SEPOLICY,
                warnings = listOf("Binary sepolicy parsing is not supported yet. This tool analyzes textual SELinux contexts and AVC logs.")
            )
        }

        val reader = BufferedReader(InputStreamReader(bufferedInput, Charsets.UTF_8), 32 * 1024)
        
        var lineCount = 0L
        var skippedCount = 0L
        var approxBytesRead = 0L

        val avcList = mutableListOf<AvcDenial>()
        val fileContextList = mutableListOf<FileContextEntry>()
        val propertyContextList = mutableListOf<PropertyContextEntry>()
        val serviceContextList = mutableListOf<ServiceContextEntry>()
        val seappList = mutableListOf<SeappContextEntry>()
        val genfsList = mutableListOf<GenfsContextEntry>()
        val warnings = mutableListOf<String>()

        var hasPermissiveAudit = false
        var hasEnforcingAudit = false

        var line = reader.readLine()
        var lineCounterForThrottle = 0

        while (line != null) {
            lineCount++
            approxBytesRead += line.length + 1

            if (++lineCounterForThrottle >= 1000) {
                lineCounterForThrottle = 0
                ensureActive()
                val pct = if (totalBytes > 0) (approxBytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                onProgress(approxBytesRead, totalBytes, pct, "Parsing line $lineCount...")
            }

            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                if (AvcParser.isAvcLine(trimmed)) {
                    val avc = AvcParser.parseLine(trimmed)
                    if (avc != null) {
                        avcList.add(avc)
                        if (avc.isPermissive) hasPermissiveAudit = true else hasEnforcingAudit = true
                    } else {
                        skippedCount++
                    }
                } else if (trimmed.startsWith("genfscon ")) {
                    val entry = GenfsContextsParser.parseLine(trimmed)
                    if (entry != null) genfsList.add(entry) else skippedCount++
                } else if (trimmed.contains("domain=") || trimmed.contains("type=") && trimmed.contains("user=")) {
                    val entry = SeappContextsParser.parseLine(trimmed)
                    if (entry != null) {
                        seappList.add(entry)
                        if (entry.isWarning && entry.warningMessage != null) {
                            warnings.add("Line $lineCount: ${entry.warningMessage}")
                        }
                    } else {
                        skippedCount++
                    }
                } else if (trimmed.startsWith("/") && (trimmed.contains("u:object_r:") || trimmed.contains("<<none>>"))) {
                    val entry = FileContextsParser.parseLine(trimmed)
                    if (entry != null) fileContextList.add(entry) else skippedCount++
                } else if (trimmed.contains("u:object_r:") || trimmed.contains("u:r:")) {
                    // Could be property_contexts, service_contexts, or file_contexts
                    val propEntry = PropertyContextsParser.parseLine(trimmed)
                    val servEntry = ServiceContextsParser.parseLine(trimmed)
                    
                    if (fileName?.contains("property") == true || (trimmed.contains("prop") && propEntry != null)) {
                        propEntry?.let { propertyContextList.add(it) } ?: skippedCount++
                    } else if (fileName?.contains("service") == true || servEntry != null) {
                        servEntry?.let { serviceContextList.add(it) } ?: skippedCount++
                    } else if (propEntry != null) {
                        propertyContextList.add(propEntry)
                    } else {
                        skippedCount++
                    }
                } else {
                    skippedCount++
                }
            }

            line = reader.readLine()
        }

        // Determine detected file type
        val detectedType = when {
            avcList.isNotEmpty() -> SelinuxFileType.AVC_LOG
            fileContextList.isNotEmpty() -> SelinuxFileType.FILE_CONTEXTS
            propertyContextList.isNotEmpty() -> SelinuxFileType.PROPERTY_CONTEXTS
            serviceContextList.isNotEmpty() -> SelinuxFileType.SERVICE_CONTEXTS
            seappList.isNotEmpty() -> SelinuxFileType.SEAPP_CONTEXTS
            genfsList.isNotEmpty() -> SelinuxFileType.GENFS_CONTEXTS
            else -> SelinuxFileType.UNKNOWN
        }

        // Group AVC Denials
        val avcGroups = if (avcList.isNotEmpty()) {
            groupAvcDenials(avcList)
        } else {
            emptyList()
        }

        // Calculate statistics
        val avcStats = if (avcList.isNotEmpty()) {
            calculateStatistics(avcList)
        } else null

        // Detect status
        val statusDetection = SelinuxStatusDetector.detectMode(
            props = props,
            hasPermissiveAudit = hasPermissiveAudit,
            hasEnforcingAudit = hasEnforcingAudit
        )

        // Boot failure diagnosis
        val bootDiagnosis = if (avcList.isNotEmpty()) {
            diagnoseBootIssues(avcList, props)
        } else emptyList()

        SelinuxAnalysisResult(
            detectedType = detectedType,
            detectedStatus = statusDetection,
            totalLinesParsed = lineCount,
            skippedLinesCount = skippedCount,
            warnings = warnings + statusDetection.warnings,
            avcDenials = avcList,
            avcGroups = avcGroups,
            avcStatistics = avcStats,
            fileContexts = fileContextList,
            propertyContexts = propertyContextList,
            serviceContexts = serviceContextList,
            seappContexts = seappList,
            genfsContexts = genfsList,
            bootDiagnosis = bootDiagnosis
        )
    }

    /**
     * Groups AVC denials by (sourceDomain, targetDomain, tclass, permission)
     */
    fun groupAvcDenials(denials: List<AvcDenial>): List<AvcGroup> {
        val groupedMap = mutableMapOf<String, MutableList<AvcDenial>>()

        for (denial in denials) {
            for (perm in denial.permissions) {
                val key = "${denial.sourceDomain}###${denial.targetDomain}###${denial.tclass ?: "unknown"}###$perm"
                groupedMap.getOrPut(key) { mutableListOf() }.add(denial)
            }
        }

        return groupedMap.map { (key, list) ->
            val parts = key.split("###")
            val src = parts[0]
            val tgt = parts[1]
            val cls = parts[2]
            val perm = parts[3]
            AvcGroup(
                sourceDomain = src,
                targetDomain = tgt,
                tclass = cls,
                permission = perm,
                count = list.size,
                sampleDenial = list.first(),
                isPermissive = list.all { it.isPermissive }
            )
        }.sortedByDescending { it.count }
    }

    private fun calculateStatistics(denials: List<AvcDenial>): AvcStatistics {
        val total = denials.size
        val permissive = denials.count { it.isPermissive }
        val enforcing = total - permissive

        val sourceCounts = denials.groupingBy { it.sourceDomain }.eachCount().toList().sortedByDescending { it.second }
        val targetCounts = denials.groupingBy { it.targetDomain }.eachCount().toList().sortedByDescending { it.second }
        val classCounts = denials.mapNotNull { it.tclass }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        
        val permCounts = mutableMapOf<String, Int>()
        for (d in denials) {
            for (p in d.permissions) {
                permCounts[p] = (permCounts[p] ?: 0) + 1
            }
        }
        val permSorted = permCounts.toList().sortedByDescending { it.second }

        val uniqueGroups = groupAvcDenials(denials).size

        return AvcStatistics(
            totalDenials = total,
            uniqueDenials = uniqueGroups,
            permissiveCount = permissive,
            enforcingCount = enforcing,
            mostFrequentSource = sourceCounts.firstOrNull(),
            mostFrequentTarget = targetCounts.firstOrNull(),
            mostFrequentPermission = permSorted.firstOrNull(),
            mostFrequentClass = classCounts.firstOrNull(),
            topSources = sourceCounts.take(5),
            topTargets = targetCounts.take(5),
            topPermissions = permSorted.take(5),
            topClasses = classCounts.take(5)
        )
    }

    private fun diagnoseBootIssues(denials: List<AvcDenial>, props: Map<String, String>): List<String> {
        val notes = mutableListOf<String>()
        val criticalDaemons = setOf("init", "zygote", "system_server", "surfaceflinger", "vold", "servicemanager", "hwservicemanager", "vendor_init", "rild", "netd")
        
        val criticalDenials = denials.filter { it.sourceDomain in criticalDaemons && !it.isPermissive }
        if (criticalDenials.isNotEmpty()) {
            notes.add("Found ${criticalDenials.size} Enforcing AVC denial(s) from core boot processes (${criticalDenials.map { it.sourceDomain }.distinct().joinToString(", ")}). These are high-probability root causes for bootloops or service restarts.")
        }

        val missingServices = denials.filter { it.tclass == "service_manager" && it.permissions.contains("find") && !it.isPermissive }
        if (missingServices.isNotEmpty()) {
            val services = missingServices.mapNotNull { it.serviceName ?: it.targetDomain }.distinct()
            notes.add("Binder service lookup failures in enforcing mode: ${services.joinToString(", ")}. Clients cannot bind to these HALs or services.")
        }

        val propDenials = denials.filter { it.targetDomain.endsWith("_prop") && it.permissions.contains("set") && !it.isPermissive }
        if (propDenials.isNotEmpty()) {
            notes.add("Property set rejections: ${propDenials.map { "${it.sourceDomain} -> ${it.targetDomain}" }.distinct().joinToString(", ")}. Initialization scripts may fail to signal boot completion.")
        }

        if (props.containsKey("ro.hardware")) {
            notes.add("Target Hardware platform: ${props["ro.hardware"]}. Verify that vendor policies include matching board HAL macros.")
        }

        return notes
    }
}
