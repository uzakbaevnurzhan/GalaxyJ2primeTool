package com.example.ui.analyzer.kernel.studio.analyzer

import com.example.ui.analyzer.kernel.studio.models.KernelModuleInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object KernelModuleAnalyzer {

    fun analyzeModuleFile(file: File): KernelModuleInfo? {
        if (!file.exists() || file.length() < 64) return null
        return try {
            val bytes = file.readBytes()
            analyzeModuleBytes(bytes, file.name, file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun analyzeModuleBytes(bytes: ByteArray, name: String, path: String = ""): KernelModuleInfo? {
        if (bytes.size < 52) return null

        // ELF Magic: 0x7F, 'E', 'L', 'F'
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return null
        }

        val is64Bit = bytes[4].toInt() == 2
        val isLittleEndian = bytes[5].toInt() == 1
        val byteOrder = if (isLittleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
        val elfType = when (buffer.getShort(16).toInt()) {
            1 -> "REL (Relocatable object / Kernel module)"
            2 -> "EXEC (Executable)"
            3 -> "DYN (Shared object)"
            else -> "UNKNOWN"
        }

        val machine = buffer.getShort(18).toInt()
        val arch = when (machine) {
            40 -> "ARM32"
            183 -> "ARM64"
            3 -> "x86"
            62 -> "x86_64"
            else -> "Machine($machine)"
        }

        // Extract strings from .modinfo / strings table
        val strings = extractStrings(bytes)
        var vermagic = "UNKNOWN"
        val depends = mutableListOf<String>()

        for (s in strings) {
            if (s.startsWith("vermagic=")) {
                vermagic = s.removePrefix("vermagic=").trim()
            }
            if (s.startsWith("depends=")) {
                val depStr = s.removePrefix("depends=").trim()
                if (depStr.isNotEmpty()) {
                    depends.addAll(depStr.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        }

        return KernelModuleInfo(
            name = name,
            path = path,
            size = bytes.size.toLong(),
            architecture = arch,
            elfType = elfType,
            vermagic = vermagic,
            dependencies = depends.distinct()
        )
    }

    fun scanDirectoryForModules(dir: File): List<KernelModuleInfo> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val list = mutableListOf<KernelModuleInfo>()

        dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("ko", ignoreCase = true) }
            .take(500)
            .forEach { file ->
                val mod = analyzeModuleFile(file)
                if (mod != null) {
                    list.add(mod)
                }
            }

        return list.sortedBy { it.name }
    }

    private fun extractStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        val limit = bytes.size.coerceAtMost(1024 * 1024)

        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                if (sb.length >= 3) {
                    strings.add(sb.toString())
                }
                sb.setLength(0)
            }
        }
        if (sb.length >= 3) strings.add(sb.toString())
        return strings
    }
}
