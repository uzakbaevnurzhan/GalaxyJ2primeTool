package com.example.ui.analyzer.partition

import com.example.ui.analyzer.core.AnalyzerStatus
import java.io.File

class PartitionTableAnalyzer {

    data class AnalysisResultInternal(
        val table: PartitionTable,
        val issues: List<PartitionIssue>
    )

    fun analyzeFile(file: File): PartitionAnalysisResult {
        val startTime = System.currentTimeMillis()
        val parsed = PartitionTableParser.parseFile(file)
        return finalizeAnalysis(parsed.table, parsed.issues, startTime)
    }

    fun analyzeText(content: String, sourceName: String = "scatter.txt"): PartitionAnalysisResult {
        val startTime = System.currentTimeMillis()
        val parsed = ScatterParser.parseText(content, sourceName)
        return finalizeAnalysis(parsed.table, parsed.issues, startTime)
    }

    fun analyzeTable(table: PartitionTable, initialIssues: List<PartitionIssue> = emptyList()): PartitionAnalysisResult {
        val startTime = System.currentTimeMillis()
        return finalizeAnalysis(table, initialIssues, startTime)
    }

    private fun finalizeAnalysis(
        table: PartitionTable,
        initialIssues: List<PartitionIssue>,
        startTime: Long
    ): PartitionAnalysisResult {
        val issues = initialIssues.toMutableList()
        val gaps = calculateAddressGaps(table.partitions)

        // Run deeper validations
        checkAlignment(table, issues)
        checkAndroidStandardPartitions(table, issues)
        checkZeroSizePartitions(table, issues)

        // Health calculation
        val health = when {
            issues.any { it.severity == PartitionIssueSeverity.CRITICAL } -> PartitionTableHealth.CORRUPTED
            issues.any { it.severity == PartitionIssueSeverity.WARNING } -> PartitionTableHealth.WARNING
            else -> PartitionTableHealth.VALID
        }

        val status = if (table.partitions.isNotEmpty() || table.type != PartitionTableType.UNKNOWN) {
            AnalyzerStatus.SUCCESS
        } else {
            AnalyzerStatus.ERROR
        }

        val summary = buildSummary(table, health, issues, gaps)
        val details = buildDetails(table, issues, gaps)
        val elapsed = System.currentTimeMillis() - startTime

        return PartitionAnalysisResult(
            status = status,
            health = health,
            summary = summary,
            details = details,
            table = table,
            issues = issues,
            addressGaps = gaps,
            processingTimeMs = elapsed
        )
    }

    private fun calculateAddressGaps(partitions: List<PartitionEntry>): List<Pair<Long, Long>> {
        val gaps = mutableListOf<Pair<Long, Long>>()
        val sorted = partitions.filter { it.sizeBytes > 0 }.sortedBy { it.startByteOffset }
        for (i in 0 until sorted.size - 1) {
            val cur = sorted[i]
            val next = sorted[i + 1]
            val curEnd = cur.startByteOffset + cur.sizeBytes
            if (next.startByteOffset > curEnd) {
                val gapSize = next.startByteOffset - curEnd
                if (gapSize >= 512) {
                    gaps.add(Pair(curEnd, gapSize))
                }
            }
        }
        return gaps
    }

    private fun checkAlignment(table: PartitionTable, issues: MutableList<PartitionIssue>) {
        val sectorSize = table.sectorSize.coerceAtLeast(512)
        for (part in table.partitions) {
            if (part.sizeBytes == 0L) continue
            // 4KB alignment check (standard Android flash alignment)
            val is4kAligned = (part.startByteOffset % 4096L) == 0L
            if (!is4kAligned) {
                issues.add(
                    PartitionIssue(
                        id = "PARTITION_NOT_4K_ALIGNED_${part.name}",
                        severity = PartitionIssueSeverity.INFO,
                        title = "Partition '${part.name}' is Not 4KB Aligned",
                        description = "Start offset 0x${java.lang.Long.toHexString(part.startByteOffset).uppercase()} is not a multiple of 4096 bytes (offset % 4096 = ${part.startByteOffset % 4096L}).",
                        affectedPartition = part.name,
                        category = "Alignment & Performance",
                        recommendation = "Aligning partitions to 4KB or 1MB boundaries maximizes eMMC flash write throughput and lifespan."
                    )
                )
            }
        }
    }

    private fun checkAndroidStandardPartitions(table: PartitionTable, issues: MutableList<PartitionIssue>) {
        val nameMap = table.partitions.associateBy { it.name.lowercase() }

        // Check system partition size
        val systemPart = nameMap["system"]
        if (systemPart != null) {
            val systemMb = systemPart.sizeBytes / (1024 * 1024)
            if (systemMb < 500 && systemMb > 0) {
                issues.add(
                    PartitionIssue(
                        id = "SYSTEM_PARTITION_SMALL",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "System Partition is Relatively Small (${systemMb}MB)",
                        description = "Standard modern Android 10/11 GSI or custom ROMs typically require 1.2GB - 2.5GB for system.",
                        affectedPartition = "system",
                        category = "Capacity",
                        recommendation = "For Android 11 porting, ensure system partition is resized or stripped of bloated GApps."
                    )
                )
            }
        }

        // Check boot partition size
        val bootPart = nameMap["boot"]
        if (bootPart != null) {
            val bootMb = bootPart.sizeBytes / (1024 * 1024)
            if (bootMb in 1..7) {
                issues.add(
                    PartitionIssue(
                        id = "BOOT_PARTITION_TIGHT",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Boot Partition Size is Small (${bootMb}MB)",
                        description = "Android 11 kernels with uncompressed initramfs can easily exceed 8MB.",
                        affectedPartition = "boot",
                        category = "Capacity"
                    )
                )
            }
        }
    }

    private fun checkZeroSizePartitions(table: PartitionTable, issues: MutableList<PartitionIssue>) {
        for (p in table.partitions) {
            if (p.sizeBytes == 0L && p.isDownload) {
                issues.add(
                    PartitionIssue(
                        id = "ZERO_SIZE_PARTITION_${p.name}",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Partition '${p.name}' Has Size 0",
                        description = "Partition is marked as downloadable but has allocated size 0.",
                        affectedPartition = p.name,
                        category = "Configuration"
                    )
                )
            }
        }
    }

    private fun buildSummary(
        table: PartitionTable,
        health: PartitionTableHealth,
        issues: List<PartitionIssue>,
        gaps: List<Pair<Long, Long>>
    ): String {
        val sb = StringBuilder()
        sb.append("Table Type: ${table.type.displayName}\n")
        sb.append("Health: $health\n")
        sb.append("Partitions: ${table.partitions.size}\n")
        sb.append("Allocated: ${table.formattedAllocatedBytes}\n")
        if (table.platformName.isNotEmpty()) {
            sb.append("Platform: ${table.platformName} (${table.projectVersion})\n")
        }
        sb.append("Issues: ${issues.size} (Critical: ${issues.count { it.severity == PartitionIssueSeverity.CRITICAL }}, Warnings: ${issues.count { it.severity == PartitionIssueSeverity.WARNING }})\n")
        if (gaps.isNotEmpty()) {
            sb.append("Unallocated Gaps: ${gaps.size} region(s)\n")
        }
        return sb.toString().trim()
    }

    private fun buildDetails(
        table: PartitionTable,
        issues: List<PartitionIssue>,
        gaps: List<Pair<Long, Long>>
    ): String {
        val sb = StringBuilder()
        sb.append("=== PARTITION TABLE REPORT ===\n\n")
        sb.append("Source: ${table.sourceName}\n")
        sb.append("Format: ${table.type.displayName}\n")
        sb.append("Sector Size: ${table.sectorSize} bytes\n")
        sb.append("Disk GUID: ${table.diskGuid.ifEmpty { "N/A" }}\n")
        if (table.platformName.isNotEmpty()) {
            sb.append("Target Platform: ${table.platformName}\n")
            sb.append("Project / Config: ${table.projectVersion}\n")
        }
        sb.append("\n--- PARTITION LIST (${table.partitions.size}) ---\n")
        sb.append(String.format("%-4s %-16s %-14s %-14s %-10s %-12s %s\n", "#", "NAME", "START ADDR", "END ADDR", "SIZE", "REGION", "TYPE / FLAGS"))
        sb.append("-".repeat(80)).append("\n")

        for (p in table.partitions) {
            sb.append(
                String.format(
                    "%-4d %-16s %-14s %-14s %-10s %-12s %s\n",
                    p.index,
                    p.name,
                    p.startAddressHex,
                    p.endAddressHex,
                    p.sizeFormatted,
                    p.region,
                    p.typeDescription
                )
            )
        }

        if (gaps.isNotEmpty()) {
            sb.append("\n--- UNALLOCATED GAPS (${gaps.size}) ---\n")
            for ((start, size) in gaps) {
                sb.append("Gap from 0x${java.lang.Long.toHexString(start).uppercase()} (size: ${PartitionEntry.formatBytes(size)})\n")
            }
        }

        if (issues.isNotEmpty()) {
            sb.append("\n--- ISSUES & ADVISORIES (${issues.size}) ---\n")
            for (issue in issues) {
                sb.append("[${issue.severity}] ${issue.title} (${issue.affectedPartition})\n")
                sb.append("  ${issue.description}\n")
                if (issue.recommendation.isNotEmpty()) {
                    sb.append("  -> Recommendation: ${issue.recommendation}\n")
                }
            }
        }

        return sb.toString()
    }
}
