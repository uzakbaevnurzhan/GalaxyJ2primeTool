package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.engine.SelinuxAnalyzerEngine
import com.example.ui.analyzer.selinux.parser.AvcParser
import org.junit.Assert.*
import org.junit.Test

class AvcGroupingTest {

    @Test
    fun testGroupingIdenticalDenials() {
        val line1 = "type=1400 audit(1.0:1): avc: denied { read } for comm=\"system_server\" scontext=u:r:system_server:s0 tcontext=u:object_r:vendor_file:s0 tclass=file permissive=0"
        val line2 = "type=1400 audit(2.0:2): avc: denied { read } for comm=\"system_server\" scontext=u:r:system_server:s0 tcontext=u:object_r:vendor_file:s0 tclass=file permissive=0"
        val line3 = "type=1400 audit(3.0:3): avc: denied { write } for comm=\"surfaceflinger\" scontext=u:r:surfaceflinger:s0 tcontext=u:object_r:gpu_device:s0 tclass=chr_file permissive=0"

        val d1 = requireNotNull(AvcParser.parseLine(line1))
        val d2 = requireNotNull(AvcParser.parseLine(line2))
        val d3 = requireNotNull(AvcParser.parseLine(line3))

        val groups = SelinuxAnalyzerEngine.groupAvcDenials(listOf(d1, d2, d3))

        assertEquals(2, groups.size)
        val topGroup = groups[0]
        assertEquals("system_server", topGroup.sourceDomain)
        assertEquals("vendor_file", topGroup.targetDomain)
        assertEquals("file", topGroup.tclass)
        assertEquals("read", topGroup.permission)
        assertEquals(2, topGroup.count)
        assertEquals("allow system_server vendor_file:file read;", topGroup.suggestedRule)

        val secondGroup = groups[1]
        assertEquals("surfaceflinger", secondGroup.sourceDomain)
        assertEquals(1, secondGroup.count)
    }

    @Test
    fun testMultiplePermissionsInOneDenial() {
        val line = "type=1400 audit(1.0:1): avc: denied { read write open } for scontext=u:r:init:s0 tcontext=u:object_r:vendor_file:s0 tclass=file permissive=0"
        val d = requireNotNull(AvcParser.parseLine(line))

        val groups = SelinuxAnalyzerEngine.groupAvcDenials(listOf(d))
        assertEquals(3, groups.size)
        val permissions = groups.map { it.permission }.toSet()
        assertTrue(permissions.contains("read"))
        assertTrue(permissions.contains("write"))
        assertTrue(permissions.contains("open"))
    }
}
