package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import com.example.ui.analyzer.kernel.model.KernelCrashType
import com.example.ui.analyzer.kernel.model.KernelSeverity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class KernelPanicParserTest {

    @Test
    fun testParseKernelPanicNotSyncing() = runBlocking {
        val log = """
            [  123.456789] Linux version 3.18.35-mtk (android-build@google.com) (gcc version 4.9.x) #1 SMP PREEMPT
            [  123.500000] Kernel panic - not syncing: Fatal exception in interrupt
            [  123.500100] CPU: 1 PID: 1234 Comm: surfaceflinger
            [  123.500200] Hardware name: MT6737T (Device)
            [  123.500300] [<c0123456>] (unwind_backtrace) from [<c010d124>] (show_stack+0x10/0x14)
            [  123.500400] [<c010d124>] (show_stack) from [<c0683050>] (panic+0x7c/0x1ec)
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "test.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.KERNEL_PANIC, event.type)
        assertEquals(KernelSeverity.CRITICAL, event.severity)
        assertEquals("Fatal exception in interrupt", event.panicReason)
        assertEquals(1, event.cpu)
        assertEquals(1234, event.pid)
        assertEquals("surfaceflinger", event.comm)
        assertEquals("3.18.35-mtk", report.kernelVersion)
    }

    @Test
    fun testParseKernelPanicVfsMount() = runBlocking {
        val log = """
            [    2.100000] Kernel panic - not syncing: VFS: Unable to mount root fs on unknown-block(0,0)
            [    2.100100] CPU: 0 PID: 1 Comm: swapper/0
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "mount_panic.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.KERNEL_PANIC, event.type)
        assertTrue(report.bootFailureAnalysis.isBootFailureLikely)
    }
}
