package com.example.ui.analyzer.flash

import com.example.ui.analyzer.image.ImageFormat
import com.example.ui.analyzer.image.ImageFormatDetector
import com.example.ui.analyzer.partition.PartitionEntry
import java.io.File

object FlashImageMatcher {

    data class MatchedImage(
        val partitionName: String,
        val file: File,
        val format: ImageFormat,
        val sizeBytes: Long,
        val confidence: Int
    )

    fun matchFilesToPartitions(
        files: List<File>,
        partitions: List<PartitionEntry>,
        profile: DeviceProfile = DeviceProfile.GALAXY_J2_PRIME
    ): Map<String, MatchedImage> {
        val matches = mutableMapOf<String, MatchedImage>()
        val partitionMap = partitions.associateBy { it.name.lowercase() }

        for (file in files) {
            if (!file.exists() || file.isDirectory || file.length() == 0L) continue

            val format = try {
                ImageFormatDetector.detectFromFile(file)
            } catch (e: Exception) {
                ImageFormat.UNKNOWN
            }

            val rawName = file.name
            val baseName = sanitizeFileName(rawName)

            // Direct partition match
            var targetPartitionName = ""
            var confidence = 0

            val exactMatch = partitionMap.keys.find { it.equals(baseName, ignoreCase = true) }
            if (exactMatch != null) {
                targetPartitionName = exactMatch
                confidence = 100
            } else {
                // Fuzzy heuristics
                val fuzzy = findFuzzyMatch(baseName, partitionMap.keys, format)
                if (fuzzy != null) {
                    targetPartitionName = fuzzy.first
                    confidence = fuzzy.second
                }
            }

            if (targetPartitionName.isNotEmpty() && confidence >= 50) {
                val existing = matches[targetPartitionName]
                if (existing == null || existing.confidence < confidence) {
                    matches[targetPartitionName] = MatchedImage(
                        partitionName = targetPartitionName,
                        file = file,
                        format = format,
                        sizeBytes = file.length(),
                        confidence = confidence
                    )
                }
            }
        }

        return matches
    }

    fun sanitizeFileName(name: String): String {
        var base = name.lowercase()
        // Strip extensions
        val extensionsToRemove = listOf(
            ".img.ext4", ".ext4", ".img.erofs", ".erofs", ".img.f2fs", ".f2fs", ".new.dat.br", ".new.dat", ".dat.br", ".dat",
            ".img", ".bin", ".tar", ".md5", ".lz4", ".gz", ".xz"
        )
        for (ext in extensionsToRemove) {
            if (base.endsWith(ext)) {
                base = base.substring(0, base.length - ext.length)
                break
            }
        }
        // Remove common prefixes/suffixes
        base = base.replace(Regex("^(signed_|official_|custom_|twrp_|orangefox_|stock_|port_)"), "")
        base = base.replace(Regex("(_a|_b|_g532f|_g532g|_g532m|_arm|_arm64|_raw)$"), "")
        return base.trim()
    }

    private fun findFuzzyMatch(
        baseName: String,
        partitionNames: Set<String>,
        format: ImageFormat
    ): Pair<String, Int>? {
        for (p in partitionNames) {
            if (baseName == p) return Pair(p, 100)
            if (baseName.contains(p) || p.contains(baseName)) {
                val confidence = when {
                    baseName.startsWith(p) || baseName.endsWith(p) -> 90
                    else -> 70
                }
                return Pair(p, confidence)
            }
        }

        // Special aliases
        return when {
            baseName.contains("boot") && partitionNames.contains("boot") -> Pair("boot", 85)
            baseName.contains("recovery") && partitionNames.contains("recovery") -> Pair("recovery", 85)
            baseName.contains("twrp") && partitionNames.contains("recovery") -> Pair("recovery", 90)
            baseName.contains("system") && partitionNames.contains("system") -> Pair("system", 85)
            baseName.contains("vendor") && partitionNames.contains("vendor") -> Pair("vendor", 85)
            baseName.contains("preloader") && partitionNames.contains("preloader") -> Pair("preloader", 95)
            baseName.contains("lk") && partitionNames.contains("lk") -> Pair("lk", 85)
            baseName.contains("uboot") && partitionNames.contains("lk") -> Pair("lk", 80)
            baseName.contains("trustzone") && partitionNames.contains("tee1") -> Pair("tee1", 75)
            baseName.contains("logo") && partitionNames.contains("logo") -> Pair("logo", 90)
            baseName.contains("super") && partitionNames.contains("super") -> Pair("super", 90)
            baseName.contains("modem") && partitionNames.contains("md1rom") -> Pair("md1rom", 80)
            else -> null
        }
    }
}
