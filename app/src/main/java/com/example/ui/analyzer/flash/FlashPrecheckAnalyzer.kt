package com.example.ui.analyzer.flash

import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.partition.PartitionEntry
import com.example.ui.analyzer.partition.PartitionIssue
import com.example.ui.analyzer.partition.PartitionIssueSeverity
import com.example.ui.analyzer.partition.PartitionTable
import java.io.File

class FlashPrecheckAnalyzer {

    fun performPrecheck(
        partitionTable: PartitionTable?,
        imageFiles: List<File>,
        targetProfile: DeviceProfile = DeviceProfile.GALAXY_J2_PRIME
    ): FlashPrecheckResult {
        val startTime = System.currentTimeMillis()

        // Construct partitions either from table or profile
        val partitions = if (partitionTable != null && partitionTable.partitions.isNotEmpty()) {
            partitionTable.partitions
        } else {
            createPartitionsFromProfile(targetProfile)
        }

        // Match files to partitions
        val matches = FlashImageMatcher.matchFilesToPartitions(imageFiles, partitions, targetProfile)

        val planItems = mutableListOf<FlashPartitionItem>()
        val allIssues = mutableListOf<PartitionIssue>()
        var highestRisk = FlashRiskLevel.SAFE
        var totalFlashSizeBytes = 0L
        var imagesToFlashCount = 0

        for (part in partitions) {
            val matched = matches[part.name.lowercase()]
            val assessment = FlashRiskAnalyzer.assessPartition(part, matched, targetProfile)

            allIssues.addAll(assessment.issues)
            highestRisk = FlashRiskAnalyzer.maxRisk(highestRisk, assessment.riskLevel)

            if (matched != null) {
                imagesToFlashCount++
                totalFlashSizeBytes += matched.sizeBytes
            }

            planItems.add(
                FlashPartitionItem(
                    partition = part,
                    matchedImageFile = matched?.file?.absolutePath,
                    matchedImageSizeBytes = matched?.sizeBytes ?: 0L,
                    detectedFormat = matched?.format ?: com.example.ui.analyzer.image.ImageFormat.UNKNOWN,
                    action = assessment.action,
                    riskLevel = assessment.riskLevel,
                    matchConfidence = matched?.confidence ?: 0,
                    isSizeValid = assessment.isSizeValid,
                    sizeDifferenceBytes = assessment.sizeDiff,
                    validationNotes = assessment.notes,
                    isProtected = assessment.isProtected
                )
            )
        }

        val plan = FlashPlan(
            title = "Flash Plan for ${targetProfile.modelName}",
            targetProfile = targetProfile,
            items = planItems,
            totalImagesToFlash = imagesToFlashCount,
            totalFlashSizeBytes = totalFlashSizeBytes,
            highestRisk = highestRisk
        )

        // Run cross-image compatibility checks
        val compatIssues = FlashCompatibilityChecker.checkCompatibility(plan, targetProfile)
        allIssues.addAll(compatIssues)

        // Determine verdict
        val verdict = when {
            allIssues.any { it.id.startsWith("FLASH_SIZE_OVERFLOW") } -> FlashVerdict.FATAL_SIZE_MISMATCH
            allIssues.any { it.severity == PartitionIssueSeverity.CRITICAL } -> FlashVerdict.UNSAFE_DO_NOT_FLASH
            allIssues.any { it.severity == PartitionIssueSeverity.WARNING } -> FlashVerdict.WARNING_CAUTION
            else -> FlashVerdict.SAFE_TO_FLASH
        }

        val checklist = generatePreFlashChecklist(targetProfile, plan, allIssues)
        val summary = buildSummary(targetProfile, verdict, plan, allIssues)
        val report = buildReport(targetProfile, verdict, plan, allIssues, checklist)
        val elapsed = System.currentTimeMillis() - startTime

        return FlashPrecheckResult(
            status = AnalyzerStatus.SUCCESS,
            verdict = verdict,
            plan = plan,
            issues = allIssues,
            summary = summary,
            detailedReport = report,
            preFlashChecklist = checklist,
            processingTimeMs = elapsed
        )
    }

    private fun createPartitionsFromProfile(profile: DeviceProfile): List<PartitionEntry> {
        val list = mutableListOf<PartitionEntry>()
        var offset = 0L
        var idx = 1

        val partMap = if (profile.referencePartitions.isNotEmpty()) {
            profile.referencePartitions
        } else {
            mapOf("boot" to 32L * 1024 * 1024, "recovery" to 32L * 1024 * 1024, "system" to 2048L * 1024 * 1024, "userdata" to 4096L * 1024 * 1024)
        }

        for ((name, size) in partMap) {
            val isProtected = profile.protectedPartitions.any { it.equals(name, ignoreCase = true) }
            list.add(
                PartitionEntry(
                    index = idx++,
                    name = name,
                    startLba = offset / 512,
                    endLba = if (size > 0) (offset + size - 1) / 512 else offset / 512,
                    startByteOffset = offset,
                    sizeBytes = size,
                    typeGuidOrId = "REFERENCE_PROFILE",
                    typeDescription = if (isProtected) "Protected Partition" else "Standard Android Partition",
                    isReadOnly = isProtected,
                    originalFileName = "$name.img"
                )
            )
            offset += size
        }
        return list
    }

    private fun generatePreFlashChecklist(
        profile: DeviceProfile,
        plan: FlashPlan,
        issues: List<PartitionIssue>
    ): List<String> {
        val list = mutableListOf<String>()
        list.add("Battery charged to at least 50% (prevents mid-flash power-off brick)")
        list.add("Full Nandroid / TWRP backup created of NVRAM, EFS, and System")
        list.add("Device Model verified: Target is '${profile.modelName}' (${profile.chipset})")
        list.add("Verified USB cable integrity and direct PC port connection")

        if (profile.id == DeviceProfile.GALAXY_J2_PRIME.id) {
            list.add("MTK VCOM / CDC USB Drivers installed for MT6737T")
            list.add("Samsung OEM Unlock enabled in Developer Options")
            list.add("FRP (Factory Reset Protection) lock removed / Google Account logged out")
        }

        if (issues.any { it.severity == PartitionIssueSeverity.CRITICAL }) {
            list.add("DO NOT FLASH: Critical partition size or protection issues must be resolved first!")
        }

        return list
    }

    private fun buildSummary(
        profile: DeviceProfile,
        verdict: FlashVerdict,
        plan: FlashPlan,
        issues: List<PartitionIssue>
    ): String {
        val sb = StringBuilder()
        sb.append("Pre-Check Verdict: ${verdict.label}\n")
        sb.append("Target Device: ${profile.modelName} (${profile.chipset})\n")
        sb.append("Architecture: ${profile.arch.uppercase()} | RAM: ${PartitionEntry.formatBytes(profile.totalRamBytes)}\n")
        sb.append("Images Matched: ${plan.totalImagesToFlash} (${PartitionEntry.formatBytes(plan.totalFlashSizeBytes)})\n")
        sb.append("Overall Risk: ${plan.highestRisk.label}\n")
        sb.append("Safety Advisories: ${issues.size} (Critical: ${issues.count { it.severity == PartitionIssueSeverity.CRITICAL }}, Warnings: ${issues.count { it.severity == PartitionIssueSeverity.WARNING }})")
        return sb.toString().trim()
    }

    private fun buildReport(
        profile: DeviceProfile,
        verdict: FlashVerdict,
        plan: FlashPlan,
        issues: List<PartitionIssue>,
        checklist: List<String>
    ): String {
        val sb = StringBuilder()
        sb.append("=== FLASH PRE-CHECK SAFETY REPORT ===\n\n")
        sb.append("Verdict: ${verdict.label}\n")
        sb.append("Target Device: ${profile.marketingName} (${profile.modelName})\n")
        sb.append("Chipset: ${profile.chipset} | Architecture: ${profile.arch.uppercase()}\n")
        sb.append("Max Safe Capacity: ${PartitionEntry.formatBytes(profile.totalStorageBytes)}\n\n")

        sb.append("--- PARTITION FLASH PLAN (${plan.items.size} partitions) ---\n")
        sb.append(String.format("%-14s %-12s %-12s %-10s %-14s %s\n", "PARTITION", "MAX SIZE", "IMG SIZE", "UTIL%", "ACTION", "RISK"))
        sb.append("-".repeat(80)).append("\n")

        for (item in plan.items) {
            val partName = item.partition.name
            val maxSize = item.maxPartitionSizeFormatted
            val imgSize = if (item.matchedImageFile != null) item.sizeFormatted else "-"
            val util = if (item.matchedImageFile != null) "${item.utilizationPercent}%" else "-"
            val action = item.action.displayName
            val risk = item.riskLevel.label

            sb.append(String.format("%-14s %-12s %-12s %-10s %-14s %s\n", partName, maxSize, imgSize, util, action, risk))
            for (note in item.validationNotes) {
                sb.append("  * $note\n")
            }
        }

        if (checklist.isNotEmpty()) {
            sb.append("\n--- PRE-FLASH SAFETY CHECKLIST ---\n")
            for ((idx, chk) in checklist.withIndex()) {
                sb.append("[${idx + 1}] $chk\n")
            }
        }

        if (issues.isNotEmpty()) {
            sb.append("\n--- ISSUES & SAFEGUARDS (${issues.size}) ---\n")
            for (issue in issues) {
                sb.append("[${issue.severity}] ${issue.title} (${issue.affectedPartition})\n")
                sb.append("  ${issue.description}\n")
                if (issue.recommendation.isNotEmpty()) {
                    sb.append("  -> Action: ${issue.recommendation}\n")
                }
            }
        }

        return sb.toString()
    }
}
