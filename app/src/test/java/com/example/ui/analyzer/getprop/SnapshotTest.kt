package com.example.ui.analyzer.getprop

import org.junit.Assert.*
import org.junit.Test

class SnapshotTest {

    @Test
    fun testDeviceSummaryExtractionAndAbiDetection() {
        val input = """
            ro.product.model=SM-G532F
            ro.product.brand=samsung
            ro.product.manufacturer=samsung
            ro.product.name=grandpplte
            ro.product.device=grandpplte
            ro.product.board=mt6737t
            ro.board.platform=mt6737t
            ro.hardware=mt6737t
            ro.soc.model=mt6737t
            ro.build.version.release=6.0.1
            ro.build.version.sdk=23
            ro.build.version.codename=REL
            ro.build.id=MMB29T
            ro.build.display.id=MMB29T.G532FXWU1ARH1
            ro.build.version.security_patch=2018-08-01
            ro.build.version.incremental=G532FXWU1ARH1
            ro.product.cpu.abi=armeabi-v7a
            ro.product.cpu.abilist=armeabi-v7a,armeabi
            ro.product.cpu.abilist32=armeabi-v7a,armeabi
            ro.boot.selinux=enforcing
            ro.debuggable=0
            ro.secure=1
            ro.adb.secure=1
            ro.build.tags=release-keys
        """.trimIndent()

        val result = GetpropAnalyzer.analyzeString(input, "system/build.prop", "Galaxy J2 Prime Snapshot")
        val s = result.snapshot.deviceSummary

        assertEquals("SM-G532F", s.model)
        assertEquals("samsung", s.brand)
        assertEquals("6.0.1", s.androidVersion)
        assertEquals(23, s.sdk)
        assertEquals("MMB29T", s.buildId)
        assertEquals("2018-08-01", s.securityPatch)
        assertEquals("armeabi-v7a", s.primaryAbi)
        assertEquals("ARM32 only", s.abiType)
        assertEquals("Enforcing", s.selinuxMode)
        assertEquals(false, s.isDebuggable)
        assertEquals(true, s.isSecure)
        assertEquals(true, s.isAdbSecure)
        assertEquals("release-keys", s.buildTags)
        assertEquals("mt6737t", result.hardwareSoc.platform)
    }

    @Test
    fun testArm64AbiDetection() {
        val input = """
            ro.product.model=Pixel 7
            ro.product.cpu.abi=arm64-v8a
            ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi
            ro.product.cpu.abilist64=arm64-v8a
            ro.build.version.sdk=33
            ro.build.version.release=13
        """.trimIndent()

        val result = GetpropAnalyzer.analyzeString(input, "build.prop")
        val s = result.snapshot.deviceSummary

        assertEquals("arm64-v8a", s.primaryAbi)
        assertEquals("ARM64 (64-bit)", s.abiType)
        assertEquals(33, s.sdk)
    }
}
