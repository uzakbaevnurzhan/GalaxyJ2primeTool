package com.example.ui.analyzer.kernel.parser

import com.example.ui.analyzer.kernel.model.CrashDomain
import com.example.ui.analyzer.kernel.model.KernelCrashType
import com.example.ui.analyzer.kernel.model.KernelSeverity
import java.util.regex.Pattern

data class CrashDetectionCandidate(
    val type: KernelCrashType,
    val severity: KernelSeverity,
    val domain: CrashDomain,
    val primaryReason: String,
    val cpu: Int?,
    val pid: Int?,
    val comm: String?,
    val faultAddress: String?
)

object KernelCrashDetector {

    private val CPU_REGEX = Pattern.compile("(?:CPU|cpu)\\s*[:#]?\\s*(\\d+)|on\\s+CPU\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
    private val PID_REGEX = Pattern.compile("PID\\s*[:=]\\s*(\\d+)|pid\\s*=\\s*(\\d+)|pid\\s+(\\d+)|:(\\d+)\\s+blocked", Pattern.CASE_INSENSITIVE)
    private val COMM_REGEX = Pattern.compile("Comm\\s*[:=]\\s*([^\r\n,\\s]+)|comm=\"([^\"]+)\"|comm=([^\r\n,\\s]+)", Pattern.CASE_INSENSITIVE)
    private val PROCESS_TASK_REGEX = Pattern.compile("(?:Process|Task|task)\\s+([^\r\n,\\s:]+)(?::(\\d+))?", Pattern.CASE_INSENSITIVE)
    private val BRACKETED_COMM_PID_REGEX = Pattern.compile("\\[([^\r\n\\]:]+):(\\d+)\\]")
    private val FAULT_ADDR_REGEX = Pattern.compile("(?:virtual\\s+address|fault\\s+address|address)\\s+([0-9a-fA-FxX]+)", Pattern.CASE_INSENSITIVE)
    private val PANIC_REASON_REGEX = Pattern.compile("Kernel\\s+panic\\s*-\\s*not\\s+syncing\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE)
    private val OOPS_REASON_REGEX = Pattern.compile("(?:Internal\\s+error|Oops|BUG|kernel\\s+BUG)(?:\\s*:\\s*|\\s+at\\s+)(.*)$", Pattern.CASE_INSENSITIVE)

    /**
     * Examines a single line or header to determine if a crash event begins.
     */
    fun detectCrashStart(line: String): CrashDetectionCandidate? {
        val trimmed = KernelTimestampParser.parseLine(line).cleanedLine
        val lower = trimmed.lowercase()

        // 1. Kernel Panic
        if (lower.contains("kernel panic")) {
            val panicMatcher = PANIC_REASON_REGEX.matcher(trimmed)
            val reason = if (panicMatcher.find()) panicMatcher.group(1)?.trim() ?: trimmed else trimmed
            return CrashDetectionCandidate(
                type = KernelCrashType.KERNEL_PANIC,
                severity = KernelSeverity.CRITICAL,
                domain = CrashDomain.KERNEL,
                primaryReason = reason.ifBlank { "Kernel Panic" },
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = extractFaultAddress(trimmed)
            )
        }

        // 2. Kernel BUG
        if (lower.contains("kernel bug at") || lower.startsWith("bug:") || (lower.contains("bug: failure at") && !lower.contains("debug"))) {
            val reason = extractOopsReason(trimmed) ?: trimmed
            return CrashDetectionCandidate(
                type = KernelCrashType.KERNEL_BUG,
                severity = KernelSeverity.CRITICAL,
                domain = CrashDomain.KERNEL,
                primaryReason = reason,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = extractFaultAddress(trimmed)
            )
        }

        // 3. Unable to handle kernel ... (Page fault / NULL pointer / Data abort / Prefetch abort)
        if (lower.contains("unable to handle kernel")) {
            val type = when {
                lower.contains("null pointer") -> KernelCrashType.NULL_POINTER_DEREFERENCE
                lower.contains("paging request") -> KernelCrashType.PAGE_FAULT
                lower.contains("data abort") -> KernelCrashType.DATA_ABORT
                lower.contains("prefetch abort") -> KernelCrashType.PREFETCH_ABORT
                else -> KernelCrashType.PAGE_FAULT
            }
            return CrashDetectionCandidate(
                type = type,
                severity = KernelSeverity.CRITICAL,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = extractFaultAddress(trimmed)
            )
        }

        // 4. Kernel Oops / Internal error
        if (lower.startsWith("oops:") || lower.contains("oops: 0000") || lower.startsWith("internal error:")) {
            val reason = extractOopsReason(trimmed) ?: trimmed
            return CrashDetectionCandidate(
                type = KernelCrashType.OOPS,
                severity = KernelSeverity.CRITICAL,
                domain = CrashDomain.KERNEL,
                primaryReason = reason,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = extractFaultAddress(trimmed)
            )
        }

        // 5. Watchdog / Lockup
        if (lower.contains("watchdog:") || lower.contains("watchdog timeout") || lower.contains("soft lockup") || lower.contains("hard lockup")) {
            val type = when {
                lower.contains("hard lockup") || lower.contains("hard lockup on") -> KernelCrashType.HARD_LOCKUP
                lower.contains("soft lockup") -> KernelCrashType.SOFT_LOCKUP
                else -> KernelCrashType.WATCHDOG_TIMEOUT
            }
            return CrashDetectionCandidate(
                type = type,
                severity = KernelSeverity.ERROR,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = null
            )
        }

        // 6. Hung task / Blocked tasks
        if (lower.contains("blocked for more than") && (lower.contains("task") || lower.contains("info: task"))) {
            return CrashDetectionCandidate(
                type = KernelCrashType.HUNG_TASK,
                severity = KernelSeverity.ERROR,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = null
            )
        }

        // 7. RCU Stall
        if (lower.contains("rcu_preempt self-detected stall") || lower.contains("rcu_sched detected stalls") || lower.contains("rcu stall")) {
            return CrashDetectionCandidate(
                type = KernelCrashType.RCU_STALL,
                severity = KernelSeverity.ERROR,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = null
            )
        }

        // 8. Kernel WARNING
        if (lower.startsWith("warning: at") || lower.startsWith("warning: cpu:") || (lower.contains("---[ end trace") && lower.contains("warn"))) {
            return CrashDetectionCandidate(
                type = KernelCrashType.KERNEL_WARNING,
                severity = KernelSeverity.WARNING,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = null
            )
        }

        // 9. Fatal Exception in Interrupt or kernel context
        if (lower.contains("fatal exception in interrupt") || lower.contains("fatal exception")) {
            return CrashDetectionCandidate(
                type = KernelCrashType.FATAL_EXCEPTION,
                severity = KernelSeverity.CRITICAL,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = extractFaultAddress(trimmed)
            )
        }

        // 10. General Protection Fault / Segmentation fault
        if (lower.contains("general protection fault") || (lower.contains("segfault at") && lower.contains("kernel"))) {
            return CrashDetectionCandidate(
                type = if (lower.contains("general protection fault")) KernelCrashType.GENERAL_PROTECTION_FAULT else KernelCrashType.SEGMENTATION_FAULT,
                severity = KernelSeverity.CRITICAL,
                domain = CrashDomain.KERNEL,
                primaryReason = trimmed,
                cpu = extractCpu(trimmed),
                pid = extractPid(trimmed),
                comm = extractComm(trimmed),
                faultAddress = extractFaultAddress(trimmed)
            )
        }

        // 11. Android Core System Service Fatal userspace crashes
        if (lower.contains("fatal signal 11") || lower.contains("fatal signal 6") || lower.contains("fatal signal 4") || lower.contains("died: signal")) {
            val isCoreService = lower.contains("surfaceflinger") || lower.contains("system_server") ||
                                lower.contains("zygote") || lower.contains("init") || lower.contains("vold") ||
                                lower.contains("servicemanager") || lower.contains("hwservicemanager") ||
                                lower.contains("rild") || lower.contains("cameraserver") || lower.contains("audioserver")
            if (isCoreService) {
                return CrashDetectionCandidate(
                    type = KernelCrashType.USERSPACE_FATAL,
                    severity = KernelSeverity.ERROR,
                    domain = CrashDomain.USERSPACE,
                    primaryReason = trimmed,
                    cpu = extractCpu(trimmed),
                    pid = extractPid(trimmed),
                    comm = extractComm(trimmed),
                    faultAddress = extractFaultAddress(trimmed)
                )
            }
        }

        return null
    }

    fun extractCpu(text: String): Int? {
        val matcher = CPU_REGEX.matcher(text)
        if (matcher.find()) {
            val v = matcher.group(1) ?: matcher.group(2)
            return v?.toIntOrNull()
        }
        return null
    }

    fun extractPid(text: String): Int? {
        val matcher = PID_REGEX.matcher(text)
        if (matcher.find()) {
            val v = matcher.group(1) ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4)
            return v?.toIntOrNull()
        }
        val bracketMatcher = BRACKETED_COMM_PID_REGEX.matcher(text)
        if (bracketMatcher.find()) {
            return bracketMatcher.group(2)?.toIntOrNull()
        }
        val procMatcher = PROCESS_TASK_REGEX.matcher(text)
        if (procMatcher.find()) {
            return procMatcher.group(2)?.toIntOrNull()
        }
        return null
    }

    fun extractComm(text: String): String? {
        val commMatcher = COMM_REGEX.matcher(text)
        if (commMatcher.find()) {
            return commMatcher.group(1) ?: commMatcher.group(2) ?: commMatcher.group(3)
        }
        val bracketMatcher = BRACKETED_COMM_PID_REGEX.matcher(text)
        if (bracketMatcher.find()) {
            return bracketMatcher.group(1)
        }
        val procMatcher = PROCESS_TASK_REGEX.matcher(text)
        if (procMatcher.find()) {
            return procMatcher.group(1)
        }
        return null
    }

    fun extractFaultAddress(text: String): String? {
        val matcher = FAULT_ADDR_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun extractOopsReason(text: String): String? {
        val matcher = OOPS_REASON_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }
}
