package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class LargeLogTest {

    @Test
    fun testStreamingLargeLogWithSurroundingNoise() = runBlocking {
        val sb = java.lang.StringBuilder()

        // Generate 5000 lines of normal dmesg noise
        for (i in 1..2500) {
            sb.appendLine("[$i.000000] normal kernel log message info counter=$i")
        }

        // Insert a crash event in the middle
        sb.appendLine("[2501.000000] Kernel panic - not syncing: Fatal exception in interrupt")
        sb.appendLine("[2501.000100] CPU: 0 PID: 99 Comm: kworker")
        sb.appendLine("[2501.000200] Call trace:")
        sb.appendLine("[2501.000300] [<c0100000>] (bad_func+0x10/0x20)")
        sb.appendLine("[2501.000400] Code: 00000000")

        // Generate 2500 lines of post noise
        for (i in 2502..5000) {
            sb.appendLine("[$i.000000] post-panic or reboot line counter=$i")
        }

        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        val engine = KernelCrashEngine(contextLinesBeforeCount = 10, contextLinesAfterCount = 10)
        val report = engine.analyzeStream(ByteArrayInputStream(bytes), "large_log.txt", bytes.size.toLong())

        assertEquals(1, report.totalEvents)
        val ev = report.crashEvents.first()
        assertEquals("Fatal exception in interrupt", ev.panicReason)
        assertEquals(10, ev.contextLinesBefore.size)
        assertTrue(report.totalLinesAnalyzed >= 5000L)
    }
}
