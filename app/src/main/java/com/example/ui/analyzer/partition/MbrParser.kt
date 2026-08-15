package com.example.ui.analyzer.partition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object MbrParser {
    const val MBR_SIGNATURE = 0xAA55
    const val SECTOR_SIZE = 512

    data class MbrParseResult(
        val table: PartitionTable,
        val issues: List<PartitionIssue>
    )

    fun parse(channel: FileChannel): MbrParseResult? {
        val origPos = channel.position()
        try {
            if (channel.size() < SECTOR_SIZE) return null
            channel.position(0)
            val buf = ByteBuffer.allocate(SECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val read = channel.read(buf)
            if (read < SECTOR_SIZE) return null
            buf.flip()

            val sig = buf.getShort(510).toInt() and 0xFFFF
            if (sig != MBR_SIGNATURE) {
                return null
            }

            return parseBuffer(buf, channel, channel.size())
        } catch (e: Exception) {
            return null
        } finally {
            channel.position(origPos)
        }
    }

    fun parseBuffer(buf: ByteBuffer, channel: FileChannel?, fileSize: Long): MbrParseResult {
        val issues = mutableListOf<PartitionIssue>()
        val partitions = mutableListOf<PartitionEntry>()

        val sig = buf.getShort(510).toInt() and 0xFFFF
        val isMbrSigValid = (sig == MBR_SIGNATURE)

        var hasProtectiveGpt = false
        var activePartitionsCount = 0

        // Parse 4 primary partition records at 0x1BE (446)
        for (i in 0 until 4) {
            val entryOffset = 446 + (i * 16)
            val status = buf.get(entryOffset).toInt() and 0xFF
            val typeId = buf.get(entryOffset + 4).toInt() and 0xFF
            val startLba = buf.getInt(entryOffset + 8).toLong() and 0xFFFFFFFFL
            val sectorCount = buf.getInt(entryOffset + 12).toLong() and 0xFFFFFFFFL

            if (typeId == 0x00 && sectorCount == 0L) {
                continue // Empty slot
            }

            if (typeId == 0xEE) {
                hasProtectiveGpt = true
            }

            val isBootable = (status == 0x80)
            if (isBootable) {
                activePartitionsCount++
            } else if (status != 0x00) {
                issues.add(
                    PartitionIssue(
                        id = "MBR_INVALID_STATUS_BYTE_$i",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Invalid MBR Boot Indicator (Slot ${i + 1})",
                        description = "Status byte is 0x${Integer.toHexString(status).uppercase()} instead of standard 0x80 or 0x00.",
                        affectedPartition = "Slot ${i + 1}",
                        category = "Standards Compliance"
                    )
                )
            }

            val sizeBytes = sectorCount * SECTOR_SIZE
            val endLba = if (sectorCount > 0) startLba + sectorCount - 1 else startLba
            val typeDesc = mapMbrTypeIdToDescription(typeId)
            val typeHex = "0x" + String.format("%02X", typeId)

            val partName = when (typeId) {
                0xEE -> "Protective GPT"
                0x83 -> "Linux_$i"
                0x82 -> "Linux_Swap_$i"
                0x0C, 0x0B -> "FAT32_$i"
                0x07 -> "NTFS_exFAT_$i"
                0x05, 0x0F -> "Extended_$i"
                else -> "Partition_${i + 1}"
            }

            // Check boundaries
            if (fileSize > 0 && (startLba + sectorCount) * SECTOR_SIZE > fileSize + (1024 * 1024)) {
                issues.add(
                    PartitionIssue(
                        id = "MBR_PARTITION_EXCEEDS_IMAGE_$i",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Partition Exceeds Image Size ($partName)",
                        description = "Partition ending sector (${startLba + sectorCount}) exceeds image size ($fileSize bytes).",
                        affectedPartition = partName,
                        category = "Geometry"
                    )
                )
            }

            partitions.add(
                PartitionEntry(
                    index = i + 1,
                    name = partName,
                    startLba = startLba,
                    endLba = endLba,
                    startByteOffset = startLba * SECTOR_SIZE,
                    sizeBytes = sizeBytes,
                    typeGuidOrId = typeHex,
                    typeDescription = typeDesc,
                    isBootable = isBootable,
                    sectorSize = SECTOR_SIZE,
                    originalFileName = "$partName.img"
                )
            )

            // If extended partition and channel available, parse logical partitions
            if ((typeId == 0x05 || typeId == 0x0F) && channel != null && sectorCount > 0) {
                parseExtendedPartitions(channel, startLba, partitions, issues)
            }
        }

        if (activePartitionsCount > 1) {
            issues.add(
                PartitionIssue(
                    id = "MBR_MULTIPLE_ACTIVE_PARTITIONS",
                    severity = PartitionIssueSeverity.WARNING,
                    title = "Multiple Active/Bootable Partitions Detected ($activePartitionsCount)",
                    description = "Traditional MBR allows only 1 active boot partition.",
                    recommendation = "Verify bootable flag for each partition.",
                    category = "Bootability"
                )
            )
        }

        GptParser.checkPartitionOverlaps(partitions, issues)

        val tableType = if (hasProtectiveGpt) PartitionTableType.HYBRID else PartitionTableType.MBR
        val rawFields = linkedMapOf<String, String>()
        rawFields["MBR Signature"] = "0x${Integer.toHexString(sig).uppercase()}"
        rawFields["Is Signature Valid"] = isMbrSigValid.toString()
        rawFields["Has Protective GPT"] = hasProtectiveGpt.toString()
        rawFields["Primary Partitions Count"] = partitions.size.toString()
        rawFields["Active Partitions Count"] = activePartitionsCount.toString()

        val table = PartitionTable(
            type = tableType,
            sourceName = "Master Boot Record (MBR)",
            sectorSize = SECTOR_SIZE,
            diskSize = fileSize,
            numberOfEntries = partitions.size,
            mbrSignatureHex = "0x${Integer.toHexString(sig).uppercase()}",
            isMbrSignatureValid = isMbrSigValid,
            partitions = partitions,
            rawHeaderFields = rawFields
        )

        return MbrParseResult(table, issues)
    }

    private fun parseExtendedPartitions(
        channel: FileChannel,
        extendedBaseLba: Long,
        partitions: MutableList<PartitionEntry>,
        issues: MutableList<PartitionIssue>
    ) {
        var currentEbrLba = extendedBaseLba
        var logicalIndex = 5
        var iterations = 0
        val maxEbr = 32

        val ebrBuf = ByteBuffer.allocate(SECTOR_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        while (currentEbrLba > 0 && iterations < maxEbr) {
            iterations++
            try {
                val ebrOffset = currentEbrLba * SECTOR_SIZE
                if (ebrOffset + SECTOR_SIZE > channel.size()) break
                channel.position(ebrOffset)
                ebrBuf.clear()
                val r = channel.read(ebrBuf)
                if (r < SECTOR_SIZE) break
                ebrBuf.flip()

                val sig = ebrBuf.getShort(510).toInt() and 0xFFFF
                if (sig != MBR_SIGNATURE) break

                // Entry 1: Logical Partition
                val type1 = ebrBuf.get(446 + 4).toInt() and 0xFF
                val startLba1 = ebrBuf.getInt(446 + 8).toLong() and 0xFFFFFFFFL
                val count1 = ebrBuf.getInt(446 + 12).toLong() and 0xFFFFFFFFL

                if (type1 != 0 && count1 > 0) {
                    val absStartLba = currentEbrLba + startLba1
                    val absEndLba = absStartLba + count1 - 1
                    val partName = "Logical_$logicalIndex"
                    partitions.add(
                        PartitionEntry(
                            index = logicalIndex++,
                            name = partName,
                            startLba = absStartLba,
                            endLba = absEndLba,
                            startByteOffset = absStartLba * SECTOR_SIZE,
                            sizeBytes = count1 * SECTOR_SIZE,
                            typeGuidOrId = "0x" + String.format("%02X", type1),
                            typeDescription = "Logical: ${mapMbrTypeIdToDescription(type1)}",
                            sectorSize = SECTOR_SIZE,
                            originalFileName = "$partName.img"
                        )
                    )
                }

                // Entry 2: Next EBR
                val type2 = ebrBuf.get(446 + 16 + 4).toInt() and 0xFF
                val startLba2 = ebrBuf.getInt(446 + 16 + 8).toLong() and 0xFFFFFFFFL
                val count2 = ebrBuf.getInt(446 + 16 + 12).toLong() and 0xFFFFFFFFL

                if ((type2 == 0x05 || type2 == 0x0F) && count2 > 0) {
                    currentEbrLba = extendedBaseLba + startLba2
                } else {
                    break
                }
            } catch (e: Exception) {
                issues.add(
                    PartitionIssue(
                        id = "EBR_PARSE_ERROR",
                        severity = PartitionIssueSeverity.WARNING,
                        title = "Error Parsing EBR Chain",
                        description = e.message ?: "Failed reading EBR sector",
                        category = "Geometry"
                    )
                )
                break
            }
        }
    }

    fun mapMbrTypeIdToDescription(typeId: Int): String {
        return when (typeId) {
            0x00 -> "Empty"
            0x01 -> "FAT12"
            0x04 -> "FAT16 (<32MB)"
            0x05 -> "Extended (CHS)"
            0x06 -> "FAT16 (>32MB)"
            0x07 -> "NTFS / exFAT / HPFS"
            0x0B -> "FAT32 (CHS)"
            0x0C -> "FAT32 (LBA)"
            0x0E -> "FAT16 (LBA)"
            0x0F -> "Extended (LBA)"
            0x82 -> "Linux Swap / Solaris"
            0x83 -> "Linux Native (ext2/ext3/ext4/f2fs)"
            0x8E -> "Linux LVM"
            0xA5 -> "FreeBSD"
            0xA8 -> "macOS UFS"
            0xAF -> "macOS HFS / HFS+"
            0xEE -> "GPT Protective MBR"
            0xEF -> "EFI System Partition"
            else -> "Unknown (0x${Integer.toHexString(typeId).uppercase()})"
        }
    }
}
