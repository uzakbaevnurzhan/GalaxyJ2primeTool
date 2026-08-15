package com.example.ui.analyzer.dat

import com.example.ui.analyzer.dat.engine.*
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class DatEngineTest {

    @Test
    fun testBlockRangeParser() {
        // "4,0,10,20,30" -> count = 4, ranges = [0-10], [20-30]
        val set1 = DatBlockSet.parse("4,0,10,20,30")
        assertEquals(4, set1.count)
        assertEquals(2, set1.ranges.size)
        assertEquals(0L, set1.ranges[0].start)
        assertEquals(10L, set1.ranges[0].end)
        assertEquals(20L, set1.ranges[1].start)
        assertEquals(30L, set1.ranges[1].end)
        assertEquals(20L, set1.totalBlocks)
    }

    @Test
    fun testTransferListParsing() {
        val listData = """
            4
            2048
            0
            0
            new 2,0,1024
            zero 2,1024,2048
        """.trimIndent()

        val reader = BufferedReader(StringReader(listData))
        val parsed = DatTransferList.parse(reader)

        assertEquals(4, parsed.version)
        assertEquals(2048L, parsed.totalBlocks)
        assertEquals(2, parsed.commands.size)

        assertTrue(parsed.commands[0] is DatCommand.New)
        assertTrue(parsed.commands[1] is DatCommand.Zero)

        assertEquals(1024L, parsed.newBlocks)
        assertEquals(1024L, parsed.zeroBlocks)
        assertFalse(parsed.isIncremental)
    }

    @Test
    fun testValidator_Valid() {
        val listData = """
            4
            100
            0
            0
            new 2,0,50
            zero 2,50,100
        """.trimIndent()
        val parsed = DatTransferList.parse(BufferedReader(StringReader(listData)))
        val result = DatValidator.validate(parsed)
        assertEquals(DatValidator.Status.VALID, result.status)
    }

    @Test
    fun testValidator_Overlap() {
        val listData = """
            4
            100
            0
            0
            new 2,0,60
            new 2,50,100
        """.trimIndent()
        val parsed = DatTransferList.parse(BufferedReader(StringReader(listData)))
        val result = DatValidator.validate(parsed)
        assertEquals(DatValidator.Status.INVALID, result.status)
        assertTrue(result.messages.any { it.contains("Overlapping blocks") })
    }

    @Test
    fun testValidator_Incremental() {
        val listData = """
            4
            100
            1
            100
            move e3b0c442 2,0,10 2,20,30
        """.trimIndent()
        val parsed = DatTransferList.parse(BufferedReader(StringReader(listData)))
        val result = DatValidator.validate(parsed)
        // Without new/zero commands, it's INVALID ("No 'new' or 'zero' commands found.")
        assertEquals(DatValidator.Status.INVALID, result.status)
        assertTrue(result.messages.any { it.contains("Incremental OTA detected") })
    }
}
