package com.example.ui.analyzer.kernel.parser

import com.example.ui.analyzer.kernel.model.KernelStackFrame
import java.util.regex.Pattern

object KernelTraceParser {

    // Matches standard Linux kernel stack frame:
    // e.g. "[<bf012024>] (wlan_probe+0x24/0x80 [wlan_mtk]) from [<c0456789>] (driver_probe_device+0x48/0x90)"
    // e.g. "[<c0123456>] func_name+0x20/0x100 [mod]"
    // e.g. "mali_render_job+0x34/0x120"
    private val STACK_FRAME_REGEX = Pattern.compile(
        "(?:\\[<([0-9a-fA-F]+)>\\]\\s*)?" + // Group 1: [<address>]
        "(?:\\?\\s*)?" +                     // Optional '?'
        "\\(?\\s*" +
        "([a-zA-Z0-9_.$]+)" +               // Group 2: symbol
        "\\+([0-9a-fA-FxX]+)" +             // Group 3: offset
        "(?:/([0-9a-fA-FxX]+))?" +          // Group 4: size
        "(?:\\s*\\[([a-zA-Z0-9_-]+)\\])?" + // Group 5: module
        "\\)?"                              // Optional closing paren
    )

    private val SIMPLE_ADDR_SYM_REGEX = Pattern.compile(
        "(?:\\[<([0-9a-fA-F]+)>\\]\\s*)?" +
        "\\(?\\s*([a-zA-Z0-9_.$]+)\\)?" +
        "(?:\\s*from\\s+\\[<([0-9a-fA-F]+)>\\])?"
    )

    fun isTraceHeader(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("call trace:") ||
               lower.contains("call trace :") ||
               lower.contains("backtrace:") ||
               lower.contains("stack:")
    }

    fun isTraceTerminator(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("code:") ||
               lower.contains("---[ end trace") ||
               lower.contains("modules linked in:") ||
               lower.contains("kernel panic - not syncing") ||
               lower.contains("end trace") ||
               lower.contains("panic:")
    }

    fun parseFrame(line: String, frameIndex: Int): KernelStackFrame? {
        val cleaned = KernelTimestampParser.parseLine(line).cleanedLine
        if (cleaned.isBlank() || isTraceHeader(cleaned) || isTraceTerminator(cleaned)) return null

        // Try primary symbol+offset parser
        val matcher = STACK_FRAME_REGEX.matcher(cleaned)
        if (matcher.find()) {
            val addr = matcher.group(1)
            val sym = matcher.group(2)
            val offset = matcher.group(3)
            val size = matcher.group(4)
            val module = matcher.group(5)

            if (!sym.isNullOrBlank() && sym != "0x" && sym.toIntOrNull(16) == null) {
                return KernelStackFrame(
                    frameIndex = frameIndex,
                    address = addr,
                    symbol = sym,
                    offsetHex = offset,
                    sizeHex = size,
                    module = module,
                    rawLine = cleaned
                )
            }
        }

        // Try secondary simpler symbol / address matcher
        val simpleMatcher = SIMPLE_ADDR_SYM_REGEX.matcher(cleaned)
        if (simpleMatcher.find()) {
            val addr = simpleMatcher.group(1) ?: simpleMatcher.group(3)
            val sym = simpleMatcher.group(2)

            if (!sym.isNullOrBlank() && sym != "0x" && sym.toIntOrNull(16) == null && sym != "show_stack" && sym != "dump_stack") {
                return KernelStackFrame(
                    frameIndex = frameIndex,
                    address = addr,
                    symbol = sym,
                    offsetHex = null,
                    sizeHex = null,
                    module = null,
                    rawLine = cleaned
                )
            } else if (addr != null) {
                return KernelStackFrame(
                    frameIndex = frameIndex,
                    address = addr,
                    symbol = sym?.takeIf { it != "show_stack" && it != "dump_stack" },
                    offsetHex = null,
                    sizeHex = null,
                    module = null,
                    rawLine = cleaned
                )
            }
        }

        // If line contains bracketed hex address e.g. "[<c0123456>]"
        if (cleaned.contains("[<") && cleaned.contains(">]")) {
            val hexStart = cleaned.indexOf("[<") + 2
            val hexEnd = cleaned.indexOf(">]", hexStart)
            if (hexEnd > hexStart) {
                val addr = cleaned.substring(hexStart, hexEnd).trim()
                return KernelStackFrame(
                    frameIndex = frameIndex,
                    address = addr,
                    symbol = null,
                    offsetHex = null,
                    sizeHex = null,
                    module = null,
                    rawLine = cleaned
                )
            }
        }

        return null
    }
}
