package com.example.ui.analyzer.partition

enum class PartitionTableType(val displayName: String) {
    GPT("GUID Partition Table (GPT)"),
    MBR("Master Boot Record (MBR)"),
    HYBRID("Hybrid MBR / GPT"),
    MTK_SCATTER("MTK Android Scatter"),
    PROC_PARTITIONS("Linux /proc/partitions"),
    UNKNOWN("Unknown / Raw")
}

data class PartitionEntry(
    val index: Int = 0,
    val name: String = "",
    val startLba: Long = 0L,
    val endLba: Long = 0L,
    val startByteOffset: Long = 0L,
    val sizeBytes: Long = 0L,
    val typeGuidOrId: String = "",
    val uniqueGuid: String = "",
    val typeDescription: String = "",
    val attributesHex: String = "",
    val isBootable: Boolean = false,
    val isReadOnly: Boolean = false,
    val sectorSize: Int = 512,
    val region: String = "EMMC_USER",
    val storage: String = "EMMC",
    val isDownload: Boolean = true,
    val operationType: String = "UPDATE",
    val originalFileName: String = "",
    val detectedFilesystem: String = "Unknown",
    val flags: List<String> = emptyList()
) {
    val sectorCount: Long
        get() = if (endLba >= startLba) (endLba - startLba + 1) else if (sectorSize > 0) sizeBytes / sectorSize else 0L

    val sizeFormatted: String
        get() = formatBytes(sizeBytes)

    val startAddressHex: String
        get() = "0x" + java.lang.Long.toHexString(startByteOffset).uppercase()

    val endAddressHex: String
        get() = "0x" + java.lang.Long.toHexString(startByteOffset + sizeBytes).uppercase()

    companion object {
        fun formatBytes(bytes: Long): String {
            val kib = 1024.0
            val mib = kib * 1024.0
            val gib = mib * 1024.0
            return when {
                bytes >= gib -> String.format(java.util.Locale.US, "%.2f GB", bytes / gib)
                bytes >= mib -> String.format(java.util.Locale.US, "%.2f MB", bytes / mib)
                bytes >= kib -> String.format(java.util.Locale.US, "%.2f KB", bytes / kib)
                else -> "$bytes B"
            }
        }
    }
}
