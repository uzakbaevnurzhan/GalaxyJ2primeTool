package com.example.ui.analyzer.kernel.studio.dtb

import com.example.ui.analyzer.kernel.studio.models.DtbHardwareNode
import com.example.ui.analyzer.kernel.studio.models.DtboInfo
import com.example.ui.analyzer.kernel.studio.models.KernelNode

data class DtbAnalysisOutput(
    val rootNode: KernelNode?,
    val dtboInfo: DtboInfo?,
    val compatibleStrings: List<String>,
    val hardwareNodes: List<DtbHardwareNode>,
    val detectedDtbCount: Int,
    val isAppendedDtb: Boolean = false,
    val notes: List<String> = emptyList()
)

object DtbAnalyzer {

    fun analyze(bytes: ByteArray): DtbAnalysisOutput {
        if (bytes.isEmpty()) {
            return DtbAnalysisOutput(
                rootNode = null,
                dtboInfo = null,
                compatibleStrings = emptyList(),
                hardwareNodes = emptyList(),
                detectedDtbCount = 0,
                notes = listOf("Empty buffer")
            )
        }

        val notes = mutableListOf<String>()

        // 1. Check if DTBO Table first
        val dtbo = DtboAnalyzer.parse(bytes)
        if (dtbo != null) {
            notes.add("Parsed Android DTBO table with ${dtbo.entries.size} entries")
            val firstRoot = dtbo.entries.firstOrNull { it.rootNode != null }?.rootNode
            val compats = if (firstRoot != null) DtbHardwareAnalyzer.extractCompatibleStrings(firstRoot) else emptyList()
            val hw = if (firstRoot != null) DtbHardwareAnalyzer.detectHardware(firstRoot) else emptyList()

            return DtbAnalysisOutput(
                rootNode = firstRoot,
                dtboInfo = dtbo,
                compatibleStrings = compats,
                hardwareNodes = hw,
                detectedDtbCount = dtbo.entries.size,
                notes = notes
            )
        }

        // 2. Scan for FDT magic (0xd00dfeed)
        val dtbOffsets = findFdtOffsets(bytes)
        if (dtbOffsets.isEmpty()) {
            return DtbAnalysisOutput(
                rootNode = null,
                dtboInfo = null,
                compatibleStrings = emptyList(),
                hardwareNodes = emptyList(),
                detectedDtbCount = 0,
                notes = listOf("No valid FDT / DTB / DTBO signature found")
            )
        }

        notes.add("Found ${dtbOffsets.size} FDT header(s) at offset(s): ${dtbOffsets.take(5).joinToString(", ")}")
        val firstOffset = dtbOffsets.first()
        val fdtHeader = DtbHeaderParser.parse(bytes, firstOffset)

        val rootNode = if (fdtHeader.isValid) {
            DtbNodeParser.parseTree(bytes, fdtHeader, firstOffset)
        } else {
            notes.add("FDT header parsing warning: ${fdtHeader.errorMessage}")
            null
        }

        val compats = if (rootNode != null) DtbHardwareAnalyzer.extractCompatibleStrings(rootNode) else emptyList()
        val hw = if (rootNode != null) DtbHardwareAnalyzer.detectHardware(rootNode) else emptyList()

        return DtbAnalysisOutput(
            rootNode = rootNode,
            dtboInfo = null,
            compatibleStrings = compats,
            hardwareNodes = hw,
            detectedDtbCount = dtbOffsets.size,
            isAppendedDtb = firstOffset > 0,
            notes = notes
        )
    }

    private fun findFdtOffsets(bytes: ByteArray): List<Int> {
        val list = mutableListOf<Int>()
        val magic = byteArrayOf(0xD0.toByte(), 0x0D.toByte(), 0xFE.toByte(), 0xED.toByte())
        val limit = bytes.size - 40

        var i = 0
        while (i <= limit) {
            if (bytes[i] == magic[0] && bytes[i + 1] == magic[1] &&
                bytes[i + 2] == magic[2] && bytes[i + 3] == magic[3]
            ) {
                list.add(i)
                // Skip ahead at least 4 bytes
                i += 4
            } else {
                i += 4 // FDT headers are 4-byte aligned
            }
        }

        return list
    }
}
