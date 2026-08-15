package com.example.ui.analyzer.flash

import com.example.ui.analyzer.image.ImageFormat
import com.example.ui.analyzer.partition.PartitionEntry
import com.example.ui.analyzer.partition.PartitionIssue
import com.example.ui.analyzer.partition.PartitionIssueSeverity

object FlashRiskAnalyzer {

    data class PartitionRiskAssessment(
        val action: FlashAction,
        val riskLevel: FlashRiskLevel,
        val isSizeValid: Boolean,
        val sizeDiff: Long,
        val isProtected: Boolean,
        val notes: List<String>,
        val issues: List<PartitionIssue>
    )

    fun assessPartition(
        part: PartitionEntry,
        matched: FlashImageMatcher.MatchedImage?,
        profile: DeviceProfile
    ): PartitionRiskAssessment {
        val notes = mutableListOf<String>()
        val issues = mutableListOf<PartitionIssue>()
        val pName = part.name.lowercase()

        val isProtected = profile.protectedPartitions.any { it.equals(pName, ignoreCase = true) }

        if (matched == null) {
            val action = if (isProtected) FlashAction.PROTECT else FlashAction.SKIP
            val risk = FlashRiskLevel.SAFE
            return PartitionRiskAssessment(
                action = action,
                riskLevel = risk,
                isSizeValid = true,
                sizeDiff = 0L,
                isProtected = isProtected,
                notes = listOf("No image selected (partition unchanged)"),
                issues = emptyList()
            )
        }

        val imageSize = matched.sizeBytes
        val partSize = part.sizeBytes
        val isSizeValid = if (partSize > 0) imageSize <= partSize else true
        val sizeDiff = if (partSize > 0) partSize - imageSize else 0L

        var riskLevel = FlashRiskLevel.LOW
        var action = FlashAction.FLASH

        // 1. Size overflow validation
        if (!isSizeValid) {
            riskLevel = FlashRiskLevel.CRITICAL_BRICK
            action = FlashAction.WARNING_OVERWRITE
            val overflowMb = (imageSize - partSize) / (1024 * 1024)
            notes.add("FATAL: Image size exceeds partition capacity by ${overflowMb}MB")
            issues.add(
                PartitionIssue(
                    id = "FLASH_SIZE_OVERFLOW_${part.name}",
                    severity = PartitionIssueSeverity.CRITICAL,
                    title = "Image Size Exceeds Partition: ${part.name}",
                    description = "Image '${matched.file.name}' (${PartitionEntry.formatBytes(imageSize)}) is larger than target partition (${PartitionEntry.formatBytes(partSize)}).",
                    evidence = "Overflow: ${PartitionEntry.formatBytes(imageSize - partSize)}",
                    recommendation = "Flashing will corrupt adjacent partitions causing permanent brick! Resize filesystem or use sparse image.",
                    affectedPartition = part.name,
                    category = "Safety Pre-Check"
                )
            )
        }

        // 2. Protected partition checks (NVRAM, PRELOADER, SECRO)
        if (isProtected) {
            if (pName == "nvram" || pName == "nvdata" || pName == "protect_f" || pName == "protect_s" || pName == "efs") {
                riskLevel = FlashRiskLevel.CRITICAL_BRICK
                action = FlashAction.PROTECT
                notes.add("CRITICAL: Calibration / IMEI partition. Overwriting will cause permanent IMEI loss!")
                issues.add(
                    PartitionIssue(
                        id = "FLASH_OVERWRITE_PROTECTED_${part.name}",
                        severity = PartitionIssueSeverity.CRITICAL,
                        title = "Attempted Overwrite of Protected NVRAM/IMEI Partition: ${part.name}",
                        description = "Partition '${part.name}' stores unique device calibration, Wi-Fi MAC, and IMEI.",
                        recommendation = "Exclude '${part.name}' from flash operations. Backup first!",
                        affectedPartition = part.name,
                        category = "Security & Hardware Safety"
                    )
                )
            } else if (pName == "preloader" || pName == "lk" || pName == "bootloader") {
                riskLevel = FlashRiskLevel.HIGH
                notes.add("HIGH RISK: Bootloader/Preloader partition. Mismatched binary will cause hard brick.")
                issues.add(
                    PartitionIssue(
                        id = "FLASH_BOOTLOADER_OVERWRITE_${part.name}",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Flashing Bootloader: ${part.name}",
                        description = "Flashing '${part.name}' requires exact MTK DA / chipset signature match.",
                        recommendation = "Ensure preloader is built for ${profile.chipset} (${profile.modelName}).",
                        affectedPartition = part.name,
                        category = "Bootloader Safety"
                    )
                )
            }
        }

        // 3. Format specific checks
        if (matched.format == ImageFormat.SPARSE) {
            notes.add("Sparse Android Image: Safe for fastboot/SP Flash Tool.")
        } else if (matched.format == ImageFormat.SUPER) {
            if (!profile.hasDynamicPartitions) {
                riskLevel = maxRisk(riskLevel, FlashRiskLevel.CRITICAL_BRICK)
                notes.add("Dynamic 'super' image on non-dynamic device (${profile.modelName}).")
                issues.add(
                    PartitionIssue(
                        id = "FLASH_DYNAMIC_SUPER_MISMATCH",
                        severity = PartitionIssueSeverity.CRITICAL,
                        title = "Dynamic Super Image on Legacy Partition Layout",
                        description = "Image is Android 10+ dynamic super partition, but target '${profile.modelName}' uses static partitions.",
                        recommendation = "Extract individual raw system/vendor partitions before flashing.",
                        affectedPartition = part.name,
                        category = "Compatibility"
                    )
                )
            }
        }

        // 4. Utilization note
        if (partSize > 0) {
            val pct = ((imageSize.toDouble() / partSize.toDouble()) * 100).toInt()
            notes.add("Space utilization: $pct% (${PartitionEntry.formatBytes(imageSize)} / ${PartitionEntry.formatBytes(partSize)})")
        }

        return PartitionRiskAssessment(
            action = action,
            riskLevel = riskLevel,
            isSizeValid = isSizeValid,
            sizeDiff = sizeDiff,
            isProtected = isProtected,
            notes = notes,
            issues = issues
        )
    }

    fun maxRisk(a: FlashRiskLevel, b: FlashRiskLevel): FlashRiskLevel {
        return if (a.ordinal >= b.ordinal) a else b
    }
}
