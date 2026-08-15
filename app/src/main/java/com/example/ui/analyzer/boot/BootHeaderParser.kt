package com.example.ui.analyzer.boot

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object BootHeaderParser {

    private val ANDROID_BOOT_MAGIC = "ANDROID!".toByteArray(Charsets.US_ASCII)

    fun parse(file: File): BootHeaderInfo {
        if (!file.exists() || file.length() < 64) {
            return createInvalidHeader("File does not exist or is too small (<64 bytes)")
        }

        RandomAccessFile(file, "r").use { raf ->
            val headerBytes = ByteArray(4096)
            val bytesRead = raf.read(headerBytes)
            if (bytesRead < 64) {
                return createInvalidHeader("Could not read enough bytes for boot header")
            }

            return parseHeaderBytes(headerBytes, file.length())
        }
    }

    fun parseStream(inputStream: InputStream, totalSize: Long = 0): BootHeaderInfo {
        val headerBytes = ByteArray(4096)
        var offset = 0
        while (offset < 4096) {
            val count = inputStream.read(headerBytes, offset, 4096 - offset)
            if (count <= 0) break
            offset += count
        }

        if (offset < 64) {
            return createInvalidHeader("Stream did not contain enough bytes for boot header (read $offset bytes)")
        }

        return parseHeaderBytes(headerBytes, totalSize)
    }

    fun parseHeaderBytes(headerBytes: ByteArray, totalFileSize: Long): BootHeaderInfo {
        for (i in 0..7) {
            if (i >= headerBytes.size || headerBytes[i] != ANDROID_BOOT_MAGIC[i]) {
                return createInvalidHeader("Missing ANDROID! magic at offset 0 (Found: ${getSafeString(headerBytes, 0, 8)})")
            }
        }

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8) // Skip ANDROID!

        // Determine header version first
        // In v0..v2: header_version is at offset 40
        // In v3..v4: header_version is at offset 12 or 40 depending on structure
        val v3CheckVersion = if (headerBytes.size >= 16) {
            buffer.position(12)
            buffer.getInt()
        } else 0

        // In standard v0..v2:
        // offset 8: kernel_size (uint32)
        // offset 12: kernel_addr (uint32)
        // offset 16: ramdisk_size (uint32)
        // offset 20: ramdisk_addr (uint32)
        // offset 24: second_size (uint32)
        // offset 28: second_addr (uint32)
        // offset 32: tags_addr (uint32)
        // offset 36: page_size (uint32)
        // offset 40: header_version (uint32)
        // offset 44: os_version (uint32)
        // offset 48: name (16 bytes)
        // offset 64: cmdline (512 bytes)
        // offset 576: id (32 bytes SHA)
        // offset 608: extra_cmdline (1024 bytes)
        // offset 1632: recovery_dtbo_size (uint32 in v1)
        // offset 1636: recovery_dtbo_offset (uint64 in v1)
        // offset 1644: header_size (uint32 in v1)
        // offset 1648: dtb_size (uint32 in v2)
        // offset 1652: dtb_addr (uint64 in v2)

        buffer.position(40)
        val headerVersionStandard = if (headerBytes.size >= 44) buffer.getInt() else 0

        return if (v3CheckVersion in 3..4 && (headerVersionStandard < 0 || headerVersionStandard > 4)) {
            parseV3V4Header(headerBytes, v3CheckVersion, totalFileSize)
        } else if (headerVersionStandard in 3..4) {
            parseV3V4Header(headerBytes, headerVersionStandard, totalFileSize)
        } else {
            parseV0V1V2Header(headerBytes, headerVersionStandard.coerceIn(0, 2), totalFileSize)
        }
    }

    private fun parseV0V1V2Header(bytes: ByteArray, version: Int, totalFileSize: Long): BootHeaderInfo {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)

        val kernelSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val kernelAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ramdiskSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ramdiskAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val secondSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val secondAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val tagsAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val pageSize = buffer.getInt().coerceAtLeast(2048)
        buffer.getInt() // header_version
        val osVersionRaw = buffer.getInt()

        val boardName = getNullTerminatedString(bytes, 48, 16)
        val cmdline = getNullTerminatedString(bytes, 64, 512)
        val shaBytes = ByteArray(32)
        System.arraycopy(bytes, 576, shaBytes, 0, 32.coerceAtMost(bytes.size - 576))
        val shaString = shaBytes.joinToString("") { "%02x".format(it) }

        var extraCmdline = ""
        var recoveryDtboSize = 0L
        var recoveryDtboOffset = 0L
        var headerSize = pageSize
        var dtbSize = 0L
        var dtbLoadAddr = 0L

        if (bytes.size >= 1632) {
            extraCmdline = getNullTerminatedString(bytes, 608, 1024)
        }

        if (version >= 1 && bytes.size >= 1648) {
            buffer.position(1632)
            recoveryDtboSize = buffer.getInt().toLong() and 0xFFFFFFFFL
            recoveryDtboOffset = buffer.getLong()
            headerSize = buffer.getInt()
        }

        if (version >= 2 && bytes.size >= 1660) {
            buffer.position(1648)
            dtbSize = buffer.getInt().toLong() and 0xFFFFFFFFL
            dtbLoadAddr = buffer.getLong()
        }

        val (osVer, patchLevel) = decodeOsVersionAndPatch(osVersionRaw)

        // Calculate page alignments
        val pageCount = { size: Long -> ((size + pageSize - 1) / pageSize) * pageSize }
        val kernelOffset = pageSize.toLong()
        val ramdiskOffset = kernelOffset + pageCount(kernelSize)
        val secondOffset = ramdiskOffset + pageCount(ramdiskSize)
        val tagsOffset = tagsAddr

        return BootHeaderInfo(
            isValid = true,
            headerVersion = version,
            magic = "ANDROID!",
            kernelSize = kernelSize,
            kernelLoadAddr = kernelAddr,
            ramdiskSize = ramdiskSize,
            ramdiskLoadAddr = ramdiskAddr,
            secondStageSize = secondSize,
            secondStageLoadAddr = secondAddr,
            tagsLoadAddr = tagsAddr,
            pageSize = pageSize,
            headerSize = headerSize,
            osVersionRaw = osVersionRaw,
            osVersionString = osVer,
            osPatchLevelString = patchLevel,
            boardName = boardName,
            cmdline = cmdline,
            extraCmdline = extraCmdline,
            recoveryDtboSize = recoveryDtboSize,
            recoveryDtboOffset = recoveryDtboOffset,
            dtbSize = dtbSize,
            dtbLoadAddr = dtbLoadAddr,
            signatureSha = shaString,
            kernelOffset = kernelOffset,
            ramdiskOffset = ramdiskOffset,
            secondOffset = secondOffset,
            tagsOffset = tagsOffset,
            isPartialSupport = false,
            notes = "Parsed standard Android boot header v$version (Page size: $pageSize)"
        )
    }

    private fun parseV3V4Header(bytes: ByteArray, version: Int, totalFileSize: Long): BootHeaderInfo {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Android Boot v3/v4 format:
        // offset 0: magic (8 bytes)
        // offset 8: kernel_size (uint32)
        // offset 12: ramdisk_size (uint32)
        // offset 16: os_version (uint32)
        // offset 20: header_size (uint32)
        // offset 24: reserved (16 bytes)
        // offset 40: header_version (uint32)
        // offset 44: cmdline (1536 bytes in v3/v4)
        // in v4: signature_size (uint32 at offset 1580)
        
        buffer.position(8)
        val kernelSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ramdiskSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val osVersionRaw = buffer.getInt()
        val headerSize = buffer.getInt()
        buffer.position(40)
        val readVersion = buffer.getInt()
        val cmdline = getNullTerminatedString(bytes, 44, 1536.coerceAtMost(bytes.size - 44))

        val (osVer, patchLevel) = decodeOsVersionAndPatch(osVersionRaw)
        val pageSize = 4096 // Boot v3/v4 fixed page size = 4096

        val pageCount = { size: Long -> ((size + 4095) / 4096) * 4096 }
        val kernelOffset = 4096L
        val ramdiskOffset = kernelOffset + pageCount(kernelSize)

        return BootHeaderInfo(
            isValid = true,
            headerVersion = version,
            magic = "ANDROID!",
            kernelSize = kernelSize,
            kernelLoadAddr = 0L,
            ramdiskSize = ramdiskSize,
            ramdiskLoadAddr = 0L,
            secondStageSize = 0L,
            secondStageLoadAddr = 0L,
            tagsLoadAddr = 0L,
            pageSize = pageSize,
            headerSize = headerSize,
            osVersionRaw = osVersionRaw,
            osVersionString = osVer,
            osPatchLevelString = patchLevel,
            boardName = "",
            cmdline = cmdline,
            extraCmdline = "",
            recoveryDtboSize = 0L,
            recoveryDtboOffset = 0L,
            dtbSize = 0L,
            dtbLoadAddr = 0L,
            signatureSha = "",
            kernelOffset = kernelOffset,
            ramdiskOffset = ramdiskOffset,
            secondOffset = 0L,
            tagsOffset = 0L,
            isPartialSupport = false,
            notes = "Parsed modern Android boot header v$version (Page size fixed 4096)"
        )
    }

    private fun decodeOsVersionAndPatch(osVersionRaw: Int): Pair<String, String> {
        if (osVersionRaw == 0) return Pair("Unknown", "Unknown")
        val osVersion = osVersionRaw shr 11
        val a = (osVersion shr 14) and 0x7F
        val b = (osVersion shr 7) and 0x7F
        val c = osVersion and 0x7F
        val osVerString = if (a > 0) "$a.$b.$c" else "Unknown"

        val osPatch = osVersionRaw and 0x7FF
        val y = 2000 + (osPatch shr 4)
        val m = osPatch and 0xF
        val patchString = if (y in 2010..2040 && m in 1..12) "%04d-%02d".format(y, m) else "Unknown"

        return Pair(osVerString, patchString)
    }

    private fun getNullTerminatedString(bytes: ByteArray, offset: Int, maxLen: Int): String {
        if (offset >= bytes.size) return ""
        val actualMax = maxLen.coerceAtMost(bytes.size - offset)
        var end = offset
        while (end < offset + actualMax && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun getSafeString(bytes: ByteArray, offset: Int, len: Int): String {
        if (offset >= bytes.size) return ""
        val actualLen = len.coerceAtMost(bytes.size - offset)
        return String(bytes, offset, actualLen, Charsets.US_ASCII).replace("\u0000", " ")
    }

    private fun createInvalidHeader(reason: String): BootHeaderInfo {
        return BootHeaderInfo(
            isValid = false,
            headerVersion = -1,
            magic = "INVALID",
            kernelSize = 0,
            kernelLoadAddr = 0,
            ramdiskSize = 0,
            ramdiskLoadAddr = 0,
            secondStageSize = 0,
            secondStageLoadAddr = 0,
            tagsLoadAddr = 0,
            pageSize = 0,
            headerSize = 0,
            osVersionRaw = 0,
            osVersionString = "None",
            osPatchLevelString = "None",
            boardName = "",
            cmdline = "",
            extraCmdline = "",
            recoveryDtboSize = 0,
            recoveryDtboOffset = 0,
            dtbSize = 0,
            dtbLoadAddr = 0,
            signatureSha = "",
            kernelOffset = 0,
            ramdiskOffset = 0,
            secondOffset = 0,
            tagsOffset = 0,
            isPartialSupport = false,
            notes = "INVALID / UNKNOWN FORMAT: $reason"
        )
    }
}
