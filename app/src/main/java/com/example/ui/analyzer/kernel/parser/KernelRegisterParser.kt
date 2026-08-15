package com.example.ui.analyzer.kernel.parser

import com.example.ui.analyzer.kernel.model.KernelArchitecture
import com.example.ui.analyzer.kernel.model.KernelRegisterSet
import java.util.regex.Pattern

object KernelRegisterParser {

    private val PC_IS_AT_REGEX = Pattern.compile("PC\\s+is\\s+at\\s+(?:\\[<([0-9a-fA-F]+)>\\]\\s+)?([^\r\n]+)", Pattern.CASE_INSENSITIVE)
    private val LR_IS_AT_REGEX = Pattern.compile("LR\\s+is\\s+at\\s+(?:\\[<([0-9a-fA-F]+)>\\]\\s+)?([^\r\n]+)", Pattern.CASE_INSENSITIVE)
    private val FAULT_ADDR_REGEX = Pattern.compile("(?:virtual\\s+address|fault\\s+address|address)\\s+([0-9a-fA-FxX]+)", Pattern.CASE_INSENSITIVE)
    private val ESR_REGEX = Pattern.compile("ESR(?:_EL1)?\\s*[:=]\\s*([0-9a-fA-FxX]+)", Pattern.CASE_INSENSITIVE)
    private val FAR_REGEX = Pattern.compile("FAR(?:_EL1)?\\s*[:=]\\s*([0-9a-fA-FxX]+)", Pattern.CASE_INSENSITIVE)
    private val CPSR_PSR_REGEX = Pattern.compile("(?:cpsr|psr|pstate):\\s*([0-9a-fA-FxX]+)", Pattern.CASE_INSENSITIVE)

    // Token pattern for register name and hex value: e.g. "r0: 00000000", "x29: ffffff8008080000", "pc : [<c0123456>]", "sp : 00000000"
    private val REG_PAIR_REGEX = Pattern.compile("([a-zA-Z0-9_]+)\\s*[:=]\\s*(?:\\[<)?([0-9a-fA-F]{1,16})(?:>\\])?")

    fun parseRegisterBlock(lines: List<String>): KernelRegisterSet {
        val registers = mutableMapOf<String, String>()
        var pc: String? = null
        var lr: String? = null
        var sp: String? = null
        var cpsr: String? = null
        var esr: String? = null
        var far: String? = null
        var faultAddr: String? = null
        var isArm64 = false
        var isArm32 = false

        for (line in lines) {
            val trimmed = line.trim()

            // Fault address
            if (faultAddr == null) {
                val fMatcher = FAULT_ADDR_REGEX.matcher(trimmed)
                if (fMatcher.find()) {
                    faultAddr = fMatcher.group(1)
                }
            }

            // PC is at ...
            val pcMatcher = PC_IS_AT_REGEX.matcher(trimmed)
            if (pcMatcher.find()) {
                val hex = pcMatcher.group(1)
                val sym = pcMatcher.group(2)?.trim()
                if (hex != null) pc = hex else if (pc == null) pc = sym
            }

            // LR is at ...
            val lrMatcher = LR_IS_AT_REGEX.matcher(trimmed)
            if (lrMatcher.find()) {
                val hex = lrMatcher.group(1)
                val sym = lrMatcher.group(2)?.trim()
                if (hex != null) lr = hex else if (lr == null) lr = sym
            }

            // ESR
            val esrMatcher = ESR_REGEX.matcher(trimmed)
            if (esrMatcher.find()) {
                esr = esrMatcher.group(1)
                isArm64 = true
            }

            // FAR
            val farMatcher = FAR_REGEX.matcher(trimmed)
            if (farMatcher.find()) {
                far = farMatcher.group(1)
                isArm64 = true
            }

            // CPSR / PSR / PSTATE
            val cpsrMatcher = CPSR_PSR_REGEX.matcher(trimmed)
            if (cpsrMatcher.find()) {
                cpsr = cpsrMatcher.group(1)
            }

            // Extract all key:value pairs on this line
            val pairMatcher = REG_PAIR_REGEX.matcher(trimmed)
            while (pairMatcher.find()) {
                val name = pairMatcher.group(1)?.lowercase() ?: ""
                val value = pairMatcher.group(2) ?: ""

                if (name.isNotEmpty() && value.isNotEmpty()) {
                    registers[name] = value

                    when (name) {
                        "pc" -> pc = value // Explicit pc register takes priority over symbol text
                        "lr" -> lr = value
                        "sp" -> sp = value
                        "cpsr", "psr", "pstate" -> if (cpsr == null) cpsr = value
                        "esr", "esr_el1" -> if (esr == null) esr = value
                        "far", "far_el1" -> if (far == null) far = value
                    }

                    if (name.startsWith("x") && name.length in 2..3 && name.substring(1).toIntOrNull() != null) {
                        isArm64 = true
                    } else if (name.startsWith("r") && name.length in 2..3 && name.substring(1).toIntOrNull() != null) {
                        isArm32 = true
                    }
                }
            }
        }

        val arch = when {
            isArm64 -> KernelArchitecture.ARM64
            isArm32 -> KernelArchitecture.ARM32
            else -> KernelArchitecture.UNKNOWN
        }

        return KernelRegisterSet(
            architecture = arch,
            pc = pc,
            lr = lr,
            sp = sp,
            cpsr = cpsr,
            esr = esr,
            far = far,
            faultAddress = faultAddr,
            registers = registers,
            rawBlock = lines.joinToString("\n")
        )
    }
}
