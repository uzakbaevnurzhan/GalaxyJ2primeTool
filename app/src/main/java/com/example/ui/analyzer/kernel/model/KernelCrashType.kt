package com.example.ui.analyzer.kernel.model

enum class KernelCrashType(val displayName: String, val defaultSeverity: KernelSeverity) {
    KERNEL_PANIC("Kernel Panic", KernelSeverity.CRITICAL),
    OOPS("Kernel Oops", KernelSeverity.CRITICAL),
    KERNEL_BUG("Kernel BUG", KernelSeverity.CRITICAL),
    INTERNAL_ERROR("Internal Kernel Error", KernelSeverity.CRITICAL),
    FATAL_EXCEPTION("Fatal Exception", KernelSeverity.CRITICAL),
    NULL_POINTER_DEREFERENCE("NULL Pointer Dereference", KernelSeverity.CRITICAL),
    PAGE_FAULT("Kernel Page Fault", KernelSeverity.CRITICAL),
    DATA_ABORT("Data Abort", KernelSeverity.CRITICAL),
    PREFETCH_ABORT("Prefetch Abort", KernelSeverity.CRITICAL),
    SEGMENTATION_FAULT("Segmentation Fault", KernelSeverity.CRITICAL),
    GENERAL_PROTECTION_FAULT("General Protection Fault", KernelSeverity.CRITICAL),
    WATCHDOG_TIMEOUT("Watchdog Timeout / Lockup", KernelSeverity.ERROR),
    SOFT_LOCKUP("Soft Lockup", KernelSeverity.ERROR),
    HARD_LOCKUP("Hard Lockup", KernelSeverity.ERROR),
    HUNG_TASK("Hung Task Blocked", KernelSeverity.ERROR),
    RCU_STALL("RCU Stall", KernelSeverity.ERROR),
    KERNEL_WARNING("Kernel WARNING", KernelSeverity.WARNING),
    DRIVER_TIMEOUT("Driver / Device Timeout", KernelSeverity.WARNING),
    USERSPACE_FATAL("Userspace Fatal Crash", KernelSeverity.ERROR),
    UNKNOWN("Unknown Crash / Event", KernelSeverity.INFO)
}
