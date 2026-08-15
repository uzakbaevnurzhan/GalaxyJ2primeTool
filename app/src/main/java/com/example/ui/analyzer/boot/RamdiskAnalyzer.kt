package com.example.ui.analyzer.boot

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

object RamdiskAnalyzer {

    fun analyze(ramdiskBytes: ByteArray): RamdiskDetailsInfo {
        if (ramdiskBytes.isEmpty()) {
            return RamdiskDetailsInfo(
                present = false,
                size = 0,
                compression = "none",
                cpioEntriesCount = 0,
                fileTree = emptyList(),
                foundKeyFiles = emptyList(),
                notes = "No ramdisk found in boot image"
            )
        }

        val compression = detectCompression(ramdiskBytes)
        val cpioBytes = tryDecompress(ramdiskBytes, compression) ?: ramdiskBytes
        val (entries, keyFiles, isCorrupt, notes) = parseCpioArchive(cpioBytes)

        val fileTree = buildFileTree(entries)

        return RamdiskDetailsInfo(
            present = true,
            size = ramdiskBytes.size.toLong(),
            compression = compression,
            cpioEntriesCount = entries.size,
            fileTree = fileTree,
            foundKeyFiles = keyFiles,
            isCorrupt = isCorrupt,
            notes = notes
        )
    }

    private fun detectCompression(bytes: ByteArray): String {
        if (bytes.size < 6) return "raw"
        // Gzip: 1F 8B
        if (bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) return "gzip"
        // LZ4: 04 22 4D 18
        if (bytes[0] == 0x04.toByte() && bytes[1] == 0x22.toByte() && bytes[2] == 0x4D.toByte() && bytes[3] == 0x18.toByte()) return "lz4"
        // XZ: FD 37 7A 58 5A 00
        if (bytes[0] == 0xFD.toByte() && bytes[1] == 0x37.toByte() && bytes[2] == 0x7A.toByte() && bytes[3] == 0x58.toByte()) return "xz"
        // ZSTD: 28 B5 2F FD
        if (bytes[0] == 0x28.toByte() && bytes[1] == 0xB5.toByte() && bytes[2] == 0x2F.toByte() && bytes[3] == 0xFD.toByte()) return "zstd"
        // BZIP2: 'B' 'Z' 'h'
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'Z'.code.toByte() && bytes[2] == 'h'.code.toByte()) return "bzip2"
        // CPIO newc magic: "070701" or "070702"
        if (bytes.size >= 6) {
            val magic = String(bytes, 0, 6, Charsets.US_ASCII)
            if (magic == "070701" || magic == "070702" || magic == "070707") return "cpio_raw"
        }
        return "unknown"
    }

    private fun tryDecompress(bytes: ByteArray, compression: String): ByteArray? {
        return try {
            when (compression) {
                "gzip" -> GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class ParsedCpioItem(val path: String, val size: Long, val isDir: Boolean)

    private fun parseCpioArchive(bytes: ByteArray): CpioParseResult {
        val entries = mutableListOf<ParsedCpioItem>()
        val keyFiles = mutableListOf<String>()
        var offset = 0
        var isCorrupt = false
        var notes = "Extracted CPIO structure successfully"

        val keyFileNames = setOf(
            "init", "init.rc", "init.environ.rc", "init.usb.rc", "ueventd.rc",
            "default.prop", "prop.default", "fstab", "sepolicy", "etc", "sbin"
        )

        while (offset + 110 <= bytes.size) {
            val magic = String(bytes, offset, 6, Charsets.US_ASCII)
            if (magic != "070701" && magic != "070702") {
                // If not standard ASCII CPIO, stop or check trailer
                if (entries.isEmpty()) {
                    isCorrupt = true
                    notes = "Not a recognized CPIO newc archive (Magic: $magic)"
                }
                break
            }

            val mode = parseHex(bytes, offset + 14, 8)
            val fileSize = parseHex(bytes, offset + 54, 8)
            val namesize = parseHex(bytes, offset + 94, 8).toInt()

            val nameOffset = offset + 110
            if (nameOffset + namesize > bytes.size) {
                isCorrupt = true
                break
            }

            var name = String(bytes, nameOffset, namesize.coerceAtLeast(1) - 1, Charsets.UTF_8).trim()
            if (name == "TRAILER!!!") {
                break
            }

            if (name.startsWith("./")) {
                name = name.substring(2)
            }
            if (name.isEmpty()) name = "/"

            val isDir = (mode and 0x4000L) != 0L

            entries.add(ParsedCpioItem(name, fileSize, isDir))

            // Check if key file
            val baseName = name.substringAfterLast('/')
            if (keyFileNames.contains(baseName) || baseName.startsWith("init.") || baseName.startsWith("fstab.")) {
                keyFiles.add(name)
            }

            // Align header + namesize to 4 bytes
            val headLen = 110 + namesize
            val alignedHeadLen = ((headLen + 3) / 4) * 4

            // Align file data to 4 bytes
            val alignedDataLen = ((fileSize + 3) / 4) * 4

            offset += alignedHeadLen + alignedDataLen.toInt()
        }

        if (entries.isEmpty() && !isCorrupt) {
            notes = "CPIO archive parsed but contained 0 file entries."
        }

        return CpioParseResult(entries, keyFiles.distinct(), isCorrupt, notes)
    }

    private fun parseHex(bytes: ByteArray, offset: Int, len: Int): Long {
        if (offset + len > bytes.size) return 0L
        val hexStr = String(bytes, offset, len, Charsets.US_ASCII)
        return hexStr.toLongOrNull(16) ?: 0L
    }

    private fun buildFileTree(entries: List<ParsedCpioItem>): List<RamdiskEntryNode> {
        val root = RamdiskEntryNode("RAMDISK", "/", isDirectory = true)
        val nodeMap = mutableMapOf<String, RamdiskEntryNode>("/" to root)

        for (entry in entries.sortedBy { it.path }) {
            if (entry.path == "/" || entry.path.isEmpty()) continue

            val parts = entry.path.split('/')
            var currentPath = ""
            var parentNode = root

            for (i in parts.indices) {
                val part = parts[i]
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                val isLast = (i == parts.lastIndex)

                val existing = nodeMap[currentPath]
                if (existing != null) {
                    parentNode = existing
                } else {
                    val newNode = RamdiskEntryNode(
                        name = part,
                        fullPath = currentPath,
                        isDirectory = if (isLast) entry.isDir else true,
                        size = if (isLast) entry.size else 0
                    )
                    parentNode.children.add(newNode)
                    nodeMap[currentPath] = newNode
                    parentNode = newNode
                }
            }
        }

        return listOf(root)
    }

    private data class CpioParseResult(
        val entries: List<ParsedCpioItem>,
        val keyFiles: List<String>,
        val isCorrupt: Boolean,
        val notes: String
    )
}
