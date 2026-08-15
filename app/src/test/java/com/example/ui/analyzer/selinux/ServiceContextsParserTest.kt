package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.ServiceContextsParser
import org.junit.Assert.*
import org.junit.Test

class ServiceContextsParserTest {

    @Test
    fun testParseServiceContext() {
        val line = "media.camera    u:object_r:cameraserver_service:s0"
        val entry = ServiceContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("media.camera", entry!!.serviceName)
        assertEquals("cameraserver_service", entry.context?.type)
    }

    @Test
    fun testWildcardService() {
        val line = "*    u:object_r:default_android_service:s0"
        val entry = ServiceContextsParser.parseLine(line)
        assertNotNull(entry)
        assertEquals("*", entry!!.serviceName)
        assertEquals("default_android_service", entry.context?.type)
    }
}
