package com.example.ui.analyzer.kernel.parser

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class SystemMapSymbol(
    val address: Long,
    val type: Char,
    val name: String
)

class SystemMapParser {
    private val symbols = mutableListOf<SystemMapSymbol>()

    fun loadFromStream(inputStream: InputStream) {
        symbols.clear()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line = reader.readLine()
        while (line != null) {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3) {
                val addr = parts[0].toLongOrNull(16)
                val type = parts[1].firstOrNull() ?: '?'
                val name = parts[2]
                if (addr != null && name.isNotEmpty()) {
                    symbols.add(SystemMapSymbol(addr, type, name))
                }
            }
            line = reader.readLine()
        }
        symbols.sortBy { it.address }
    }

    val isLoaded: Boolean
        get() = symbols.isNotEmpty()

    val symbolCount: Int
        get() = symbols.size

    fun resolveAddress(addressHex: String): Pair<String, Long>? {
        val targetAddr = addressHex.removePrefix("0x").toLongOrNull(16) ?: return null
        if (symbols.isEmpty()) return null

        var low = 0
        var high = symbols.size - 1
        var bestMatch: SystemMapSymbol? = null

        while (low <= high) {
            val mid = (low + high) ushr 1
            val sym = symbols[mid]

            if (sym.address == targetAddr) {
                return Pair(sym.name, 0L)
            } else if (sym.address < targetAddr) {
                bestMatch = sym
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (bestMatch != null) {
            val offset = targetAddr - bestMatch.address
            // If offset is reasonably small (< 64KB), treat as symbol+offset
            if (offset in 0..65535) {
                return Pair(bestMatch.name, offset)
            }
        }

        return null
    }
}
