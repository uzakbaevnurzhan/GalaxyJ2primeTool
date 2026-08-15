package com.example.ui.analyzer.boot

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

object BootKernelAnalyzer {

    fun analyzeKernel(kernelBytes: ByteArray): KernelDetailsInfo {
        if (kernelBytes.isEmpty()) {
            return KernelDetailsInfo(
                detectedFormat = "missing",
                detectedArch = "unknown",
                kernelSize = 0
            )
        }

        val format = detectCompressionFormat(kernelBytes)
        val decompressedBytes = tryDecompress(kernelBytes, format) ?: kernelBytes
        val arch = detectKernelArchitecture(decompressedBytes)
        val (versionStr, compilerStr, isSmp, configs, rawStrings) = extractKernelStrings(decompressedBytes)

        return KernelDetailsInfo(
            detectedFormat = format,
            detectedArch = arch,
            kernelVersionString = versionStr,
            compilerString = compilerStr,
            isSmp = isSmp,
            kernelConfigCount = configs.size,
            sampleConfigs = configs.take(15),
            kernelSize = kernelBytes.size.toLong(),
            rawStringsFound = rawStrings
        )
    }

    private fun detectCompressionFormat(bytes: ByteArray): String {
        if (bytes.size < 8) return "raw"
        // Gzip: 1F 8B
        if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            return "gzip"
        }
        // LZ4: 04 22 4D 18 or 02 21 4C 18
        if (bytes[0] == 0x04.toByte() && bytes[1] == 0x22.toByte() && bytes[2] == 0x4D.toByte() && bytes[3] == 0x18.toByte()) {
            return "lz4"
        }
        // XZ: FD 37 7A 58 5A 00
        if (bytes[0] == 0xFD.toByte() && bytes[1] == 0x37.toByte() && bytes[2] == 0x7A.toByte() && bytes[3] == 0x58.toByte() && bytes[4] == 0x5A.toByte()) {
            return "xz"
        }
        // ZSTD: 28 B5 2F FD
        if (bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() && bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte()) {
            return "zstd"
        }
        // LZMA: 5D 00 00
        if (bytes[0] == 0x5D.toByte() && bytes[1] == 0x00.toByte() && bytes[2] == 0x00.toByte()) {
            return "lzma"
        }
        // Linux ARM64 Image header magic at offset 56: 'ARM\x64' (0x644d5241)
        if (bytes.size >= 64) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(56)
            val magic = buf.getInt()
            if (magic == 0x644d5241) {
                return "raw_arm64_image"
            }
        }
        // Linux ARM32 zImage magic at offset 36: 0x016f2818
        if (bytes.size >= 40) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(36)
            val magic = buf.getInt()
            if (magic == 0x016f2818) {
                return "raw_arm32_zImage"
            }
        }

        return "raw"
    }

    private fun tryDecompress(bytes: ByteArray, format: String): ByteArray? {
        return try {
            if (format == "gzip") {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun detectKernelArchitecture(bytes: ByteArray): String {
        // 1. ARM64 Image magic
        if (bytes.size >= 64) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(56)
            if (buf.getInt() == 0x644d5241) return "ARM64"
        }
        // 2. ARM32 zImage magic
        if (bytes.size >= 40) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(36)
            if (buf.getInt() == 0x016f2818) return "ARM"
        }

        // 3. String scanning
        val text = extractAsciiStrings(bytes, 1024 * 1024)
        val hasAarch64 = text.any { it.contains("aarch64", ignoreCase = true) || it.contains("ARM64", ignoreCase = false) }
        val hasArmv7 = text.any { it.contains("armv7", ignoreCase = true) || it.contains("ARMv7", ignoreCase = false) || it.contains("arm-linux-androideabi", ignoreCase = true) }

        if (hasAarch64 && !hasArmv7) return "ARM64"
        if (hasArmv7 && !hasAarch64) return "ARM"
        if (hasAarch64 && hasArmv7) return "ARM64" // Often contains 32-bit compat strings

        return "unknown"
    }

    private fun extractKernelStrings(bytes: ByteArray): KernelStringResults {
        val stringList = extractAsciiStrings(bytes, 4 * 1024 * 1024)
        var kernelVersion: String? = null
        var compiler: String? = null
        var isSmp = false
        val configs = mutableListOf<String>()
        val keyFound = mutableListOf<String>()

        val linuxVersionRegex = Regex("""Linux version (\d+\.\d+[\w.-]*)""", RegexOption.IGNORE_CASE)
        val gccRegex = Regex("""(gcc version [\d.]+|clang version [\d.]+)""", RegexOption.IGNORE_CASE)

        for (s in stringList) {
            if (kernelVersion == null) {
                val match = linuxVersionRegex.find(s)
                if (match != null) {
                    kernelVersion = match.value
                    keyFound.add(s)
                }
            }
            if (compiler == null) {
                val match = gccRegex.find(s)
                if (match != null) {
                    compiler = match.value
                    keyFound.add(s)
                }
            }
            if (s.contains("SMP", ignoreCase = false) || s.contains("PREEMPT SMP", ignoreCase = false)) {
                isSmp = true
            }
            if (s.startsWith("CONFIG_") && s.contains("=")) {
                configs.add(s)
            }
        }

        return KernelStringResults(kernelVersion, compiler, isSmp, configs, keyFound)
    }

    private fun extractAsciiStrings(bytes: ByteArray, maxScan: Int): List<String> {
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        val limit = bytes.size.coerceAtMost(maxScan)

        for (i in 0 until limit) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                if (sb.length >= 6) {
                    strings.add(sb.toString())
                }
                sb.setLength(0)
            }
        }
        if (sb.length >= 6) strings.add(sb.toString())
        return strings
    }

    private data class KernelStringResults(
        val versionStr: String?,
        val compilerStr: String?,
        val isSmp: Boolean,
        val configs: List<String>,
        val keyFound: List<String>
    )
}
