package com.example.ui.analyzer.kernel.studio.analyzer

import com.example.ui.analyzer.kernel.studio.models.ConfigCategory
import com.example.ui.analyzer.kernel.studio.models.ConfigState
import com.example.ui.analyzer.kernel.studio.models.ConfigType
import com.example.ui.analyzer.kernel.studio.models.KernelConfig
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

object KernelConfigAnalyzer {

    private val IKCONFIG_MAGIC = "IKCFG_ST".toByteArray(Charsets.US_ASCII)

    fun parseConfigs(bytes: ByteArray, extractedStrings: List<String> = emptyList()): List<KernelConfig> {
        val configs = mutableMapOf<String, KernelConfig>()

        // 1. Try extracting from IKCONFIG header in kernel
        val ikconfigBytes = extractIkconfig(bytes)
        if (ikconfigBytes != null) {
            parseConfigStream(ikconfigBytes, configs)
        }

        // 2. Parse from extracted strings
        for (s in extractedStrings) {
            if (s.startsWith("CONFIG_") && s.contains("=")) {
                val parsed = parseConfigLine(s)
                if (parsed != null && !configs.containsKey(parsed.name)) {
                    configs[parsed.name] = parsed
                }
            } else if (s.startsWith("# CONFIG_") && s.endsWith(" is not set")) {
                val parsed = parseConfigLine(s)
                if (parsed != null && !configs.containsKey(parsed.name)) {
                    configs[parsed.name] = parsed
                }
            }
        }

        return configs.values.sortedBy { it.name }
    }

    fun parseConfigText(text: String): List<KernelConfig> {
        val configs = mutableMapOf<String, KernelConfig>()
        text.lineSequence().forEach { line ->
            val parsed = parseConfigLine(line.trim())
            if (parsed != null) {
                configs[parsed.name] = parsed
            }
        }
        return configs.values.sortedBy { it.name }
    }

    private fun parseConfigStream(decompressed: ByteArray, map: MutableMap<String, KernelConfig>) {
        try {
            BufferedReader(InputStreamReader(ByteArrayInputStream(decompressed), Charsets.UTF_8)).useLines { lines ->
                lines.forEach { line ->
                    val parsed = parseConfigLine(line.trim())
                    if (parsed != null) {
                        map[parsed.name] = parsed
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    fun parseConfigLine(line: String): KernelConfig? {
        if (line.isBlank()) return null

        // Format: CONFIG_FOO=y or # CONFIG_FOO is not set
        if (line.startsWith("# CONFIG_") && line.endsWith(" is not set")) {
            val name = line.removePrefix("# ").removeSuffix(" is not set").trim()
            return KernelConfig(
                name = name,
                value = "is not set",
                type = ConfigType.BOOL_N,
                state = ConfigState.DISABLED,
                category = categorizeConfig(name),
                description = describeConfig(name)
            )
        }

        if (line.startsWith("CONFIG_") && line.contains("=")) {
            val parts = line.split("=", limit = 2)
            val name = parts[0].trim()
            val rawVal = parts[1].trim()
            val cleanVal = rawVal.removeSurrounding("\"")

            val (type, state) = when {
                cleanVal == "y" -> Pair(ConfigType.BOOL_Y, ConfigState.ENABLED)
                cleanVal == "m" -> Pair(ConfigType.MODULE_M, ConfigState.MODULE)
                cleanVal == "n" -> Pair(ConfigType.BOOL_N, ConfigState.DISABLED)
                cleanVal.startsWith("0x", ignoreCase = true) -> Pair(ConfigType.HEX, ConfigState.ENABLED)
                cleanVal.toLongOrNull() != null -> Pair(ConfigType.INTEGER, ConfigState.ENABLED)
                rawVal.startsWith("\"") -> Pair(ConfigType.STRING, ConfigState.ENABLED)
                else -> Pair(ConfigType.UNKNOWN, ConfigState.ENABLED)
            }

            return KernelConfig(
                name = name,
                value = cleanVal,
                type = type,
                state = state,
                category = categorizeConfig(name),
                description = describeConfig(name)
            )
        }

        return null
    }

    private fun extractIkconfig(bytes: ByteArray): ByteArray? {
        if (bytes.size < 16) return null
        val offset = findBytes(bytes, IKCONFIG_MAGIC)
        if (offset < 0) return null

        // After IKCFG_ST comes gzip data (1F 8B)
        var gzStart = offset + IKCONFIG_MAGIC.size
        while (gzStart < bytes.size - 2 && !(bytes[gzStart] == 0x1F.toByte() && bytes[gzStart + 1] == 0x8B.toByte())) {
            gzStart++
            if (gzStart - offset > 32) return null
        }

        return try {
            val stream = ByteArrayInputStream(bytes, gzStart, bytes.size - gzStart)
            GZIPInputStream(stream).use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun findBytes(haystack: ByteArray, needle: ByteArray): Int {
        val limit = haystack.size - needle.size
        for (i in 0..limit) {
            var match = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun categorizeConfig(name: String): ConfigCategory {
        return when {
            name.contains("ANDROID") || name.contains("BINDER") || name.contains("ASHMEM") || name.contains("ION") -> ConfigCategory.ANDROID
            name.contains("_FS") || name.contains("EXT4") || name.contains("F2FS") || name.contains("EROFS") || name.contains("SQUASHFS") || name.contains("TMPFS") -> ConfigCategory.FILESYSTEM
            name.contains("NET") || name.contains("INET") || name.contains("WLAN") || name.contains("BT") || name.contains("BLUETOOTH") || name.contains("WIRELESS") -> ConfigCategory.NETWORK
            name.contains("SECURITY") || name.contains("SELINUX") || name.contains("CRYPTO") || name.contains("DM_VERITY") || name.contains("TRUST") -> ConfigCategory.SECURITY
            name.contains("USB") || name.contains("GPIO") || name.contains("I2C") || name.contains("SPI") || name.contains("MMC") || name.contains("DRM") || name.contains("FB") -> ConfigCategory.HARDWARE
            name.contains("SMP") || name.contains("PREEMPT") || name.contains("ARM") || name.contains("CPU") || name.contains("ARCH") || name.contains("CGROUP") || name.contains("NAMESPACES") -> ConfigCategory.CORE
            else -> ConfigCategory.OTHER
        }
    }

    private fun describeConfig(name: String): String {
        return when (name) {
            "CONFIG_ANDROID_BINDER_IPC" -> "Android IPC Binder Driver (Essential for Android)"
            "CONFIG_ANDROID_BINDERFS" -> "Android BinderFS Virtual Filesystem (Android 10+)"
            "CONFIG_ASHMEM" -> "Android Shared Memory Driver"
            "CONFIG_ION" -> "Android ION Memory Allocator (Pre-Android 11 DMA-BUF)"
            "CONFIG_DM_VERITY" -> "Device Mapper dm-verity for Verified Boot"
            "CONFIG_SECURITY_SELINUX" -> "SELinux mandatory access control (Required for Android)"
            "CONFIG_NAMESPACES" -> "Linux Namespaces for containerization/isolation"
            "CONFIG_CGROUPS" -> "Control Groups resource limitation"
            "CONFIG_EXT4_FS" -> "EXT4 Filesystem Driver"
            "CONFIG_F2FS_FS" -> "Flash-Friendly File System (F2FS)"
            "CONFIG_EROFS_FS" -> "Enhanced Read-Only File System (Android 10+ system/vendor)"
            "CONFIG_SQUASHFS" -> "SquashFS compressed read-only filesystem"
            "CONFIG_TMPFS" -> "Virtual memory file system"
            "CONFIG_USB_GADGET" -> "USB Peripheral / Device support"
            "CONFIG_USB_CONFIGFS" -> "USB Gadget ConfigFS (Required for Android MTP/ADB)"
            "CONFIG_MODULES" -> "Loadable Kernel Module (LKM) support"
            else -> ""
        }
    }
}
