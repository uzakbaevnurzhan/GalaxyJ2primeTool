package com.example.ui.analyzer.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SquashFsAnalyzer {
    const val SUPERBLOCK_SIZE = 96

    fun parseSuperblock(channel: FileChannel, baseOffset: Long = 0L): ImageMetadata? {
        val originalPos = channel.position()
        try {
            if (channel.size() < baseOffset + SUPERBLOCK_SIZE) return null

            channel.position(baseOffset)
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
        if (buffer.remaining() < 48) return null

        val magic = buffer.getInt(0x00)
        val isLe = (magic == ImageFormatDetector.SQUASHFS_MAGIC_LE)
        val isBe = (magic == ImageFormatDetector.SQUASHFS_MAGIC_BE)
        if (!isLe && !isBe) return null

        if (isBe) buffer.order(ByteOrder.BIG_ENDIAN)

        val inodes = buffer.getInt(0x04).toLong() and 0xFFFFFFFFL
        val mkfsTime = buffer.getInt(0x08).toLong() and 0xFFFFFFFFL
        val blockSize = buffer.getInt(0x0C)
        val fragments = buffer.getInt(0x10).toLong() and 0xFFFFFFFFL
        val compression = buffer.getShort(0x14).toInt() and 0xFFFF
        val blockLog = buffer.getShort(0x16).toInt() and 0xFFFF
        val flags = buffer.getShort(0x18).toInt() and 0xFFFF
        val noIds = buffer.getShort(0x1A).toInt() and 0xFFFF
        val sMajor = buffer.getShort(0x1C).toInt() and 0xFFFF
        val sMinor = buffer.getShort(0x1E).toInt() and 0xFFFF
        val rootInode = buffer.getLong(0x20)
        val bytesUsed = buffer.getLong(0x28)

        val compName = when (compression) {
            1 -> "GZIP (zlib)"
            2 -> "LZMA"
            3 -> "LZO"
            4 -> "XZ"
            5 -> "LZ4"
            6 -> "ZSTD"
            else -> "Unknown ($compression)"
        }

        val featuresList = listOf("Compression: $compName", "SquashFS $sMajor.$sMinor")
        val flagsList = listOf(
            "Flags: 0x${Integer.toHexString(flags)}",
            "Inodes: $inodes",
            "Fragments: $fragments",
            "Root Inode Ref: 0x${java.lang.Long.toHexString(rootInode)}",
            "Read-Only"
        )

        val rawFields = linkedMapOf<String, String>()
        rawFields["s_magic"] = "0x${Integer.toHexString(magic).uppercase()} (SquashFS)"
        rawFields["version"] = "$sMajor.$sMinor"
        rawFields["compression"] = compName
        rawFields["block_size"] = "$blockSize bytes"
        rawFields["inodes"] = inodes.toString()
        rawFields["bytes_used"] = "$bytesUsed bytes"

        if (mkfsTime > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            rawFields["mkfs_time"] = sdf.format(Date(mkfsTime * 1000L))
        }

        val totalBlocks = if (blockSize > 0) (bytesUsed + blockSize - 1) / blockSize else 0L

        return ImageMetadata(
            fileName = "",
            fileSize = totalFileSize,
            format = ImageFormat.SQUASHFS,
            magicString = "sqsh",
            magicHex = "0x${Integer.toHexString(magic).uppercase()}",
            blockSize = if (blockSize > 0) blockSize else 4096,
            totalBlocks = totalBlocks,
            uncompressedSize = bytesUsed,
            compressionRatio = if (totalFileSize > 0 && bytesUsed > 0) totalFileSize.toDouble() / bytesUsed.toDouble() else 1.0,
            filesystemType = "SquashFS",
            volumeName = "",
            uuid = "",
            isReadOnly = true,
            inodeCount = inodes,
            freeInodes = 0L,
            freeBlocks = 0L,
            usedBlocks = totalBlocks,
            features = featuresList,
            flags = flagsList,
            mountPointHint = "",
            rawHeaderFields = rawFields
        )
    }
}
