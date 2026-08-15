package com.example.ui.analyzer.kernel.model

data class KernelStackFrame(
    val frameIndex: Int,
    val address: String? = null,
    val symbol: String? = null,
    val offsetHex: String? = null,
    val sizeHex: String? = null,
    val module: String? = null,
    val rawLine: String
) {
    val formattedDisplay: String
        get() {
            val addr = if (address != null) "[<$address>] " else ""
            val sym = if (symbol != null) {
                val off = if (offsetHex != null) "+$offsetHex" else ""
                val sz = if (sizeHex != null) "/$sizeHex" else ""
                val mod = if (module != null) " [$module]" else ""
                "$symbol$off$sz$mod"
            } else {
                rawLine.trim()
            }
            return "#$frameIndex $addr$sym".trim()
        }

    val offsetLong: Long?
        get() = offsetHex?.removePrefix("0x")?.toLongOrNull(16)

    val sizeLong: Long?
        get() = sizeHex?.removePrefix("0x")?.toLongOrNull(16)
}
