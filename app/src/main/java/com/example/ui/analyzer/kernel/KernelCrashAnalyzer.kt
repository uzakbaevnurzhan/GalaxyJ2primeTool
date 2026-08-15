package com.example.ui.analyzer.kernel

import android.content.Context
import android.net.Uri
import com.example.ui.analyzer.core.AnalyzerResult
import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.kernel.engine.KernelCrashEngine
import com.example.ui.analyzer.kernel.engine.KernelCrashExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified analyzer entry point for Kernel Crash parsing.
 */
object KernelCrashAnalyzer {
    suspend fun parse(context: Context, uri: Uri): AnalyzerResult = withContext(Dispatchers.IO) {
        try {
            val engine = KernelCrashEngine()
            val report = context.contentResolver.openInputStream(uri)?.use { stream ->
                engine.analyzeStream(stream, "kernel_log.txt")
            }

            if (report == null) {
                return@withContext AnalyzerResult(
                    status = AnalyzerStatus.ERROR,
                    summary = "Failed to open kernel log",
                    details = "Could not open stream for the selected file."
                )
            }

            if (report.totalEvents == 0) {
                return@withContext AnalyzerResult(
                    status = AnalyzerStatus.WARNING,
                    summary = "No Kernel Crash Detected",
                    details = "The log does not contain standard kernel panic, oops, BUG, or call trace signatures.\n\nKernel: ${report.kernelVersion ?: "Unknown"}\nLines Analyzed: ${report.totalLinesAnalyzed}"
                )
            }

            val status = if (report.criticalEvents > 0) AnalyzerStatus.ERROR else AnalyzerStatus.WARNING
            val summary = buildString {
                appendLine("Kernel Crash Events: ${report.totalEvents} (${report.criticalEvents} Critical, ${report.errorEvents} Error, ${report.warningEvents} Warning)")
                if (report.kernelVersion != null) appendLine("Kernel: ${report.kernelVersion} (${report.architecture})")
                if (report.bootFailureAnalysis.isBootFailureLikely) {
                    appendLine("Boot Failure Detected: ${report.bootFailureAnalysis.detectedBlockers.firstOrNull() ?: "Yes"}")
                }
            }.trim()

            val details = KernelCrashExporter.toMarkdown(report)

            AnalyzerResult(
                status = status,
                summary = summary,
                details = details
            )
        } catch (e: Exception) {
            AnalyzerResult(AnalyzerStatus.ERROR, "Exception during kernel crash analysis", e.message ?: "Unknown error")
        }
    }
}
