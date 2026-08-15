package com.example.ui.analyzer.getprop

import java.util.UUID

/**
 * Metadata for a processed source file.
 */
data class SourceFileInfo(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val parsedCount: Int = 0,
    val skippedCount: Int = 0
)

/**
 * High-level extracted device & build summary.
 */
data class DeviceSummary(
    val model: String = "Unknown",
    val name: String = "Unknown",
    val device: String = "Unknown",
    val board: String = "Unknown",
    val brand: String = "Unknown",
    val manufacturer: String = "Unknown",
    val hardware: String = "Unknown",
    val platform: String = "Unknown",
    val socModel: String = "Unknown",
    val androidVersion: String = "Unknown",
    val sdk: Int = 0,
    val codename: String = "Unknown",
    val buildId: String = "Unknown",
    val buildDisplayId: String = "Unknown",
    val securityPatch: String = "Unknown",
    val incremental: String = "Unknown",
    val primaryAbi: String = "Unknown",
    val abiList: List<String> = emptyList(),
    val abiType: String = "Unknown",
    val selinuxMode: String = "Unknown",
    val isDebuggable: Boolean? = null,
    val isSecure: Boolean? = null,
    val isAdbSecure: Boolean? = null,
    val buildTags: String = "Unknown"
)

/**
 * Represents a complete snapshot of analyzed properties from one or more sources.
 */
data class GetpropSnapshot(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<SourceFileInfo>,
    val properties: Map<String, GetpropEntry>,
    val allEntries: List<GetpropEntry>,
    val deviceSummary: DeviceSummary,
    val totalPropertiesCount: Int = properties.size,
    val duplicateCount: Int = 0,
    val conflictCount: Int = 0
)
