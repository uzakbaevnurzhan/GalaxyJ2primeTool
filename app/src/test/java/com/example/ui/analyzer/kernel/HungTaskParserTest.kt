package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import com.example.ui.analyzer.kernel.model.KernelCrashType
import com.example.ui.analyzer.kernel.model.KernelSeverity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class HungTaskParserTest {

    @Test
    fun testParseHungTaskBlocked() = runBlocking {
        val log = """
            [  120.000000] INFO: task system_server:1200 blocked for more than 120 seconds.
            [  120.000100]       Tainted: G        W    3.18.35 #1
            [  120.000200] "echo 0 > /proc/sys/kernel/hung_task_timeout_secs" disables this message.
            [  120.000300] system_server   D c0123456     0  1200    500 0x00000000
            [  120.000400] [<c0123456>] (__schedule) from [<c0890123>] (mutex_lock+0x20/0x40)
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "hung_task.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.HUNG_TASK, event.type)
        assertEquals(KernelSeverity.ERROR, event.severity)
        assertEquals(1200, event.pid)
        assertEquals("system_server", event.comm)
    }

    @Test
    fun testParseRcuStall() = runBlocking {
        val log = """
            [  150.000000] INFO: rcu_preempt self-detected stall on CPU 0
            [  150.000100] CPU: 0 PID: 300 Comm: ksoftirqd/0
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "rcu.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.RCU_STALL, event.type)
        assertEquals(KernelSeverity.ERROR, event.severity)
        assertEquals(0, event.cpu)
        assertEquals(300, event.pid)
    }
}
