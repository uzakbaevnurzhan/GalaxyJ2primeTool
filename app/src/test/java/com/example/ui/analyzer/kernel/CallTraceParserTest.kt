package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.parser.KernelTraceParser
import org.junit.Assert.*
import org.junit.Test

class CallTraceParserTest {

    @Test
    fun testParseStandardTraceFrames() {
        val line1 = "[<c0123456>] (vfs_read+0x80/0x120) from [<c0123900>] (sys_read+0x30/0x60)"
        val frame1 = KernelTraceParser.parseFrame(line1, 0)

        assertNotNull(frame1)
        assertEquals("c0123456", frame1?.address)
        assertEquals("vfs_read", frame1?.symbol)
        assertEquals("0x80", frame1?.offsetHex)
        assertEquals("0x120", frame1?.sizeHex)
        assertNull(frame1?.module)

        val line2 = "[<bf001234>] mtk_wlan_rx+0x18/0x40 [wlan_mt6737]"
        val frame2 = KernelTraceParser.parseFrame(line2, 1)

        assertNotNull(frame2)
        assertEquals("bf001234", frame2?.address)
        assertEquals("mtk_wlan_rx", frame2?.symbol)
        assertEquals("0x18", frame2?.offsetHex)
        assertEquals("0x40", frame2?.sizeHex)
        assertEquals("wlan_mt6737", frame2?.module)

        val line3 = " [ 123.456789] [<ffffff8008081234>] ? el1_da+0x1c/0x90"
        val frame3 = KernelTraceParser.parseFrame(line3, 2)

        assertNotNull(frame3)
        assertEquals("ffffff8008081234", frame3?.address)
        assertEquals("el1_da", frame3?.symbol)
        assertEquals("0x1c", frame3?.offsetHex)
    }

    @Test
    fun testTraceHeaderAndTerminator() {
        assertTrue(KernelTraceParser.isTraceHeader("Call trace:"))
        assertTrue(KernelTraceParser.isTraceHeader("Backtrace:"))
        assertTrue(KernelTraceParser.isTraceTerminator("Code: e5943004"))
        assertTrue(KernelTraceParser.isTraceTerminator("---[ end trace 123456789 ]---"))
        assertTrue(KernelTraceParser.isTraceTerminator("Modules linked in:"))
    }
}
