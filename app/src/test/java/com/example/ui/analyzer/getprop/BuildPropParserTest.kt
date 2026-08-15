package com.example.ui.analyzer.getprop

import org.junit.Assert.*
import org.junit.Test

class BuildPropParserTest {

    @Test
    fun testParseStandardBuildPropFormat() {
        val input = """
            # This is a sample build.prop comment
            ro.build.id=MMB29T
            ro.build.display.id=MMB29T.G532FXWU1ARH1
            ro.build.version.incremental=G532FXWU1ARH1
            ro.build.version.sdk=23
            ro.build.version.release=6.0.1
            ro.build.version.security_patch=2018-08-01
            ro.product.model=SM-G532F
            ro.product.brand=samsung
            ro.product.name=grandpplte
            ro.product.device=grandpplte
            ro.product.board=mt6737t
            ro.product.cpu.abi=armeabi-v7a
            ro.product.cpu.abilist=armeabi-v7a,armeabi
            ro.product.cpu.abilist32=armeabi-v7a,armeabi
            ro.hardware=mt6737t
            ro.board.platform=mt6737t
            ro.hardware.egl=mali
            ro.opengles.version=196610
            dalvik.vm.heapgrowthlimit=128m
            dalvik.vm.heapsize=256m
            rild.libpath=/system/lib/libmtk-ril.so
            ro.boot.selinux=enforcing
            ro.secure=1
            ro.debuggable=0
        """.trimIndent()

        val parsed = GetpropParser.parseString(input, "system/build.prop")
        assertTrue(parsed.parsedCount >= 22)
        assertEquals(0, parsed.skippedCount)

        val glesProp = parsed.entries.find { it.key == "ro.opengles.version" }
        assertNotNull(glesProp)
        assertEquals("196610", glesProp?.value)
        assertEquals(PropertyValueType.INTEGER, glesProp?.valueType)
        assertEquals(GetpropCategory.GRAPHICS, glesProp?.category)
    }

    @Test
    fun testParseEqualSignInValue() {
        val input = """
            ro.cmdline=console=tty0 root=/dev/mmcblk0p1 androidboot.hardware=mt6737t
            ro.empty=
        """.trimIndent()

        val parsed = GetpropParser.parseString(input, "default.prop")
        assertEquals(2, parsed.parsedCount)

        val cmdline = parsed.entries.find { it.key == "ro.cmdline" }
        assertEquals("console=tty0 root=/dev/mmcblk0p1 androidboot.hardware=mt6737t", cmdline?.value)

        val empty = parsed.entries.find { it.key == "ro.empty" }
        assertEquals("", empty?.value)
    }
}
