package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import com.example.ui.analyzer.kernel.model.KernelCrashType
import com.example.ui.analyzer.kernel.model.KernelSeverity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class OopsParserTest {

    @Test
    fun testParseArm32OopsNullPointer() = runBlocking {
        val log = """
            [   45.123456] Unable to handle kernel NULL pointer dereference at virtual address 00000004
            [   45.123500] pgd = c0004000
            [   45.123600] [00000004] *pgd=00000000
            [   45.123700] Internal error: Oops: 5 [#1] PREEMPT SMP ARM
            [   45.123800] CPU: 2 PID: 456 Comm: mtk_wcn
            [   45.123900] PC is at wlan_probe+0x24/0x80 [wlan_mtk]
            [   45.124000] LR is at driver_probe_device+0x48/0x90
            [   45.124100] pc : [<bf012024>]    lr : [<c0456789>]    psr: 60000013
            [   45.124200] sp : d1234560  ip : 00000000  fp : d1234580
            [   45.124300] r10: 00000000  r9 : 00000001  r8 : d1234000
            [   45.124400] r7 : 00000000  r6 : 00000000  r5 : c0abcdef  r4 : 00000000
            [   45.124500] r3 : 00000000  r2 : 00000000  r1 : 00000000  r0 : 00000000
            [   45.124600] Flags: nZCv  IRQs on  FIQs on  Mode SVC_32  ISA ARM  Segment user
            [   45.124700] Call trace:
            [   45.124800] [<bf012024>] (wlan_probe+0x24/0x80 [wlan_mtk]) from [<c0456789>] (driver_probe_device+0x48/0x90)
            [   45.124900] [<c0456789>] (driver_probe_device) from [<c0456900>] (__driver_attach+0x60/0x90)
            [   45.125000] Code: e5943004 e3530000 0a000005 e5930000 (e5903004)
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "oops.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.NULL_POINTER_DEREFERENCE, event.type)
        assertEquals(KernelSeverity.CRITICAL, event.severity)
        assertEquals("00000004", event.faultAddress)
        assertEquals(2, event.cpu)
        assertEquals(456, event.pid)
        assertEquals("mtk_wcn", event.comm)
        assertEquals("bf012024", event.registers.pc)
        assertEquals("c0456789", event.registers.lr)
        assertEquals("d1234560", event.registers.sp)
        assertEquals(2, event.stackFrames.size)
        assertEquals("wlan_probe", event.stackFrames[0].symbol)
        assertEquals("0x24", event.stackFrames[0].offsetHex)
        assertEquals("wlan_mtk", event.stackFrames[0].module)
    }

    @Test
    fun testParseArm64OopsPageFault() = runBlocking {
        val log = """
            [  100.123456] Unable to handle kernel paging request at virtual address ffffff8000001000
            [  100.123500] Mem abort info:
            [  100.123600]   ESR_EL1 = 0x96000004
            [  100.123700]   FAR_EL1 = 0xffffff8000001000
            [  100.123800] Internal error: Oops: 96000004 [#1] PREEMPT SMP
            [  100.123900] CPU: 3 PID: 789 Comm: mali_disp
            [  100.124000] pc : ffffff8008456780 lr : ffffff8008456700 pstate: 60000145
            [  100.124100] sp : ffffff8009876540
            [  100.124200] x29: ffffff8009876550 x28: 0000000000000000
            [  100.124300] x0 : 0000000000000000 x1 : 0000000000000001
            [  100.124400] Call trace:
            [  100.124500]  mali_render_job+0x34/0x120
            [  100.124600]  kbase_job_submit+0x50/0x80
        """.trimIndent()

        val engine = KernelCrashEngine()
        val report = engine.analyzeStream(ByteArrayInputStream(log.toByteArray()), "arm64_oops.log")

        assertEquals(1, report.totalEvents)
        val event = report.crashEvents.first()
        assertEquals(KernelCrashType.PAGE_FAULT, event.type)
        assertEquals("ffffff8000001000", event.faultAddress)
        assertEquals(3, event.cpu)
        assertEquals(789, event.pid)
        assertEquals("mali_disp", event.comm)
    }
}
