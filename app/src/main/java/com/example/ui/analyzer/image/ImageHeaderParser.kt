package com.example.ui.analyzer.image

import java.nio.channels.FileChannel

object ImageHeaderParser {
    fun parseHeader(channel: FileChannel, format: ImageFormat, fileName: String = ""): ImageMetadata {
        return when (format) {
            ImageFormat.EXT4 -> {
                val meta = Ext4Analyzer.parseSuperblock(channel) ?: fallbackMetadata(channel, format, fileName)
                meta.copy(fileName = fileName)
            }
            ImageFormat.EROFS -> {
                val meta = ErofsAnalyzer.parseSuperblock(channel) ?: fallbackMetadata(channel, format, fileName)
                meta.copy(fileName = fileName)
            }
            ImageFormat.F2FS -> {
                val meta = F2fsAnalyzer.parseSuperblock(channel) ?: fallbackMetadata(channel, format, fileName)
                meta.copy(fileName = fileName)
            }
            ImageFormat.SQUASHFS -> {
                val meta = SquashFsAnalyzer.parseSuperblock(channel) ?: fallbackMetadata(channel, format, fileName)
                meta.copy(fileName = fileName)
            }
            ImageFormat.SUPER -> {
                val res = SuperImageAnalyzer.analyze(channel)
                res?.metadata?.copy(fileName = fileName) ?: fallbackMetadata(channel, format, fileName)
            }
            ImageFormat.SPARSE -> {
                val sparseInfo = SparseImageParser.parseHeader(channel)
                if (sparseInfo != null) {
                    val (hdr, stats) = sparseInfo
                    val rawSize = hdr.totalBlks * hdr.blkSz.toLong()
                    val rawFields = linkedMapOf<String, String>()
                    rawFields["magic"] = "0x${Integer.toHexString(hdr.magic).uppercase()}"
                    rawFields["version"] = "${hdr.majorVersion}.${hdr.minorVersion}"
                    rawFields["file_header_size"] = "${hdr.fileHdrSz} bytes"
                    rawFields["chunk_header_size"] = "${hdr.chunkHdrSz} bytes"
                    rawFields["block_size"] = "${hdr.blkSz} bytes"
                    rawFields["total_blocks"] = "${hdr.totalBlks}"
                    rawFields["total_chunks"] = "${hdr.totalChunks}"
                    rawFields["raw_chunks"] = "${stats.rawChunks} (${stats.rawBlocks} blocks)"
                    rawFields["fill_chunks"] = "${stats.fillChunks} (${stats.fillBlocks} blocks)"
                    rawFields["dont_care_chunks"] = "${stats.dontCareChunks} (${stats.dontCareBlocks} blocks)"
                    rawFields["crc_chunks"] = "${stats.crcChunks}"

                    ImageMetadata(
                        fileName = fileName,
                        fileSize = channel.size(),
                        format = ImageFormat.SPARSE,
                        magicString = "0xED26FF3A",
                        magicHex = "0xED26FF3A",
                        blockSize = hdr.blkSz,
                        totalBlocks = hdr.totalBlks,
                        uncompressedSize = rawSize,
                        compressionRatio = if (channel.size() > 0 && rawSize > 0) rawSize.toDouble() / channel.size().toDouble() else 1.0,
                        filesystemType = "Sparse Image",
                        volumeName = "",
                        uuid = "",
                        isReadOnly = false,
                        inodeCount = 0L,
                        freeInodes = 0L,
                        freeBlocks = stats.dontCareBlocks,
                        usedBlocks = stats.rawBlocks + stats.fillBlocks,
                        features = listOf("Android Sparse Format v${hdr.majorVersion}.${hdr.minorVersion}"),
                        flags = listOf("Chunks: ${hdr.totalChunks}", "Blocks: ${hdr.totalBlks}"),
                        mountPointHint = "",
                        rawHeaderFields = rawFields
                    )
                } else {
                    fallbackMetadata(channel, format, fileName)
                }
            }
            else -> fallbackMetadata(channel, format, fileName)
        }
    }

    private fun fallbackMetadata(channel: FileChannel, format: ImageFormat, fileName: String): ImageMetadata {
        val size = try { channel.size() } catch (e: Exception) { 0L }
        return ImageMetadata(
            fileName = fileName,
            fileSize = size,
            format = format,
            magicString = "",
            magicHex = "",
            blockSize = 4096,
            totalBlocks = (size + 4095) / 4096,
            uncompressedSize = size,
            compressionRatio = 1.0,
            filesystemType = format.displayName,
            volumeName = "",
            uuid = "",
            isReadOnly = false,
            inodeCount = 0L,
            freeInodes = 0L,
            freeBlocks = 0L,
            usedBlocks = (size + 4095) / 4096,
            features = emptyList(),
            flags = emptyList(),
            mountPointHint = "",
            rawHeaderFields = mapOf("file_size" to "$size bytes", "format" to format.displayName)
        )
    }
}
