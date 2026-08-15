package com.example.ui.analyzer.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object SuperImageAnalyzer {
    const val LP_PARTITION_RESERVED_BYTES = 4096L
    const val LP_METADATA_GEOMETRY_MAGIC = 0x616C6F67 // "gola"
    const val LP_METADATA_HEADER_MAGIC = 0x414C5030 // "0PLA"

    const val LP_PARTITION_ATTR_READONLY = 0x0001
    const val LP_PARTITION_ATTR_SLOT_SUFFIXED = 0x0002
    const val LP_PARTITION_ATTR_UPDATED = 0x0004
    const val LP_PARTITION_ATTR_DISABLED = 0x0008

    const val LP_TARGET_TYPE_LINEAR = 0
    const val LP_TARGET_TYPE_ZERO = 1

    data class SuperAnalysisResult(
        val metadata: ImageMetadata,
        val partitions: List<ImagePartition>,
        val groups: List<String>,
        val totalAllocatedBytes: Long
    )

    fun analyze(channel: FileChannel): SuperAnalysisResult? {
        val originalPos = channel.position()
        try {
            // Check for geometry header at offset 4096 or offset 0
            var geometryOffset = LP_PARTITION_RESERVED_BYTES
            if (channel.size() < geometryOffset + 64) {
                geometryOffset = 0L
            }

            channel.position(geometryOffset)
            var buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            var read = channel.read(buffer)
            if (read < 64) return null
            buffer.flip()

            var magic = buffer.getInt(0)
            if (magic != LP_METADATA_GEOMETRY_MAGIC && magic != ImageFormatDetector.LP_METADATA_GEOMETRY_MAGIC_REV) {
                // Try offset 0
                channel.position(0)
                buffer.clear()
                read = channel.read(buffer)
                if (read < 64) return null
                buffer.flip()
                magic = buffer.getInt(0)
                if (magic != LP_METADATA_GEOMETRY_MAGIC && magic != ImageFormatDetector.LP_METADATA_GEOMETRY_MAGIC_REV) {
                    return null
                }
                geometryOffset = 0L
            }

            val structSize = buffer.getInt(4)
            val metadataMaxSize = buffer.getInt(40)
            val metadataSlotCount = buffer.getInt(44)
            val logicalBlockSize = buffer.getInt(48)

            // Primary metadata header follows geometry header (usually at geometryOffset + 4096)
            val headerOffset = geometryOffset + 4096L
            channel.position(headerOffset)
            buffer = ByteBuffer.allocate(if (metadataMaxSize in 4096..1048576) metadataMaxSize else 65536).order(ByteOrder.LITTLE_ENDIAN)
            read = channel.read(buffer)
            if (read < 128) return null
            buffer.flip()

            val headerMagic = buffer.getInt(0)
            if (headerMagic != LP_METADATA_HEADER_MAGIC) {
                return null
            }

            val majorVersion = buffer.getShort(4).toInt() and 0xFFFF
            val minorVersion = buffer.getShort(6).toInt() and 0xFFFF
            val headerSize = buffer.getInt(8)
            val tablesSize = buffer.getInt(44)

            // Tables descriptor (partitions table at offset 80, extents at 92, groups at 104, block_devs at 116)
            val partTableOffset = buffer.getInt(80)
            val partNumEntries = buffer.getInt(84)
            val partEntrySize = buffer.getInt(88)

            val extTableOffset = buffer.getInt(92)
            val extNumEntries = buffer.getInt(96)
            val extEntrySize = buffer.getInt(100)

            val grpTableOffset = buffer.getInt(104)
            val grpNumEntries = buffer.getInt(108)
            val grpEntrySize = buffer.getInt(112)

            val baseTablesOffset = headerSize

            // Read Groups
            val groupsList = mutableListOf<String>()
            for (i in 0 until grpNumEntries) {
                val offset = baseTablesOffset + grpTableOffset + (i * grpEntrySize)
                if (offset + 36 <= buffer.limit()) {
                    val nameBytes = ByteArray(36)
                    buffer.position(offset)
                    buffer.get(nameBytes)
                    val gName = String(nameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    if (gName.isNotEmpty()) groupsList.add(gName)
                }
            }

            // Read Extents
            val extentsList = mutableListOf<PartitionExtent>()
            for (i in 0 until extNumEntries) {
                val offset = baseTablesOffset + extTableOffset + (i * extEntrySize)
                if (offset + 24 <= buffer.limit()) {
                    buffer.position(offset)
                    val numSectors = buffer.getLong()
                    val targetType = buffer.getInt()
                    val targetData = buffer.getLong()
                    val targetSource = buffer.getInt()

                    extentsList.add(
                        PartitionExtent(
                            targetType = if (targetType == LP_TARGET_TYPE_ZERO) "ZERO" else "LINEAR",
                            targetData = targetData,
                            numSectors = numSectors,
                            targetBlockDevice = "super_blk_$targetSource"
                        )
                    )
                }
            }

            // Read Partitions
            val partitionsList = mutableListOf<ImagePartition>()
            var totalAllocatedBytes = 0L

            for (i in 0 until partNumEntries) {
                val offset = baseTablesOffset + partTableOffset + (i * partEntrySize)
                if (offset + 52 <= buffer.limit()) {
                    buffer.position(offset)
                    val nameBytes = ByteArray(36)
                    buffer.get(nameBytes)
                    val pName = String(nameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }

                    if (pName.isNotEmpty()) {
                        val attributes = buffer.getInt()
                        val firstExtentIndex = buffer.getInt()
                        val numExtents = buffer.getInt()
                        val groupIndex = buffer.getInt()

                        val attrList = mutableListOf<String>()
                        if ((attributes and LP_PARTITION_ATTR_READONLY) != 0) attrList.add("readonly")
                        if ((attributes and LP_PARTITION_ATTR_SLOT_SUFFIXED) != 0) attrList.add("slot_suffixed")
                        if ((attributes and LP_PARTITION_ATTR_UPDATED) != 0) attrList.add("updated")
                        if ((attributes and LP_PARTITION_ATTR_DISABLED) != 0) attrList.add("disabled")

                        val pExtents = mutableListOf<PartitionExtent>()
                        var pSizeBytes = 0L
                        var pStartOffset = 0L

                        for (e in 0 until numExtents) {
                            val extIdx = firstExtentIndex + e
                            if (extIdx in extentsList.indices) {
                                val ext = extentsList[extIdx]
                                pExtents.add(ext)
                                val extBytes = ext.numSectors * 512L
                                pSizeBytes += extBytes
                                if (e == 0 && ext.targetType == "LINEAR") {
                                    pStartOffset = ext.targetData * 512L
                                }
                            }
                        }

                        totalAllocatedBytes += pSizeBytes
                        val groupName = if (groupIndex in groupsList.indices) groupsList[groupIndex] else "default"

                        // Probe filesystem of inner partition if accessible
                        var innerFs = ImageFormat.UNKNOWN
                        if (pStartOffset > 0 && pStartOffset + 2048 <= channel.size()) {
                            try {
                                val probeBuf = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
                                channel.position(pStartOffset)
                                val pr = channel.read(probeBuf)
                                if (pr >= 1084) {
                                    probeBuf.flip()
                                    innerFs = ImageFormatDetector.detectFromBuffer(probeBuf)
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }

                        partitionsList.add(
                            ImagePartition(
                                name = pName,
                                sizeBytes = pSizeBytes,
                                startOffset = pStartOffset,
                                blockCount = if (logicalBlockSize > 0) pSizeBytes / logicalBlockSize else 0L,
                                filesystem = innerFs,
                                isReadOnly = (attributes and LP_PARTITION_ATTR_READONLY) != 0,
                                groupName = groupName,
                                attributes = attrList,
                                extents = pExtents
                            )
                        )
                    }
                }
            }

            val rawFields = linkedMapOf<String, String>()
            rawFields["geometry_magic"] = "0x${Integer.toHexString(magic).uppercase()}"
            rawFields["header_magic"] = "0x${Integer.toHexString(headerMagic).uppercase()} (LP_METADATA)"
            rawFields["version"] = "$majorVersion.$minorVersion"
            rawFields["logical_block_size"] = "$logicalBlockSize bytes"
            rawFields["slot_count"] = metadataSlotCount.toString()
            rawFields["partitions_count"] = partitionsList.size.toString()
            rawFields["groups_count"] = groupsList.size.toString()
            rawFields["groups"] = groupsList.joinToString(", ")

            val featuresList = listOf(
                "Android Dynamic Partitions (Super)",
                "Logical Block Size: $logicalBlockSize",
                "Groups: ${groupsList.joinToString(", ")}"
            )

            val flagsList = listOf(
                "Partitions: ${partitionsList.size}",
                "Allocated: ${formatBytes(totalAllocatedBytes)}",
                "Version $majorVersion.$minorVersion"
            )

            val meta = ImageMetadata(
                fileName = "super.img",
                fileSize = channel.size(),
                format = ImageFormat.SUPER,
                magicString = "0x616C6F67",
                magicHex = "0x${Integer.toHexString(magic).uppercase()}",
                blockSize = if (logicalBlockSize > 0) logicalBlockSize else 4096,
                totalBlocks = if (logicalBlockSize > 0) totalAllocatedBytes / logicalBlockSize else 0L,
                uncompressedSize = totalAllocatedBytes,
                compressionRatio = 1.0,
                filesystemType = "LP Metadata (Super)",
                volumeName = "super",
                uuid = "",
                isReadOnly = true,
                inodeCount = 0L,
                freeInodes = 0L,
                freeBlocks = 0L,
                usedBlocks = if (logicalBlockSize > 0) totalAllocatedBytes / logicalBlockSize else 0L,
                features = featuresList,
                flags = flagsList,
                mountPointHint = "/super",
                rawHeaderFields = rawFields
            )

            return SuperAnalysisResult(meta, partitionsList, groupsList, totalAllocatedBytes)
        } catch (e: Exception) {
            return null
        } finally {
            channel.position(originalPos)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        return when {
            bytes >= gib -> String.format(java.util.Locale.US, "%.2f GB", bytes / gib)
            bytes >= mib -> String.format(java.util.Locale.US, "%.2f MB", bytes / mib)
            bytes >= kib -> String.format(java.util.Locale.US, "%.2f KB", bytes / kib)
            else -> "$bytes B"
        }
    }
}
