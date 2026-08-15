package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.SelinuxContextParser
import org.junit.Assert.*
import org.junit.Test

class SelinuxContextParserTest {

    @Test
    fun testValidBasicContext() {
        val ctx = SelinuxContextParser.parse("u:r:system_server:s0")
        assertNotNull(ctx)
        assertEquals("u", ctx!!.user)
        assertEquals("r", ctx.role)
        assertEquals("system_server", ctx.type)
        assertEquals("s0", ctx.level)
        assertTrue(ctx.isSystemServer)
        assertFalse(ctx.isVendorDomain)
    }

    @Test
    fun testObjectContextWithComplexLevel() {
        val ctx = SelinuxContextParser.parse("u:object_r:vendor_file:s0:c123,c456")
        assertNotNull(ctx)
        assertEquals("u", ctx!!.user)
        assertEquals("object_r", ctx.role)
        assertEquals("vendor_file", ctx.type)
        assertEquals("s0:c123,c456", ctx.level)
        assertTrue(ctx.isVendorDomain)
        assertFalse(ctx.isSystemServer)
    }

    @Test
    fun testMLSLevelRange() {
        val ctx = SelinuxContextParser.parse("u:r:shell:s0-s0:c0.c1023")
        assertNotNull(ctx)
        assertEquals("u", ctx!!.user)
        assertEquals("r", ctx.role)
        assertEquals("shell", ctx.type)
        assertEquals("s0-s0:c0.c1023", ctx.level)
    }

    @Test
    fun testContextWithoutLevel() {
        val ctx = SelinuxContextParser.parse("system_u:system_r:init_t")
        assertNotNull(ctx)
        assertEquals("system_u", ctx!!.user)
        assertEquals("system_r", ctx.role)
        assertEquals("init_t", ctx.type)
        assertNull(ctx.level)
    }

    @Test
    fun testInvalidOrNullContext() {
        assertNull(SelinuxContextParser.parse(null))
        assertNull(SelinuxContextParser.parse(""))
        assertNull(SelinuxContextParser.parse("    "))
        assertNull(SelinuxContextParser.parse("invalid_context"))
        assertNull(SelinuxContextParser.parse("u:r"))
    }
}
