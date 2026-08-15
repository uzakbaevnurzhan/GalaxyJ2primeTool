package com.example.ui.analyzer.selinux

import com.example.ui.analyzer.selinux.engine.SelinuxAnalyzerEngine
import com.example.ui.analyzer.selinux.model.SelinuxFileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class SelinuxLargeLogTest {

    @Test
    fun testCorruptedLinesAndValidAvcMixture() = runBlocking {
        val mixedContent = """
            # Log header
            random non-avc log line 1
            type=1400 audit(1.0:1): avc: denied { read } for comm="system_server" scontext=u:r:system_server:s0 tcontext=u:object_r:vendor_file:s0 tclass=file permissive=0
            broken avc: denied missing closing brace
            [   12.345678] avc: denied { find } for service="media.camera" pid=456 scontext=u:r:cameraserver:s0 tcontext=u:object_r:cameraserver_service:s0 tclass=service_manager permissive=1
            random trailing gibberish &*#@!
        """.trimIndent()

        val input = ByteArrayInputStream(mixedContent.toByteArray())
        val result = SelinuxAnalyzerEngine.analyzeStream(input, totalBytes = mixedContent.length.toLong())

        assertEquals(SelinuxFileType.AVC_LOG, result.detectedType)
        assertEquals(2, result.avcDenials.size)
        assertTrue(result.skippedLinesCount > 0)
        assertNotNull(result.avcStatistics)
        assertEquals(2, result.avcStatistics!!.totalDenials)
    }

    @Test
    fun testLargeLogSimulation() = runBlocking {
        val sb = StringBuilder()
        for (i in 1..2000) {
            val perm = if (i % 2 == 0) "read" else "write"
            sb.appendLine("type=1400 audit($i.0:$i): avc: denied { $perm } for pid=$i comm=\"proc_$i\" scontext=u:r:init:s0 tcontext=u:object_r:vendor_file:s0 tclass=file permissive=0")
        }

        val input = ByteArrayInputStream(sb.toString().toByteArray())
        val result = SelinuxAnalyzerEngine.analyzeStream(input, totalBytes = sb.length.toLong())

        assertEquals(2000, result.avcDenials.size)
        assertEquals(2, result.avcGroups.size)
        assertEquals(1000, result.avcGroups[0].count)
        assertEquals(1000, result.avcGroups[1].count)
    }
}
