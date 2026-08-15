package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.model.SelinuxMode
import com.example.ui.analyzer.selinux.parser.SelinuxStatusDetector
import org.junit.Assert.*
import org.junit.Test

class SelinuxStatusTest {

    @Test
    fun testEnforcingFromGetenforce() {
        val detection = SelinuxStatusDetector.detectMode(
            getenforceOutput = "Enforcing\n",
            props = mapOf("ro.boot.selinux" to "enforcing")
        )
        assertEquals(SelinuxMode.ENFORCING, detection.mode)
        assertFalse(detection.hasConflict)
        assertTrue(detection.warnings.isEmpty())
    }

    @Test
    fun testPermissiveFromCmdline() {
        val detection = SelinuxStatusDetector.detectMode(
            cmdline = "console=ttyMT0,921600n1 androidboot.selinux=permissive"
        )
        assertEquals(SelinuxMode.PERMISSIVE, detection.mode)
    }

    @Test
    fun testConflictingStatusGeneratesWarning() {
        val detection = SelinuxStatusDetector.detectMode(
            getenforceOutput = "Enforcing",
            props = mapOf("ro.boot.selinux" to "permissive"),
            hasPermissiveAudit = true
        )
        assertTrue(detection.hasConflict)
        assertTrue(detection.warnings.isNotEmpty())
    }

    @Test
    fun testDisabledStatus() {
        val detection = SelinuxStatusDetector.detectMode(
            getenforceOutput = "Disabled"
        )
        assertEquals(SelinuxMode.DISABLED, detection.mode)
    }
}
