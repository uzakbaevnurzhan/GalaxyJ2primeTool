package com.example.ui.analyzer.kernel.engine

import android.content.Context
import android.net.Uri
import com.example.ui.analyzer.kernel.model.*
import com.example.ui.analyzer.kernel.parser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class EngineProgress(
    val processedBytes: Long,
    val totalBytes: Long,
    val percent: Int,
    val linesCount: Long,
    val eventsCount: Int
)

class KernelCrashEngine(
    private val contextLinesBeforeCount: Int = 20,
    private val contextLinesAfterCount: Int = 50,
    private val systemMap: SystemMapParser? = null
) {

    suspend fun analyzeStream(
        inputStream: InputStream,
        fileName: String,
        totalBytes: Long = 0L,
        onProgress: ((EngineProgress) -> Unit)? = null
    ): KernelCrashReport = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 64 * 1024)

        var totalLines = 0L
        var bytesProcessed = 0L
        var lastReportedTime = System.currentTimeMillis()

        var kernelRelease: String? = null
        var compilerInfo: String? = null
        var buildDate: String? = null
        var detectedArch = KernelArchitecture.UNKNOWN

        val ringBufferBefore = RingBuffer<String>(contextLinesBeforeCount)
        val crashEvents = mutableListOf<KernelCrashEvent>()
        val warnings = mutableListOf<KernelWarning>()

        // State machine for multi-line crash blocks
        var currentCandidate: CrashDetectionCandidate? = null
        var currentCrashType = KernelCrashType.UNKNOWN
        var currentSeverity = KernelSeverity.INFO
        var currentPanicReason: String? = null
        var currentCpu: Int? = null
        var currentPid: Int? = null
        var currentComm: String? = null
        var currentFaultAddr: String? = null

        var currentEventStartLine = 0L
        var currentEventTimestamp: String? = null
        var currentEventUptime: Double? = null
        var currentEventContextBefore: List<String> = emptyList()
        val currentEventRawLines = mutableListOf<String>()
        val currentEventRegLines = mutableListOf<String>()
        val currentEventTraceLines = mutableListOf<String>()
        var collectingPostContextRemaining = 0
        var inCallTrace = false

        // Boot failure metrics
        var initCrashed = false
        var zygoteCrashed = false
        var systemServerCrashed = false
        var surfaceFlingerCrashed = false
        var criticalDriverFailed = false
        var mountFailureDetected = false
        var selinuxDenialsCount = 0

        var line = reader.readLine()
        while (line != null) {
            coroutineContext.ensureActive()
            totalLines++
            val lineBytes = line.toByteArray(Charsets.UTF_8).size.toLong() + 1
            bytesProcessed += lineBytes
            digest.update(line.toByteArray(Charsets.UTF_8))

            val parsedTs = KernelTimestampParser.parseLine(line)
            val cleaned = parsedTs.cleanedLine
            val lower = cleaned.lowercase()

            // 1. Detect Kernel Version line
            if (kernelRelease == null && lower.contains("linux version")) {
                val parsedVer = KernelVersionParser.parse(cleaned)
                if (parsedVer != null) {
                    kernelRelease = parsedVer.kernelRelease
                    compilerInfo = parsedVer.compilerInfo
                    buildDate = parsedVer.buildDate
                    if (parsedVer.architecture != KernelArchitecture.UNKNOWN) {
                        detectedArch = parsedVer.architecture
                    }
                }
            }

            // 2. Track boot failure signals
            if (lower.contains("avc: denied")) {
                selinuxDenialsCount++
            }
            if (lower.contains("vfs: unable to mount root fs") || lower.contains("mount: failed to mount") || lower.contains("cannot mount /vendor") || lower.contains("cannot mount /system")) {
                mountFailureDetected = true
            }
            if (lower.contains("init crashed") || (lower.contains("fatal signal") && lower.contains("init"))) {
                initCrashed = true
            }
            if (lower.contains("zygote") && (lower.contains("died: signal") || lower.contains("fatal signal") || lower.contains("aborting"))) {
                zygoteCrashed = true
            }
            if (lower.contains("system_server") && (lower.contains("died: signal") || lower.contains("fatal signal") || lower.contains("watchdog: system_server"))) {
                systemServerCrashed = true
            }
            if (lower.contains("surfaceflinger") && (lower.contains("died: signal") || lower.contains("fatal signal"))) {
                surfaceFlingerCrashed = true
            }

            // 3. Crash detection triggers
            val candidate = KernelCrashDetector.detectCrashStart(line)

            if (candidate != null && currentCandidate == null) {
                // Begin new crash event block
                currentCandidate = candidate
                currentCrashType = candidate.type
                currentSeverity = candidate.severity
                currentPanicReason = candidate.primaryReason
                currentCpu = candidate.cpu ?: KernelCrashDetector.extractCpu(cleaned)
                currentPid = candidate.pid ?: KernelCrashDetector.extractPid(cleaned)
                currentComm = candidate.comm ?: KernelCrashDetector.extractComm(cleaned)
                currentFaultAddr = candidate.faultAddress ?: KernelCrashDetector.extractFaultAddress(cleaned)

                currentEventStartLine = totalLines
                currentEventTimestamp = parsedTs.rawTimestamp.ifBlank { null }
                currentEventUptime = parsedTs.uptimeSeconds
                currentEventContextBefore = ringBufferBefore.toList()
                currentEventRawLines.clear()
                currentEventRegLines.clear()
                currentEventTraceLines.clear()
                currentEventRawLines.add(line)
                collectingPostContextRemaining = contextLinesAfterCount
                inCallTrace = false
            } else if (currentCandidate != null) {
                // Inside an active crash event block
                currentEventRawLines.add(line)

                // Refine candidate attributes from this line
                if (currentCpu == null) currentCpu = KernelCrashDetector.extractCpu(cleaned)
                if (currentPid == null) currentPid = KernelCrashDetector.extractPid(cleaned)
                if (currentComm == null) currentComm = KernelCrashDetector.extractComm(cleaned)
                if (currentFaultAddr == null) currentFaultAddr = KernelCrashDetector.extractFaultAddress(cleaned)

                val subCandidate = KernelCrashDetector.detectCrashStart(line)
                if (subCandidate != null) {
                    if (subCandidate.type == KernelCrashType.KERNEL_PANIC) {
                        currentPanicReason = subCandidate.primaryReason
                    }
                    if (subCandidate.cpu != null && currentCpu == null) currentCpu = subCandidate.cpu
                    if (subCandidate.pid != null && currentPid == null) currentPid = subCandidate.pid
                    if (subCandidate.comm != null && currentComm == null) currentComm = subCandidate.comm
                    if (subCandidate.faultAddress != null && currentFaultAddr == null) currentFaultAddr = subCandidate.faultAddress
                }

                // Detect Call Trace section
                if (KernelTraceParser.isTraceHeader(cleaned)) {
                    inCallTrace = true
                } else if (inCallTrace) {
                    if (KernelTraceParser.isTraceTerminator(cleaned) || cleaned.isBlank()) {
                        inCallTrace = false
                    } else {
                        currentEventTraceLines.add(line)
                    }
                }

                // Collect register dump lines
                if (cleaned.contains("pc :") || cleaned.contains("sp :") || cleaned.contains("r0 :") ||
                    cleaned.contains("r0:") || cleaned.contains("x0 :") || cleaned.contains("x0:") ||
                    cleaned.contains("cpsr:") || cleaned.contains("Flags:") ||
                    cleaned.contains("ESR") || cleaned.contains("FAR") || cleaned.contains("PC is at") ||
                    cleaned.contains("LR is at")) {
                    currentEventRegLines.add(cleaned)
                }

                // Decrement post-context counter
                collectingPostContextRemaining--
                if (collectingPostContextRemaining <= 0) {
                    // Finalize current crash event
                    finalizeCrashEvent(
                        type = currentCrashType,
                        severity = currentSeverity,
                        domain = currentCandidate!!.domain,
                        primaryReason = currentPanicReason ?: currentCandidate!!.primaryReason,
                        cpu = currentCpu,
                        pid = currentPid,
                        comm = currentComm,
                        faultAddress = currentFaultAddr,
                        startLine = currentEventStartLine,
                        timestamp = currentEventTimestamp,
                        uptime = currentEventUptime,
                        contextBefore = currentEventContextBefore,
                        rawLines = currentEventRawLines,
                        regLines = currentEventRegLines,
                        traceLines = currentEventTraceLines,
                        kernelRelease = kernelRelease,
                        detectedArch = detectedArch,
                        destination = crashEvents
                    )
                    currentCandidate = null
                }
            }

            ringBufferBefore.add(line)

            // Periodic progress update
            val now = System.currentTimeMillis()
            if (now - lastReportedTime > 200 && onProgress != null) {
                lastReportedTime = now
                val percent = if (totalBytes > 0) ((bytesProcessed * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                onProgress(EngineProgress(bytesProcessed, totalBytes, percent, totalLines, crashEvents.size))
            }

            line = reader.readLine()
        }

        // Finalize pending candidate at EOF
        if (currentCandidate != null) {
            finalizeCrashEvent(
                type = currentCrashType,
                severity = currentSeverity,
                domain = currentCandidate!!.domain,
                primaryReason = currentPanicReason ?: currentCandidate!!.primaryReason,
                cpu = currentCpu,
                pid = currentPid,
                comm = currentComm,
                faultAddress = currentFaultAddr,
                startLine = currentEventStartLine,
                timestamp = currentEventTimestamp,
                uptime = currentEventUptime,
                contextBefore = currentEventContextBefore,
                rawLines = currentEventRawLines,
                regLines = currentEventRegLines,
                traceLines = currentEventTraceLines,
                kernelRelease = kernelRelease,
                detectedArch = detectedArch,
                destination = crashEvents
            )
        }

        // Group call traces
        val repeatedTraces = groupCallTraces(crashEvents)

        // Compute top processes & symbols
        val topProcesses = crashEvents.mapNotNull { it.processName ?: it.comm }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(10)

        val topSymbols = crashEvents.flatMap { it.stackFrames }.mapNotNull { it.symbol }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(10)

        // Aggregate suspected subsystems
        val allTextTokens = mutableListOf<String>()
        crashEvents.forEach { ev ->
            ev.comm?.let { allTextTokens.add(it) }
            ev.topSymbol?.let { allTextTokens.add(it) }
            ev.stackFrames.mapNotNull { it.module }.forEach { allTextTokens.add(it) }
        }
        val suspectedSubsystems = KernelSubsystemDetector.detectSubsystems(allTextTokens)

        // Synthesize Boot Failure analysis
        val hasPanic = crashEvents.any { it.type == KernelCrashType.KERNEL_PANIC || it.type == KernelCrashType.OOPS || it.type == KernelCrashType.KERNEL_BUG }
        val hasWatchdog = crashEvents.any { it.type == KernelCrashType.WATCHDOG_TIMEOUT || it.type == KernelCrashType.HARD_LOCKUP }
        val isBootFailureLikely = hasPanic || hasWatchdog || initCrashed || zygoteCrashed || systemServerCrashed || surfaceFlingerCrashed || mountFailureDetected

        val blockers = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (hasPanic) {
            val firstPanic = crashEvents.firstOrNull { it.type == KernelCrashType.KERNEL_PANIC }
            blockers.add("Kernel Panic: ${firstPanic?.panicReason ?: "Fatal crash"}")
            recommendations.add("Check kernel call trace and PC instruction for driver fault.")
        }
        if (mountFailureDetected) {
            blockers.add("Storage / Rootfs mount failure")
            recommendations.add("Verify fstab mount flags, filesystem types (ext4/f2fs/erofs), and partition size.")
        }
        if (initCrashed) {
            blockers.add("Early init process crashed (PID 1)")
            recommendations.add("Check /init binary architecture (32-bit vs 64-bit) and dynamic linker dependencies.")
        }
        if (zygoteCrashed) {
            blockers.add("Zygote daemon crash loop")
            recommendations.add("Inspect libandroid_runtime.so, ART runtime flags, and SELinux zygote policies.")
        }
        if (systemServerCrashed) {
            blockers.add("system_server died during framework bootstrap")
            recommendations.add("Check missing vendor HAL services (AIDL/HIDL) and services.jar compatibility.")
        }
        if (surfaceFlingerCrashed) {
            blockers.add("SurfaceFlinger graphics composer crashed")
            recommendations.add("Check Mali GPU driver / libGLES / libgui.so compatibility.")
        }
        if (selinuxDenialsCount > 0) {
            blockers.add("$selinuxDenialsCount SELinux AVC denials recorded")
            recommendations.add("Review SELinux Policy Analyzer for blocked access to vendor devices or properties.")
        }

        val bootAnalysis = BootFailureAnalysis(
            isBootFailureLikely = isBootFailureLikely,
            kernelPanicPresent = hasPanic,
            watchdogTriggered = hasWatchdog,
            initCrashed = initCrashed,
            zygoteCrashed = zygoteCrashed,
            systemServerCrashed = systemServerCrashed,
            surfaceFlingerCrashed = surfaceFlingerCrashed,
            criticalDriverFailed = criticalDriverFailed,
            mountFailureDetected = mountFailureDetected,
            selinuxEnforcingDenialsDetected = selinuxDenialsCount > 0,
            detectedBlockers = blockers,
            recoveryRecommendations = recommendations
        )

        // Compute sha256
        val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }

        onProgress?.invoke(EngineProgress(bytesProcessed, totalBytes, 100, totalLines, crashEvents.size))

        KernelCrashReport(
            fileName = fileName,
            fileSize = bytesProcessed,
            fileSha256 = sha256Hex,
            totalLinesAnalyzed = totalLines,
            kernelVersion = kernelRelease,
            compilerInfo = compilerInfo,
            buildDate = buildDate,
            architecture = detectedArch,
            totalEvents = crashEvents.size,
            criticalEvents = crashEvents.count { it.severity == KernelSeverity.CRITICAL },
            errorEvents = crashEvents.count { it.severity == KernelSeverity.ERROR },
            warningEvents = crashEvents.count { it.severity == KernelSeverity.WARNING },
            crashEvents = crashEvents,
            repeatedTraces = repeatedTraces,
            warnings = warnings,
            bootFailureAnalysis = bootAnalysis,
            topProcesses = topProcesses,
            topSymbols = topSymbols,
            suspectedSubsystems = suspectedSubsystems
        )
    }

    private fun finalizeCrashEvent(
        type: KernelCrashType,
        severity: KernelSeverity,
        domain: CrashDomain,
        primaryReason: String,
        cpu: Int?,
        pid: Int?,
        comm: String?,
        faultAddress: String?,
        startLine: Long,
        timestamp: String?,
        uptime: Double?,
        contextBefore: List<String>,
        rawLines: List<String>,
        regLines: List<String>,
        traceLines: List<String>,
        kernelRelease: String?,
        detectedArch: KernelArchitecture,
        destination: MutableList<KernelCrashEvent>
    ) {
        val regSet = KernelRegisterParser.parseRegisterBlock(regLines)
        val stackFrames = mutableListOf<KernelStackFrame>()

        var frameIdx = 0
        for (tLine in traceLines) {
            val frame = KernelTraceParser.parseFrame(tLine, frameIdx)
            if (frame != null) {
                val resolvedFrame = if (systemMap != null && systemMap.isLoaded && frame.address != null) {
                    val resolved = systemMap.resolveAddress(frame.address)
                    if (resolved != null) {
                        frame.copy(symbol = resolved.first, offsetHex = "0x" + resolved.second.toString(16))
                    } else frame
                } else frame

                stackFrames.add(resolvedFrame)
                frameIdx++
            }
        }

        val arch = if (regSet.architecture != KernelArchitecture.UNKNOWN) regSet.architecture else detectedArch

        // Process name & PID resolution
        val finalComm = comm ?: regSet.registers["comm"]
        val finalPid = pid
        val finalCpu = cpu
        val finalFaultAddr = faultAddress ?: regSet.faultAddress

        val pcSymbol = regSet.pc ?: stackFrames.firstOrNull()?.symbol
        val stackSymbols = stackFrames.mapNotNull { it.symbol }

        val rootCause = KernelSubsystemDetector.analyzeRootCause(
            type = type,
            panicReason = primaryReason,
            faultAddress = finalFaultAddr,
            pcSymbol = pcSymbol,
            stackSymbols = stackSymbols,
            processName = finalComm,
            contextLines = rawLines
        )

        val event = KernelCrashEvent(
            id = "CRASH-${destination.size + 1}",
            type = type,
            severity = severity,
            domain = domain,
            timestamp = timestamp,
            uptimeSeconds = uptime,
            cpu = finalCpu,
            pid = finalPid,
            comm = finalComm,
            processName = finalComm,
            faultAddress = finalFaultAddr,
            panicReason = primaryReason,
            kernelVersion = kernelRelease,
            architecture = arch,
            sourceLineIndex = startLine,
            registers = regSet,
            stackFrames = stackFrames,
            contextLinesBefore = contextBefore,
            contextLinesAfter = rawLines.takeLast(contextLinesAfterCount),
            rawBlock = rawLines.joinToString("\n"),
            analysis = rootCause
        )

        destination.add(event)
    }

    private fun groupCallTraces(events: List<KernelCrashEvent>): List<KernelTraceGroup> {
        val groups = events.groupBy { it.traceSignature }
        return groups.map { (sig, groupEvents) ->
            KernelTraceGroup(
                signature = sig,
                sampleEvent = groupEvents.first(),
                occurrences = groupEvents.size,
                firstTimestamp = groupEvents.firstOrNull()?.timestamp,
                lastTimestamp = groupEvents.lastOrNull()?.timestamp,
                events = groupEvents
            )
        }.sortedByDescending { it.occurrences }
    }
}
