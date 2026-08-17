package com.example.ui.analyzer.flash

import com.example.ui.analyzer.image.ImageFormat
import com.example.ui.analyzer.partition.PartitionEntry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FlashPrecheckTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testImageMatcherFuzzy() {
        val bootFile = tempFolder.newFile("boot_lineage_18.1_g532f.img").apply { writeBytes(ByteArray(1024)) }
        val systemFile = tempFolder.newFile("system.img.ext4").apply { writeBytes(ByteArray(1024)) }
        val recoveryFile = tempFolder.newFile("twrp-3.7.0_9-0-g532f.img").apply { writeBytes(ByteArray(1024)) }

        val partitions = listOf(
            PartitionEntry(index = 1, name = "boot", sizeBytes = 32L * 1024 * 1024),
            PartitionEntry(index = 2, name = "recovery", sizeBytes = 32L * 1024 * 1024),
            PartitionEntry(index = 3, name = "system", sizeBytes = 2400L * 1024 * 1024)
        )

        val matches = FlashImageMatcher.matchFilesToPartitions(
            listOf(bootFile, systemFile, recoveryFile),
            partitions,
            DeviceProfile.GALAXY_J2_PRIME
        )

        assertEquals(3, matches.size)
        assertNotNull(matches["boot"])
        assertNotNull(matches["recovery"])
        assertNotNull(matches["system"])
    }

    @Test
    fun testSizeOverflowRiskDetection() {
        val oversizedFile = tempFolder.newFile("boot.img")
        oversizedFile.writeBytes(ByteArray(40 * 1024 * 1024)) // 40MB image

        val bootPartition = PartitionEntry(index = 1, name = "boot", sizeBytes = 32L * 1024 * 1024) // 32MB max
        val matched = FlashImageMatcher.MatchedImage(
            partitionName = "boot",
            file = oversizedFile,
            format = ImageFormat.RAW,
            sizeBytes = oversizedFile.length(),
            confidence = 100
        )

        val assessment = FlashRiskAnalyzer.assessPartition(bootPartition, matched, DeviceProfile.GALAXY_J2_PRIME)
        assertFalse(assessment.isSizeValid)
        assertEquals(FlashRiskLevel.CRITICAL_BRICK, assessment.riskLevel)
        assertEquals(FlashAction.WARNING_OVERWRITE, assessment.action)
        assertTrue(assessment.issues.any { it.id.contains("FLASH_SIZE_OVERFLOW") })
    }

    @Test
    fun testProtectedPartitionNvramSafeguard() {
        val nvramFile = tempFolder.newFile("nvram.img")
        nvramFile.writeBytes(ByteArray(2 * 1024 * 1024))

        val nvramPart = PartitionEntry(index = 7, name = "nvram", sizeBytes = 5L * 1024 * 1024)
        val matched = FlashImageMatcher.MatchedImage(
            partitionName = "nvram",
            file = nvramFile,
            format = ImageFormat.RAW,
            sizeBytes = nvramFile.length(),
            confidence = 100
        )

        val assessment = FlashRiskAnalyzer.assessPartition(nvramPart, matched, DeviceProfile.GALAXY_J2_PRIME)
        assertEquals(FlashAction.PROTECT, assessment.action)
        assertEquals(FlashRiskLevel.CRITICAL_BRICK, assessment.riskLevel)
        assertTrue(assessment.issues.any { it.id.contains("FLASH_OVERWRITE_PROTECTED") })
    }

    @Test
    fun testPrecheckAnalyzerEndToEnd() {
        val bootFile = tempFolder.newFile("boot.img")
        bootFile.writeBytes(ByteArray(12 * 1024 * 1024)) // 12MB

        val analyzer = FlashPrecheckAnalyzer()
        val result = analyzer.performPrecheck(
            partitionTable = null,
            imageFiles = listOf(bootFile),
            targetProfile = DeviceProfile.GALAXY_J2_PRIME
        )

        assertNotNull(result)
        assertEquals(1, result.plan.totalImagesToFlash)
        assertTrue(result.preFlashChecklist.isNotEmpty())
    }
}
