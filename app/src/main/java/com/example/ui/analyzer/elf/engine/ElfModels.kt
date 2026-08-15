package com.example.ui.analyzer.elf.engine

enum class ElfClass { ELF32, ELF64, UNKNOWN }
enum class ElfEndian { LITTLE, BIG, UNKNOWN }

data class ElfHeader(
    val elfClass: ElfClass,
    val endian: ElfEndian,
    val osAbi: Int,
    val abiVersion: Int,
    val type: Int,
    val machine: Int,
    val version: Int,
    val entryPoint: Long,
    val phOff: Long,
    val shOff: Long,
    val flags: Int,
    val ehSize: Int,
    val phEntSize: Int,
    val phNum: Int,
    val shEntSize: Int,
    val shNum: Int,
    val shStrNdx: Int
) {
    val architectureName: String
        get() = when (machine) {
            3 -> "x86"
            8 -> "MIPS"
            40 -> "ARM32"
            62 -> "x86_64"
            183 -> "AArch64"
            243 -> "RISC-V"
            else -> "Unknown (0x${machine.toString(16)})"
        }
}

data class ElfProgramHeader(
    val type: Int,
    val offset: Long,
    val vaddr: Long,
    val paddr: Long,
    val fileSize: Long,
    val memSize: Long,
    val flags: Int,
    val align: Long
) {
    val typeName: String
        get() = when (type) {
            0 -> "PT_NULL"
            1 -> "PT_LOAD"
            2 -> "PT_DYNAMIC"
            3 -> "PT_INTERP"
            4 -> "PT_NOTE"
            5 -> "PT_SHLIB"
            6 -> "PT_PHDR"
            7 -> "PT_TLS"
            0x6474e550 -> "PT_GNU_EH_FRAME"
            0x6474e551 -> "PT_GNU_STACK"
            0x6474e552 -> "PT_GNU_RELRO"
            0x6ffffffa -> "PT_LOSUNW"
            0x6ffffffb -> "PT_SUNWBSS"
            0x6ffffffc -> "PT_SUNWSTACK"
            else -> if (type in 0x70000000..0x7fffffff) "PT_LOPROC+" else "UNKNOWN (0x${type.toString(16)})"
        }

    val flagsString: String
        get() {
            val r = if ((flags and 4) != 0) "R" else "-"
            val w = if ((flags and 2) != 0) "W" else "-"
            val x = if ((flags and 1) != 0) "X" else "-"
            return "$r$w$x"
        }
}

data class ElfSectionHeader(
    val nameOffset: Int,
    var name: String,
    val type: Int,
    val flags: Long,
    val addr: Long,
    val offset: Long,
    val size: Long,
    val link: Int,
    val info: Int,
    val align: Long,
    val entSize: Long
) {
    val typeName: String
        get() = when (type) {
            0 -> "SHT_NULL"
            1 -> "SHT_PROGBITS"
            2 -> "SHT_SYMTAB"
            3 -> "SHT_STRTAB"
            4 -> "SHT_RELA"
            5 -> "SHT_HASH"
            6 -> "SHT_DYNAMIC"
            7 -> "SHT_NOTE"
            8 -> "SHT_NOBITS"
            9 -> "SHT_REL"
            10 -> "SHT_SHLIB"
            11 -> "SHT_DYNSYM"
            14 -> "SHT_INIT_ARRAY"
            15 -> "SHT_FINI_ARRAY"
            0x6ffffff6 -> "SHT_GNU_HASH"
            0x6ffffffe -> "SHT_GNU_verneed"
            0x6fffffff -> "SHT_GNU_versym"
            else -> "UNKNOWN (0x${type.toString(16)})"
        }
}

data class ElfDynamicEntry(
    val tag: Long,
    val value: Long,
    var stringValue: String? = null
) {
    val tagName: String
        get() = when (tag) {
            0L -> "DT_NULL"
            1L -> "DT_NEEDED"
            2L -> "DT_PLTRELSZ"
            3L -> "DT_PLTGOT"
            4L -> "DT_HASH"
            5L -> "DT_STRTAB"
            6L -> "DT_SYMTAB"
            7L -> "DT_RELA"
            8L -> "DT_RELASZ"
            9L -> "DT_RELAENT"
            10L -> "DT_STRSZ"
            11L -> "DT_SYMENT"
            12L -> "DT_INIT"
            13L -> "DT_FINI"
            14L -> "DT_SONAME"
            15L -> "DT_RPATH"
            16L -> "DT_SYMBOLIC"
            17L -> "DT_REL"
            18L -> "DT_RELSZ"
            19L -> "DT_RELENT"
            20L -> "DT_PLTREL"
            21L -> "DT_DEBUG"
            22L -> "DT_TEXTREL"
            23L -> "DT_JMPREL"
            24L -> "DT_BIND_NOW"
            25L -> "DT_INIT_ARRAY"
            26L -> "DT_FINI_ARRAY"
            27L -> "DT_INIT_ARRAYSZ"
            28L -> "DT_FINI_ARRAYSZ"
            29L -> "DT_RUNPATH"
            30L -> "DT_FLAGS"
            0x6ffffef5L -> "DT_GNU_HASH"
            0x6ffffffbL -> "DT_FLAGS_1"
            0x6ffffffaL -> "DT_RELCOUNT"
            else -> "UNKNOWN (0x${tag.toString(16)})"
        }
}

data class ElfSymbol(
    val nameOffset: Int,
    var name: String,
    val value: Long,
    val size: Long,
    val info: Int,
    val other: Int,
    val shndx: Int
) {
    val binding: String
        get() = when (info shr 4) {
            0 -> "LOCAL"
            1 -> "GLOBAL"
            2 -> "WEAK"
            else -> "UNKNOWN"
        }

    val type: String
        get() = when (info and 0xF) {
            0 -> "NOTYPE"
            1 -> "OBJECT"
            2 -> "FUNC"
            3 -> "SECTION"
            4 -> "FILE"
            5 -> "COMMON"
            6 -> "TLS"
            else -> "UNKNOWN"
        }
        
    val visibility: String
        get() = when (other and 0x3) {
            0 -> "DEFAULT"
            1 -> "INTERNAL"
            2 -> "HIDDEN"
            3 -> "PROTECTED"
            else -> "UNKNOWN"
        }
}

data class ElfFile(
    val header: ElfHeader,
    val programHeaders: List<ElfProgramHeader>,
    val sections: List<ElfSectionHeader>,
    val dynamicTable: List<ElfDynamicEntry>,
    val symbols: List<ElfSymbol>
) {
    val soname: String?
        get() = dynamicTable.find { it.tag == 14L }?.stringValue
        
    val neededLibraries: List<String>
        get() = dynamicTable.filter { it.tag == 1L }.mapNotNull { it.stringValue }
        
    val exports: List<ElfSymbol>
        get() = symbols.filter { it.shndx != 0 && it.binding != "LOCAL" }
        
    val imports: List<ElfSymbol>
        get() = symbols.filter { it.shndx == 0 && (it.binding == "GLOBAL" || it.binding == "WEAK") }
}

enum class ElfStatus { VALID, WARNING, CORRUPTED, UNSUPPORTED }

data class ElfValidationResult(
    val status: ElfStatus,
    val messages: List<String>
)
