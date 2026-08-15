package com.example.ui.analyzer.image

data class ImageMetadata(
    val fileName: String = "",
    val fileSize: Long = 0L,
    val format: ImageFormat = ImageFormat.UNKNOWN,
    val magicString: String = "",
    val magicHex: String = "",
    val blockSize: Int = 4096,
    val totalBlocks: Long = 0L,
    val uncompressedSize: Long = 0L,
    val compressionRatio: Double = 1.0,
    val filesystemType: String = "None",
    val volumeName: String = "",
    val uuid: String = "",
    val isReadOnly: Boolean = false,
    val inodeCount: Long = 0L,
    val freeInodes: Long = 0L,
    val freeBlocks: Long = 0L,
    val usedBlocks: Long = 0L,
    val features: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
    val mountPointHint: String = "",
    val md5Hash: String = "",
    val sha1Hash: String = "",
    val sha256Hash: String = "",
    val crc32: Long = 0L,
    val rawHeaderFields: Map<String, String> = emptyMap()
) {
    val usagePercentage: Double
        get() = if (totalBlocks > 0) (usedBlocks.toDouble() / totalBlocks.toDouble()) * 100.0 else 0.0

    val freeBytes: Long
        get() = freeBlocks * blockSize.toLong()

    val usedBytes: Long
        get() = usedBlocks * blockSize.toLong()
}
