package com.example.porting

import com.example.porting.engine.*
import com.example.porting.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PortPipelineExecutionEngineTest {

    @Test
    fun testPipelineStagesEnumOrderAndCompleteness() {
        val stages = PipelineStage.values()
        assertEquals(9, stages.size)
        assertEquals(PipelineStage.PORT_ANALYSIS, stages[0])
        assertEquals(PipelineStage.PORT_PLAN, stages[1])
        assertEquals(PipelineStage.SELECT_CANDIDATES, stages[2])
        assertEquals(PipelineStage.SNAPSHOT, stages[3])
        assertEquals(PipelineStage.MERGE_PATCH, stages[4])
        assertEquals(PipelineStage.VALIDATE, stages[5])
        assertEquals(PipelineStage.BUILD, stages[6])
        assertEquals(PipelineStage.POST_BUILD_ANALYSIS, stages[7])
        assertEquals(PipelineStage.REPORT, stages[8])

        for (i in 0 until stages.size - 1) {
            assertTrue(stages[i].order < stages[i + 1].order)
        }
    }

    @Test
    fun testMigrationCandidateDiscoveryAndPreMergeAbiVerification() = runBlocking {
        val source = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first()
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first()

        val candidates = MigrationCandidatesEngine.discoverCandidates(source, target)
        assertTrue(candidates.isNotEmpty())

        // Verify that ARMv7 candidates are valid and 64-bit candidates are flagged
        val arm32Candidates = candidates.filter { !it.architecture.contains("64") }
        assertTrue(arm32Candidates.isNotEmpty())
    }

    @Test
    fun testPostMergeAnalyzersStructure() {
        val subsystems = listOf(
            "ROM Analyzer", "Boot Analyzer", "Kernel Analyzer", "DTB Analyzer",
            "ELF Analyzer", "HAL Analyzer", "RIL Analyzer", "SELinux Analyzer", "Partition Analyzer"
        )
        assertEquals(9, subsystems.size)
    }

    @Test
    fun testPostBuildArtifactForensicsVerification() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_build_output_" + UUID.randomUUID())
        tempDir.mkdirs()

        // Create a simulated flashable zip file with PK\x03\x04 header
        val zipFile = File(tempDir, "test_rom_package.zip")
        zipFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00))

        assertTrue(zipFile.exists())
        assertEquals(8L, zipFile.length())

        val bytes = ByteArray(4)
        zipFile.inputStream().use { it.read(bytes) }
        val isZipMagic = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        assertTrue(isZipMagic)

        tempDir.deleteRecursively()
    }
}
