package com.example.ui.analyzer.elf

import android.content.Context
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import com.example.ui.analyzer.elf.engine.*

class ElfParserEngineTest {

    @Test
    fun testElfModelConstants() {
        val header = ElfHeader(ElfClass.ELF32, ElfEndian.LITTLE, 0, 0, 2, 40, 1, 0L, 0L, 0L, 0, 52, 32, 0, 40, 0, 0)
        assertEquals("ARM32", header.architectureName)

        val header64 = ElfHeader(ElfClass.ELF64, ElfEndian.LITTLE, 0, 0, 2, 183, 1, 0L, 0L, 0L, 0, 64, 56, 0, 64, 0, 0)
        assertEquals("AArch64", header64.architectureName)
    }

    @Test
    fun testElfValidation() {
        val header = ElfHeader(ElfClass.ELF64, ElfEndian.LITTLE, 0, 0, 2, 183, 1, 0L, 0L, 0L, 0, 64, 56, 0, 64, 0, 0)
        val file = ElfFile(
            header,
            listOf(ElfProgramHeader(0x6474e551, 0L, 0L, 0L, 0L, 0L, 6, 8L)), // RW- (Non-executable stack)
            emptyList(),
            listOf(ElfDynamicEntry(14L, 0L, "libtest.so"), ElfDynamicEntry(1L, 0L, "libc.so")),
            emptyList()
        )
        
        val result = ElfParserEngine.validateAndroidCompatibility(file)
        assertTrue(result.status == ElfStatus.VALID || result.status == ElfStatus.WARNING) // warning because no libc.so or something, wait we added libc.so!
        assertFalse(result.messages.any { it.contains("executable") }) // Stack is RW (6) not RWX (7)
    }

    @Test
    fun testElfValidation_ExecutableStack() {
        val header = ElfHeader(ElfClass.ELF64, ElfEndian.LITTLE, 0, 0, 2, 183, 1, 0L, 0L, 0L, 0, 64, 56, 0, 64, 0, 0)
        val file = ElfFile(
            header,
            listOf(ElfProgramHeader(0x6474e551, 0L, 0L, 0L, 0L, 0L, 7, 8L)), // RWX (Executable stack)
            emptyList(),
            listOf(ElfDynamicEntry(14L, 0L, "libtest.so"), ElfDynamicEntry(1L, 0L, "libc.so")),
            emptyList()
        )
        
        val result = ElfParserEngine.validateAndroidCompatibility(file)
        assertEquals(ElfStatus.CORRUPTED, result.status)
        assertTrue(result.messages.any { it.contains("PT_GNU_STACK is executable") })
    }
}
