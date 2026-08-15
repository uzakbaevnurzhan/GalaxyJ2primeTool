package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.GenfsContextsParser
import org.junit.Assert.*
import org.junit.Test

class GenfsContextsParserTest {

    @Test
    fun testParseGenfscon() {
        val line = "genfscon proc /sys/kernel/random/ u:object_r:proc_random:s0"
        val entry = GenfsContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("proc", entry!!.filesystem)
        assertEquals("/sys/kernel/random/", entry.path)
        assertEquals("proc_random", entry.context?.type)
    }

    @Test
    fun testParseGenfsSysfs() {
        val line = "genfscon sysfs /devices/system/cpu u:object_r:sysfs_devices_system_cpu:s0"
        val entry = GenfsContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("sysfs", entry!!.filesystem)
        assertEquals("/devices/system/cpu", entry.path)
        assertEquals("sysfs_devices_system_cpu", entry.context?.type)
    }
}
