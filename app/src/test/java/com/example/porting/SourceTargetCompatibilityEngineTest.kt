package com.example.porting

import com.example.porting.engine.RomPortAssistantEngine
import com.example.porting.engine.SourceTargetCompatibilityEngine
import com.example.porting.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SourceTargetCompatibilityEngineTest {

    @Test
    fun testAll25SubsystemsEvaluated() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val source = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first { it.id == "source_lineageos_18_1_g532" }

        val result = SourceTargetCompatibilityEngine.evaluateCompatibility(source, target) { _, _ -> }

        // Must evaluate all 25 subsystems
        val expectedSubsystems = listOf(
            "DEVICE", "SOC", "CPU", "GPU", "ARCHITECTURE", "ABI",
            "ANDROID", "KERNEL", "BOOT", "DTB", "DTBO", "PARTITIONS",
            "SYSTEM", "VENDOR", "PRODUCT", "ODM", "HAL", "RIL",
            "SELINUX", "ELF", "PROPERTIES", "INIT", "FILESYSTEM", "TREBLE", "A/B"
        )

        assertEquals("Should evaluate exactly 25 subsystems", 25, result.items.size)

        expectedSubsystems.forEach { sub ->
            val item = result.items.firstOrNull { it.subsystem == sub }
            assertNotNull("Subsystem $sub must be present in compatibility matrix", item)
            assertNotNull("Subsystem $sub must have evidence", item?.evidence)
            assertTrue("Subsystem $sub confidence must be > 0", (item?.confidence ?: 0f) > 0f)
        }

        assertTrue("Overall score must be >= 70 for LineageOS 18.1", result.overallScore >= 70)
        assertTrue("Can proceed to port LineageOS 18.1", result.canProceedToPort)
        assertEquals("Blockers should be 0", 0, result.blockerCount)
    }

    @Test
    fun testARM64ConflictDetection() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val source64Bit = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first { it.id == "source_oneui_g570f_port" }

        val result = SourceTargetCompatibilityEngine.evaluateCompatibility(source64Bit, target) { _, _ -> }

        // ABI and ARCHITECTURE must be CONFLICT & BLOCKER
        val archItem = result.items.first { it.subsystem == "ARCHITECTURE" }
        assertEquals(CompatibilityStatus.CONFLICT, archItem.status)
        assertEquals(CompatibilitySeverity.BLOCKER, archItem.severity)
        assertTrue(archItem.isBlocker)

        val abiItem = result.items.first { it.subsystem == "ABI" }
        assertEquals(CompatibilityStatus.CONFLICT, abiItem.status)
        assertEquals(CompatibilitySeverity.BLOCKER, abiItem.severity)

        // ELF 64-bit binaries
        val elfItem = result.items.first { it.subsystem == "ELF" }
        assertEquals(CompatibilityStatus.CONFLICT, elfItem.status)

        assertFalse("Cannot proceed to port 64-bit ROM directly", result.canProceedToPort)
        assertTrue("Must contain blockers", result.blockerCount > 0)
    }

    @Test
    fun testPartitionOverflowConflict() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val oversizedSource = SourceRomProfile(
            id = "oversized_rom",
            name = "Oversized ROM",
            source = ProfileSourceType.PROJECT,
            androidVersion = "10",
            sdkInt = 29,
            securityPatch = "2021-01-01",
            architecture = "arm (32-bit)",
            is64Bit = false,
            isTreble = false,
            systemFsType = "ext4",
            systemSizeBytes = 2_500_000_000L, // 2.5 GB exceeds 1.6 GB
            bootImgSize = 12_000_000L,
            kernelCmdline = "bootopt=64S3,32N2,32N2",
            targetChipset = "MT6737T",
            buildDisplayId = "test",
            fingerprint = "test/fingerprint",
            selinuxMode = "Enforcing"
        )

        val result = SourceTargetCompatibilityEngine.evaluateCompatibility(oversizedSource, target) { _, _ -> }

        val partItem = result.items.first { it.subsystem == "PARTITIONS" }
        assertEquals(CompatibilityStatus.CONFLICT, partItem.status)
        assertEquals(CompatibilitySeverity.BLOCKER, partItem.severity)
        assertTrue(partItem.isBlocker)
        assertTrue("Must contain system partition overflow blocker", result.blockerCount > 0)
        assertTrue("Cannot proceed to port with oversized system partition", !result.canProceedToPort)
    }

    @Test
    fun testDifferentNotMarkedAsFatalBlocker() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val trebleSource = SourceRomProfile(
            id = "treble_rom",
            name = "AOSP 11 Treble GSI",
            source = ProfileSourceType.PROJECT,
            androidVersion = "11",
            sdkInt = 30,
            securityPatch = "2021-06-01",
            architecture = "arm (32-bit)",
            is64Bit = false,
            isTreble = true, // Treble ROM on Non-Treble target
            systemFsType = "ext4",
            systemSizeBytes = 1_200_000_000L,
            bootImgSize = 10_000_000L,
            kernelCmdline = "androidboot.selinux=permissive",
            targetChipset = "Generic MTK",
            buildDisplayId = "test",
            fingerprint = "generic/gsi",
            selinuxMode = "Permissive"
        )

        val result = SourceTargetCompatibilityEngine.evaluateCompatibility(trebleSource, target) { _, _ -> }

        val trebleItem = result.items.first { it.subsystem == "TREBLE" }
        assertEquals(CompatibilityStatus.DIFFERENT, trebleItem.status)
        assertEquals(CompatibilitySeverity.WARNING, trebleItem.severity)
        assertFalse("Treble adaptation is a warning, not a fatal blocker", trebleItem.isBlocker)

        val selinuxItem = result.items.first { it.subsystem == "SELINUX" }
        assertEquals(CompatibilityStatus.DIFFERENT, selinuxItem.status)
        assertFalse("Permissive SELinux is a warning, not a fatal blocker", selinuxItem.isBlocker)
    }

    @Test
    fun testUnknownAndroidVersionHandling() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val unknownSource = SourceRomProfile(
            id = "unknown_rom",
            name = "Unknown ROM",
            source = ProfileSourceType.IMPORTED_FILE,
            androidVersion = "UNKNOWN",
            sdkInt = -1,
            securityPatch = "UNKNOWN",
            architecture = "arm (32-bit)",
            is64Bit = false,
            isTreble = false,
            systemFsType = "ext4",
            systemSizeBytes = 1_000_000_000L,
            bootImgSize = 10_000_000L,
            kernelCmdline = "",
            targetChipset = "",
            buildDisplayId = "",
            fingerprint = "",
            selinuxMode = "UNKNOWN"
        )

        val result = SourceTargetCompatibilityEngine.evaluateCompatibility(unknownSource, target) { _, _ -> }

        val androidItem = result.items.first { it.subsystem == "ANDROID" }
        assertEquals(CompatibilityStatus.UNKNOWN, androidItem.status)
        assertEquals("UNKNOWN", androidItem.sourceValue)
        assertTrue(result.unknownCount >= 1)
    }
}
