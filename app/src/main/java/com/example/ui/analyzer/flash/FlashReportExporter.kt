package com.example.ui.analyzer.flash

import com.example.ui.analyzer.partition.PartitionAnalysisResult
import java.io.File

object FlashReportExporter {

    fun exportPartitionAnalysisMarkdown(result: PartitionAnalysisResult, destination: File) {
        val md = buildString {
            appendLine("# Partition Table Analysis Report")
            appendLine()
            appendLine("- **Table Type:** ${result.table.type.displayName}")
            appendLine("- **Health Status:** ${result.health}")
            appendLine("- **Sector Size:** ${result.table.sectorSize} bytes")
            appendLine("- **Partitions Count:** ${result.table.partitions.size}")
            appendLine("- **Allocated Space:** ${result.table.formattedAllocatedBytes}")
            appendLine()
            appendLine("## Partition Table")
            appendLine("| # | Name | Start Address | End Address | Size | Region | Type / Flags |")
            appendLine("|---|---|---|---|---|---|---|")
            for (p in result.table.partitions) {
                appendLine("| ${p.index} | `${p.name}` | `${p.startAddressHex}` | `${p.endAddressHex}` | ${p.sizeFormatted} | ${p.region} | ${p.typeDescription} |")
            }
            appendLine()
            if (result.issues.isNotEmpty()) {
                appendLine("## Detected Issues & Warnings")
                for (issue in result.issues) {
                    appendLine("### [${issue.severity}] ${issue.title}")
                    appendLine("- **Partition:** `${issue.affectedPartition}`")
                    appendLine("- **Description:** ${issue.description}")
                    if (issue.recommendation.isNotEmpty()) {
                        appendLine("- **Recommendation:** ${issue.recommendation}")
                    }
                    appendLine()
                }
            }
        }
        destination.writeText(md, Charsets.UTF_8)
    }

    fun exportFlashPrecheckMarkdown(result: FlashPrecheckResult, destination: File) {
        val md = buildString {
            appendLine("# Android Safe Flash Pre-Check Report")
            appendLine()
            appendLine("- **Verdict:** ${result.verdict.label}")
            appendLine("- **Target Device:** ${result.plan.targetProfile.marketingName} (${result.plan.targetProfile.modelName})")
            appendLine("- **Chipset:** ${result.plan.targetProfile.chipset} (${result.plan.targetProfile.arch.uppercase()})")
            appendLine("- **Matched Images:** ${result.plan.totalImagesToFlash}")
            appendLine("- **Highest Risk Level:** ${result.plan.highestRisk.label}")
            appendLine()
            appendLine("## Flashing Plan")
            appendLine("| Partition | Max Size | Image Size | Utilization | Action | Risk Level |")
            appendLine("|---|---|---|---|---|---|")
            for (item in result.plan.items) {
                val imgSize = if (item.matchedImageFile != null) item.sizeFormatted else "None"
                val util = if (item.matchedImageFile != null) "${item.utilizationPercent}%" else "0%"
                appendLine("| `${item.partition.name}` | ${item.maxPartitionSizeFormatted} | $imgSize | $util | ${item.action.displayName} | **${item.riskLevel.label}** |")
            }
            appendLine()
            appendLine("## Pre-Flash Checklist")
            for (chk in result.preFlashChecklist) {
                appendLine("- [ ] $chk")
            }
            appendLine()
            if (result.issues.isNotEmpty()) {
                appendLine("## Safety Issues & Advisories")
                for (issue in result.issues) {
                    appendLine("### [${issue.severity}] ${issue.title}")
                    appendLine("- **Target:** `${issue.affectedPartition}`")
                    appendLine("- **Description:** ${issue.description}")
                    if (issue.recommendation.isNotEmpty()) {
                        appendLine("- **Remedy:** ${issue.recommendation}")
                    }
                    appendLine()
                }
            }
        }
        destination.writeText(md, Charsets.UTF_8)
    }

    fun exportPartitionCsv(result: PartitionAnalysisResult, destination: File) {
        val csv = buildString {
            appendLine("Index,Name,StartHex,EndHex,SizeBytes,SizeFormatted,Region,TypeDescription")
            for (p in result.table.partitions) {
                appendLine("${p.index},\"${p.name}\",\"${p.startAddressHex}\",\"${p.endAddressHex}\",${p.sizeBytes},\"${p.sizeFormatted}\",\"${p.region}\",\"${p.typeDescription}\"")
            }
        }
        destination.writeText(csv, Charsets.UTF_8)
    }

    fun exportFlashPlanCsv(result: FlashPrecheckResult, destination: File) {
        val csv = buildString {
            appendLine("Partition,MaxSizeBytes,MatchedFile,ImageSizeBytes,UtilizationPercent,Action,RiskLevel")
            for (item in result.plan.items) {
                val file = item.matchedImageFile ?: ""
                appendLine("\"${item.partition.name}\",${item.partition.sizeBytes},\"$file\",${item.matchedImageSizeBytes},${item.utilizationPercent},\"${item.action.name}\",\"${item.riskLevel.name}\"")
            }
        }
        destination.writeText(csv, Charsets.UTF_8)
    }
}
