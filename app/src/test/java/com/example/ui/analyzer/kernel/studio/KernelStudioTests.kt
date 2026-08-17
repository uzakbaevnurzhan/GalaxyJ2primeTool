package com.example.ui.analyzer.kernel.studio

import com.example.ui.analyzer.kernel.studio.analyzer.*
import com.example.ui.analyzer.kernel.studio.compatibility.KernelCompatibilityAnalyzer
import com.example.ui.analyzer.kernel.studio.compatibility.KernelHardwareAnalyzer
import com.example.ui.analyzer.kernel.studio.dtb.*
import com.example.ui.analyzer.kernel.studio.models.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

class KernelStudioTests {

    @Test
    fun testKernelFormatDetector_ARM64Image() {
        val bytes = ByteArray(128)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(16)
        buf.putLong(65536)
        buf.position(56)
        buf.putInt(0x644d5241) // 'ARM\x64'

        val format = KernelFormatDetector.detect(bytes)
        assertEquals("Image", format.format)
        assertEquals("none", format.compression)
        assertEquals("ARM64", format.architecture)
    }

    @Test
    fun testKernelFormatDetector_ARM32ZImage() {
        val bytes = ByteArray(64)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(28)
        buf.putInt(0x00000000)
        buf.putInt(0x00100000)
        buf.putInt(0x016f2818) // zImage magic

        val format = KernelFormatDetector.detect(bytes)
        assertEquals("zImage", format.format)
        assertEquals("ARM32", format.architecture)
    }

    @Test
    fun testKernelFormatDetector_Gzip() {
        val bytes = byteArrayOf(0x1F.toByte(), 0x8B.toByte(), 0x08, 0x00)
        val format = KernelFormatDetector.detect(bytes)
        assertEquals("Image.gz", format.format)
        assertEquals("gzip", format.compression)
    }

    @Test
    fun testKernelVersionParser() {
        val versionStr = "Linux version 3.18.140-grandpplte-perf (root@build-host) (gcc version 4.9.x) #1 SMP PREEMPT Thu Jan 1 00:00:00 UTC 2021"
        val info = KernelVersionParser.parse(versionStr)

        assertEquals(3, info.major)
        assertEquals(18, info.minor)
        assertEquals(140, info.patch)
        assertTrue(info.extraVersion.contains("grandpplte"))
    }

    @Test
    fun testKernelStringAnalyzer() {
        val text = "Linux version 4.19.113-android11 (user@host) (clang version 10.0.7) #1 SMP PREEMPT\nCONFIG_ANDROID_BINDER_IPC=y\nCONFIG_SECURITY_SELINUX=y\n"
        val bytes = text.toByteArray(Charsets.US_ASCII)

        val analysis = KernelStringAnalyzer.analyze(bytes)
        assertEquals(4, analysis.versionInfo.major)
        assertEquals(19, analysis.versionInfo.minor)
        assertEquals("Clang", analysis.compiler)
        assertTrue(analysis.isSmp)
        assertTrue(analysis.isPreempt)
    }

    @Test
    fun testKernelConfigAnalyzer() {
        val configText = """
            CONFIG_ANDROID_BINDER_IPC=y
            CONFIG_ANDROID_BINDERFS=y
            CONFIG_EXT4_FS=y
            CONFIG_F2FS_FS=m
            # CONFIG_EROFS_FS is not set
            CONFIG_MAX_CPUS=8
        """.trimIndent()

        val configs = KernelConfigAnalyzer.parseConfigText(configText)
        assertEquals(6, configs.size)

        val binder = configs.first { it.name == "CONFIG_ANDROID_BINDER_IPC" }
        assertEquals(ConfigState.ENABLED, binder.state)
        assertEquals(ConfigCategory.ANDROID, binder.category)

        val f2fs = configs.first { it.name == "CONFIG_F2FS_FS" }
        assertEquals(ConfigState.MODULE, f2fs.state)

        val erofs = configs.first { it.name == "CONFIG_EROFS_FS" }
        assertEquals(ConfigState.DISABLED, erofs.state)
    }

    @Test
    fun testKernelCmdlineAnalyzer() {
        val cmdline = "console=ttyMT0,921600n1 root=/dev/ram0 androidboot.selinux=permissive androidboot.hardware=mt6737t loglevel=8"
        val entries = KernelCmdlineAnalyzer.parse(cmdline)

        assertEquals(5, entries.size)
        val selinux = entries.first { it.key == "androidboot.selinux" }
        assertEquals("permissive", selinux.value)
        assertEquals(CmdlineCategory.SELINUX, selinux.category)

        val console = entries.first { it.key == "console" }
        assertEquals(CmdlineCategory.CONSOLE, console.category)
    }

    @Test
    fun testDtbHeaderAndNodeParser() {
        // Construct a synthetic FDT buffer
        val baos = ByteArrayOutputStream()
        val strStream = ByteArrayOutputStream()

        // Write Strings block: "compatible\u0000model\u0000"
        val compatOffset = strStream.size()
        strStream.write("compatible\u0000".toByteArray(Charsets.UTF_8))
        val modelOffset = strStream.size()
        strStream.write("model\u0000".toByteArray(Charsets.UTF_8))
        val strBytes = strStream.toByteArray()

        // Structure block
        val structStream = ByteArrayOutputStream()
        fun writeInt(s: ByteArrayOutputStream, v: Int) {
            s.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array())
        }

        // Root Node (name "")
        writeInt(structStream, 1) // FDT_BEGIN_NODE
        structStream.write(0) // empty name null byte
        structStream.write(0) // pad to 4 bytes
        structStream.write(0)
        structStream.write(0)

        // Prop compatible = "mediatek,mt6737t\u0000"
        val compatVal = "mediatek,mt6737t\u0000".toByteArray(Charsets.UTF_8)
        writeInt(structStream, 3) // FDT_PROP
        writeInt(structStream, compatVal.size)
        writeInt(structStream, compatOffset)
        structStream.write(compatVal)
        val pad = ((compatVal.size + 3) / 4) * 4 - compatVal.size
        for (i in 0 until pad) structStream.write(0)

        // Child Node "cpus"
        writeInt(structStream, 1) // FDT_BEGIN_NODE
        structStream.write("cpus\u0000".toByteArray(Charsets.UTF_8))
        structStream.write(0) // pad 5 bytes -> 8 bytes
        structStream.write(0)
        structStream.write(0)

        writeInt(structStream, 2) // FDT_END_NODE (cpus)
        writeInt(structStream, 2) // FDT_END_NODE (root)
        writeInt(structStream, 9) // FDT_END

        val structBytes = structStream.toByteArray()

        // Build Header
        val offStruct = 48
        val offStrings = offStruct + structBytes.size
        val totalsize = offStrings + strBytes.size

        val headBuf = ByteBuffer.allocate(48).order(ByteOrder.BIG_ENDIAN)
        headBuf.putInt(0xd00dfeed.toInt()) // magic
        headBuf.putInt(totalsize)
        headBuf.putInt(offStruct)
        headBuf.putInt(offStrings)
        headBuf.putInt(40) // rsvmap
        headBuf.putInt(17) // version
        headBuf.putInt(16) // last_comp
        headBuf.putInt(0)  // boot_cpuid
        headBuf.putInt(strBytes.size)
        headBuf.putInt(structBytes.size)

        baos.write(headBuf.array())
        baos.write(structBytes)
        baos.write(strBytes)

        val fdtData = baos.toByteArray()

        val header = DtbHeaderParser.parse(fdtData)
        assertTrue(header.isValid)
        assertEquals(0xd00dfeedL, header.magic)

        val rootNode = DtbNodeParser.parseTree(fdtData, header)
        assertNotNull(rootNode)
        assertEquals("/", rootNode?.name)
        assertEquals(1, rootNode?.children?.size)
        assertEquals("cpus", rootNode?.children?.first()?.name)

        val compats = DtbHardwareAnalyzer.extractCompatibleStrings(rootNode!!)
        assertTrue(compats.contains("mediatek,mt6737t"))

        val hw = DtbHardwareAnalyzer.detectHardware(rootNode)
        assertTrue(hw.any { it.category == "CPU" })
    }

    @Test
    fun testKernelCompatibilityAnalyzer() {
        val kernelInfo = KernelInfo(
            formatInfo = KernelFormatInfo("zImage", "none", architecture = "ARM32"),
            versionInfo = KernelVersionInfo("Linux version 3.18.140", major = 3, minor = 18, patch = 140),
            architecture = "ARM32",
            rawSize = 1000000,
            decompressedSize = 1000000,
            detectedStringsCount = 100
        )

        val configs = listOf(
            KernelConfig("CONFIG_ANDROID_BINDER_IPC", "y", ConfigType.BOOL_Y, ConfigState.ENABLED, ConfigCategory.ANDROID),
            KernelConfig("CONFIG_SECURITY_SELINUX", "y", ConfigType.BOOL_Y, ConfigState.ENABLED, ConfigCategory.SECURITY),
            KernelConfig("CONFIG_EXT4_FS", "y", ConfigType.BOOL_Y, ConfigState.ENABLED, ConfigCategory.FILESYSTEM)
        )

        val signals = KernelCompatibilityAnalyzer.analyzeAndroid11Signals(
            kernelInfo,
            configs,
            listOf("mediatek,mt6737t", "samsung,grandpplte")
        )

        assertTrue(signals.isNotEmpty())
        assertTrue(signals.any { it.title.contains("Binder") })
        assertTrue(signals.any { it.title.contains("3.18") })
    }
}
