package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.parser.AvcParser
import org.junit.Assert.*
import org.junit.Test

class AvcParserTest {

    @Test
    fun testParseAuditKernelFormat() {
        val line = "type=1400 audit(1620000000.123:45): avc:  denied  { read write } for  pid=1234 comm=\"surfaceflinger\" path=\"/dev/mali0\" dev=\"tmpfs\" ino=12345 scontext=u:r:surfaceflinger:s0 tcontext=u:object_r:gpu_device:s0 tclass=chr_file permissive=0"
        val avc = AvcParser.parseLine(line)
        assertNotNull(avc)
        assertEquals("surfaceflinger", avc!!.comm)
        assertEquals(1234, avc.pid)
        assertEquals(listOf("read", "write"), avc.permissions)
        assertEquals("/dev/mali0", avc.path)
        assertEquals("surfaceflinger", avc.scontext?.type)
        assertEquals("gpu_device", avc.tcontext?.type)
        assertEquals("chr_file", avc.tclass)
        assertFalse(avc.isPermissive)
        assertEquals("denied", avc.operation)
        assertEquals("1620000000.123", avc.timestamp)
        assertEquals(12345L, avc.ino)
    }

    @Test
    fun testParseLogcatFormat() {
        val line = "05-15 12:34:56.789 1234 1234 I auditd  : type=1400 audit(0.0:45): avc: denied { ioctl } for comm=\"rild\" path=\"/dev/radio\" dev=\"tmpfs\" ino=567 ioctlcmd=0x8910 scontext=u:r:rild:s0 tcontext=u:object_r:radio_device:s0 tclass=chr_file permissive=1"
        val avc = AvcParser.parseLine(line)
        assertNotNull(avc)
        assertEquals("rild", avc!!.comm)
        assertEquals(listOf("ioctl"), avc.permissions)
        assertEquals("0x8910", avc.ioctlCmd)
        assertTrue(avc.isPermissive)
        assertEquals("05-15 12:34:56.789", avc.timestamp)
    }

    @Test
    fun testParseDmesgServiceManagerFormat() {
        val line = "[   12.345678] avc: denied { find } for service=\"media.camera\" pid=456 scontext=u:r:cameraserver:s0 tcontext=u:object_r:cameraserver_service:s0 tclass=service_manager permissive=0"
        val avc = AvcParser.parseLine(line)
        assertNotNull(avc)
        assertEquals("media.camera", avc!!.serviceName)
        assertEquals(456, avc.pid)
        assertEquals("service_manager", avc.tclass)
        assertEquals("[12.345678s]", avc.timestamp)
        assertFalse(avc.isPermissive)
    }

    @Test
    fun testNonAvcLineReturnsNull() {
        val line = "05-15 12:34:56.789 1234 1234 I ActivityManager: Starting activity com.android.settings"
        assertNull(AvcParser.parseLine(line))
        assertFalse(AvcParser.isAvcLine(line))
    }

    @Test
    fun testCorruptedOrIncompleteAvcLine() {
        val brokenLine = "avc: denied without braces or contexts"
        val avc = AvcParser.parseLine(brokenLine)
        assertNull(avc)
    }
}
