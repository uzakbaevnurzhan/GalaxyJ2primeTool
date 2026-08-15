package com.example.ui.analyzer.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object F2fsAnalyzer {
    const val SUPERBLOCK_OFFSET = 1024L
    const val SUPERBLOCK_SIZE = 1024

    fun parseSuperblock(channel: FileChannel, baseOffset: Long = 0L): ImageMetadata? {
        val originalPos = channel.position()
        try {
            val sbOffset = baseOffset + SUPERBLOCK_OFFSET
            if (channel.size() < sbOffset + SUPERBLOCK_SIZE) return null

            channel.position(sbOffset)
            val buffer = ByteBuffer.allocate(SUPERBLOCK_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val read = channel.read(buffer)
            if (read < SUPERBLOCK_SIZE) return null
            buffer.flip()

            return parseSuperblockBuffer(buffer, channel.size())
        } catch (e: Exception) {
            return null
        } finally {
            channel.position(originalPos)
        }
    }

    fun parseSuperblockBuffer(buffer: ByteBuffer, totalFileSize: Long = 0L): ImageMetadata? {
        if (buffer.remaining() < 256) return null

        val magic = buffer.getInt(0x00)
        if (magic != ImageFormatDetector.F2FS_MAGIC) return null

        val majorVer = buffer.getShort(0x04).toInt() and 0xFFFF
        val minorVer = buffer.getShort(0x06).toInt() and 0xFFFF
        val logSectorSize = buffer.getInt(0x08)
        val logSectorsPerBlock = buffer.getInt(0x0C)
        val logBlocksize = buffer.getInt(0x10)
        val logBlocksPerSeg = buffer.getInt(0x14)
        val segsPerSec = buffer.getInt(0x18)
        val secsPerZone = buffer.getInt(0x1C)
        val totalSections = buffer.getInt(0x20)
        val segmentCount = buffer.getInt(0x24)
        val blockCount = buffer.getLong(0x28)
        val sectionCount = buffer.getInt(0x30)
        val segmentCountMain = buffer.getInt(0x34)
        val rootIno = buffer.getInt(0x50).toLong() and 0xFFFFFFFFL

        val blockSize = 1 shl (if (logBlocksize in 9..16) logBlocksize else 12)

        val uuidBytes = ByteArray(16)
        buffer.position(0x6C)
        buffer.get(uuidBytes)
        val uuidStr = formatUuid(uuidBytes)

        // Volume name is UTF-16LE 512 bytes
        var volumeName = ""
        try {
            val volNameBytes = ByteArray(512)
            buffer.position(0x7C)
            buffer.get(volNameBytes)
            volumeName = String(volNameBytes, Charsets.UTF_16LE).trim { it <= ' ' || it == '\u0000' }
        } catch (e: Exception) {
            // Ignore volume name parse error
        }

        val featuresList = listOf("flash_friendly", "lfs_logging", "in_place_update")
        val flagsList = listOf(
            "F2FS Version: $majorVer.$minorVer",
            "Root Inode: $rootIno",
            "Segment Count: $segmentCount",
            "Main Segments: $segmentCountMain",
            "Sections: $totalSections"
        )

        val rawFields = linkedMapOf<String, String>()
        rawFields["magic"] = "0x${Integer.toHexString(magic).uppercase()} (F2FS)"
        rawFields["version"] = "$majorVer.$minorVer"
        rawFields["block_size"] = "$blockSize bytes"
        rawFields["block_count"] = blockCount.toString()
        rawFields["segment_count"] = segmentCount.toString()
        rawFields["uuid"] = uuidStr
        rawFields["volume_name"] = volumeName.ifEmpty { "(none)" }

        val uncompressedSize = blockCount * blockSize.toLong()

        return ImageMetadata(
            fileName = "",
            fileSize = totalFileSize,
            format = ImageFormat.F2FS,
            magicString = "0xF2F52010",
            magicHex = "0xF2F52010",
            blockSize = blockSize,
            totalBlocks = blockCount,
            uncompressedSize = uncompressedSize,
            compressionRatio = 1.0,
            filesystemType = "F2FS",
            volumeName = volumeName,
            uuid = uuidStr,
            isReadOnly = false,
            inodeCount = 0L,
            freeInodes = 0L,
            freeBlocks = 0L,
            usedBlocks = blockCount,
            features = featuresList,
            flags = flagsList,
            mountPointHint = "",
            rawHeaderFields = rawFields
        )
    }

    private fun formatUuid(bytes: ByteArray): String {
        if (bytes.size != 16) return ""
        val sb = java.lang.StringBuilder()
        for (i in bytes.indices) {
            sb.append(String.format("%02x", bytes[i]))
            if (i == 3 || i == 5 || i == 7 || i == 9) sb.append("-")
        }
        return sb.toString()
    }
}
