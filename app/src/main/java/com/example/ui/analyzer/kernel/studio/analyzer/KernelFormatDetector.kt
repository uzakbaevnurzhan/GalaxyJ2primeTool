package com.example.ui.analyzer.kernel.studio.analyzer

import com.example.ui.analyzer.kernel.studio.models.KernelFormatInfo
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

object KernelFormatDetector {

    fun detect(bytes: ByteArray): KernelFormatInfo {
        if (bytes.isEmpty()) {
            return KernelFormatInfo(
                format = "unknown",
                compression = "none",
                offset = 0L,
                size = 0L,
                architecture = "unknown"
            )
        }

        // 1. Check uImage magic: 0x27051956 (Big Endian)
        if (bytes.size >= 64) {
            val bufBe = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val uImageMagic = bufBe.getInt(0)
            if (uImageMagic == 0x27051956) {
                val dataSize = bufBe.getInt(12).toLong() and 0xFFFFFFFFL
                val archByte = bytes[29].toInt() and 0xFF
                val compByte = bytes[30].toInt() and 0xFF
                val arch = when (archByte) {
                    2 -> "ARM32"
                    3 -> "x86"
                    7 -> "MIPS"
                    8 -> "ARM64"
                    else -> "unknown"
                }
                val comp = when (compByte) {
                    0 -> "none"
                    1 -> "gzip"
                    2 -> "bzip2"
                    3 -> "lzma"
                    4 -> "lzo"
                    5 -> "lz4"
                    6 -> "zstd"
                    else -> "unknown"
                }
                return KernelFormatInfo(
                    format = "uImage",
                    compression = comp,
                    offset = 64L,
                    size = dataSize,
                    architecture = arch
                )
            }
        }

        // 2. Check ARM32 zImage magic: 0x016f2818 at offset 36
        if (bytes.size >= 40) {
            val bufLe = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val zImageMagic = bufLe.getInt(36)
            if (zImageMagic == 0x016f2818) {
                val start = bufLe.getInt(28).toLong() and 0xFFFFFFFFL
                val end = bufLe.getInt(32).toLong() and 0xFFFFFFFFL
                val size = if (end > start) end - start else bytes.size.toLong()
                return KernelFormatInfo(
                    format = "zImage",
                    compression = "gzip/self-extracting",
                    offset = 0L,
                    size = size,
                    architecture = "ARM32"
                )
            }
        }

        // 3. Check ARM64 raw Image magic: 'ARM\x64' (0x644d5241) at offset 56
        if (bytes.size >= 64) {
            val bufLe = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val arm64Magic = bufLe.getInt(56)
            if (arm64Magic == 0x644d5241) {
                val imageSize = bufLe.getLong(16)
                return KernelFormatInfo(
                    format = "Image",
                    compression = "none",
                    offset = 0L,
                    size = if (imageSize in 1..(bytes.size * 2)) imageSize else bytes.size.toLong(),
                    architecture = "ARM64"
                )
            }
        }

        // 4. Compression signatures
        // GZIP: 1F 8B
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            return KernelFormatInfo(
                format = "Image.gz",
                compression = "gzip",
                offset = 0L,
                size = bytes.size.toLong(),
                architecture = "unknown"
            )
        }

        // LZ4 standard framing: 04 22 4D 18 or legacy 02 21 4C 18
        if (bytes.size >= 4) {
            if ((bytes[0] == 0x04.toByte() && bytes[1] == 0x22.toByte() && bytes[2] == 0x4D.toByte() && bytes[3] == 0x18.toByte()) ||
                (bytes[0] == 0x02.toByte() && bytes[1] == 0x21.toByte() && bytes[2] == 0x4C.toByte() && bytes[3] == 0x18.toByte())
            ) {
                return KernelFormatInfo(
                    format = "Image.lz4",
                    compression = "lz4",
                    offset = 0L,
                    size = bytes.size.toLong(),
                    architecture = "unknown"
                )
            }
        }

        // XZ: FD 37 7A 58 5A 00
        if (bytes.size >= 6 &&
            bytes[0] == 0xFD.toByte() && bytes[1] == 0x37.toByte() && bytes[2] == 0x7A.toByte() &&
            bytes[3] == 0x58.toByte() && bytes[4] == 0x5A.toByte() && bytes[5] == 0x00.toByte()
        ) {
            return KernelFormatInfo(
                format = "Image.xz",
                compression = "xz",
                offset = 0L,
                size = bytes.size.toLong(),
                architecture = "unknown"
            )
        }

        // ZSTD: 28 B5 2F FD
        if (bytes.size >= 4 &&
            bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() &&
            bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte()
        ) {
            return KernelFormatInfo(
                format = "Image.zst",
                compression = "zstd",
                offset = 0L,
                size = bytes.size.toLong(),
                architecture = "unknown"
            )
        }

        // LZMA: 5D 00 00
        if (bytes.size >= 3 &&
            bytes[0] == 0x5D.toByte() && bytes[1] == 0x00.toByte() && bytes[2] == 0x00.toByte()
        ) {
            return KernelFormatInfo(
                format = "Image.lzma",
                compression = "lzma",
                offset = 0L,
                size = bytes.size.toLong(),
                architecture = "unknown"
            )
        }

        return KernelFormatInfo(
            format = "raw",
            compression = "none",
            offset = 0L,
            size = bytes.size.toLong(),
            architecture = "unknown"
        )
    }

    fun decompressIfPossible(bytes: ByteArray, compression: String): ByteArray? {
        return try {
            if (compression == "gzip" || compression == "gzip/self-extracting") {
                // Find GZIP magic offset if not at 0
                val gzOffset = findMagicOffset(bytes, byteArrayOf(0x1F.toByte(), 0x8B.toByte()))
                if (gzOffset >= 0) {
                    val stream = ByteArrayInputStream(bytes, gzOffset, bytes.size - gzOffset)
                    GZIPInputStream(stream).use { it.readBytes() }
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun findMagicOffset(bytes: ByteArray, magic: ByteArray): Int {
        val limit = (bytes.size - magic.size).coerceAtMost(65536)
        for (i in 0..limit) {
            var match = true
            for (j in magic.indices) {
                if (bytes[i + j] != magic[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}
