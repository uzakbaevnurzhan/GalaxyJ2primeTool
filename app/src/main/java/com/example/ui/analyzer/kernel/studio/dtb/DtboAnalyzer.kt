package com.example.ui.analyzer.kernel.studio.dtb

import com.example.ui.analyzer.kernel.studio.models.DtboEntry
import com.example.ui.analyzer.kernel.studio.models.DtboInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DtboAnalyzer {

    private const val DTBO_MAGIC_BE = 0xd7b7ab1eL
    private const val DTBO_MAGIC_LE = 0x1eabb7d7L

    fun parse(bytes: ByteArray): DtboInfo? {
        if (bytes.size < 32) return null

        val bufBe = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magicBe = bufBe.getInt().toLong() and 0xFFFFFFFFL

        val isBigEndian: Boolean
        val magicString: String

        if (magicBe == DTBO_MAGIC_BE) {
            isBigEndian = true
            magicString = "0xD7B7AB1E (Big-Endian DTBO Table)"
        } else {
            val bufLe = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magicLe = bufLe.getInt().toLong() and 0xFFFFFFFFL
            if (magicLe == DTBO_MAGIC_BE || magicLe == DTBO_MAGIC_LE) {
                isBigEndian = false
                magicString = "0x1EABB7D7 (Little-Endian DTBO Table)"
            } else {
                return null
            }
        }

        val buf = ByteBuffer.wrap(bytes).order(if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        buf.position(0)
        buf.getInt() // skip magic

        val totalSize = buf.getInt().toLong() and 0xFFFFFFFFL
        val headerSize = buf.getInt().toLong() and 0xFFFFFFFFL
        val dtEntrySize = buf.getInt().toLong() and 0xFFFFFFFFL
        val dtEntryCount = buf.getInt()
        val dtEntriesOffset = buf.getInt().toLong() and 0xFFFFFFFFL
        val pageSize = buf.getInt().toLong() and 0xFFFFFFFFL
        val version = buf.getInt().toLong() and 0xFFFFFFFFL

        val entries = mutableListOf<DtboEntry>()
        val startOffset = if (dtEntriesOffset > 0) dtEntriesOffset.toInt() else headerSize.toInt()
        val entrySize = if (dtEntrySize >= 24) dtEntrySize.toInt() else 24

        for (i in 0 until dtEntryCount.coerceAtMost(128)) {
            val currentEntryPos = startOffset + (i * entrySize)
            if (currentEntryPos + 24 > bytes.size) break

            buf.position(currentEntryPos)
            val dtSize = buf.getInt().toLong() and 0xFFFFFFFFL
            val dtOffset = buf.getInt().toLong() and 0xFFFFFFFFL
            val id = buf.getInt().toLong() and 0xFFFFFFFFL
            val rev = buf.getInt().toLong() and 0xFFFFFFFFL
            val custom1 = buf.getInt().toLong() and 0xFFFFFFFFL
            val custom2 = buf.getInt().toLong() and 0xFFFFFFFFL

            // Try parsing the DTB at dtOffset
            val rootNode = if (dtOffset + dtSize <= bytes.size && dtSize >= 40) {
                val fdtHeader = DtbHeaderParser.parse(bytes, dtOffset.toInt())
                if (fdtHeader.isValid) {
                    DtbNodeParser.parseTree(bytes, fdtHeader, dtOffset.toInt())
                } else null
            } else null

            entries.add(
                DtboEntry(
                    index = i,
                    dtSize = dtSize,
                    dtOffset = dtOffset,
                    id = id,
                    rev = rev,
                    custom = listOf(custom1, custom2),
                    rootNode = rootNode
                )
            )
        }

        return DtboInfo(
            magic = magicString,
            totalSize = totalSize,
            headerSize = headerSize,
            dtEntrySize = dtEntrySize,
            dtEntryCount = dtEntryCount,
            dtEntriesOffset = dtEntriesOffset,
            pageSize = pageSize,
            version = version,
            entries = entries
        )
    }
}
