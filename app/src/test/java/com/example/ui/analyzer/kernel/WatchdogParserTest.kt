package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import com.example.ui.analyzer.kernel.model.KernelCrashType
import com.example.ui.analyzer.kernel.model.KernelSeverity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class WatchdogParserTest {

    @Test
    fun testParseSoftLockup() = runBlocking {
        val log = """
            [  200.123456] watchdog: BUG: soft lockup - CPU#2 stuck for 22s! [kworker/u8:2:123]
            [  200.123500] Modules linked in: mtk_wlan
            [  200.123600] CPU: 2 PID: 123 Comm: kworker/u8:2
            [  200.123700] [<c0123456>] (dump_stack) from [<c010d124>] (watchdog_timer_fn+0x10/0x14)
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "watchdog.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.SOFT_LOCKUP, event.type)
        assertEquals(KernelSeverity.ERROR, event.severity)
        assertEquals(2, event.cpu)
        assertEquals(123, event.pid)
        assertEquals("kworker/u8:2", event.comm)
    }

    @Test
    fun testParseHardLockup() = runBlocking {
        val log = """
            [  300.000000] watchdog: Watchdog caught hard LOCKUP on cpu 1
            [  300.000100] CPU: 1 PID: 45 Comm: mmcqd/0
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "hard_lockup.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.HARD_LOCKUP, event.type)
        assertEquals(1, event.cpu)
    }
}
