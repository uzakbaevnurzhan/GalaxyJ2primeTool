package com.example.ui.analyzer.getprop

import org.junit.Assert.*
import org.junit.Test

class PortingCheckTest {

    @Test
    fun testPortingCheckDetectsAbiMismatchError() {
        val baseProps = """
            ro.product.model=SM-G532F
            ro.product.cpu.abi=armeabi-v7a
            ro.product.cpu.abilist=armeabi-v7a,armeabi
            ro.product.cpu.abilist32=armeabi-v7a,armeabi
            ro.board.platform=mt6737t
            ro.build.version.sdk=23
            ro.build.version.release=6.0.1
        """.trimIndent()

        val portProps = """
            ro.product.model=Generic ARM64 Port
            ro.product.cpu.abi=arm64-v8a
            ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi
            ro.product.cpu.abilist64=arm64-v8a
            ro.board.platform=mt6737t
            ro.build.version.sdk=28
            ro.build.version.release=9.0
        """.trimIndent()

        val baseSnap = GetpropAnalyzer.analyzeString(baseProps, "base.prop", "Base ROM").snapshot
        val portSnap = GetpropAnalyzer.analyzeString(portProps, "port.prop", "Port ROM").snapshot

        val check = GetpropPortingChecker.performCheck(baseSnap, portSnap)

        assertEquals(PortingCheckLevel.ERROR, check.overallLevel)
        assertTrue(check.errorCount > 0)
        assertTrue(check.items.any { it.title == "ABI Architecture Mismatch" && it.level == PortingCheckLevel.ERROR })
    }

    @Test
    fun testPortingCheckPassesOnCompatibleRom() {
        val baseProps = """
            ro.product.model=SM-G532F
            ro.product.cpu.abi=armeabi-v7a
            ro.board.platform=mt6737t
            ro.hardware.egl=mali
            ro.build.version.sdk=24
            ro.build.version.release=7.0
            ro.boot.selinux=enforcing
        """.trimIndent()

        val portProps = """
            ro.product.model=SM-G532F_LineageOS
            ro.product.cpu.abi=armeabi-v7a
            ro.board.platform=mt6737t
            ro.hardware.egl=mali
            ro.build.version.sdk=25
            ro.build.version.release=7.1.2
            ro.boot.selinux=enforcing
            ro.debuggable=1
        """.trimIndent()

        val baseSnap = GetpropAnalyzer.analyzeString(baseProps, "base.prop", "Base ROM").snapshot
        val portSnap = GetpropAnalyzer.analyzeString(portProps, "port.prop", "Port ROM").snapshot

        val check = GetpropPortingChecker.performCheck(baseSnap, portSnap)

        assertEquals(PortingCheckLevel.PASS, check.overallLevel)
        assertEquals(0, check.errorCount)
        assertEquals(0, check.warningCount)
        assertTrue(check.passedCount >= 5)
    }
}
