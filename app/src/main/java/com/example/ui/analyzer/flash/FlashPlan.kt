package com.example.ui.analyzer.flash

import com.example.ui.analyzer.image.ImageFormat
import com.example.ui.analyzer.partition.PartitionEntry

enum class FlashAction(val displayName: String) {
    FLASH("Flash Image"),
    PROTECT("Protected (Do Not Flash)"),
    ERASE("Format / Erase"),
    SKIP("Skip (Unchanged)"),
    WARNING_OVERWRITE("Dangerous Overwrite")
}

enum class FlashRiskLevel(val label: String, val colorCode: String) {
    SAFE("Safe", "0xFF4CAF50"),
    LOW("Low Risk", "0xFF8BC34A"),
    MODERATE("Moderate Risk", "0xFFFF9800"),
    HIGH("High Risk", "0xFFFF5722"),
    CRITICAL_BRICK("Fatal / High Brick Risk", "0xFFF44336")
}

data class FlashPartitionItem(
    val partition: PartitionEntry,
    val matchedImageFile: String? = null,
    val matchedImageSizeBytes: Long = 0L,
    val detectedFormat: ImageFormat = ImageFormat.UNKNOWN,
    val action: FlashAction = FlashAction.SKIP,
    val riskLevel: FlashRiskLevel = FlashRiskLevel.SAFE,
    val matchConfidence: Int = 0, // 0 - 100%
    val isSizeValid: Boolean = true,
    val sizeDifferenceBytes: Long = 0L,
    val validationNotes: List<String> = emptyList(),
    val isProtected: Boolean = false
) {
    val sizeFormatted: String
        get() = PartitionEntry.formatBytes(matchedImageSizeBytes)

    val maxPartitionSizeFormatted: String
        get() = PartitionEntry.formatBytes(partition.sizeBytes)

    val utilizationPercent: Int
        get() = if (partition.sizeBytes > 0 && matchedImageSizeBytes > 0) {
            ((matchedImageSizeBytes.toDouble() / partition.sizeBytes.toDouble()) * 100).toInt().coerceAtMost(999)
        } else {
            0
        }
}

data class FlashPlan(
    val title: String = "Flash Plan",
    val targetProfile: DeviceProfile = DeviceProfile.GALAXY_J2_PRIME,
    val items: List<FlashPartitionItem> = emptyList(),
    val totalImagesToFlash: Int = 0,
    val totalFlashSizeBytes: Long = 0L,
    val highestRisk: FlashRiskLevel = FlashRiskLevel.SAFE
)
