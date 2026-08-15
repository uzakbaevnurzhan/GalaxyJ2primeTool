package com.example.ui.analyzer.selinux.model

/**
 * Entry in file_contexts
 */
data class FileContextEntry(
    val pathRegex: String,
    val fileTypeQualifier: String?, // e.g. "--", "-d", "-c", "-b", "-s", "-l", "-p"
    val context: SelinuxContext?,
    val rawLine: String,
    val isNone: Boolean = false
) {
    val fileTypeDescription: String
        get() = when (fileTypeQualifier) {
            "--" -> "Regular File"
            "-d" -> "Directory"
            "-c" -> "Character Device"
            "-b" -> "Block Device"
            "-s" -> "Socket"
            "-l" -> "Symlink"
            "-p" -> "Named Pipe (FIFO)"
            null, "" -> "Any Type"
            else -> fileTypeQualifier
        }
}

/**
 * Entry in property_contexts
 */
data class PropertyContextEntry(
    val propertyPattern: String,
    val context: SelinuxContext?,
    val typeClass: String?, // e.g. "int", "string", "bool", "double", "enum"
    val rawLine: String
)

/**
 * Entry in service_contexts
 */
data class ServiceContextEntry(
    val serviceName: String,
    val context: SelinuxContext?,
    val rawLine: String
)

/**
 * Entry in seapp_contexts
 */
data class SeappContextEntry(
    val rawLine: String,
    val isSystemServer: Boolean? = null,
    val isPrivApp: Boolean? = null,
    val isEphemeralApp: Boolean? = null,
    val user: String? = null,
    val seinfo: String? = null,
    val name: String? = null,
    val domain: String? = null,
    val type: String? = null,
    val levelFrom: String? = null,
    val level: String? = null,
    val minTargetSdkVersion: String? = null,
    val isWarning: Boolean = false,
    val warningMessage: String? = null
)

/**
 * Entry in genfs_contexts
 */
data class GenfsContextEntry(
    val filesystem: String,
    val path: String,
    val context: SelinuxContext?,
    val rawLine: String
)
