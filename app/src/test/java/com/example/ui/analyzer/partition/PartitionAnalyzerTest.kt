package com.example.ui.analyzer.partition

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PartitionAnalyzerTest {

    @Test
    fun testMtkScatterParsing() {
        val scatterContent = """
############################################################################################################
#
#  General Setting
#
############################################################################################################
- general: MTK_PLATFORM_CFG
  info:
    - config_version: V1.1.2
      platform: MT6737T
      project: grandpplte
      storage: EMMC
      boot_channel: MSDC_0
      block_size: 0x20000
############################################################################################################
- partition_index: SYS0
  partition_name: preloader
  file_name: preloader_grandpplte.bin
  is_download: true
  type: SV5_BL_BIN
  linear_start_addr: 0x0
  physical_start_addr: 0x0
  partition_size: 0x40000
  region: EMMC_BOOT_1
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: BOOTLOADERS
  reserve: 0x00
- partition_index: SYS1
  partition_name: boot
  file_name: boot.img
  is_download: true
  type: NORMAL_ROM
  linear_start_addr: 0x140000
  physical_start_addr: 0x140000
  partition_size: 0x2000000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
- partition_index: SYS2
  partition_name: system
  file_name: system.img
  is_download: true
  type: YAFFS_IMG
  linear_start_addr: 0x2140000
  physical_start_addr: 0x2140000
  partition_size: 0x96000000
  region: EMMC_USER
  storage: HW_STORAGE_EMMC
  boundary_check: true
  is_reserved: false
  operation_type: UPDATE
  reserve: 0x00
        """.trimIndent()

        val analyzer = PartitionTableAnalyzer()
        val result = analyzer.analyzeText(scatterContent, "MT6737T_scatter.txt")

        assertEquals(PartitionTableType.MTK_SCATTER, result.table.type)
        assertEquals("MT6737T", result.table.platformName)
        assertEquals(3, result.table.partitions.size)

        val bootPart = result.table.partitions.find { it.name == "boot" }
        assertNotNull(bootPart)
        assertEquals(0x140000L, bootPart?.startByteOffset)
        assertEquals(0x2000000L, bootPart?.sizeBytes) // 32MB
        assertEquals("32.00 MB", bootPart?.sizeFormatted)
    }

    @Test
    fun testMbrParsing() {
        val mbrBuffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        // Partition 1 at offset 446: Bootable Linux Native
        mbrBuffer.put(446, 0x80.toByte()) // bootable
        mbrBuffer.put(446 + 4, 0x83.toByte()) // Linux native
        mbrBuffer.putInt(446 + 8, 2048) // Start LBA = 2048 (1MB offset)
        mbrBuffer.putInt(446 + 12, 65536) // Sector count = 65536 (32MB)

        // Signature at 510
        mbrBuffer.putShort(510, 0xAA55.toShort())

        val result = MbrParser.parseBuffer(mbrBuffer, null, 100 * 1024 * 1024L)
        assertEquals(1, result.table.partitions.size)
        val p1 = result.table.partitions[0]
        assertTrue(p1.isBootable)
        assertEquals(2048L, p1.startLba)
        assertEquals(65536L * 512L, p1.sizeBytes)
        assertEquals("0x83", p1.typeGuidOrId)
    }

    @Test
    fun testPartitionOverlapDetection() {
        val partitions = listOf(
            PartitionEntry(index = 1, name = "part1", startLba = 100, endLba = 200, sizeBytes = 51200),
            PartitionEntry(index = 2, name = "part2", startLba = 150, endLba = 300, sizeBytes = 76800) // Overlaps with part1!
        )

        val issues = mutableListOf<PartitionIssue>()
        GptParser.checkPartitionOverlaps(partitions, issues)

        assertTrue(issues.any { it.severity == PartitionIssueSeverity.CRITICAL && it.id.contains("PARTITION_OVERLAP") })
    }

    @Test
    fun testProcPartitionsParsing() {
        val procContent = """
major minor  #blocks  name

 179        0    7634944 mmcblk0
 179        1       4096 mmcblk0p1
 179        2      32768 mmcblk0p2
        """.trimIndent()

        val parsed = ScatterParser.parseText(procContent, "proc_partitions.txt")
        assertEquals(PartitionTableType.PROC_PARTITIONS, parsed.table.type)
        assertEquals(3, parsed.table.partitions.size)
    }
}
