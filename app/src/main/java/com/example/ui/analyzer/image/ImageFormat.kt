package com.example.ui.analyzer.image

enum class ImageFormat(
    val displayName: String,
    val description: String,
    val defaultExtension: String,
    val isCompressed: Boolean
) {
    RAW("RAW Image", "Uncompressed byte-for-byte partition image", ".img", false),
    SPARSE("Android Sparse Image", "Google sparse format containing non-empty chunks (simg)", ".img", true),
    EXT4("EXT4 Filesystem", "Standard Linux extended filesystem version 4", ".img", false),
    EROFS("EROFS Filesystem", "Enhanced Read-Only Filesystem for modern Android (Android 11+)", ".img", true),
    F2FS("F2FS Filesystem", "Flash-Friendly File System designed for NAND flash storage", ".img", false),
    SQUASHFS("SquashFS", "Compressed read-only Linux filesystem", ".img", true),
    SUPER("Android Super Image", "Android Dynamic Partitions (LP metadata container)", "super.img", false),
    DAT("Android Block OTA (DAT)", "Android transfer.list + new.dat block-based OTA payload", ".dat", false),
    DAT_BR("Android Brotli OTA (DAT.BR)", "Brotli-compressed transfer.list + new.dat.br block-based OTA", ".dat.br", true),
    PAYLOAD_BIN("Android OTA Payload", "ChromeOS / Android Seamless A/B OTA payload.bin", "payload.bin", true),
    BOOT_IMG("Android Boot Image", "Android kernel and ramdisk container (boot/recovery)", "boot.img", false),
    UNKNOWN("Unknown / Unrecognized", "Unrecognized partition image or raw binary stream", ".bin", false);

    companion object {
        fun fromExtension(fileName: String): ImageFormat {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".new.dat.br") -> DAT_BR
                lower.endsWith(".new.dat") -> DAT
                lower.endsWith(".transfer.list") -> DAT
                lower == "payload.bin" || lower.endsWith("/payload.bin") -> PAYLOAD_BIN
                lower == "super.img" || lower.endsWith("/super.img") -> SUPER
                lower == "boot.img" || lower == "recovery.img" -> BOOT_IMG
                lower.endsWith(".simg") -> SPARSE
                lower.endsWith(".img") || lower.endsWith(".raw") || lower.endsWith(".bin") -> RAW
                else -> UNKNOWN
            }
        }
    }
}
