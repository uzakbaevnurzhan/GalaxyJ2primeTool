package com.example.ui.analyzer.kernel.model

enum class KernelSeverity {
    CRITICAL,
    ERROR,
    WARNING,
    INFO
}

enum class KernelArchitecture {
    ARM32,
    ARM64,
    X86,
    X86_64,
    MIPS,
    RISCV,
    UNKNOWN
}

enum class CrashDomain {
    KERNEL,
    USERSPACE,
    HAL,
    DRIVER,
    UNKNOWN
}

enum class AnalysisConfidence {
    HIGH,
    MEDIUM,
    LOW
}
