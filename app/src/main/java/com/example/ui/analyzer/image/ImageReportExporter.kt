package com.example.ui.analyzer.image

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageReportExporter {

    fun exportToMarkdown(result: ImageAnalysisResult): String {
        val sb = StringBuilder()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        sb.appendLine("# Android ROM Image Analysis Report")
        sb.appendLine("*Generated on $dateStr by Galaxy J2 Prime Tool (ROM Image Analyzer)*\n")

        sb.appendLine("## 1. Image Summary")
        sb.appendLine("| Property | Value |")
        sb.appendLine("| --- | --- |")
        sb.appendLine("| File Name | `${result.metadata.fileName}` |")
        sb.appendLine("| Detected Format | **${result.metadata.format.displayName}** |")
        sb.appendLine("| File Size | ${formatBytes(result.metadata.fileSize)} |")
        sb.appendLine("| Uncompressed / Raw Size | ${formatBytes(result.metadata.uncompressedSize)} |")
        sb.appendLine("| Compression Ratio | ${"%.2f".format(result.metadata.compressionRatio)}x |")
        sb.appendLine("| Block Size | ${result.metadata.blockSize} bytes |")
        sb.appendLine("| Total Blocks | ${result.metadata.totalBlocks} |")
        sb.appendLine("| Filesystem Type | ${result.metadata.filesystemType} |")
        sb.appendLine("| Volume Name | ${result.metadata.volumeName.ifEmpty { "(none)" }} |")
        sb.appendLine("| UUID | `${result.metadata.uuid.ifEmpty { "(none)" }}` |")
        sb.appendLine("| Mount Point Hint | `${result.metadata.mountPointHint.ifEmpty { "(none)" }}` |")
        sb.appendLine("| Read-Only | ${if (result.metadata.isReadOnly) "Yes" else "No"} |\n")

        if (result.metadata.md5Hash.isNotEmpty() || result.metadata.sha256Hash.isNotEmpty()) {
            sb.appendLine("## 2. Cryptographic Hashes")
            if (result.metadata.md5Hash.isNotEmpty()) sb.appendLine("- **MD5:** `${result.metadata.md5Hash}`")
            if (result.metadata.sha1Hash.isNotEmpty()) sb.appendLine("- **SHA-1:** `${result.metadata.sha1Hash}`")
            if (result.metadata.sha256Hash.isNotEmpty()) sb.appendLine("- **SHA-256:** `${result.metadata.sha256Hash}`")
            sb.appendLine()
        }

        if (result.metadata.rawHeaderFields.isNotEmpty()) {
            sb.appendLine("## 3. Header Raw Fields")
            sb.appendLine("| Field | Value |")
            sb.appendLine("| --- | --- |")
            for ((k, v) in result.metadata.rawHeaderFields) {
                sb.appendLine("| `$k` | `$v` |")
            }
            sb.appendLine()
        }

        if (result.partitions.isNotEmpty()) {
            sb.appendLine("## 4. Partitions Table (${result.partitions.size} detected)")
            sb.appendLine("| Partition | Size | Offset | Filesystem | Group | Read-Only |")
            sb.appendLine("| --- | --- | --- | --- | --- | --- |")
            for (p in result.partitions) {
                sb.appendLine("| **${p.name}** | ${formatBytes(p.sizeBytes)} | 0x${java.lang.Long.toHexString(p.startOffset)} | ${p.filesystem.displayName} | ${p.groupName} | ${if (p.isReadOnly) "RO" else "RW"} |")
            }
            sb.appendLine()
        }

        if (result.issues.isNotEmpty()) {
            sb.appendLine("## 5. Detected Issues & Porting Diagnostics (${result.issues.size})")
            for (issue in result.issues) {
                val icon = when (issue.severity) {
                    IssueSeverity.CRITICAL -> "🔴 [CRITICAL]"
                    IssueSeverity.WARNING -> "🟡 [WARNING]"
                    IssueSeverity.INFO -> "ℹ️ [INFO]"
                }
                sb.appendLine("### $icon ${issue.title}")
                sb.appendLine("- **Category:** ${issue.category}")
                sb.appendLine("- **Affected Partition:** `${issue.affectedPartition}`")
                sb.appendLine("- **Description:** ${issue.description}")
                sb.appendLine("- **Recommendation:** ${issue.recommendation}\n")
            }
        } else {
            sb.appendLine("## 5. Issues & Diagnostics")
            sb.appendLine("✅ No structural, filesystem, or porting issues detected.\n")
        }

        return sb.toString()
    }

    fun exportToJson(result: ImageAnalysisResult): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"fileName\": \"${escapeJson(result.metadata.fileName)}\",")
        sb.appendLine("  \"format\": \"${result.metadata.format.name}\",")
        sb.appendLine("  \"fileSize\": ${result.metadata.fileSize},")
        sb.appendLine("  \"uncompressedSize\": ${result.metadata.uncompressedSize},")
        sb.appendLine("  \"blockSize\": ${result.metadata.blockSize},")
        sb.appendLine("  \"totalBlocks\": ${result.metadata.totalBlocks},")
        sb.appendLine("  \"filesystemType\": \"${escapeJson(result.metadata.filesystemType)}\",")
        sb.appendLine("  \"volumeName\": \"${escapeJson(result.metadata.volumeName)}\",")
        sb.appendLine("  \"uuid\": \"${escapeJson(result.metadata.uuid)}\",")
        sb.appendLine("  \"md5\": \"${result.metadata.md5Hash}\",")
        sb.appendLine("  \"sha256\": \"${result.metadata.sha256Hash}\",")

        sb.appendLine("  \"partitions\": [")
        val partStrs = result.partitions.map { p ->
            """    {
      "name": "${escapeJson(p.name)}",
      "sizeBytes": ${p.sizeBytes},
      "startOffset": ${p.startOffset},
      "filesystem": "${p.filesystem.name}",
      "groupName": "${escapeJson(p.groupName)}",
      "isReadOnly": ${p.isReadOnly}
    }"""
        }
        sb.appendLine(partStrs.joinToString(",\n"))
        sb.appendLine("  ],")

        sb.appendLine("  \"issues\": [")
        val issueStrs = result.issues.map { i ->
            """    {
      "id": "${escapeJson(i.id)}",
      "severity": "${i.severity.name}",
      "title": "${escapeJson(i.title)}",
      "category": "${escapeJson(i.category)}",
      "description": "${escapeJson(i.description)}",
      "recommendation": "${escapeJson(i.recommendation)}"
    }"""
        }
        sb.appendLine(issueStrs.joinToString(",\n"))
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    fun exportToTxt(result: ImageAnalysisResult): String {
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("ANDROID ROM IMAGE ANALYSIS REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("File Name:          ${result.metadata.fileName}")
        sb.appendLine("Format:             ${result.metadata.format.displayName}")
        sb.appendLine("Size:               ${formatBytes(result.metadata.fileSize)}")
        sb.appendLine("Uncompressed:       ${formatBytes(result.metadata.uncompressedSize)}")
        sb.appendLine("Block Size:         ${result.metadata.blockSize} bytes")
        sb.appendLine("Filesystem:         ${result.metadata.filesystemType}")
        sb.appendLine("Volume Name:        ${result.metadata.volumeName}")
        sb.appendLine("UUID:               ${result.metadata.uuid}")
        sb.appendLine("MD5:                ${result.metadata.md5Hash}")
        sb.appendLine("SHA-256:            ${result.metadata.sha256Hash}")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("PARTITIONS (${result.partitions.size}):")
        for (p in result.partitions) {
            sb.appendLine(" - ${p.name.padEnd(16)} ${formatBytes(p.sizeBytes).padEnd(12)} [${p.filesystem.displayName}]")
        }
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("ISSUES (${result.issues.size}):")
        for (i in result.issues) {
            sb.appendLine(" [${i.severity.name}] ${i.title}")
            sb.appendLine("   Desc: ${i.description}")
            sb.appendLine("   Rec:  ${i.recommendation}")
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }

    fun exportToCsv(result: ImageAnalysisResult): String {
        val sb = StringBuilder()
        sb.appendLine("Type,Name/Key,Value/Size,Details,Extra")
        sb.appendLine("METADATA,FileName,\"${result.metadata.fileName}\",,")
        sb.appendLine("METADATA,Format,\"${result.metadata.format.displayName}\",,")
        sb.appendLine("METADATA,FileSize,${result.metadata.fileSize},,")
        sb.appendLine("METADATA,BlockSize,${result.metadata.blockSize},,")
        sb.appendLine("METADATA,Filesystem,\"${result.metadata.filesystemType}\",,")
        sb.appendLine("METADATA,MD5,\"${result.metadata.md5Hash}\",,")
        sb.appendLine("METADATA,SHA256,\"${result.metadata.sha256Hash}\",,")

        for (p in result.partitions) {
            sb.appendLine("PARTITION,\"${p.name}\",${p.sizeBytes},\"${p.filesystem.displayName}\",\"${p.groupName}\"")
        }

        for (i in result.issues) {
            sb.appendLine("ISSUE,\"${i.id}\",\"${i.severity.name}\",\"${escapeCsv(i.title)}\",\"${escapeCsv(i.recommendation)}\"")
        }

        return sb.toString()
    }

    private fun escapeJson(str: String): String = str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    private fun escapeCsv(str: String): String = str.replace("\"", "\"\"")

    private fun formatBytes(bytes: Long): String {
        val kib = 1024.0
        val mib = kib * 1024.0
        val gib = mib * 1024.0
        return when {
            bytes >= gib -> String.format(Locale.US, "%.2f GB", bytes / gib)
            bytes >= mib -> String.format(Locale.US, "%.2f MB", bytes / mib)
            bytes >= kib -> String.format(Locale.US, "%.2f KB", bytes / kib)
            else -> "$bytes B"
        }
    }
}
