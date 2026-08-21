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
        if (headerBytes.size < 64) {
            return createInvalidHeader("Header bytes array too small")
        }
        for (i in 0..7) {
            if (i >= headerBytes.size || headerBytes[i] != ANDROID_BOOT_MAGIC[i]) {
                return createInvalidHeader("Missing ANDROID! magic at offset 0 (Found: ${getSafeString(headerBytes, 0, 8)})")
            }
        }

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)

        val v3CheckVersion = if (headerBytes.size >= 16) {
            buffer.position(12)
            buffer.getInt()
        } else 0

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
        if (bytes.size < 48) return createInvalidHeader("Truncated V0 header")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(8)

        val kernelSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val kernelAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ramdiskSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ramdiskAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val secondSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val secondAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val tagsAddr = buffer.getInt().toLong() and 0xFFFFFFFFL
        val pageSizeRaw = buffer.getInt()
        val pageSize = if (pageSizeRaw > 0) pageSizeRaw else 2048
        
        if (pageSize < 2048 || pageSize > 16384) {
             return createInvalidHeader("Invalid page size: $pageSize")
        }

        buffer.getInt() // header_version
        val osVersionRaw = buffer.getInt()

        val boardName = getNullTerminatedString(bytes, 48, 16)
        val cmdline = getNullTerminatedString(bytes, 64, 512)
        val shaBytes = ByteArray(32)
        if (bytes.size >= 608) {
             System.arraycopy(bytes, 576, shaBytes, 0, 32)
        }
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

        val pageCount = { size: Long -> ((size + pageSize - 1) / pageSize) * pageSize }
        
        var totalOffset = pageSize.toLong()
        val kernelOffset = totalOffset
        
        if (totalOffset > Long.MAX_VALUE - pageCount(kernelSize)) return createInvalidHeader("Kernel size overflow")
        totalOffset += pageCount(kernelSize)
        val ramdiskOffset = totalOffset
        
        if (totalOffset > Long.MAX_VALUE - pageCount(ramdiskSize)) return createInvalidHeader("Ramdisk size overflow")
        totalOffset += pageCount(ramdiskSize)
        val secondOffset = totalOffset

        if (totalOffset > Long.MAX_VALUE - pageCount(secondSize)) return createInvalidHeader("Second size overflow")
        totalOffset += pageCount(secondSize)
        
        // Ensure total file size check if full image is provided
        if (totalFileSize > pageSize && (kernelOffset + kernelSize > totalFileSize || ramdiskOffset + ramdiskSize > totalFileSize)) {
             return createInvalidHeader("Sizes exceed total file size")
        }

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
        if (bytes.size < 44) return createInvalidHeader("Truncated V3/V4 header")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.position(8)
        val kernelSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val ramdiskSize = buffer.getInt().toLong() and 0xFFFFFFFFL
        val osVersionRaw = buffer.getInt()
        val headerSize = buffer.getInt()
        
        if (bytes.size < 48) return createInvalidHeader("Truncated V3/V4 header version")
        buffer.position(40)
        val readVersion = buffer.getInt()
        val cmdline = getNullTerminatedString(bytes, 44, 1536.coerceAtMost(bytes.size - 44))

        val (osVer, patchLevel) = decodeOsVersionAndPatch(osVersionRaw)
        val pageSize = 4096

        val pageCount = { size: Long -> ((size + 4095) / 4096) * 4096 }
        val kernelOffset = 4096L
        
        if (kernelOffset > Long.MAX_VALUE - pageCount(kernelSize)) return createInvalidHeader("Kernel size overflow")
        val ramdiskOffset = kernelOffset + pageCount(kernelSize)

        if (totalFileSize > pageSize && (kernelOffset + kernelSize > totalFileSize || ramdiskOffset + ramdiskSize > totalFileSize)) {
             return createInvalidHeader("Sizes exceed total file size")
        }

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
        if (offset < 0 || offset >= bytes.size || maxLen < 0) return ""
        val actualMax = maxLen.coerceAtMost(bytes.size - offset)
        var end = offset
        while (end < offset + actualMax && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun getSafeString(bytes: ByteArray, offset: Int, len: Int): String {
        if (offset < 0 || offset >= bytes.size || len < 0) return ""
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
