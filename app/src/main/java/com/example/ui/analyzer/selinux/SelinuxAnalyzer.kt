package com.example.ui.analyzer.selinux

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ui.analyzer.core.AnalyzerResult
import com.example.ui.analyzer.core.AnalyzerStatus
import com.example.ui.analyzer.selinux.engine.SelinuxAnalyzerEngine
import com.example.ui.analyzer.selinux.engine.SelinuxExporter
import com.example.ui.analyzer.selinux.model.SelinuxFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SelinuxAnalyzer {

    /**
     * Unified analyzer entry point that connects to the complete SelinuxAnalyzerEngine.
     */
    suspend fun parse(context: Context, uri: Uri): AnalyzerResult = withContext(Dispatchers.IO) {
        try {
            var fileName = "unknown"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val analysisResult = SelinuxAnalyzerEngine.analyzeStream(
                    inputStream = inputStream,
                    totalBytes = fileSize,
                    fileName = fileName
                )

                if (analysisResult.detectedType == SelinuxFileType.BINARY_SEPOLICY) {
                    return@withContext AnalyzerResult(
                        status = AnalyzerStatus.UNSUPPORTED,
                        summary = "Binary SEPolicy Detected",
                        details = "Binary sepolicy parsing is not supported yet.\n\nThis tool currently parses textual SELinux contexts (file_contexts, property_contexts, service_contexts, seapp_contexts, genfs_contexts) and AVC audit denial logs."
                    )
                }

                if (analysisResult.detectedType == SelinuxFileType.UNKNOWN && analysisResult.totalLinesParsed == 0L) {
                    return@withContext AnalyzerResult(
                        status = AnalyzerStatus.WARNING,
                        summary = "Empty or Unrecognized File",
                        details = "Parsed 0 valid SELinux context definitions or AVC log lines."
                    )
                }

                val status = if (analysisResult.warnings.isNotEmpty()) AnalyzerStatus.WARNING else AnalyzerStatus.SUCCESS
                val summary = when (analysisResult.detectedType) {
                    SelinuxFileType.AVC_LOG -> {
                        val stats = analysisResult.avcStatistics
                        "SELinux AVC Audit Log (${stats?.totalDenials ?: 0} denials, ${stats?.uniqueDenials ?: 0} unique rules)"
                    }
                    SelinuxFileType.FILE_CONTEXTS -> "SELinux File Contexts (${analysisResult.fileContexts.size} entries)"
                    SelinuxFileType.PROPERTY_CONTEXTS -> "SELinux Property Contexts (${analysisResult.propertyContexts.size} entries)"
                    SelinuxFileType.SERVICE_CONTEXTS -> "SELinux Service Contexts (${analysisResult.serviceContexts.size} entries)"
                    SelinuxFileType.SEAPP_CONTEXTS -> "SELinux Seapp Contexts (${analysisResult.seappContexts.size} entries)"
                    SelinuxFileType.GENFS_CONTEXTS -> "SELinux GenFS Contexts (${analysisResult.genfsContexts.size} entries)"
                    else -> "SELinux Configuration File (${analysisResult.totalLinesParsed} lines)"
                }

                val details = SelinuxExporter.exportToText(analysisResult)

                AnalyzerResult(
                    status = status,
                    summary = summary,
                    details = details
                )
            } ?: AnalyzerResult(AnalyzerStatus.ERROR, "Failed to Open File", "Could not resolve stream for Uri: $uri")
        } catch (e: Exception) {
            AnalyzerResult(AnalyzerStatus.ERROR, "SELinux Analysis Failed", e.message ?: "Unknown error")
        }
    }
}
