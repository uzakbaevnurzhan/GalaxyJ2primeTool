package com.example.ui.analyzer.getprop

import org.junit.Assert.*
import org.junit.Test

class DuplicatePropertyTest {

    @Test
    fun testDetectIdenticalDuplicates() {
        val input1 = "ro.foo=bar\nro.other=123"
        val input2 = "ro.foo=bar\nro.vendor=abc"

        val raw1 = GetpropParser.parseString(input1, "system/build.prop")
        val raw2 = GetpropParser.parseString(input2, "vendor/build.prop")

        val result = GetpropAnalyzer.analyzeRawList(listOf(raw1, raw2), "Test Snapshot", emptyList(), emptyList())

        assertEquals(3, result.snapshot.totalPropertiesCount)
        assertEquals(1, result.snapshot.duplicateCount)
        assertEquals(0, result.snapshot.conflictCount)

        val fooEntry = result.snapshot.properties["ro.foo"]
        assertNotNull(fooEntry)
        assertTrue(fooEntry!!.isDuplicate)
        assertEquals(ConflictStatus.DUPLICATE_IDENTICAL, fooEntry.conflictStatus)
        assertEquals(2, fooEntry.occurrences.size)
    }

    @Test
    fun testDetectConflictingDuplicates() {
        val input1 = "ro.product.model=SM-G532F\nro.hardware=mt6737t"
        val input2 = "ro.product.model=SM-G532M\nro.hardware=mt6737t"

        val raw1 = GetpropParser.parseString(input1, "system/build.prop")
        val raw2 = GetpropParser.parseString(input2, "vendor/build.prop")

        val result = GetpropAnalyzer.analyzeRawList(listOf(raw1, raw2), "Conflict Test", emptyList(), emptyList())

        assertEquals(2, result.snapshot.totalPropertiesCount)
        assertEquals(1, result.snapshot.conflictCount)

        val modelEntry = result.snapshot.properties["ro.product.model"]
        assertNotNull(modelEntry)
        assertEquals(ConflictStatus.CONFLICT_VALUE_MISMATCH, modelEntry!!.conflictStatus)
        assertTrue(result.conflictsList.any { it.key == "ro.product.model" })
        assertTrue(result.warnings.any { it.contains("Property conflict for 'ro.product.model'") })
    }
}
