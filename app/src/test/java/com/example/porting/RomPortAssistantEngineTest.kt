package com.example.porting

import com.example.porting.engine.RomPortAssistantEngine
import com.example.porting.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RomPortAssistantEngineTest {

    @Test
    fun testReferenceProfilesIntegrity() {
        val targetDevices = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES
        val sourceRoms = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS

        assertTrue("Reference target devices list should not be empty", targetDevices.isNotEmpty())
        assertTrue("Reference source ROMs list should not be empty", sourceRoms.isNotEmpty())

        val j2PrimeStock = targetDevices.first { it.model == "SM-G532F" }
        assertEquals(ProfileSourceType.REFERENCE_PROFILE, j2PrimeStock.source)
        assertEquals("mt6737t", j2PrimeStock.platform)
        assertEquals(1719664640L, j2PrimeStock.maxSystemPartitionBytes)
        assertFalse("J2 Prime CPU is 32-bit", j2PrimeStock.is64Bit)
    }

    @Test
    fun testLineageOS18Compatibility() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val source = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first { it.id == "source_lineageos_18_1_g532" }

        val result = RomPortAssistantEngine.analyzePortCompatibility(source, target) { _, _ -> }

        assertEquals(0, result.blockers.size)
        assertTrue("LineageOS 18.1 32-bit should be buildable", result.readiness.canProceedToBuild)
        assertTrue("Readiness score should be high (>= 70)", result.readiness.score >= 70)
        assertTrue("Pass count should be greater than 0", result.passes.isNotEmpty())

        // Verify evaluated properties include CPU ABI, System budget, and Kernel Binder
        val abiProp = result.evaluatedProperties.firstOrNull { it.key == "cpu_abi" }
        assertNotNull(abiProp)
        assertEquals(PortStatus.PASS, abiProp?.status)
    }

    @Test
    fun test64BitAndOverflowBlockersDetection() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val source64Bit = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first { it.id == "source_oneui_g570f_port" }

        val result = RomPortAssistantEngine.analyzePortCompatibility(source64Bit, target) { _, _ -> }

        assertTrue("Should contain at least 2 critical blockers", result.blockers.size >= 2)
        assertFalse("Cannot proceed to build with blockers", result.readiness.canProceedToBuild)
        assertEquals(PortStatus.BLOCKER, result.readiness.status)

        // Check ABI blocker
        val abiBlocker = result.blockers.firstOrNull { it.id == "issue_abi_64bit_blocker" }
        assertNotNull("64-bit ABI blocker must be detected", abiBlocker)
        assertTrue(abiBlocker!!.confidence >= 0.95f)

        // Check System overflow blocker
        val overflowBlocker = result.blockers.firstOrNull { it.id == "issue_system_overflow_blocker" }
        assertNotNull("System overflow blocker must be detected", overflowBlocker)
    }

    @Test
    fun testSourceTargetProvenanceIsolation() {
        val liveProfile = TargetDeviceProfile(
            id = "live_test",
            name = "Live Test Device",
            source = ProfileSourceType.LIVE_DEVICE,
            model = "SM-G532F",
            board = "grandpplte",
            platform = "mt6737t",
            cpuArch = "arm32",
            is64Bit = false,
            maxKernelVersion = "3.18.35",
            maxSystemPartitionBytes = 1719664640L,
            maxBootPartitionBytes = 16777216L,
            selinuxMode = "Enforcing",
            rootAvailable = true
        )

        val importedSource = SourceRomProfile(
            id = "imported_test",
            name = "Imported File Source",
            source = ProfileSourceType.IMPORTED_FILE,
            androidVersion = "11",
            sdkInt = 30,
            securityPatch = "2022-01-01",
            architecture = "arm (32-bit)",
            is64Bit = false,
            isTreble = false,
            systemFsType = "ext4",
            systemSizeBytes = 800000000L,
            bootImgSize = 12000000L,
            kernelCmdline = "bootopt=64S3,32N2,32N2",
            targetChipset = "MT6737T",
            buildDisplayId = "test",
            fingerprint = "test/fingerprint",
            selinuxMode = "Permissive"
        )

        // Verify distinct provenance types
        assertEquals(ProfileSourceType.LIVE_DEVICE, liveProfile.source)
        assertEquals(ProfileSourceType.IMPORTED_FILE, importedSource.source)
        assertNotEquals(liveProfile.source, importedSource.source)
    }

    @Test
    fun testMigrationCandidatesDiscovery() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val source = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first { it.id == "source_lineageos_18_1_g532" }

        val result = RomPortAssistantEngine.analyzePortCompatibility(source, target) { _, _ -> }
        val candidates = result.migrationCandidates

        assertTrue("Migration candidates must be discovered", candidates.isNotEmpty())
        assertTrue("Must discover at least 15 candidates across subsystems", candidates.size >= 15)

        // Check required fields on every candidate
        candidates.forEach { cand ->
            assertNotNull("Candidate ID must not be null", cand.id)
            assertTrue("Candidate name must not be blank", cand.name.isNotBlank())
            assertTrue("Candidate path must not be blank", cand.path.isNotBlank())
            assertTrue("Candidate source must not be blank", cand.source.isNotBlank())
            assertTrue("Candidate target must not be blank", cand.target.isNotBlank())
            assertTrue("Candidate architecture must not be blank", cand.architecture.isNotBlank())
            assertTrue("Candidate reason must not be blank", cand.reason.isNotBlank())
            assertTrue("Confidence must be between 0.0 and 1.0", cand.confidence in 0.0f..1.0f)
            assertNotNull("Candidate status must be defined", cand.status)
            assertNotNull("Risk must be defined", cand.risk)
        }

        // Verify key subsystem categories are covered
        val categories = candidates.map { it.category }.toSet()
        assertTrue("Must include LIBRARIES category", categories.contains(CandidateCategory.LIBRARIES))
        assertTrue("Must include HAL category", categories.contains(CandidateCategory.HAL))
        assertTrue("Must include CONFIGS category", categories.contains(CandidateCategory.CONFIGS))
        assertTrue("Must include INIT_SERVICES category", categories.contains(CandidateCategory.INIT_SERVICES))
        assertTrue("Must include PROPERTIES category", categories.contains(CandidateCategory.PROPERTIES))
        assertTrue("Must include DTB_NODES category", categories.contains(CandidateCategory.DTB_NODES))
        assertTrue("Must include DTBO_ENTRIES category", categories.contains(CandidateCategory.DTBO_ENTRIES))
        assertTrue("Must include FIRMWARE_REFS category", categories.contains(CandidateCategory.FIRMWARE_REFS))
        assertTrue("Must include PERMISSIONS category", categories.contains(CandidateCategory.PERMISSIONS))
        assertTrue("Must include SELINUX_CONTEXTS category", categories.contains(CandidateCategory.SELINUX_CONTEXTS))
        assertTrue("Must include SYSTEM_FILES category", categories.contains(CandidateCategory.SYSTEM_FILES))
        assertTrue("Must include VENDOR_FILES category", categories.contains(CandidateCategory.VENDOR_FILES))

        // Check statuses present
        val statuses = candidates.map { it.status }.toSet()
        assertTrue("Must contain SAFE_TO_INVESTIGATE or CANDIDATE", statuses.contains(CandidateStatus.SAFE_TO_INVESTIGATE) || statuses.contains(CandidateStatus.CANDIDATE))
    }

    @Test
    fun testStructuredPortPlanSections() = runBlocking {
        val target = RomPortAssistantEngine.REFERENCE_TARGET_DEVICES.first { it.model == "SM-G532F" }
        val source = RomPortAssistantEngine.REFERENCE_SOURCE_ROMS.first { it.id == "source_lineageos_18_1_g532" }

        val result = RomPortAssistantEngine.analyzePortCompatibility(source, target) { _, _ -> }
        val plan = result.structuredPortPlan

        assertNotNull("Structured port plan must be generated", plan)
        assertEquals("Port plan must contain all 11 architectural sections", 11, plan!!.sections.size)

        val sectionTypes = plan.sections.map { it.sectionType }.toSet()
        val expectedSections = setOf(
            PortPlanSectionType.KERNEL,
            PortPlanSectionType.BOOT,
            PortPlanSectionType.DTB,
            PortPlanSectionType.SYSTEM,
            PortPlanSectionType.VENDOR,
            PortPlanSectionType.HAL,
            PortPlanSectionType.RIL,
            PortPlanSectionType.SELINUX,
            PortPlanSectionType.PROPERTIES,
            PortPlanSectionType.INIT,
            PortPlanSectionType.PARTITIONS
        )
        assertEquals("All 11 sections must be present in the plan", expectedSections, sectionTypes)

        // Validate each section and task
        plan.sections.forEach { section ->
            assertTrue("Section ${section.sectionType} must have tasks", section.tasks.isNotEmpty())
            section.tasks.forEach { task ->
                assertTrue("Task title must not be blank", task.title.isNotBlank())
                assertTrue("Task description must not be blank", task.description.isNotBlank())
                assertNotNull("Task risk must be defined", task.risk)
                assertNotNull("Task status must be defined", task.status)
            }
        }

        assertTrue("Total tasks must be greater than 20", plan.totalTasks >= 20)
    }
}
