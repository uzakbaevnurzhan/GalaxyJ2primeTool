package com.example.ui.analyzer.kernel.model

data class KernelRegister(
    val name: String,
    val hexValue: String,
    val description: String? = null
)

data class KernelRegisterSet(
    val architecture: KernelArchitecture = KernelArchitecture.UNKNOWN,
    val pc: String? = null,
    val lr: String? = null,
    val sp: String? = null,
    val cpsr: String? = null,
    val esr: String? = null,
    val far: String? = null,
    val faultAddress: String? = null,
    val registers: Map<String, String> = emptyMap(),
    val rawBlock: String = ""
) {
    val isEmpty: Boolean
        get() = pc == null && lr == null && sp == null && faultAddress == null && registers.isEmpty()
}
