package com.example.ui.analyzer.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErofsAnalyzer {
    const val SUPERBLOCK_OFFSET = 1024L
    const val SUPERBLOCK_SIZE = 128

    const val EROFS_FEATURE_INCOMPAT_LZ4_0PADDING = 0x00000001
    const val EROFS_FEATURE_INCOMPAT_COMPR_CFGS = 0x00000002
    const val EROFS_FEATURE_INCOMPAT_BIG_PCLUSTER = 0x00000002
    const val EROFS_FEATURE_INCOMPAT_CHUNKED_FILE = 0x00000004
    const val EROFS_FEATURE_INCOMPAT_DEVICE_TABLE = 0x00000008
    const val EROFS_FEATURE_INCOMPAT_ZTAILPACKING = 0x00000010
    const val EROFS_FEATURE_INCOMPAT_FRAGMENTS = 0x00000020

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
        if (buffer.remaining() < 128) return null

        val magic = buffer.getInt(0x00)
        if (magic != ImageFormatDetector.EROFS_MAGIC) return null

        val checksum = buffer.getInt(0x04)
        val featureCompat = buffer.getInt(0x08)
        val blkszbits = buffer.get(0x0C).toInt() and 0xFF
        val blockSize = 1 shl (if (blkszbits in 9..16) blkszbits else 12)
        val sbExtSlots = buffer.get(0x0D).toInt() and 0xFF
        val rootNid = buffer.getShort(0x0E).toInt() and 0xFFFF
        val inos = buffer.getLong(0x10)
        val buildTime = buffer.getLong(0x18)
        val buildTimeNsec = buffer.getInt(0x20)
        val blocks = buffer.getInt(0x24).toLong() and 0xFFFFFFFFL
        val metaBlkAddr = buffer.getInt(0x28).toLong() and 0xFFFFFFFFL
        val xattrBlkAddr = buffer.getInt(0x2C).toLong() and 0xFFFFFFFFL

        val uuidBytes = ByteArray(16)
        buffer.position(0x30)
        buffer.get(uuidBytes)
        val uuidStr = formatUuid(uuidBytes)

        val volNameBytes = ByteArray(16)
        buffer.position(0x40)
        buffer.get(volNameBytes)
        val volumeName = String(volNameBytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }

        val featureIncompat = buffer.getInt(0x50)

        val featuresList = mutableListOf<String>()
        if ((featureIncompat and EROFS_FEATURE_INCOMPAT_LZ4_0PADDING) != 0) featuresList.add("lz4_0padding")
        if ((featureIncompat and EROFS_FEATURE_INCOMPAT_COMPR_CFGS) != 0) featuresList.add("compr_cfgs")
        if ((featureIncompat and EROFS_FEATURE_INCOMPAT_CHUNKED_FILE) != 0) featuresList.add("chunked_file")
        if ((featureIncompat and EROFS_FEATURE_INCOMPAT_DEVICE_TABLE) != 0) featuresList.add("device_table")
        if ((featureIncompat and EROFS_FEATURE_INCOMPAT_ZTAILPACKING) != 0) featuresList.add("ztailpacking")
        if ((featureIncompat and EROFS_FEATURE_INCOMPAT_FRAGMENTS) != 0) featuresList.add("fragments")
        if (featuresList.isEmpty()) featuresList.add("uncompressed / standard")

        val flagsList = listOf(
            "Root NID: $rootNid",
            "Meta Block Addr: $metaBlkAddr",
            "XAttr Block Addr: $xattrBlkAddr",
            "SB Extra Slots: $sbExtSlots",
            "Read-Only Filesystem"
        )

        val rawFields = linkedMapOf<String, String>()
        rawFields["s_magic"] = "0x${Integer.toHexString(magic).uppercase()} (EROFS)"
        rawFields["s_checksum"] = "0x${Integer.toHexString(checksum).uppercase()}"
        rawFields["s_block_size"] = "$blockSize bytes (bit shift: $blkszbits)"
        rawFields["s_inos"] = inos.toString()
        rawFields["s_blocks"] = blocks.toString()
        rawFields["s_volume_name"] = volumeName.ifEmpty { "(none)" }
        rawFields["s_uuid"] = uuidStr
        rawFields["s_feature_compat"] = "0x${Integer.toHexString(featureCompat).uppercase()}"
        rawFields["s_feature_incompat"] = "0x${Integer.toHexString(featureIncompat).uppercase()}"

        if (buildTime > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            rawFields["s_build_time"] = sdf.format(Date(buildTime * 1000L))
        }

        val uncompressedSize = blocks * blockSize.toLong()

        return ImageMetadata(
            fileName = "",
            fileSize = totalFileSize,
            format = ImageFormat.EROFS,
            magicString = "0xE0F5E1E2",
            magicHex = "0xE0F5E1E2",
            blockSize = blockSize,
            totalBlocks = blocks,
            uncompressedSize = uncompressedSize,
            compressionRatio = if (totalFileSize > 0) uncompressedSize.toDouble() / totalFileSize.toDouble() else 1.0,
            filesystemType = "EROFS",
            volumeName = volumeName,
            uuid = uuidStr,
            isReadOnly = true,
            inodeCount = inos,
            freeInodes = 0L,
            freeBlocks = 0L,
            usedBlocks = blocks,
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
