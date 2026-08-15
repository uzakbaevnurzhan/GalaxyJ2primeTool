package com.example.ui.analyzer.kernel.model

data class KernelCrashEvent(
    val id: String,
    val type: KernelCrashType,
    val severity: KernelSeverity,
    val domain: CrashDomain = CrashDomain.KERNEL,
    val timestamp: String? = null,
    val uptimeSeconds: Double? = null,
    val cpu: Int? = null,
    val pid: Int? = null,
    val comm: String? = null,
    val processName: String? = null,
    val faultAddress: String? = null,
    val panicReason: String? = null,
    val kernelVersion: String? = null,
    val architecture: KernelArchitecture = KernelArchitecture.UNKNOWN,
    val sourceLineIndex: Long,
    val startByteOffset: Long = 0L,
    val endByteOffset: Long = 0L,
    val registers: KernelRegisterSet = KernelRegisterSet(),
    val stackFrames: List<KernelStackFrame> = emptyList(),
    val contextLinesBefore: List<String> = emptyList(),
    val contextLinesAfter: List<String> = emptyList(),
    val rawBlock: String = "",
    val analysis: KernelRootCauseAnalysis? = null
) {
    val topSymbol: String?
        get() = stackFrames.firstOrNull { it.symbol != null }?.symbol

    val traceSignature: String
        get() {
            val symbols = stackFrames.mapNotNull { it.symbol }
            return if (symbols.isNotEmpty()) {
                symbols.take(5).joinToString(" -> ")
            } else {
                "${type.name}:${faultAddress ?: "no_addr"}"
            }
        }
}

data class KernelTraceGroup(
    val signature: String,
    val sampleEvent: KernelCrashEvent,
    val occurrences: Int,
    val firstTimestamp: String?,
    val lastTimestamp: String?,
    val events: List<KernelCrashEvent>
)
