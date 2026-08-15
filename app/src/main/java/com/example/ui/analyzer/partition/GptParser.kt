package com.example.ui.analyzer.partition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.CRC32

object GptParser {
    val GPT_SIGNATURE = "EFI PART".toByteArray(Charsets.US_ASCII)
    const val GPT_HEADER_OFFSET_LBA1 = 512L
    const val DEFAULT_SECTOR_SIZE = 512

    data class GptParseResult(
        val table: PartitionTable,
        val issues: List<PartitionIssue>
    )

    fun parse(channel: FileChannel, sectorSize: Int = DEFAULT_SECTOR_SIZE): GptParseResult? {
        val originalPos = channel.position()
        try {
            val fileSize = channel.size()
            if (fileSize < sectorSize * 2) return null

            // Seek to LBA 1 (or 0 if image is raw GPT header alone)
            var headerOffset = sectorSize.toLong()
            channel.position(headerOffset)
            var headerBuf = ByteBuffer.allocate(sectorSize).order(ByteOrder.LITTLE_ENDIAN)
            var read = channel.read(headerBuf)
            if (read < 92) return null
            headerBuf.flip()

            if (!isGptSignature(headerBuf, 0)) {
                // Try LBA 0 (some tools dump header directly)
                channel.position(0)
                headerBuf.clear()
                read = channel.read(headerBuf)
                if (read < 92 || !isGptSignature(headerBuf, 0)) {
                    return null
                }
                headerOffset = 0L
            }

            return parseHeaderBuffer(headerBuf, channel, headerOffset, sectorSize, fileSize)
        } catch (e: Exception) {
            return null
        } finally {
            channel.position(originalPos)
        }
    }

    fun parseHeaderBuffer(
        headerBuf: ByteBuffer,
        channel: FileChannel?,
        headerOffset: Long,
        sectorSize: Int,
        fileSize: Long
    ): GptParseResult {
        val issues = mutableListOf<PartitionIssue>()

        val revision = headerBuf.getInt(8)
        val headerSize = headerBuf.getInt(12)
        val headerCrcStored = headerBuf.getInt(16).toLong() and 0xFFFFFFFFL
        val currentLba = headerBuf.getLong(24)
        val backupLba = headerBuf.getLong(32)
        val firstUsableLba = headerBuf.getLong(40)
        val lastUsableLba = headerBuf.getLong(48)

        val diskGuidBytes = ByteArray(16)
        headerBuf.position(56)
        headerBuf.get(diskGuidBytes)
        val diskGuid = formatGuid(diskGuidBytes)

        val partEntryLba = headerBuf.getLong(72)
        val numEntries = headerBuf.getInt(80)
        val entrySize = headerBuf.getInt(84)
        val tableCrcStored = headerBuf.getInt(88).toLong() and 0xFFFFFFFFL

        // Compute Header CRC
        val headerCrcComputed = computeHeaderCrc(headerBuf, headerSize)
        val isHeaderCrcValid = (headerCrcStored == headerCrcComputed)

        if (!isHeaderCrcValid) {
            issues.add(
                PartitionIssue(
                    id = "GPT_HEADER_CRC_MISMATCH",
                    severity = PartitionIssueSeverity.CRITICAL,
                    title = "GPT Header CRC32 Checksum Mismatch",
                    description = "Stored CRC32 (0x${java.lang.Long.toHexString(headerCrcStored).uppercase()}) does not match computed CRC32 (0x${java.lang.Long.toHexString(headerCrcComputed).uppercase()}).",
                    evidence = "Stored: 0x${java.lang.Long.toHexString(headerCrcStored)} vs Computed: 0x${java.lang.Long.toHexString(headerCrcComputed)}",
                    recommendation = "GPT header is corrupted or modified externally. Do not flash without fixing partition table.",
                    affectedPartition = "GPT Header",
                    category = "Integrity"
                )
            )
        }

        // Validate LBA ranges
        if (firstUsableLba >= lastUsableLba && lastUsableLba > 0) {
            issues.add(
                PartitionIssue(
                    id = "GPT_INVALID_USABLE_LBA",
                    severity = PartitionIssueSeverity.CRITICAL,
                    title = "Invalid First/Last Usable LBA Range",
                    description = "First usable LBA ($firstUsableLba) is greater than or equal to Last usable LBA ($lastUsableLba).",
                    evidence = "firstUsableLba=$firstUsableLba, lastUsableLba=$lastUsableLba",
                    recommendation = "Check disk geometry and GPT layout.",
                    category = "Geometry"
                )
            )
        }

        // Read Partition Entries
        val partitions = mutableListOf<PartitionEntry>()
        var tableCrcComputed = 0L
        var isTableCrcValid = true

        if (channel != null && numEntries > 0 && entrySize >= 128) {
            val totalTableBytes = (numEntries * entrySize).toLong()
            val entryTableOffset = if (headerOffset == 0L) 92L else (partEntryLba * sectorSize)

            try {
                if (channel.size() >= entryTableOffset + totalTableBytes) {
                    val tableCrc32 = CRC32()
                    val entriesBuf = ByteBuffer.allocate(numEntries * entrySize).order(ByteOrder.LITTLE_ENDIAN)
                    channel.position(entryTableOffset)
                    val r = channel.read(entriesBuf)
                    if (r > 0) {
                        entriesBuf.flip()
                        val rawArray = entriesBuf.array()
                        tableCrc32.update(rawArray, 0, numEntries * entrySize)
                        tableCrcComputed = tableCrc32.value

                        isTableCrcValid = (tableCrcStored == tableCrcComputed)
                        if (!isTableCrcValid) {
                            issues.add(
                                PartitionIssue(
                                    id = "GPT_TABLE_CRC_MISMATCH",
                                    severity = PartitionIssueSeverity.CRITICAL,
                                    title = "GPT Partition Array CRC32 Mismatch",
                                    description = "Partition table entries CRC (0x${java.lang.Long.toHexString(tableCrcStored).uppercase()}) does not match computed (0x${java.lang.Long.toHexString(tableCrcComputed).uppercase()}).",
                                    evidence = "Stored: 0x${java.lang.Long.toHexString(tableCrcStored)} vs Computed: 0x${java.lang.Long.toHexString(tableCrcComputed)}",
                                    recommendation = "One or more partition entries are corrupted.",
                                    affectedPartition = "Partition Array",
                                    category = "Integrity"
                                )
                            )
                        }

                        // Parse entries
                        val guidsSeen = mutableSetOf<String>()
                        for (i in 0 until numEntries) {
                            val entryStart = i * entrySize
                            if (entryStart + 128 > entriesBuf.limit()) break

                            entriesBuf.position(entryStart)
                            val typeGuidBytes = ByteArray(16)
                            entriesBuf.get(typeGuidBytes)

                            // Empty entry check (type GUID all 0)
                            var isAllZero = true
                            for (b in typeGuidBytes) {
                                if (b.toInt() != 0) {
                                    isAllZero = false
                                    break
                                }
                            }
                            if (isAllZero) continue

                            val typeGuid = formatGuid(typeGuidBytes)
                            val uniqueGuidBytes = ByteArray(16)
                            entriesBuf.get(uniqueGuidBytes)
                            val uniqueGuid = formatGuid(uniqueGuidBytes)

                            val pFirstLba = entriesBuf.getLong()
                            val pLastLba = entriesBuf.getLong()
                            val pAttributes = entriesBuf.getLong()

                            // Name UTF-16LE 72 bytes (36 chars)
                            val nameChars = CharArray(36)
                            var nameLen = 0
                            for (c in 0 until 36) {
                                val ch = entriesBuf.getChar()
                                if (ch == '\u0000') {
                                    // continue to consume all 72 bytes
                                } else if (nameLen == c) {
                                    nameChars[nameLen++] = ch
                                }
                            }
                            val pName = String(nameChars, 0, nameLen).trim()

                            val pSizeBytes = if (pLastLba >= pFirstLba) (pLastLba - pFirstLba + 1) * sectorSize else 0L
                            val pOffset = pFirstLba * sectorSize

                            // Duplicate unique GUID check
                            if (uniqueGuid.isNotEmpty() && guidsSeen.contains(uniqueGuid)) {
                                issues.add(
                                    PartitionIssue(
                                        id = "GPT_DUPLICATE_UNIQUE_GUID",
                                        severity = PartitionIssueSeverity.WARNING,
                                        title = "Duplicate Partition Unique GUID ($pName)",
                                        description = "Partition '$pName' shares identical Unique GUID with another partition: $uniqueGuid",
                                        evidence = "GUID: $uniqueGuid",
                                        recommendation = "Unique GUIDs must be distinct across partitions.",
                                        affectedPartition = pName,
                                        category = "Standards Compliance"
                                    )
                                )
                            } else if (uniqueGuid.isNotEmpty()) {
                                guidsSeen.add(uniqueGuid)
                            }

                            // Range checks
                            if (pFirstLba > pLastLba) {
                                issues.add(
                                    PartitionIssue(
                                        id = "GPT_INVALID_PARTITION_RANGE",
                                        severity = PartitionIssueSeverity.CRITICAL,
                                        title = "Invalid LBA Range ($pName)",
                                        description = "Start LBA ($pFirstLba) exceeds End LBA ($pLastLba).",
                                        evidence = "First: $pFirstLba, Last: $pLastLba",
                                        recommendation = "Fix partition boundaries.",
                                        affectedPartition = pName,
                                        category = "Geometry"
                                    )
                                )
                            }

                            if (lastUsableLba > 0 && pLastLba > lastUsableLba) {
                                issues.add(
                                    PartitionIssue(
                                        id = "GPT_PARTITION_OUT_OF_BOUNDS",
                                        severity = PartitionIssueSeverity.CRITICAL,
                                        title = "Partition Out of Usable Disk Bounds ($pName)",
                                        description = "End LBA ($pLastLba) exceeds Last Usable LBA ($lastUsableLba).",
                                        evidence = "pLastLba=$pLastLba > lastUsableLba=$lastUsableLba",
                                        recommendation = "Partition extends beyond physical or usable boundary.",
                                        affectedPartition = pName,
                                        category = "Geometry"
                                    )
                                )
                            }

                            val isReadOnly = (pAttributes and 0x0000000000000001L) != 0L
                            val isBootable = (pAttributes and 0x0000000000000004L) != 0L // Legacy BIOS bootable

                            partitions.add(
                                PartitionEntry(
                                    index = i + 1,
                                    name = pName.ifEmpty { "part_${i + 1}" },
                                    startLba = pFirstLba,
                                    endLba = pLastLba,
                                    startByteOffset = pOffset,
                                    sizeBytes = pSizeBytes,
                                    typeGuidOrId = typeGuid,
                                    uniqueGuid = uniqueGuid,
                                    typeDescription = mapTypeGuidToDescription(typeGuid),
                                    attributesHex = "0x" + java.lang.Long.toHexString(pAttributes).uppercase(),
                                    isBootable = isBootable,
                                    isReadOnly = isReadOnly,
                                    sectorSize = sectorSize,
                                    originalFileName = "$pName.img"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                issues.add(
                    PartitionIssue(
                        id = "GPT_ENTRY_READ_ERROR",
                        severity = PartitionIssueSeverity.CRITICAL,
                        title = "Failed to Read Partition Entries",
                        description = e.message ?: "Unknown I/O error",
                        category = "I/O Error"
                    )
                )
            }
        }

        // Check for overlapping partitions
        checkPartitionOverlaps(partitions, issues)

        val rawFields = linkedMapOf<String, String>()
        rawFields["Signature"] = "EFI PART"
        rawFields["Revision"] = "0x${Integer.toHexString(revision).uppercase()}"
        rawFields["Header Size"] = "$headerSize bytes"
        rawFields["Header CRC32 (Stored)"] = "0x${java.lang.Long.toHexString(headerCrcStored).uppercase()}"
        rawFields["Header CRC32 (Computed)"] = "0x${java.lang.Long.toHexString(headerCrcComputed).uppercase()}"
        rawFields["Current LBA"] = currentLba.toString()
        rawFields["Backup LBA"] = backupLba.toString()
        rawFields["First Usable LBA"] = firstUsableLba.toString()
        rawFields["Last Usable LBA"] = lastUsableLba.toString()
        rawFields["Disk GUID"] = diskGuid
        rawFields["Partition Entry LBA"] = partEntryLba.toString()
        rawFields["Number of Entries"] = numEntries.toString()
        rawFields["Size of Entry"] = "$entrySize bytes"
        rawFields["Table CRC32 (Stored)"] = "0x${java.lang.Long.toHexString(tableCrcStored).uppercase()}"
        rawFields["Table CRC32 (Computed)"] = "0x${java.lang.Long.toHexString(tableCrcComputed).uppercase()}"

        val table = PartitionTable(
            type = PartitionTableType.GPT,
            sourceName = "GPT Partition Table",
            sectorSize = sectorSize,
            diskSize = if (lastUsableLba > 0) (lastUsableLba + 34) * sectorSize else fileSize,
            diskGuid = diskGuid,
            currentLba = currentLba,
            backupLba = backupLba,
            firstUsableLba = firstUsableLba,
            lastUsableLba = lastUsableLba,
            partitionEntryLba = partEntryLba,
            numberOfEntries = numEntries,
            sizeOfEntry = entrySize,
            headerCrc = headerCrcStored,
            headerCrcComputed = headerCrcComputed,
            isHeaderCrcValid = isHeaderCrcValid,
            partitionTableCrc = tableCrcStored,
            partitionTableCrcComputed = tableCrcComputed,
            isTableCrcValid = isTableCrcValid,
            gptRevision = if (revision == 0x00010000) "1.0" else "0x${Integer.toHexString(revision)}",
            partitions = partitions,
            rawHeaderFields = rawFields
        )

        return GptParseResult(table, issues)
    }

    private fun isGptSignature(buf: ByteBuffer, offset: Int): Boolean {
        if (buf.limit() < offset + 8) return false
        for (i in 0 until 8) {
            if (buf.get(offset + i) != GPT_SIGNATURE[i]) return false
        }
        return true
    }

    fun computeHeaderCrc(buf: ByteBuffer, headerSize: Int): Long {
        val size = if (headerSize in 92..512) headerSize else 92
        val copy = ByteArray(size)
        val origPos = buf.position()
        buf.position(0)
        buf.get(copy, 0, minOf(size, buf.remaining()))
        buf.position(origPos)

        // Zero out CRC field at bytes 16..19
        copy[16] = 0
        copy[17] = 0
        copy[18] = 0
        copy[19] = 0

        val crc = CRC32()
        crc.update(copy, 0, size)
        return crc.value
    }

    fun checkPartitionOverlaps(partitions: List<PartitionEntry>, issues: MutableList<PartitionIssue>) {
        val sorted = partitions.filter { it.sizeBytes > 0 }.sortedBy { it.startLba }
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            if (current.endLba >= next.startLba) {
                issues.add(
                    PartitionIssue(
                        id = "PARTITION_OVERLAP_${current.name}_${next.name}",
                        severity = PartitionIssueSeverity.CRITICAL,
                        title = "Partition Overlap Detected: '${current.name}' & '${next.name}'",
                        description = "Partition '${current.name}' (LBA ${current.startLba}..${current.endLba}) overlaps with '${next.name}' (LBA ${next.startLba}..${next.endLba}).",
                        evidence = "Overlap range: LBA ${next.startLba} to ${current.endLba}",
                        recommendation = "Fix starting LBA and size in partition table to prevent data corruption during flash.",
                        affectedPartition = "${current.name}, ${next.name}",
                        category = "Geometry"
                    )
                )
            }
        }
    }

    fun formatGuid(bytes: ByteArray): String {
        if (bytes.size != 16) return ""
        // Little-endian mixed format for GPT GUIDs: Data1 (4B LE), Data2 (2B LE), Data3 (2B LE), Data4 (8B BE)
        val data1 = (bytes[3].toLong() and 0xFF shl 24) or (bytes[2].toLong() and 0xFF shl 16) or (bytes[1].toLong() and 0xFF shl 8) or (bytes[0].toLong() and 0xFF)
        val data2 = (bytes[5].toInt() and 0xFF shl 8) or (bytes[4].toInt() and 0xFF)
        val data3 = (bytes[7].toInt() and 0xFF shl 8) or (bytes[6].toInt() and 0xFF)
        val data4Part1 = String.format("%02x%02x", bytes[8], bytes[9])
        val data4Part2 = String.format("%02x%02x%02x%02x%02x%02x", bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15])

        return String.format("%08x-%04x-%04x-%s-%s", data1, data2, data3, data4Part1, data4Part2).uppercase()
    }

    fun mapTypeGuidToDescription(guid: String): String {
        val upper = guid.uppercase()
        return when {
            upper.startsWith("0FC63DAF-8483-4772-8E79") -> "Linux Filesystem Data"
            upper.startsWith("EBD0A0A2-B9E5-4433-87C0") -> "Basic Data Partition (FAT/NTFS/exFAT)"
            upper.startsWith("49A4D17F-93A3-45C1-A067") -> "Android Boot"
            upper.startsWith("4177C022-9E49-4EB6-A612") -> "Android Recovery"
            upper.startsWith("EF32A77E-A452-4626-A832") -> "Android Misc"
            upper.startsWith("20117F86-E985-4357-B9EE") -> "Android Metadata"
            upper.startsWith("1B81E7E6-F50D-4194-A739") -> "Android Userdata"
            upper.startsWith("5C0A093C-37B9-450F-8180") -> "Android System"
            upper.startsWith("C23EB98D-9E91-4E40-9E9B") -> "Android Vendor"
            upper.startsWith("C12A7328-F81F-11D2-BA4B") -> "EFI System Partition (ESP)"
            upper.startsWith("0657FD6D-A4AB-43C4-84E5") -> "Linux Swap"
            else -> "Standard Partition"
        }
    }
}
