package com.example.ui.analyzer.boot

import com.example.ui.analyzer.rom.RomCompatibilityAnalyzer
import com.example.ui.analyzer.rom.RomStructureAnalyzer
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BootAnalyzerEngineTest {

    @Test
    fun testValidBootHeaderV0Parsing() {
        val headerBytes = ByteArray(2048)
        val buf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Magic: "ANDROID!"
        val magic = "ANDROID!".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, headerBytes, 0, 8)

        buf.position(8)
        buf.putInt(5000000) // kernel size
        buf.putInt(0x80008000.toInt()) // kernel addr
        buf.putInt(1500000) // ramdisk size
        buf.putInt(0x81000000.toInt()) // ramdisk addr
        buf.putInt(0) // second size
        buf.putInt(0) // second addr
        buf.putInt(0x80000100.toInt()) // tags addr
        buf.putInt(2048) // page size
        buf.putInt(0) // header_version
        buf.putInt(0x05800000) // os version (Android 11)

        val header = BootHeaderParser.parseHeaderBytes(headerBytes, 2048L)

        assertTrue(header.isValid)
        assertEquals(0, header.headerVersion)
        assertEquals("ANDROID!", header.magic)
        assertEquals(5000000L, header.kernelSize)
        assertEquals(1500000L, header.ramdiskSize)
        assertEquals(2048, header.pageSize)
    }

    @Test
    fun testInvalidBootHeaderParsing() {
        val invalidBytes = ByteArray(1024)
        val header = BootHeaderParser.parseHeaderBytes(invalidBytes, 1024L)

        assertFalse(header.isValid)
        assertEquals(-1, header.headerVersion)
        assertEquals("INVALID", header.magic)
    }

    @Test
    fun testInitRcParserActionsAndServices() {
        val script = """
            on early-init
                setprop ro.boot.early 1
                mkdir /mnt 0755 root system

            on boot
                mount ext4 /dev/block/bootdevice/by-name/system /system ro

            service surfaceflinger /system/bin/surfaceflinger
                class core
                user system
                group graphics
                critical
                onrestart restart zygote

            service vendor.ril-daemon /vendor/bin/hw/rild
                class main
                user radio
                group radio
                disabled
        """.trimIndent()

        val result = InitRcParser.parse(script, "test_init.rc")

        assertEquals(2, result.stagesFound.size)
        assertTrue(result.stagesFound.containsKey("EARLY_INIT"))
        assertTrue(result.stagesFound.containsKey("BOOT"))
        assertEquals("1", result.setProps["ro.boot.early"])
        assertEquals(2, result.services.size)

        val sf = result.services.first { it.name == "surfaceflinger" }
        assertEquals("/system/bin/surfaceflinger", sf.binaryPath)
        assertEquals("core", sf.className)
        assertEquals("system", sf.user)
        assertTrue(sf.isCritical)

        val ril = result.services.first { it.name == "vendor.ril-daemon" }
        assertEquals("/vendor/bin/hw/rild", ril.binaryPath)
        assertTrue(ril.isDisabled)
    }

    @Test
    fun testFstabParserMandatoryMounts() {
        val fstabContent = """
            # Android fstab file
            /dev/block/platform/mtk-msdc.0/by-name/system /system ext4 ro,barrier=1 wait
            /dev/block/platform/mtk-msdc.0/by-name/userdata /data ext4 noatime,nosuid wait,check,formattable
            /dev/block/platform/mtk-msdc.0/by-name/cache /cache ext4 noatime,nosuid wait,check
        """.trimIndent()

        val res = FstabParser.parse(fstabContent, "fstab.mt6737t")

        assertEquals(3, res.entries.size)
        assertTrue(res.missingMandatoryPartitions.isEmpty())
        assertEquals("/system", res.entries[0].mountTarget)
        assertEquals("ext4", res.entries[0].filesystem)
    }

    @Test
    fun testTrebleAndABDetection() {
        val props = mapOf(
            "ro.treble.enabled" to "true",
            "ro.vndk.version" to "30",
            "ro.build.ab_update" to "true"
        )
        val files = listOf("system/bin/init", "vendor/build.prop", "vendor/etc/vintf/manifest.xml")

        val treble = RomStructureAnalyzer.detectTreble(true, props, files)
        assertTrue(treble.isTreble)
        assertEquals("HIGH", treble.confidence)

        val ab = RomStructureAnalyzer.detectAbSlots(props, listOf("boot_a", "boot_b", "system_a", "system_b"), files)
        assertTrue(ab.isAb)
    }

    @Test
    fun testArchitectureCheck() {
        val archPure32 = RomStructureAnalyzer.analyzeArchitecture("ARM", "ARM32", "ARM32", "ARM32")
        assertEquals("ARM32", archPure32.overallArch)
        assertTrue(archPure32.isConsistent)

        val archMixed = RomStructureAnalyzer.analyzeArchitecture("ARM64", "ARM32", "ARM64", "ARM32")
        assertEquals("MIXED", archMixed.overallArch)
        assertFalse(archMixed.isConsistent)
    }

    @Test
    fun testBootStageDetectorAndIssues() {
        val header = BootHeaderInfo(
            isValid = true,
            headerVersion = 0,
            magic = "ANDROID!",
            kernelSize = 4000000,
            kernelLoadAddr = 0,
            ramdiskSize = 1000000,
            ramdiskLoadAddr = 0,
            secondStageSize = 0,
            secondStageLoadAddr = 0,
            tagsLoadAddr = 0,
            pageSize = 2048,
            headerSize = 2048,
            osVersionRaw = 0,
            osVersionString = "11.0",
            osPatchLevelString = "2021-08",
            boardName = "mt6737t",
            cmdline = "bootopt=64S3,32N2,32N2",
            extraCmdline = "",
            recoveryDtboSize = 0,
            recoveryDtboOffset = 0,
            dtbSize = 0,
            dtbLoadAddr = 0,
            signatureSha = "",
            kernelOffset = 2048,
            ramdiskOffset = 4002048,
            secondOffset = 0,
            tagsOffset = 0
        )

        val kernel = KernelDetailsInfo(
            detectedFormat = "raw_arm32_zImage",
            detectedArch = "ARM",
            kernelVersionString = "Linux version 3.18.140",
            compilerString = "gcc version 4.9",
            isSmp = true,
            kernelConfigCount = 120,
            kernelSize = 12000000
        )

        val issues = BootIssueDetector.detectAllIssues(
            header = header,
            kernel = kernel,
            ramdisk = null,
            init = null,
            fstab = null,
            treble = null,
            ab = null,
            arch = RomStructureAnalyzer.analyzeArchitecture("ARM", "ARM", "ARM", "ARM"),
            versions = RomStructureAnalyzer.analyzeVersions("11.0", emptyMap(), emptyMap()),
            vendor = null
        )

        val stages = BootStageDetector.evaluateStages(
            header = header,
            kernel = kernel,
            ramdisk = null,
            init = null,
            fstab = null,
            vendor = null,
            allIssues = issues
        )

        assertEquals(BootStageStatus.PASS, stages.stageMap[BootStage.BOOTLOADER]?.status)
        assertEquals(BootStageStatus.PASS, stages.stageMap[BootStage.KERNEL]?.status)
    }
}
