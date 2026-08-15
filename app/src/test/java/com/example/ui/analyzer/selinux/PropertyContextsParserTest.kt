package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.PropertyContextsParser
import org.junit.Assert.*
import org.junit.Test

class PropertyContextsParserTest {

    @Test
    fun testParseStandardPropertyContext() {
        val line = "ro.build.version.    u:object_r:build_prop:s0"
        val entry = PropertyContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("ro.build.version.", entry!!.propertyPattern)
        assertEquals("build_prop", entry.context?.type)
        assertNull(entry.typeClass)
    }

    @Test
    fun testParsePropertyContextWithType() {
        val line = "persist.vendor.camera.    u:object_r:vendor_camera_prop:s0    int"
        val entry = PropertyContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("persist.vendor.camera.", entry!!.propertyPattern)
        assertEquals("vendor_camera_prop", entry.context?.type)
        assertEquals("int", entry.typeClass)
    }

    @Test
    fun testCommentsAndEmpty() {
        assertNull(PropertyContextsParser.parseLine("# comment"))
        assertNull(PropertyContextsParser.parseLine("   "))
    }
}
