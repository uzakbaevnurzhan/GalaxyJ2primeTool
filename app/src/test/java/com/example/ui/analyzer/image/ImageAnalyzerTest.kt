package com.example.ui.analyzer.image

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ImageAnalyzerTest {

    @Test
    fun testSparseFormatDetection() {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, ImageFormatDetector.SPARSE_MAGIC)
        buffer.position(0)
        val format = ImageFormatDetector.detectFromBuffer(buffer)
        assertEquals(ImageFormat.SPARSE, format)
    }

    @Test
    fun testExt4FormatDetection() {
        val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(1080, ImageFormatDetector.EXT4_MAGIC.toShort())
        buffer.position(0)
        val format = ImageFormatDetector.detectFromBuffer(buffer)
        assertEquals(ImageFormat.EXT4, format)
    }

    @Test
    fun testErofsFormatDetection() {
        val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1024, ImageFormatDetector.EROFS_MAGIC)
        buffer.position(0)
        val format = ImageFormatDetector.detectFromBuffer(buffer)
        assertEquals(ImageFormat.EROFS, format)
    }

    @Test
    fun testF2fsFormatDetection() {
        val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(1024, ImageFormatDetector.F2FS_MAGIC)
        buffer.position(0)
        val format = ImageFormatDetector.detectFromBuffer(buffer)
        assertEquals(ImageFormat.F2FS, format)
    }

    @Test
    fun testSquashFsFormatDetection() {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, ImageFormatDetector.SQUASHFS_MAGIC_LE)
        buffer.position(0)
        val format = ImageFormatDetector.detectFromBuffer(buffer)
        assertEquals(ImageFormat.SQUASHFS, format)
    }

    @Test
    fun testExt4SuperblockParsing() {
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x00, 1000) // inodes count
        buffer.putInt(0x04, 50000) // blocks count
        buffer.putInt(0x0C, 20000) // free blocks count
        buffer.putInt(0x10, 800) // free inodes count
        buffer.putInt(0x18, 2) // log block size (4096 bytes)
        buffer.putShort(0x38, ImageFormatDetector.EXT4_MAGIC.toShort()) // magic
        buffer.putShort(0x3A, 1.toShort()) // state clean
        buffer.putShort(0x58, 256.toShort()) // inode size
        buffer.putInt(0x60, Ext4Analyzer.INCOMPAT_EXTENTS or Ext4Analyzer.INCOMPAT_64BIT)

        val meta = Ext4Analyzer.parseSuperblockBuffer(buffer, 50000L * 4096L)
        assertNotNull(meta)
        assertEquals(ImageFormat.EXT4, meta!!.format)
        assertEquals(4096, meta.blockSize)
        assertEquals(50000L, meta.totalBlocks)
        assertEquals(20000L, meta.freeBlocks)
        assertEquals(30000L, meta.usedBlocks)
        assertTrue(meta.features.contains("extents"))
        assertTrue(meta.features.contains("64bit"))
    }

    @Test
    fun testErofsSuperblockParsing() {
        val buffer = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x00, ImageFormatDetector.EROFS_MAGIC)
        buffer.put(0x0C, 12.toByte()) // 1 << 12 = 4096 block size
        buffer.putLong(0x10, 500L) // inodes
        buffer.putInt(0x24, 10000) // blocks

        val meta = ErofsAnalyzer.parseSuperblockBuffer(buffer, 10000L * 4096L)
        assertNotNull(meta)
        assertEquals(ImageFormat.EROFS, meta!!.format)
        assertEquals(4096, meta.blockSize)
        assertEquals(10000L, meta.totalBlocks)
        assertEquals(500L, meta.inodeCount)
        assertTrue(meta.isReadOnly)
    }

    @Test
    fun testIssueDetection64BitMismatch() {
        val meta = ImageMetadata(
            fileName = "system.img",
            format = ImageFormat.EXT4,
            blockSize = 4096,
            totalBlocks = 100000L,
            freeBlocks = 50000L
        )
        val props = mapOf(
            "ro.product.cpu.abi" to "armeabi-v7a",
            "ro.build.version.sdk" to "30"
        )
        val elfArchs = setOf("AArch64")

        val issues = AndroidImageAnalyzer.analyzeIssues(meta, emptyList(), props, elfArchs)
        val mismatchIssue = issues.firstOrNull { it.id == "ABI_64BIT_MISMATCH" }
        assertNotNull(mismatchIssue)
        assertEquals(IssueSeverity.CRITICAL, mismatchIssue!!.severity)
    }

    @Test
    fun testReportExportFormats() {
        val meta = ImageMetadata(
            fileName = "system.img",
            fileSize = 104857600L,
            format = ImageFormat.EXT4,
            blockSize = 4096,
            totalBlocks = 25600L,
            uncompressedSize = 104857600L,
            filesystemType = "EXT4",
            volumeName = "system",
            uuid = "12345678-1234-1234-1234-123456789abc",
            md5Hash = "d41d8cd98f00b204e9800998ecf8427e",
            sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
        val part = ImagePartition(
            name = "system",
            sizeBytes = 104857600L,
            filesystem = ImageFormat.EXT4
        )
        val issue = ImageIssue(
            id = "TEST_ISSUE",
            severity = IssueSeverity.WARNING,
            title = "Test Warning",
            description = "Test Description",
            recommendation = "Test Recommendation",
            affectedPartition = "system"
        )
        val result = ImageAnalysisResult(
            metadata = meta,
            partitions = listOf(part),
            issues = listOf(issue)
        )

        val md = ImageReportExporter.exportToMarkdown(result)
        assertTrue(md.contains("Android ROM Image Analysis Report"))
        assertTrue(md.contains("system.img"))
        assertTrue(md.contains("EXT4 Filesystem"))

        val json = ImageReportExporter.exportToJson(result)
        assertTrue(json.contains("\"fileName\": \"system.img\""))
        assertTrue(json.contains("\"TEST_ISSUE\""))

        val txt = ImageReportExporter.exportToTxt(result)
        assertTrue(txt.contains("ANDROID ROM IMAGE ANALYSIS REPORT"))

        val csv = ImageReportExporter.exportToCsv(result)
        assertTrue(csv.contains("METADATA,FileName,\"system.img\""))
    }
}
