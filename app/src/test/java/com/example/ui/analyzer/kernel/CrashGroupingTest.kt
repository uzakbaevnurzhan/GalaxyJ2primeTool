package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CrashGroupingTest {

    @Test
    fun testGroupIdenticalCallTraces() = runBlocking {
        val log = """
            [   10.000000] Unable to handle kernel NULL pointer dereference at virtual address 00000000
            [   10.000100] Call trace:
            [   10.000200] [<c0123456>] (foo_func+0x10/0x20)
            [   10.000300] [<c0654321>] (bar_func+0x20/0x40)
            [   10.000400] Code: 00000000
            [   10.000500] ---[ end trace 1 ]---
            
            [   20.000000] Unable to handle kernel NULL pointer dereference at virtual address 00000000
            [   20.000100] Call trace:
            [   20.000200] [<c0123456>] (foo_func+0x10/0x20)
            [   20.000300] [<c0654321>] (bar_func+0x20/0x40)
            [   20.000400] Code: 00000000
            [   20.000500] ---[ end trace 2 ]---
        """.trimIndent()

        val engine = KernelCrashEngine(contextLinesAfterCount = 5)
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "grouping.log")

        assertEquals(2, report.totalEvents)
        assertEquals(1, report.repeatedTraces.size)
        val group = report.repeatedTraces.first()
        assertEquals(2, group.occurrences)
        assertTrue(group.signature.contains("foo_func"))
    }
}
