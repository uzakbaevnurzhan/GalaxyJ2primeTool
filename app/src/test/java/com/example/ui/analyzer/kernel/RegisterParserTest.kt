package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.model.KernelArchitecture
import com.example.ui.analyzer.kernel.parser.KernelRegisterParser
import org.junit.Assert.*
import org.junit.Test

class RegisterParserTest {

    @Test
    fun testParseArm32Registers() {
        val lines = listOf(
            "PC is at mtk_disp_config+0x20/0x100 [mtk_disp]",
            "LR is at disp_probe+0x10/0x40",
            "pc : [<bf102020>]    lr : [<c0203040>]    psr: 60000013",
            "sp : d0001230  ip : 00000000  fp : d0001250",
            "r10: 00000000  r9 : 00000001  r8 : d1234000",
            "r7 : 00000000  r6 : 00000000  r5 : c0abcdef  r4 : 00000000",
            "r3 : 00000000  r2 : 00000000  r1 : 00000000  r0 : 00000000",
            "Flags: nZCv  IRQs on  FIQs on  Mode SVC_32  ISA ARM  Segment user"
        )

        val regSet = KernelRegisterParser.parseRegisterBlock(lines)

        assertEquals(KernelArchitecture.ARM32, regSet.architecture)
        assertEquals("bf102020", regSet.pc)
        assertEquals("c0203040", regSet.lr)
        assertEquals("d0001230", regSet.sp)
        assertEquals("60000013", regSet.cpsr)
        assertEquals("c0abcdef", regSet.registers["r5"])
    }

    @Test
    fun testParseArm64Registers() {
        val lines = listOf(
            "ESR_EL1 = 0x96000004",
            "FAR_EL1 = 0xffffff8000001000",
            "pc : ffffff8008081234 lr : ffffff8008085678 pstate: 60000145",
            "sp : ffffff80089a1230",
            "x29: ffffff80089a1240 x28: 0000000000000000",
            "x0 : 0000000000000001 x1 : 0000000000000002"
        )

        val regSet = KernelRegisterParser.parseRegisterBlock(lines)

        assertEquals(KernelArchitecture.ARM64, regSet.architecture)
        assertEquals("ffffff8008081234", regSet.pc)
        assertEquals("ffffff8008085678", regSet.lr)
        assertEquals("ffffff80089a1230", regSet.sp)
        assertEquals("0x96000004", regSet.esr)
        assertEquals("0xffffff8000001000", regSet.far)
        assertEquals("0000000000000001", regSet.registers["x0"])
    }
}
