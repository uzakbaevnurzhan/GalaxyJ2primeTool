package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.parser.KernelTimestampParser
import org.junit.Assert.*
import org.junit.Test

class TimestampParserTest {

    @Test
    fun testParseDmesgTimestamp() {
        val line = "[  123.456789] Kernel panic - not syncing: Fatal exception"
        val parsed = KernelTimestampParser.parseLine(line)

        assertEquals("[123.456789]", parsed.rawTimestamp)
        assertEquals(123.456789, parsed.uptimeSeconds ?: 0.0, 0.00001)
        assertEquals("Kernel panic - not syncing: Fatal exception", parsed.cleanedLine)
    }

    @Test
    fun testParseDmesgLevelTimestamp() {
        val line = "[   12.345678] <0> [1:swapper/0] Kernel BUG at mm/page_alloc.c:123"
        val parsed = KernelTimestampParser.parseLine(line)

        assertEquals("[12.345678]", parsed.rawTimestamp)
        assertEquals(12.345678, parsed.uptimeSeconds ?: 0.0, 0.00001)
        assertEquals("[1:swapper/0] Kernel BUG at mm/page_alloc.c:123", parsed.cleanedLine)
    }

    @Test
    fun testParseLogcatTimestamp() {
        val line = "08-15 12:30:20.123  1234  1234 E Kernel  : Unable to handle kernel NULL pointer"
        val parsed = KernelTimestampParser.parseLine(line)

        assertEquals("08-15 12:30:20.123", parsed.rawTimestamp)
        assertEquals("Unable to handle kernel NULL pointer", parsed.cleanedLine)
    }
}
