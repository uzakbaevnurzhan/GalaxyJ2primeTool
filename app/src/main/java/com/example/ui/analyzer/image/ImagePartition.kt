package com.example.ui.analyzer.image

data class ImagePartition(
    val name: String,
    val sizeBytes: Long,
    val startOffset: Long = 0L,
    val blockCount: Long = 0L,
    val filesystem: ImageFormat = ImageFormat.UNKNOWN,
    val isReadOnly: Boolean = true,
    val groupName: String = "default",
    val attributes: List<String> = emptyList(),
    val extents: List<PartitionExtent> = emptyList(),
    val detectedProperties: Map<String, String> = emptyMap(),
    val detectedFilesCount: Int = 0
)

data class PartitionExtent(
    val targetType: String = "LINEAR", // LINEAR, ZERO
    val targetData: Long = 0L, // physical start sector or block
    val numSectors: Long = 0L,
    val targetBlockDevice: String = "super"
)
