package com.example.ui.analyzer.partition

data class PartitionTable(
    val type: PartitionTableType = PartitionTableType.UNKNOWN,
    val sourceName: String = "",
    val sectorSize: Int = 512,
    val diskSize: Long = 0L,
    val diskGuid: String = "",
    val currentLba: Long = 1L,
    val backupLba: Long = 0L,
    val firstUsableLba: Long = 34L,
    val lastUsableLba: Long = 0L,
    val partitionEntryLba: Long = 2L,
    val numberOfEntries: Int = 0,
    val sizeOfEntry: Int = 128,
    val headerCrc: Long = 0L,
    val headerCrcComputed: Long = 0L,
    val isHeaderCrcValid: Boolean = true,
    val partitionTableCrc: Long = 0L,
    val partitionTableCrcComputed: Long = 0L,
    val isTableCrcValid: Boolean = true,
    val gptRevision: String = "1.0",
    val mbrSignatureHex: String = "0x55AA",
    val isMbrSignatureValid: Boolean = true,
    val platformName: String = "",
    val projectVersion: String = "",
    val storageType: String = "EMMC",
    val partitions: List<PartitionEntry> = emptyList(),
    val rawHeaderFields: Map<String, String> = emptyMap()
) {
    val totalPartitionsCount: Int
        get() = partitions.size

    val allocatedBytes: Long
        get() = partitions.sumOf { it.sizeBytes }

    val formattedAllocatedBytes: String
        get() = PartitionEntry.formatBytes(allocatedBytes)

    val formattedDiskSize: String
        get() = PartitionEntry.formatBytes(diskSize)
}
