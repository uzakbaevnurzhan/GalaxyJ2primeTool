package com.example.ui.studio.rom

import java.io.File
import java.io.RandomAccessFile

enum class RomFormat {
    BOOT_IMG,
    SPARSE,
    EXT4,
    EROFS,
    DAT,
    DAT_BR,
    SUPER,
    CPIO,
    UNKNOWN
}

object RomFormatDetector {
    fun detect(file: File): RomFormat {
        if (!file.exists() || !file.isFile) return RomFormat.UNKNOWN
        
        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                if (raf.read(magic) != 4) return RomFormat.UNKNOWN
                
                if (magic[0] == 'A'.code.toByte() && magic[1] == 'N'.code.toByte() && magic[2] == 'D'.code.toByte() && magic[3] == 'R'.code.toByte()) {
                    return RomFormat.BOOT_IMG
                }
                
                // Sparse magic: 0xed26ff3a
                if (magic[0] == 0x3a.toByte() && magic[1] == 0xff.toByte() && magic[2] == 0x26.toByte() && magic[3] == 0xed.toByte()) {
                    return RomFormat.SPARSE
                }
                
                // EXT4 magic: 0x53EF at offset 0x438
                if (raf.length() > 0x438 + 2) {
                    raf.seek(0x438)
                    val ext4Magic = ByteArray(2)
                    raf.read(ext4Magic)
                    if (ext4Magic[0] == 0x53.toByte() && ext4Magic[1] == 0xEF.toByte()) {
                        return RomFormat.EXT4
                    }
                }
                
                // EROFS magic: 0xE2E1F5E0 at offset 1024
                if (raf.length() > 1024 + 4) {
                    raf.seek(1024)
                    val erofsMagic = ByteArray(4)
                    raf.read(erofsMagic)
                    if (erofsMagic[0] == 0xE2.toByte() && erofsMagic[1] == 0xE1.toByte() && erofsMagic[2] == 0xF5.toByte() && erofsMagic[3] == 0xE0.toByte()) {
                        return RomFormat.EROFS
                    }
                }
                
                // DAT
                if (file.name.endsWith(".dat")) return RomFormat.DAT
                
                // DAT.BR
                if (file.name.endsWith(".dat.br")) return RomFormat.DAT_BR
                
                // CPIO magic: "070701"
                raf.seek(0)
                val cpioMagic = ByteArray(6)
                if (raf.read(cpioMagic) == 6) {
                    val str = String(cpioMagic)
                    if (str == "070701" || str == "070702" || str == "070707") return RomFormat.CPIO
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return RomFormat.UNKNOWN
    }
}
