package com.example.ui.analyzer.getprop

import org.junit.Assert.*
import org.junit.Test

class DiffTest {

    @Test
    fun testSnapshotDiffAddedRemovedChangedUnchanged() {
        val inputA = """
            ro.build.version.release=8.1.0
            ro.build.version.sdk=27
            ro.product.model=SM-G532F
            ro.removed.prop=old_value
        """.trimIndent()

        val inputB = """
            ro.build.version.release=11
            ro.build.version.sdk=30
            ro.product.model=SM-G532F
            ro.product.cpu.abilist64=arm64-v8a
        """.trimIndent()

        val snapA = GetpropAnalyzer.analyzeString(inputA, "ROM_A/build.prop", "ROM A").snapshot
        val snapB = GetpropAnalyzer.analyzeString(inputB, "ROM_B/build.prop", "ROM B").snapshot

        val diff = GetpropDiffCalculator.compare(snapA, snapB)

        assertEquals(1, diff.addedCount) // ro.product.cpu.abilist64
        assertEquals(1, diff.removedCount) // ro.removed.prop
        assertEquals(2, diff.changedCount) // release & sdk
        assertEquals(1, diff.unchangedCount) // ro.product.model

        val addedEntry = diff.entries.find { it.key == "ro.product.cpu.abilist64" }
        assertNotNull(addedEntry)
        assertEquals(DiffStatus.ADDED, addedEntry?.status)
        assertEquals("arm64-v8a", addedEntry?.valueB)

        val changedRelease = diff.entries.find { it.key == "ro.build.version.release" }
        assertNotNull(changedRelease)
        assertEquals(DiffStatus.CHANGED, changedRelease?.status)
        assertEquals("8.1.0", changedRelease?.valueA)
        assertEquals("11", changedRelease?.valueB)

        val removedProp = diff.entries.find { it.key == "ro.removed.prop" }
        assertNotNull(removedProp)
        assertEquals(DiffStatus.REMOVED, removedProp?.status)
        assertEquals("old_value", removedProp?.valueA)
    }
}
