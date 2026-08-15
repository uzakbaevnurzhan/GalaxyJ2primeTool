package com.example.ui.analyzer.elf.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object ElfParserEngine {

    suspend fun parse(context: Context, uri: Uri): ElfFile = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Could not open file descriptor")
        
        pfd.use { fd ->
            val channel = FileInputStream(fd.fileDescriptor).channel
            
            // 1. Read e_ident
            val identBuffer = readBytes(channel, 0, 16)
            if (identBuffer[0] != 0x7F.toByte() ||
                identBuffer[1] != 'E'.code.toByte() ||
                identBuffer[2] != 'L'.code.toByte() ||
                identBuffer[3] != 'F'.code.toByte()) {
                throw IllegalArgumentException("Not a valid ELF file (invalid magic)")
            }

            val elfClass = when (identBuffer[4].toInt()) {
                1 -> ElfClass.ELF32
                2 -> ElfClass.ELF64
                else -> ElfClass.UNKNOWN
            }

            val endian = when (identBuffer[5].toInt()) {
                1 -> ElfEndian.LITTLE
                2 -> ElfEndian.BIG
                else -> ElfEndian.UNKNOWN
            }
            
            val is64 = elfClass == ElfClass.ELF64
            val order = if (endian == ElfEndian.BIG) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

            // 2. Read rest of header
            val headerSize = if (is64) 64 else 52
            val headerBuffer = readBytes(channel, 16, headerSize - 16).order(order)
            
            val type = headerBuffer.short.toInt() and 0xFFFF
            val machine = headerBuffer.short.toInt() and 0xFFFF
            val version = headerBuffer.int
            
            val entryPoint = if (is64) headerBuffer.long else headerBuffer.int.toLong() and 0xFFFFFFFFL
            val phOff = if (is64) headerBuffer.long else headerBuffer.int.toLong() and 0xFFFFFFFFL
            val shOff = if (is64) headerBuffer.long else headerBuffer.int.toLong() and 0xFFFFFFFFL
            
            val flags = headerBuffer.int
            val ehSize = headerBuffer.short.toInt() and 0xFFFF
            val phEntSize = headerBuffer.short.toInt() and 0xFFFF
            val phNum = headerBuffer.short.toInt() and 0xFFFF
            val shEntSize = headerBuffer.short.toInt() and 0xFFFF
            val shNum = headerBuffer.short.toInt() and 0xFFFF
            val shStrNdx = headerBuffer.short.toInt() and 0xFFFF

            val header = ElfHeader(
                elfClass, endian, identBuffer[7].toInt() and 0xFF, identBuffer[8].toInt() and 0xFF,
                type, machine, version, entryPoint, phOff, shOff, flags, ehSize, phEntSize, phNum, shEntSize, shNum, shStrNdx
            )

            // 3. Read Program Headers
            val programHeaders = mutableListOf<ElfProgramHeader>()
            if (phOff > 0 && phNum > 0) {
                val phBuffer = readBytes(channel, phOff, phEntSize * phNum).order(order)
                for (i in 0 until phNum) {
                    val pType = phBuffer.int
                    val pFlags: Int
                    val pOffset: Long
                    val pVaddr: Long
                    val pPaddr: Long
                    val pFileSize: Long
                    val pMemSize: Long
                    val pAlign: Long

                    if (is64) {
                        pFlags = phBuffer.int
                        pOffset = phBuffer.long
                        pVaddr = phBuffer.long
                        pPaddr = phBuffer.long
                        pFileSize = phBuffer.long
                        pMemSize = phBuffer.long
                        pAlign = phBuffer.long
                    } else {
                        pOffset = phBuffer.int.toLong() and 0xFFFFFFFFL
                        pVaddr = phBuffer.int.toLong() and 0xFFFFFFFFL
                        pPaddr = phBuffer.int.toLong() and 0xFFFFFFFFL
                        pFileSize = phBuffer.int.toLong() and 0xFFFFFFFFL
                        pMemSize = phBuffer.int.toLong() and 0xFFFFFFFFL
                        pFlags = phBuffer.int
                        pAlign = phBuffer.int.toLong() and 0xFFFFFFFFL
                    }
                    programHeaders.add(ElfProgramHeader(pType, pOffset, pVaddr, pPaddr, pFileSize, pMemSize, pFlags, pAlign))
                }
            }

            // 4. Read Section Headers
            val sections = mutableListOf<ElfSectionHeader>()
            if (shOff > 0 && shNum > 0 && shNum < 65000) {
                val shBuffer = readBytes(channel, shOff, shEntSize * shNum).order(order)
                for (i in 0 until shNum) {
                    val sNameOffset = shBuffer.int
                    val sType = shBuffer.int
                    val sFlags = if (is64) shBuffer.long else shBuffer.int.toLong() and 0xFFFFFFFFL
                    val sAddr = if (is64) shBuffer.long else shBuffer.int.toLong() and 0xFFFFFFFFL
                    val sOffset = if (is64) shBuffer.long else shBuffer.int.toLong() and 0xFFFFFFFFL
                    val sSize = if (is64) shBuffer.long else shBuffer.int.toLong() and 0xFFFFFFFFL
                    val sLink = shBuffer.int
                    val sInfo = shBuffer.int
                    val sAlign = if (is64) shBuffer.long else shBuffer.int.toLong() and 0xFFFFFFFFL
                    val sEntSize = if (is64) shBuffer.long else shBuffer.int.toLong() and 0xFFFFFFFFL

                    sections.add(ElfSectionHeader(sNameOffset, "", sType, sFlags, sAddr, sOffset, sSize, sLink, sInfo, sAlign, sEntSize))
                }
                
                // Resolve Section Names
                if (shStrNdx < sections.size) {
                    val strTabSec = sections[shStrNdx]
                    if (strTabSec.size in 1..1024 * 1024 * 10) { // arbitrary safe limit 10MB
                        val strTab = readBytes(channel, strTabSec.offset, strTabSec.size.toInt())
                        for (sec in sections) {
                            sec.name = getString(strTab, sec.nameOffset)
                        }
                    }
                }
            }

            // 5. Dynamic Table
            val dynamicEntries = mutableListOf<ElfDynamicEntry>()
            var dynOffset = -1L
            var dynSize = 0L

            val dynPhdr = programHeaders.find { it.type == 2 /* PT_DYNAMIC */ }
            if (dynPhdr != null) {
                dynOffset = dynPhdr.offset
                dynSize = dynPhdr.fileSize
            } else {
                val dynSec = sections.find { it.type == 6 /* SHT_DYNAMIC */ }
                if (dynSec != null) {
                    dynOffset = dynSec.offset
                    dynSize = dynSec.size
                }
            }

            if (dynOffset != -1L && dynSize > 0 && dynSize < 1024 * 1024) {
                val dynBuffer = readBytes(channel, dynOffset, dynSize.toInt()).order(order)
                val entSize = if (is64) 16 else 8
                val numEnts = (dynSize / entSize).toInt()
                
                for (i in 0 until numEnts) {
                    val tag = if (is64) dynBuffer.long else dynBuffer.int.toLong() and 0xFFFFFFFFL
                    val value = if (is64) dynBuffer.long else dynBuffer.int.toLong() and 0xFFFFFFFFL
                    dynamicEntries.add(ElfDynamicEntry(tag, value))
                    if (tag == 0L) break // DT_NULL
                }
            }

            // Resolve Dynamic Strings
            val dynStrAddr = dynamicEntries.find { it.tag == 5L /* DT_STRTAB */ }?.value
            val dynStrSz = dynamicEntries.find { it.tag == 10L /* DT_STRSZ */ }?.value
            
            var dynStrBuffer: ByteBuffer? = null
            if (dynStrAddr != null && dynStrSz != null) {
                val dynStrOffset = vaToOffset(dynStrAddr, programHeaders)
                if (dynStrOffset != null && dynStrSz < 1024 * 1024 * 50) { // max 50MB
                    dynStrBuffer = readBytes(channel, dynStrOffset, dynStrSz.toInt())
                    for (entry in dynamicEntries) {
                        if (entry.tag == 1L /* DT_NEEDED */ || entry.tag == 14L /* DT_SONAME */ || entry.tag == 15L /* DT_RPATH */ || entry.tag == 29L /* DT_RUNPATH */) {
                            entry.stringValue = getString(dynStrBuffer, entry.value.toInt())
                        }
                    }
                }
            }

            // 6. Symbols
            val symbols = mutableListOf<ElfSymbol>()
            val symSec = sections.find { it.type == 11 /* SHT_DYNSYM */ } ?: sections.find { it.type == 2 /* SHT_SYMTAB */ }
            if (symSec != null) {
                val symEntSize = if (is64) 24 else 16
                if (symSec.entSize > 0 && symSec.entSize.toInt() == symEntSize && symSec.size < 1024 * 1024 * 50) {
                    val symBuffer = readBytes(channel, symSec.offset, symSec.size.toInt()).order(order)
                    val numSyms = (symSec.size / symEntSize).toInt()
                    
                    val symStrSec = if (symSec.link < sections.size) sections[symSec.link] else null
                    var symStrBuf: ByteBuffer? = null
                    if (symStrSec != null && symStrSec.size < 1024 * 1024 * 50) {
                        symStrBuf = readBytes(channel, symStrSec.offset, symStrSec.size.toInt())
                    } else if (dynStrBuffer != null && symSec.type == 11) {
                        symStrBuf = dynStrBuffer
                    }

                    for (i in 0 until numSyms) {
                        val stName: Int
                        val stValue: Long
                        val stSize: Long
                        val stInfo: Int
                        val stOther: Int
                        val stShndx: Int
                        
                        if (is64) {
                            stName = symBuffer.int
                            stInfo = symBuffer.get().toInt() and 0xFF
                            stOther = symBuffer.get().toInt() and 0xFF
                            stShndx = symBuffer.short.toInt() and 0xFFFF
                            stValue = symBuffer.long
                            stSize = symBuffer.long
                        } else {
                            stName = symBuffer.int
                            stValue = symBuffer.int.toLong() and 0xFFFFFFFFL
                            stSize = symBuffer.int.toLong() and 0xFFFFFFFFL
                            stInfo = symBuffer.get().toInt() and 0xFF
                            stOther = symBuffer.get().toInt() and 0xFF
                            stShndx = symBuffer.short.toInt() and 0xFFFF
                        }
                        
                        val name = if (symStrBuf != null) getString(symStrBuf, stName) else "str_$stName"
                        symbols.add(ElfSymbol(stName, name, stValue, stSize, stInfo, stOther, stShndx))
                    }
                }
            }

            ElfFile(header, programHeaders, sections, dynamicEntries, symbols)
        }
    }

    private fun vaToOffset(va: Long, phdirs: List<ElfProgramHeader>): Long? {
        for (phdr in phdirs) {
            if (phdr.type == 1 /* PT_LOAD */) {
                if (va >= phdr.vaddr && va < phdr.vaddr + phdr.memSize) {
                    return va - phdr.vaddr + phdr.offset
                }
            }
        }
        return null
    }

    private fun readBytes(channel: FileChannel, offset: Long, length: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(length)
        channel.position(offset)
        var read = 0
        while (read < length) {
            val r = channel.read(buffer)
            if (r == -1) throw Exception("Unexpected EOF while parsing ELF at offset $offset")
            read += r
        }
        buffer.flip()
        return buffer
    }

    private fun getString(buffer: ByteBuffer, offset: Int): String {
        if (offset < 0 || offset >= buffer.capacity()) return ""
        var end = offset
        while (end < buffer.capacity() && buffer.get(end) != 0.toByte()) {
            end++
        }
        val bytes = ByteArray(end - offset)
        val originalPos = buffer.position()
        buffer.position(offset)
        buffer.get(bytes)
        buffer.position(originalPos)
        return String(bytes, Charsets.UTF_8)
    }

    fun validateAndroidCompatibility(elf: ElfFile): ElfValidationResult {
        val msgs = mutableListOf<String>()
        var status = ElfStatus.VALID

        // 1. Check Architecture
        val isAndroidArch = elf.header.machine in listOf(40, 183, 3, 62)
        if (!isAndroidArch) {
            msgs.add("Architecture ${elf.header.architectureName} is not a standard Android ABI.")
            status = ElfStatus.WARNING
        }

        // 2. Check PT_GNU_STACK
        val gnuStack = elf.programHeaders.find { it.type == 0x6474e551 /* PT_GNU_STACK */ }
        if (gnuStack != null) {
            if ((gnuStack.flags and 1) != 0) {
                msgs.add("PT_GNU_STACK is executable. This is a severe security vulnerability and may cause Android runtime crashes.")
                status = ElfStatus.CORRUPTED
            }
        } else {
            msgs.add("Missing PT_GNU_STACK. Android might default to executable stack on older versions.")
            if (status == ElfStatus.VALID) status = ElfStatus.WARNING
        }

        // 3. Check Dynamic dependencies
        if (elf.dynamicTable.isEmpty()) {
            msgs.add("No dynamic table found. Is this a static executable?")
        } else {
            val soname = elf.soname
            if (soname == null) {
                msgs.add("Missing DT_SONAME. Android linker prefers libraries with a declared soname.")
            }
            if (!elf.neededLibraries.contains("libc.so")) {
                msgs.add("Does not link against libc.so. This is highly unusual for Android NDK binaries.")
            }
        }

        if (msgs.isEmpty()) {
            msgs.add("Library passes basic Android ABI compatibility checks.")
        }
        return ElfValidationResult(status, msgs)
    }
}
