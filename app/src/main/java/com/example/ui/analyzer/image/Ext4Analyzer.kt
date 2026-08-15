package com.example.ui.analyzer.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Ext4Analyzer {
    const val SUPERBLOCK_OFFSET = 1024L
    const val SUPERBLOCK_SIZE = 1024

    // Feature Compat
    const val COMPAT_DIR_PREALLOC = 0x0001
    const val COMPAT_IMAGIC_INODES = 0x0002
    const val COMPAT_HAS_JOURNAL = 0x0004
    const val COMPAT_EXT_ATTR = 0x0008
    const val COMPAT_RESIZE_INODE = 0x0010
    const val COMPAT_DIR_INDEX = 0x0020

    // Feature Incompat
    const val INCOMPAT_COMPRESSION = 0x0001
    const val INCOMPAT_FILETYPE = 0x0002
    const val INCOMPAT_RECOVER = 0x0004
    const val INCOMPAT_JOURNAL_DEV = 0x0008
    const val INCOMPAT_META_BG = 0x0010
    const val INCOMPAT_EXTENTS = 0x0040
    const val INCOMPAT_64BIT = 0x0080
    const val INCOMPAT_MMP = 0x0100
    const val INCOMPAT_FLEX_BG = 0x0200
    const val INCOMPAT_EA_INODE = 0x0400
    const val INCOMPAT_DIRDATA = 0x1000
    const val INCOMPAT_CSUM_SEED = 0x2000
    const val INCOMPAT_LARGEDIR = 0x4000
    const val INCOMPAT_INLINE_DATA = 0x8000
    const val INCOMPAT_ENCRYPT = 0x10000

    // Feature RO Compat
    const val RO_COMPAT_SPARSE_SUPER = 0x0001
    const val RO_COMPAT_LARGE_FILE = 0x0002
    const val RO_COMPAT_BTREE_DIR = 0x0004
    const val RO_COMPAT_HUGE_FILE = 0x0008
    const val RO_COMPAT_GDT_CSUM = 0x0010
    const val RO_COMPAT_DIR_NLINK = 0x0020
    const val RO_COMPAT_EXTRA_ISIZE = 0x0040
    const val RO_COMPAT_HAS_SNAPSHOT = 0x0080
    const val RO_COMPAT_QUOTA = 0x0100
    const val RO_COMPAT_BIGALLOC = 0x0200
    const val RO_COMPAT_METADATA_CSUM = 0x0400
    const val RO_COMPAT_READONLY = 0x1000
    const val RO_COMPAT_PROJECT = 0x2000

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

        val inodesCount = buffer.getInt(0x00).toLong() and 0xFFFFFFFFL
        val blocksCountLo = buffer.getInt(0x04).toLong() and 0xFFFFFFFFL
        val rBlocksCountLo = buffer.getInt(0x08).toLong() and 0xFFFFFFFFL
        val freeBlocksCountLo = buffer.getInt(0x0C).toLong() and 0xFFFFFFFFL
        val freeInodesCount = buffer.getInt(0x10).toLong() and 0xFFFFFFFFL
        val firstDataBlock = buffer.getInt(0x14).toLong() and 0xFFFFFFFFL
        val logBlockSize = buffer.getInt(0x18)
        val blockSize = 1024 shl logBlockSize
        val blocksPerGroup = buffer.getInt(0x20).toLong() and 0xFFFFFFFFL
        val inodesPerGroup = buffer.getInt(0x28).toLong() and 0xFFFFFFFFL
        val mtime = buffer.getInt(0x2C).toLong() and 0xFFFFFFFFL
        val wtime = buffer.getInt(0x30).toLong() and 0xFFFFFFFFL
        val mntCount = buffer.getShort(0x34).toInt() and 0xFFFF
        val maxMntCount = buffer.getShort(0x36).toInt() and 0xFFFF
        val magic = buffer.getShort(0x38).toInt() and 0xFFFF
        val state = buffer.getShort(0x3A).toInt() and 0xFFFF
        val errors = buffer.getShort(0x3C).toInt() and 0xFFFF
        val inodeSize = buffer.getShort(0x58).toInt() and 0xFFFF
        val featureCompat = buffer.getInt(0x5C)
        val featureIncompat = buffer.getInt(0x60)
        val featureRoCompat = buffer.getInt(0x64)

        if (magic != ImageFormatDetector.EXT4_MAGIC) {
            return null
        }

        // UUID (16 bytes)
        val uuidBytes = ByteArray(16)
        buffer.position(0x68)
        buffer.get(uuidBytes)
        val uuidStr = formatUuid(uuidBytes)

        // Volume Name (16 bytes)
        val volNameBytes = ByteArray(16)
        buffer.position(0x78)
        buffer.get(volNameBytes)
        val volumeName = String(volNameBytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }

        // Last mounted path (64 bytes)
        val lastMountBytes = ByteArray(64)
        buffer.position(0x88)
        buffer.get(lastMountBytes)
        val lastMounted = String(lastMountBytes, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }

        // 64-bit high block counts if feature enabled
        var totalBlocks = blocksCountLo
        var freeBlocks = freeBlocksCountLo
        val is64Bit = (featureIncompat and INCOMPAT_64BIT) != 0
        if (is64Bit && buffer.limit() >= 0x160) {
            val blocksHi = buffer.getInt(0x150).toLong() and 0xFFFFFFFFL
            val freeBlocksHi = buffer.getInt(0x158).toLong() and 0xFFFFFFFFL
            totalBlocks = (blocksHi shl 32) or blocksCountLo
            freeBlocks = (freeBlocksHi shl 32) or freeBlocksCountLo
        }

        val usedBlocks = if (totalBlocks >= freeBlocks) totalBlocks - freeBlocks else 0L
        val uncompressedSize = totalBlocks * blockSize.toLong()

        // Decode features
        val featuresList = mutableListOf<String>()
        if ((featureCompat and COMPAT_HAS_JOURNAL) != 0) featuresList.add("has_journal")
        if ((featureCompat and COMPAT_EXT_ATTR) != 0) featuresList.add("ext_attr")
        if ((featureCompat and COMPAT_DIR_INDEX) != 0) featuresList.add("dir_index")
        if ((featureIncompat and INCOMPAT_FILETYPE) != 0) featuresList.add("filetype")
        if ((featureIncompat and INCOMPAT_EXTENTS) != 0) featuresList.add("extents")
        if (is64Bit) featuresList.add("64bit")
        if ((featureIncompat and INCOMPAT_FLEX_BG) != 0) featuresList.add("flex_bg")
        if ((featureIncompat and INCOMPAT_EA_INODE) != 0) featuresList.add("ea_inode")
        if ((featureIncompat and INCOMPAT_INLINE_DATA) != 0) featuresList.add("inline_data")
        if ((featureIncompat and INCOMPAT_ENCRYPT) != 0) featuresList.add("encrypt")
        if ((featureRoCompat and RO_COMPAT_SPARSE_SUPER) != 0) featuresList.add("sparse_super")
        if ((featureRoCompat and RO_COMPAT_LARGE_FILE) != 0) featuresList.add("large_file")
        if ((featureRoCompat and RO_COMPAT_HUGE_FILE) != 0) featuresList.add("huge_file")
        if ((featureRoCompat and RO_COMPAT_METADATA_CSUM) != 0) featuresList.add("metadata_csum")
        if ((featureRoCompat and RO_COMPAT_READONLY) != 0) featuresList.add("readonly")

        val flagsList = mutableListOf<String>()
        flagsList.add(if (state == 1) "State: Clean (0x01)" else "State: Unclean / Errors (0x$state)")
        flagsList.add("Error Behavior: ${when (errors) { 1 -> "Continue"; 2 -> "Remount RO"; 3 -> "Panic"; else -> "Unknown" }}")
        flagsList.add("Mount Count: $mntCount / $maxMntCount")
        flagsList.add("Inode Size: ${inodeSize}B")
        flagsList.add("Blocks Per Group: $blocksPerGroup")
        flagsList.add("Inodes Per Group: $inodesPerGroup")

        val rawFields = linkedMapOf<String, String>()
        rawFields["s_magic"] = "0x${Integer.toHexString(magic).uppercase()}"
        rawFields["s_inodes_count"] = inodesCount.toString()
        rawFields["s_blocks_count"] = totalBlocks.toString()
        rawFields["s_free_blocks_count"] = freeBlocks.toString()
        rawFields["s_free_inodes_count"] = freeInodesCount.toString()
        rawFields["s_log_block_size"] = "$logBlockSize (${blockSize} bytes)"
        rawFields["s_inode_size"] = "$inodeSize bytes"
        rawFields["s_volume_name"] = volumeName.ifEmpty { "(none)" }
        rawFields["s_uuid"] = uuidStr
        rawFields["s_last_mounted"] = lastMounted.ifEmpty { "(none)" }
        rawFields["s_feature_compat"] = "0x${Integer.toHexString(featureCompat).uppercase()}"
        rawFields["s_feature_incompat"] = "0x${Integer.toHexString(featureIncompat).uppercase()}"
        rawFields["s_feature_ro_compat"] = "0x${Integer.toHexString(featureRoCompat).uppercase()}"

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        if (mtime > 0) rawFields["s_mtime"] = sdf.format(Date(mtime * 1000L))
        if (wtime > 0) rawFields["s_wtime"] = sdf.format(Date(wtime * 1000L))

        return ImageMetadata(
            fileName = "",
            fileSize = totalFileSize,
            format = ImageFormat.EXT4,
            magicString = "0xEF53",
            magicHex = "0xEF53",
            blockSize = blockSize,
            totalBlocks = totalBlocks,
            uncompressedSize = uncompressedSize,
            compressionRatio = 1.0,
            filesystemType = "EXT4",
            volumeName = volumeName,
            uuid = uuidStr,
            isReadOnly = (featureRoCompat and RO_COMPAT_READONLY) != 0,
            inodeCount = inodesCount,
            freeInodes = freeInodesCount,
            freeBlocks = freeBlocks,
            usedBlocks = usedBlocks,
            features = featuresList,
            flags = flagsList,
            mountPointHint = lastMounted,
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
