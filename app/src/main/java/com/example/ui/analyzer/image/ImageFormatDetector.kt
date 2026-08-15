package com.example.ui.analyzer.image

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object ImageFormatDetector {
    const val SPARSE_MAGIC = 0xED26FF3A.toInt()
    const val EXT4_MAGIC = 0xEF53
    const val EROFS_MAGIC = 0xE0F5E1E2.toInt()
    const val F2FS_MAGIC = 0xF2F52010.toInt()
    const val SQUASHFS_MAGIC_LE = 0x73717368
    const val SQUASHFS_MAGIC_BE = 0x68737173
    const val LP_METADATA_GEOMETRY_MAGIC = 0x616C6F67 // "gola" in LE / "alog"
    const val LP_METADATA_GEOMETRY_MAGIC_REV = 0x676F6C61
    const val LP_METADATA_HEADER_MAGIC = 0x414C5030 // "0PLA"
    const val PAYLOAD_MAGIC = 0x43724155 // "CrAU"
    val BOOT_MAGIC = "ANDROID!".toByteArray(Charsets.US_ASCII)

    fun detectFromFile(file: java.io.File): ImageFormat {
        if (!file.exists() || file.length() < 4) {
            return ImageFormat.fromExtension(file.name)
        }
        return try {
            java.io.FileInputStream(file).use { fis ->
                detectFromChannel(fis.channel)
            }
        } catch (e: Exception) {
            ImageFormat.fromExtension(file.name)
        }
    }

    fun detectFromChannel(channel: FileChannel): ImageFormat {
        val originalPos = channel.position()
        try {
            // Read first 8192 bytes for header inspection
            val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            channel.position(0)
            val bytesRead = channel.read(buffer)
            if (bytesRead < 4) return ImageFormat.UNKNOWN
            buffer.flip()

            return detectFromBuffer(buffer)
        } catch (e: Exception) {
            return ImageFormat.UNKNOWN
        } finally {
            channel.position(originalPos)
        }
    }

    fun detectFromStream(inputStream: InputStream): ImageFormat {
        try {
            val headerBytes = ByteArray(8192)
            var totalRead = 0
            while (totalRead < 8192) {
                val read = inputStream.read(headerBytes, totalRead, 8192 - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            if (totalRead < 4) return ImageFormat.UNKNOWN
            val buffer = ByteBuffer.wrap(headerBytes, 0, totalRead).order(ByteOrder.LITTLE_ENDIAN)
            return detectFromBuffer(buffer)
        } catch (e: Exception) {
            return ImageFormat.UNKNOWN
        }
    }

    fun detectFromBuffer(buffer: ByteBuffer): ImageFormat {
        val pos = buffer.position()
        val limit = buffer.limit()
        if (limit < 4) return ImageFormat.UNKNOWN

        // 1. Check Offset 0 (Sparse, Boot, SquashFS, Payload, Super LP)
        val magic0 = buffer.getInt(0)
        if (magic0 == SPARSE_MAGIC) return ImageFormat.SPARSE
        if (magic0 == SQUASHFS_MAGIC_LE || magic0 == SQUASHFS_MAGIC_BE) return ImageFormat.SQUASHFS
        if (magic0 == PAYLOAD_MAGIC) return ImageFormat.PAYLOAD_BIN
        if (magic0 == LP_METADATA_GEOMETRY_MAGIC || magic0 == LP_METADATA_GEOMETRY_MAGIC_REV || magic0 == LP_METADATA_HEADER_MAGIC) return ImageFormat.SUPER

        // Boot image check (ANDROID!)
        if (limit >= 8) {
            var isBoot = true
            for (i in 0 until 8) {
                if (buffer.get(i) != BOOT_MAGIC[i]) {
                    isBoot = false
                    break
                }
            }
            if (isBoot) return ImageFormat.BOOT_IMG
        }

        // 2. Check Offset 1024 (0x400) for EXT4, EROFS, F2FS
        if (limit >= 1024 + 64) {
            // EROFS magic at offset 1024
            val erofsMagic = buffer.getInt(1024)
            if (erofsMagic == EROFS_MAGIC) return ImageFormat.EROFS

            // F2FS magic at offset 1024
            val f2fsMagic = buffer.getInt(1024)
            if (f2fsMagic == F2FS_MAGIC) return ImageFormat.F2FS

            // EXT4 magic is at offset 1024 + 0x38 = 1080
            if (limit >= 1082) {
                val ext4Magic = buffer.getShort(1080).toInt() and 0xFFFF
                if (ext4Magic == EXT4_MAGIC) return ImageFormat.EXT4
            }
        }

        // 3. Check Offset 4096 (0x1000) for Super Image LP geometry
        if (limit >= 4096 + 4) {
            val superMagic = buffer.getInt(4096)
            if (superMagic == LP_METADATA_GEOMETRY_MAGIC || superMagic == LP_METADATA_GEOMETRY_MAGIC_REV || superMagic == LP_METADATA_HEADER_MAGIC) {
                return ImageFormat.SUPER
            }
        }

        // 4. Check Offset 5120 (0x1400) for F2FS backup superblock
        if (limit >= 5120 + 4) {
            val f2fsBackup = buffer.getInt(5120)
            if (f2fsBackup == F2FS_MAGIC) return ImageFormat.F2FS
        }

        return ImageFormat.RAW
    }
}
