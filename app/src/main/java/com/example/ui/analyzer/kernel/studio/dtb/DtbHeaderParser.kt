package com.example.ui.analyzer.kernel.studio.dtb

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class FdtHeader(
    val magic: Long,
    val totalsize: Long,
    val offDtStruct: Long,
    val offDtStrings: Long,
    val offMemRsvmap: Long,
    val version: Long,
    val lastCompVersion: Long,
    val bootCpuidPhys: Long,
    val sizeDtStrings: Long,
    val sizeDtStruct: Long,
    val isValid: Boolean,
    val errorMessage: String? = null
)

object DtbHeaderParser {

    const val FDT_MAGIC = 0xd00dfeedL

    fun parse(bytes: ByteArray, offset: Int = 0): FdtHeader {
        if (bytes.size - offset < 40) {
            return FdtHeader(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, "Buffer too small for FDT header (<40 bytes)")
        }

        val buf = ByteBuffer.wrap(bytes, offset, bytes.size - offset).order(ByteOrder.BIG_ENDIAN)
        val magic = buf.getInt().toLong() and 0xFFFFFFFFL

        if (magic != FDT_MAGIC) {
            return FdtHeader(magic, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, "Invalid FDT magic 0x%08X (Expected 0xD00DFEED)".format(magic))
        }

        val totalsize = buf.getInt().toLong() and 0xFFFFFFFFL
        val offDtStruct = buf.getInt().toLong() and 0xFFFFFFFFL
        val offDtStrings = buf.getInt().toLong() and 0xFFFFFFFFL
        val offMemRsvmap = buf.getInt().toLong() and 0xFFFFFFFFL
        val version = buf.getInt().toLong() and 0xFFFFFFFFL
        val lastCompVersion = buf.getInt().toLong() and 0xFFFFFFFFL

        var bootCpuidPhys = 0L
        var sizeDtStrings = 0L
        var sizeDtStruct = 0L

        if (version >= 2 && buf.remaining() >= 4) {
            bootCpuidPhys = buf.getInt().toLong() and 0xFFFFFFFFL
        }
        if (version >= 3 && buf.remaining() >= 4) {
            sizeDtStrings = buf.getInt().toLong() and 0xFFFFFFFFL
        }
        if (version >= 17 && buf.remaining() >= 4) {
            sizeDtStruct = buf.getInt().toLong() and 0xFFFFFFFFL
        }

        val availableSize = (bytes.size - offset).toLong()
        if (totalsize > availableSize && totalsize > availableSize + 1024) {
            return FdtHeader(
                magic, totalsize, offDtStruct, offDtStrings, offMemRsvmap,
                version, lastCompVersion, bootCpuidPhys, sizeDtStrings, sizeDtStruct,
                false, "Truncated DTB: totalsize ($totalsize) exceeds available buffer ($availableSize)"
            )
        }

        return FdtHeader(
            magic = magic,
            totalsize = totalsize,
            offDtStruct = offDtStruct,
            offDtStrings = offDtStrings,
            offMemRsvmap = offMemRsvmap,
            version = version,
            lastCompVersion = lastCompVersion,
            bootCpuidPhys = bootCpuidPhys,
            sizeDtStrings = sizeDtStrings,
            sizeDtStruct = sizeDtStruct,
            isValid = true
        )
    }
}
