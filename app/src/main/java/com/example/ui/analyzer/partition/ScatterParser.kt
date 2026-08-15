package com.example.ui.analyzer.partition

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object ScatterParser {

    data class ScatterParseResult(
        val table: PartitionTable,
        val issues: List<PartitionIssue>
    )

    fun parse(inputStream: InputStream, sourceName: String = "scatter.txt"): ScatterParseResult {
        val lines = mutableListOf<String>()
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { seq ->
            seq.forEach { lines.add(it) }
        }
        return parseLines(lines, sourceName)
    }

    fun parseText(content: String, sourceName: String = "scatter.txt"): ScatterParseResult {
        val lines = content.lines()
        return parseLines(lines, sourceName)
    }

    fun parseLines(lines: List<String>, sourceName: String): ScatterParseResult {
        val issues = mutableListOf<PartitionIssue>()

        // Detect if it's /proc/partitions
        if (lines.any { it.contains("major") && it.contains("minor") && it.contains("#blocks") }) {
            return parseProcPartitions(lines, sourceName)
        }

        // MTK Scatter Parsing
        var platform = ""
        var project = ""
        var configVersion = ""
        var storage = "EMMC"
        var blockSize = 512

        val partitions = mutableListOf<PartitionEntry>()
        var currentMap: MutableMap<String, String>? = null
        var inGeneralSetting = false
        val generalFields = mutableMapOf<String, String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.contains("MTK_PLATFORM_CFG", ignoreCase = true) || line.contains("general:", ignoreCase = true)) {
                inGeneralSetting = true
                continue
            }

            if (line.startsWith("- partition_index:") || line.startsWith("partition_index:")) {
                inGeneralSetting = false
                currentMap?.let { map ->
                    buildPartitionEntry(map, partitions.size + 1)?.let { partitions.add(it) }
                }
                currentMap = mutableMapOf()
                val value = extractValue(line)
                currentMap["partition_index"] = value
                continue
            }

            if (line.startsWith("- ") || line.contains(":")) {
                val cleanLine = if (line.startsWith("- ")) line.substring(2).trim() else line
                val colonIdx = cleanLine.indexOf(':')
                if (colonIdx > 0) {
                    val key = cleanLine.substring(0, colonIdx).trim()
                    val value = cleanLine.substring(colonIdx + 1).trim()
                    if (inGeneralSetting) {
                        generalFields[key] = value
                        when (key.lowercase()) {
                            "platform" -> platform = value
                            "project" -> project = value
                            "config_version" -> configVersion = value
                            "storage" -> storage = value
                            "block_size" -> blockSize = parseHexOrDec(value).toInt().coerceAtLeast(512)
                        }
                    } else {
                        currentMap?.put(key, value)
                    }
                }
            }
        }

        // Commit last partition
        currentMap?.let { map ->
            buildPartitionEntry(map, partitions.size + 1)?.let { partitions.add(it) }
        }

        // If no YAML/v1.1 partitions found, try legacy MTK scatter format
        if (partitions.isEmpty()) {
            parseLegacyScatter(lines, partitions, generalFields)
        }

        // Validate partitions
        validateScatterPartitions(partitions, issues)

        val totalAllocated = partitions.sumOf { it.sizeBytes }

        val table = PartitionTable(
            type = PartitionTableType.MTK_SCATTER,
            sourceName = sourceName,
            sectorSize = 512,
            diskSize = totalAllocated,
            numberOfEntries = partitions.size,
            platformName = platform,
            projectVersion = project.ifEmpty { configVersion },
            storageType = storage,
            partitions = partitions,
            rawHeaderFields = generalFields
        )

        return ScatterParseResult(table, issues)
    }

    private fun extractValue(line: String): String {
        val colonIdx = line.indexOf(':')
        return if (colonIdx >= 0) line.substring(colonIdx + 1).trim() else ""
    }

    private fun buildPartitionEntry(map: Map<String, String>, index: Int): PartitionEntry? {
        val name = map["partition_name"] ?: map["name"] ?: return null
        val fileName = map["file_name"] ?: map["filename"] ?: "NONE"
        val isDownloadStr = map["is_download"] ?: "true"
        val isDownload = isDownloadStr.equals("true", ignoreCase = true)
        val type = map["type"] ?: "SV5_BL_BIN"
        val linearStartAddr = parseHexOrDec(map["linear_start_addr"] ?: "0x0")
        val physicalStartAddr = parseHexOrDec(map["physical_start_addr"] ?: map["linear_start_addr"] ?: "0x0")
        val size = parseHexOrDec(map["partition_size"] ?: "0x0")
        val region = map["region"] ?: "EMMC_USER"
        val storage = map["storage"] ?: "HW_STORAGE_EMMC"
        val operationType = map["operation_type"] ?: "UPDATE"

        val startByte = physicalStartAddr
        val endByte = if (size > 0) startByte + size - 1 else startByte
        val startLba = startByte / 512
        val endLba = endByte / 512

        val flags = mutableListOf<String>()
        if (map["boundary_check"]?.equals("true", ignoreCase = true) == true) flags.add("BOUNDARY_CHECK")
        if (map["is_reserved"]?.equals("true", ignoreCase = true) == true) flags.add("RESERVED")

        return PartitionEntry(
            index = index,
            name = name,
            startLba = startLba,
            endLba = endLba,
            startByteOffset = startByte,
            sizeBytes = size,
            typeGuidOrId = type,
            typeDescription = "MTK Scatter Partition ($type)",
            isReadOnly = (operationType.equals("INVISIBLE", ignoreCase = true) || flags.contains("RESERVED")),
            region = region,
            storage = storage,
            isDownload = isDownload,
            operationType = operationType,
            originalFileName = if (fileName.equals("NONE", ignoreCase = true)) "" else fileName,
            flags = flags
        )
    }

    private fun parseLegacyScatter(
        lines: List<String>,
        partitions: MutableList<PartitionEntry>,
        generalFields: MutableMap<String, String>
    ) {
        var idx = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 3) {
                val name = tokens[0]
                val start = parseHexOrDec(tokens[1])
                val size = parseHexOrDec(tokens[2])

                if (size > 0 || start > 0) {
                    val end = start + size - 1
                    partitions.add(
                        PartitionEntry(
                            index = idx++,
                            name = name,
                            startLba = start / 512,
                            endLba = end / 512,
                            startByteOffset = start,
                            sizeBytes = size,
                            typeGuidOrId = "LEGACY_SCATTER",
                            typeDescription = "Legacy Scatter Partition",
                            originalFileName = "$name.img"
                        )
                    )
                }
            }
        }
        if (partitions.isNotEmpty()) {
            generalFields["Format"] = "Legacy MTK Scatter"
        }
    }

    private fun parseProcPartitions(lines: List<String>, sourceName: String): ScatterParseResult {
        val issues = mutableListOf<PartitionIssue>()
        val partitions = mutableListOf<PartitionEntry>()
        var idx = 1
        var headerPassed = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.contains("major") && trimmed.contains("minor")) {
                headerPassed = true
                continue
            }
            if (!headerPassed) continue

            val tokens = trimmed.split(Regex("\\s+"))
            if (tokens.size >= 4) {
                val blocks = tokens[2].toLongOrNull() ?: 0L
                val name = tokens[3]
                val sizeBytes = blocks * 1024L

                partitions.add(
                    PartitionEntry(
                        index = idx++,
                        name = name,
                        startLba = 0L,
                        endLba = if (blocks > 0) (blocks * 2) - 1 else 0L,
                        startByteOffset = 0L,
                        sizeBytes = sizeBytes,
                        typeGuidOrId = "PROC_PARTITIONS",
                        typeDescription = "Linux Kernel Block Device",
                        originalFileName = "$name.img"
                    )
                )
            }
        }

        val table = PartitionTable(
            type = PartitionTableType.PROC_PARTITIONS,
            sourceName = sourceName,
            sectorSize = 512,
            diskSize = partitions.sumOf { it.sizeBytes },
            numberOfEntries = partitions.size,
            partitions = partitions,
            rawHeaderFields = mapOf("Source" to "/proc/partitions")
        )

        return ScatterParseResult(table, issues)
    }

    private fun validateScatterPartitions(partitions: List<PartitionEntry>, issues: MutableList<PartitionIssue>) {
        if (partitions.isEmpty()) {
            issues.add(
                PartitionIssue(
                    id = "SCATTER_EMPTY",
                    severity = PartitionIssueSeverity.CRITICAL,
                    title = "Empty Partition List",
                    description = "No valid partitions could be extracted from scatter file.",
                    recommendation = "Verify scatter file format and structure."
                )
            )
            return
        }

        // Check essential partitions for MTK / Samsung Galaxy J2 Prime
        val names = partitions.map { it.name.lowercase() }.toSet()
        val criticalPartitions = listOf("boot", "system", "recovery")
        for (crit in criticalPartitions) {
            if (!names.contains(crit)) {
                issues.add(
                    PartitionIssue(
                        id = "SCATTER_MISSING_${crit.uppercase()}",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Essential Partition Missing: '$crit'",
                        description = "Partition '$crit' was not declared in scatter table.",
                        affectedPartition = crit,
                        category = "Completeness"
                    )
                )
            }
        }

        // Check region-based overlaps
        val byRegion = partitions.groupBy { it.region }
        for ((region, regionParts) in byRegion) {
            val sorted = regionParts.filter { it.sizeBytes > 0 }.sortedBy { it.startByteOffset }
            for (i in 0 until sorted.size - 1) {
                val cur = sorted[i]
                val next = sorted[i + 1]
                val curEnd = cur.startByteOffset + cur.sizeBytes
                if (curEnd > next.startByteOffset) {
                    issues.add(
                        PartitionIssue(
                            id = "SCATTER_OVERLAP_${cur.name}_${next.name}",
                            severity = PartitionIssueSeverity.CRITICAL,
                            title = "Partition Address Overlap ($region): ${cur.name} & ${next.name}",
                            description = "${cur.name} ends at 0x${java.lang.Long.toHexString(curEnd).uppercase()} but ${next.name} starts at 0x${java.lang.Long.toHexString(next.startByteOffset).uppercase()}.",
                            affectedPartition = "${cur.name}, ${next.name}",
                            category = "Geometry"
                        )
                    )
                }
            }
        }
    }

    fun parseHexOrDec(str: String): Long {
        val trimmed = str.trim()
        return try {
            if (trimmed.startsWith("0x", ignoreCase = true)) {
                java.lang.Long.parseUnsignedLong(trimmed.substring(2), 16)
            } else {
                trimmed.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }
}
