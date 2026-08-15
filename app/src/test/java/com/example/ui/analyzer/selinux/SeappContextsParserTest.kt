package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.SeappContextsParser
import org.junit.Assert.*
import org.junit.Test

class SeappContextsParserTest {

    @Test
    fun testParseSystemServerSeapp() {
        val line = "isSystemServer=true domain=system_server"
        val entry = SeappContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals(true, entry!!.isSystemServer)
        assertEquals("system_server", entry.domain)
        assertFalse(entry.isWarning)
    }

    @Test
    fun testParseFullAppSeapp() {
        val line = "user=_app isPrivApp=true name=com.android.settings domain=settings_app type=app_data_file levelFrom=all"
        val entry = SeappContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("_app", entry!!.user)
        assertEquals(true, entry.isPrivApp)
        assertEquals("com.android.settings", entry.name)
        assertEquals("settings_app", entry.domain)
        assertEquals("app_data_file", entry.type)
        assertEquals("all", entry.levelFrom)
        assertFalse(entry.isWarning)
    }

    @Test
    fun testWarningOnUnknownSyntax() {
        val line = "broken_token_without_equals"
        val entry = SeappContextsParser.parseLine(line)
        assertNotNull(entry)
        assertTrue(entry!!.isWarning)
    }
}
