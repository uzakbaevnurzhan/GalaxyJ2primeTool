package com.example.ui.analyzer.getprop

/**
 * Represents a single instance of a property occurrence across input files or lines.
 */
data class PropertyOccurrence(
    val source: String,
    val lineNumber: Int,
    val value: String
)

/**
 * Status of property uniqueness/conflict across sources or within the same source.
 */
enum class ConflictStatus {
    NONE,
    DUPLICATE_IDENTICAL,
    CONFLICT_VALUE_MISMATCH
}

/**
 * Structured model of a parsed property.
 */
data class GetpropEntry(
    val key: String,
    val value: String,
    val source: String,
    val lineNumber: Int,
    val category: GetpropCategory = GetpropCategory.categorize(key),
    val valueType: PropertyValueType = PropertyValueType.detect(value),
    val isDuplicate: Boolean = false,
    val conflictStatus: ConflictStatus = ConflictStatus.NONE,
    val occurrences: List<PropertyOccurrence> = listOf(PropertyOccurrence(source, lineNumber, value))
)
