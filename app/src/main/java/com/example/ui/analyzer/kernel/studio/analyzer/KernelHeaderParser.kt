package com.example.ui.analyzer.kernel.studio.analyzer

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Arm64ImageHeader(
    val code0: Int,
    val code1: Int,
    val textOffset: Long,
    val imageSize: Long,
    val flags: Long,
    val magic: Int,
    val isValid: Boolean
)

data class Arm32ZImageHeader(
    val startAddr: Long,
    val endAddr: Long,
    val magic: Int,
    val isValid: Boolean
)

data class UImageHeader(
    val magic: Int,
    val headerCrc: Int,
    val timestamp: Long,
    val dataSize: Long,
    val loadAddress: Long,
    val entryPoint: Long,
    val dataCrc: Int,
    val osType: Int,
    val archType: Int,
    val imageType: Int,
    val compType: Int,
    val imageName: String,
    val isValid: Boolean
)

object KernelHeaderParser {

    fun parseArm64Header(bytes: ByteArray): Arm64ImageHeader? {
        if (bytes.size < 64) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val code0 = buf.getInt(0)
        val code1 = buf.getInt(4)
        val textOffset = buf.getLong(8)
        val imageSize = buf.getLong(16)
        val flags = buf.getLong(24)
        val magic = buf.getInt(56)

        val isValid = magic == 0x644d5241 // 'ARM\x64'
        return Arm64ImageHeader(code0, code1, textOffset, imageSize, flags, magic, isValid)
    }

    fun parseArm32ZImageHeader(bytes: ByteArray): Arm32ZImageHeader? {
        if (bytes.size < 40) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val startAddr = buf.getInt(28).toLong() and 0xFFFFFFFFL
        val endAddr = buf.getInt(32).toLong() and 0xFFFFFFFFL
        val magic = buf.getInt(36)

        val isValid = magic == 0x016f2818
        return Arm32ZImageHeader(startAddr, endAddr, magic, isValid)
    }

    fun parseUImageHeader(bytes: ByteArray): UImageHeader? {
        if (bytes.size < 64) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = buf.getInt(0)
        if (magic != 0x27051956) return null

        val hcrc = buf.getInt(4)
        val time = buf.getInt(8).toLong() and 0xFFFFFFFFL
        val size = buf.getInt(12).toLong() and 0xFFFFFFFFL
        val load = buf.getInt(16).toLong() and 0xFFFFFFFFL
        val ep = buf.getInt(20).toLong() and 0xFFFFFFFFL
        val dcrc = buf.getInt(24)
        val os = bytes[28].toInt() and 0xFF
        val arch = bytes[29].toInt() and 0xFF
        val type = bytes[30].toInt() and 0xFF
        val comp = bytes[31].toInt() and 0xFF

        val nameBytes = ByteArray(32)
        System.arraycopy(bytes, 32, nameBytes, 0, 32)
        val name = String(nameBytes, Charsets.UTF_8).trim().trimEnd('\u0000')

        return UImageHeader(
            magic = magic,
            headerCrc = hcrc,
            timestamp = time,
            dataSize = size,
            loadAddress = load,
            entryPoint = ep,
            dataCrc = dcrc,
            osType = os,
            archType = arch,
            imageType = type,
            compType = comp,
            imageName = name,
            isValid = true
        )
    }
}
