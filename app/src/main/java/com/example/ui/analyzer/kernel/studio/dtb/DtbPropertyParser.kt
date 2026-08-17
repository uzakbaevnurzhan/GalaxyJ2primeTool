package com.example.ui.analyzer.kernel.studio.dtb

import com.example.ui.analyzer.kernel.studio.models.KernelProperty
import com.example.ui.analyzer.kernel.studio.models.PropertyValueType
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DtbPropertyParser {

    fun parseProperty(name: String, rawBytes: ByteArray): KernelProperty {
        if (rawBytes.isEmpty()) {
            return KernelProperty(
                name = name,
                rawBytes = rawBytes,
                type = PropertyValueType.EMPTY,
                formattedValue = "<empty>"
            )
        }

        // 1. Phandle check
        if (name == "phandle" || name == "linux,phandle") {
            if (rawBytes.size == 4) {
                val ph = ByteBuffer.wrap(rawBytes).order(ByteOrder.BIG_ENDIAN).getInt().toLong() and 0xFFFFFFFFL
                return KernelProperty(
                    name = name,
                    rawBytes = rawBytes,
                    type = PropertyValueType.PHANDLE,
                    formattedValue = "<0x%08x>".format(ph),
                    phandle = ph,
                    u32Value = ph
                )
            }
        }

        // 2. Compatible or other string lists
        if (name == "compatible" || name == "model" || name == "device_type" || isPrintableAsciiStringList(rawBytes)) {
            val stringList = extractStringList(rawBytes)
            if (stringList.isNotEmpty()) {
                val isSingle = stringList.size == 1
                val type = if (isSingle) PropertyValueType.STRING else PropertyValueType.STRING_LIST
                val formatted = stringList.joinToString(", ") { "\"$it\"" }
                return KernelProperty(
                    name = name,
                    rawBytes = rawBytes,
                    type = type,
                    formattedValue = formatted,
                    stringList = stringList
                )
            }
        }

        // 3. Single U32
        if (rawBytes.size == 4) {
            val u32 = ByteBuffer.wrap(rawBytes).order(ByteOrder.BIG_ENDIAN).getInt().toLong() and 0xFFFFFFFFL
            val formatted = if (name.startsWith("#") || u32 < 100) {
                "<$u32>"
            } else {
                "<0x%x> ($u32)".format(u32)
            }
            return KernelProperty(
                name = name,
                rawBytes = rawBytes,
                type = PropertyValueType.U32,
                formattedValue = formatted,
                u32Value = u32
            )
        }

        // 4. Single U64
        if (rawBytes.size == 8 && (name.contains("reg") || name.contains("addr") || name.contains("size"))) {
            val u64 = ByteBuffer.wrap(rawBytes).order(ByteOrder.BIG_ENDIAN).getLong()
            return KernelProperty(
                name = name,
                rawBytes = rawBytes,
                type = PropertyValueType.U64,
                formattedValue = "<0x%016x>".format(u64)
            )
        }

        // 5. Sequence of U32 cells (like reg = <0x1000 0x200>, interrupts = <0 12 4>)
        if (rawBytes.size % 4 == 0 && rawBytes.size <= 64) {
            val buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.BIG_ENDIAN)
            val count = rawBytes.size / 4
            val cells = mutableListOf<String>()
            for (i in 0 until count) {
                val c = buf.getInt().toLong() and 0xFFFFFFFFL
                cells.add("0x%x".format(c))
            }
            return KernelProperty(
                name = name,
                rawBytes = rawBytes,
                type = PropertyValueType.BYTES,
                formattedValue = "<${cells.joinToString(" ")}>"
            )
        }

        // 6. Fallback to raw hex bytes
        val hex = rawBytes.take(32).joinToString(" ") { "%02x".format(it) } +
                if (rawBytes.size > 32) " ... (${rawBytes.size} bytes)" else ""
        return KernelProperty(
            name = name,
            rawBytes = rawBytes,
            type = PropertyValueType.RAW_BYTES,
            formattedValue = "[ $hex ]"
        )
    }

    private fun isPrintableAsciiStringList(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.last() != 0.toByte()) return false
        var printableCount = 0
        var nullCount = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0) {
                nullCount++
            } else if (v in 32..126) {
                printableCount++
            } else {
                return false
            }
        }
        return printableCount > 0 && (printableCount + nullCount == bytes.size)
    }

    private fun extractStringList(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        var start = 0
        for (i in bytes.indices) {
            if (bytes[i] == 0.toByte()) {
                if (i > start) {
                    val str = String(bytes, start, i - start, Charsets.UTF_8).trim()
                    if (str.isNotEmpty()) {
                        list.add(str)
                    }
                }
                start = i + 1
            }
        }
        return list
    }
}
