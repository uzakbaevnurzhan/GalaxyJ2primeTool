package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.FileContextsParser
import org.junit.Assert.*
import org.junit.Test

class FileContextsParserTest {

    @Test
    fun testParseStandardFileContext() {
        val line = "/system(/.*)?    u:object_r:system_file:s0"
        val entry = FileContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("/system(/.*)?", entry!!.pathRegex)
        assertNull(entry.fileTypeQualifier)
        assertEquals("system_file", entry.context?.type)
        assertFalse(entry.isNone)
    }

    @Test
    fun testParseFileContextWithQualifier() {
        val line = "/vendor/bin/hw/android\\.hardware\\.audio@.*    --    u:object_r:hal_audio_default_exec:s0"
        val entry = FileContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("/vendor/bin/hw/android\\.hardware\\.audio@.*", entry!!.pathRegex)
        assertEquals("--", entry.fileTypeQualifier)
        assertEquals("Regular File", entry.fileTypeDescription)
        assertEquals("hal_audio_default_exec", entry.context?.type)
    }

    @Test
    fun testParseNoneContext() {
        val line = "/data/local/tmp    <<none>>"
        val entry = FileContextsParser.parseLine(line)
        assertNotNull(entry)
        assertTrue(entry!!.isNone)
        assertNull(entry.context)
    }

    @Test
    fun testCommentsAndEmptyLines() {
        assertNull(FileContextsParser.parseLine("# This is a comment"))
        assertNull(FileContextsParser.parseLine("   "))
        assertNull(FileContextsParser.parseLine(""))
    }
}
