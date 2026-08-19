package com.example.porting

import com.example.porting.engine.SourceRomAnalyzerEngine
import com.example.porting.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceRomAnalyzerEngineTest {

    @Test
    fun testSynthesizeProfileWithCompleteMetadata() = runBlocking {
        val props = mapOf(
            "ro.product.model" to "SM-G532F",
            "ro.product.device" to "grandpplte",
            "ro.product.brand" to "samsung",
            "ro.product.manufacturer" to "samsung",
            "ro.build.version.release" to "11.0",
            "ro.build.version.sdk" to "30",
            "ro.product.cpu.abi" to "armeabi-v7a",
            "ro.treble.enabled" to "false",
            "ro.board.platform" to "mt6737t",
            "ro.build.display.id" to "lineage-18.1-test",
            "ro.build.version.security_patch" to "2022-04-05"
        )

        val partitions = listOf(
            PartitionInfo("system", "system.img", 900 * 1024 * 1024L, "ext4"),
            PartitionInfo("boot", "boot.img", 12 * 1024 * 1024L, "android_boot_v1")
        )

        val profile = SourceRomAnalyzerEngine.synthesizeProfile(
            id = "test_profile",
            name = "Test LineageOS 18.1",
            source = ProfileSourceType.IMPORTED_FILE,
            props = props,
            partitions = partitions,
            bootSize = 12 * 1024 * 1024L,
            totalSystemSize = 900 * 1024 * 1024L,
            elf32Count = 50,
            elf64Count = 0,
            sample32Bit = listOf("libc.so"),
            sample64Bit = emptyList(),
            halServices = listOf("android.hardware.camera.provider@2.4-service"),
            hasDtbo = false,
            dtbSize = 0L,
            hasPlatSepolicy = true,
            hasVendorSepolicy = false,
            fileContextsCount = 120,
            entryNames = listOf("system/build.prop", "boot.img"),
            fileSize = 912 * 1024 * 1024L,
            originPath = "test.zip"
        )

        assertEquals("SM-G532F", profile.model)
        assertEquals("grandpplte", profile.device)
        assertEquals("samsung", profile.brand)
        assertEquals("11.0", profile.androidVersion)
        assertEquals(30, profile.sdkInt)
        assertFalse(profile.is64Bit)
        assertFalse(profile.isTreble)
        assertFalse(profile.isAb)
        assertEquals(2, profile.partitions.size)
        assertEquals(0, profile.sourceIssues.size) // No 64-bit, no overflow

        // Verify audited fields contain model, android, sdk, abi
        val modelAudit = profile.auditedFields.firstOrNull { it.fieldKey == "model" }
        assertNotNull(modelAudit)
        assertEquals("SM-G532F", modelAudit?.value)
        assertEquals(0.98f, modelAudit?.confidence ?: 0f, 0.01f)
        assertFalse(modelAudit!!.isUnknown)
    }

    @Test
    fun testSynthesizeProfileWithUnknownAndroidVersion() = runBlocking {
        val emptyProps = emptyMap<String, String>()

        val profile = SourceRomAnalyzerEngine.synthesizeProfile(
            id = "unknown_test",
            name = "Raw Image Dump",
            source = ProfileSourceType.SINGLE_IMAGE,
            props = emptyProps,
            partitions = listOf(PartitionInfo("system", "system.img", 500 * 1024 * 1024L, "ext4")),
            totalSystemSize = 500 * 1024 * 1024L
        )

        // Strict Requirement: If Android version not found -> UNKNOWN
        assertEquals("UNKNOWN", profile.androidVersion)
        assertEquals(-1, profile.sdkInt)
        assertEquals("UNKNOWN", profile.model)
        assertEquals("UNKNOWN", profile.device)

        // Check unknown fields list
        assertTrue("Unknown fields list should track missing Android version", profile.unknownFieldsList.any { it.fieldKey == "android_version" })
        assertTrue("Unknown fields list should track missing model", profile.unknownFieldsList.any { it.fieldKey == "model" })
    }

    @Test
    fun testSynthesizeProfileDetects64BitAndSizeBlockers() = runBlocking {
        val props = mapOf(
            "ro.product.model" to "SM-G570F",
            "ro.product.cpu.abi" to "arm64-v8a",
            "ro.build.version.release" to "9.0",
            "ro.build.version.sdk" to "28"
        )

        val largePartitions = listOf(
            PartitionInfo("system", "system.img", 2000 * 1024 * 1024L, "ext4") // 2.0GB > 1.6GB
        )

        val profile = SourceRomAnalyzerEngine.synthesizeProfile(
            id = "64bit_test",
            name = "64-bit OneUI Base",
            source = ProfileSourceType.IMPORTED_FILE,
            props = props,
            partitions = largePartitions,
            totalSystemSize = 2000 * 1024 * 1024L,
            elf32Count = 20,
            elf64Count = 80,
            sample32Bit = listOf("system/lib/libc.so"),
            sample64Bit = listOf("system/lib64/libc.so"),
            entryNames = listOf("system/lib64/libc.so", "system/build.prop")
        )

        assertTrue(profile.is64Bit)
        assertEquals(2, profile.sourceIssues.size)

        val abiIssue = profile.sourceIssues.firstOrNull { it.id == "issue_source_64bit_blobs" }
        assertNotNull("Must detect 64-bit blobs blocker", abiIssue)
        assertEquals(PortStatus.BLOCKER, abiIssue?.status)

        val sizeIssue = profile.sourceIssues.firstOrNull { it.id == "issue_source_system_overflow" }
        assertNotNull("Must detect system partition overflow blocker", sizeIssue)
        assertEquals(PortStatus.BLOCKER, sizeIssue?.status)
    }

    @Test
    fun testDirectoryAnalysis() = runBlocking {
        // Create temporary folder structure simulating a ROM
        val tempDir = File.createTempFile("rom_folder_test", "").apply {
            delete()
            mkdirs()
        }

        try {
            val systemDir = File(tempDir, "system").apply { mkdirs() }
            val buildProp = File(systemDir, "build.prop")
            buildProp.writeText("""
                ro.product.model=SM-G532F
                ro.product.brand=samsung
                ro.product.device=grandpplte
                ro.build.version.release=8.1.0
                ro.build.version.sdk=27
                ro.product.cpu.abi=armeabi-v7a
                ro.board.platform=mt6737t
            """.trimIndent())

            val dummyBoot = File(tempDir, "boot.img")
            dummyBoot.writeBytes(ByteArray(2048) { 0 })

            val profile = SourceRomAnalyzerEngine.analyzeFromFolder(tempDir) { _, _ -> }

            assertEquals("SM-G532F", profile.model)
            assertEquals("8.1.0", profile.androidVersion)
            assertEquals(27, profile.sdkInt)
            assertEquals(ProfileSourceType.ROM_FOLDER, profile.source)
            assertFalse(profile.is64Bit)
            assertTrue("Should detect boot.img and system", profile.partitions.any { it.name == "boot" || it.name == "system" })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
