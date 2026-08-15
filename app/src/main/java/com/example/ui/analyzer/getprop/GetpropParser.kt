package com.example.ui.analyzer.getprop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.security.MessageDigest

/**
 * Result of parsing a single input stream/file.
 */
data class RawParsedProperties(
    val sourceName: String,
    val sourcePath: String = sourceName,
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val entries: List<GetpropEntry>,
    val parsedCount: Int,
    val skippedCount: Int,
    val warnings: List<String> = emptyList()
)

object GetpropParser {

    private val GETPROP_BRACKET_REGEX = Regex("^\\[\\s*([^\\s\\]]+)\\s*\\]\\s*:\\s*\\[(.*?)\\]$")
    private val GETPROP_FALLBACK_BRACKET = Regex("^\\[(.*?)\\]\\s*:\\s*\\[?(.*?)\\]?$")

    /**
     * Parses an input stream using streaming BufferedReader without loading entire file into memory.
     * Computes SHA-256 hash and size simultaneously.
     */
    suspend fun parseStream(
        inputStream: InputStream,
        sourceName: String = "build.prop",
        sourcePath: String = sourceName
    ): RawParsedProperties = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        val entries = mutableListOf<GetpropEntry>()
        val warnings = mutableListOf<String>()
        var parsedCount = 0
        var skippedCount = 0
        var lineNumber = 0

        // Custom counting and digesting InputStream wrapper
        val hashingStream = object : InputStream() {
            override fun read(): Int {
                val b = inputStream.read()
                if (b != -1) {
                    digest.update(b.toByte())
                    totalBytes++
                }
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = inputStream.read(b, off, len)
                if (count > 0) {
                    digest.update(b, off, count)
                    totalBytes += count
                }
                return count
            }

            override fun close() {
                inputStream.close()
            }
        }

        val reader = BufferedReader(InputStreamReader(hashingStream, Charsets.UTF_8), 32 * 1024)
        var line: String? = reader.readLine()

        while (line != null) {
            lineNumber++
            val trimmed = line.trim()

            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                line = reader.readLine()
                continue
            }

            val parsedEntry = parseLine(trimmed, sourceName, lineNumber)
            if (parsedEntry != null) {
                entries.add(parsedEntry)
                parsedCount++
            } else {
                skippedCount++
                if (warnings.size < 50) {
                    warnings.add("Line $lineNumber: malformed property syntax '$trimmed'")
                }
            }

            line = reader.readLine()
        }

        val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }

        RawParsedProperties(
            sourceName = sourceName,
            sourcePath = sourcePath,
            sizeBytes = totalBytes,
            sha256 = sha256Hex,
            entries = entries,
            parsedCount = parsedCount,
            skippedCount = skippedCount,
            warnings = warnings
        )
    }

    /**
     * Parses from a Reader.
     */
    fun parseReader(reader: Reader, sourceName: String = "input"): RawParsedProperties {
        val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)
        val entries = mutableListOf<GetpropEntry>()
        val warnings = mutableListOf<String>()
        var parsedCount = 0
        var skippedCount = 0
        var lineNumber = 0

        bufferedReader.forEachLine { rawLine ->
            lineNumber++
            val trimmed = rawLine.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                val entry = parseLine(trimmed, sourceName, lineNumber)
                if (entry != null) {
                    entries.add(entry)
                    parsedCount++
                } else {
                    skippedCount++
                    if (warnings.size < 50) {
                        warnings.add("Line $lineNumber: malformed syntax '$trimmed'")
                    }
                }
            }
        }

        return RawParsedProperties(
            sourceName = sourceName,
            sourcePath = sourceName,
            sizeBytes = 0L,
            sha256 = "",
            entries = entries,
            parsedCount = parsedCount,
            skippedCount = skippedCount,
            warnings = warnings
        )
    }

    /**
     * Parses from a String.
     */
    fun parseString(content: String, sourceName: String = "string_input"): RawParsedProperties {
        return parseReader(content.reader(), sourceName)
    }

    /**
     * Parses a single line representing a property.
     * Supports:
     * - `[key]: [value]`
     * - `[key]: []`
     * - `key=value`
     * - `key=`
     * - `key = value`
     */
    fun parseLine(line: String, sourceName: String, lineNumber: Int): GetpropEntry? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return null
        }

        // 1. Try getprop bracket syntax: [ro.product.model]: [SM-G532F]
        val matchBracket = GETPROP_BRACKET_REGEX.find(trimmed) ?: GETPROP_FALLBACK_BRACKET.find(trimmed)
        if (matchBracket != null && matchBracket.groupValues.size >= 3) {
            val key = matchBracket.groupValues[1].trim()
            val value = matchBracket.groupValues[2]
            if (key.isNotEmpty()) {
                val category = GetpropCategory.categorize(key)
                val type = PropertyValueType.detect(value)
                return GetpropEntry(
                    key = key,
                    value = value,
                    source = sourceName,
                    lineNumber = lineNumber,
                    category = category,
                    valueType = type
                )
            }
        }

        // 2. Try build.prop key=value syntax
        val equalIdx = trimmed.indexOf('=')
        if (equalIdx > 0) {
            val key = trimmed.substring(0, equalIdx).trim()
            val value = if (equalIdx + 1 < trimmed.length) trimmed.substring(equalIdx + 1).trim() else ""
            if (key.isNotEmpty()) {
                val category = GetpropCategory.categorize(key)
                val type = PropertyValueType.detect(value)
                return GetpropEntry(
                    key = key,
                    value = value,
                    source = sourceName,
                    lineNumber = lineNumber,
                    category = category,
                    valueType = type
                )
            }
        }

        // 3. Try colon separated without brackets: key: value
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx > 0 && !trimmed.contains("=")) {
            val key = trimmed.substring(0, colonIdx).trim()
            val value = if (colonIdx + 1 < trimmed.length) trimmed.substring(colonIdx + 1).trim() else ""
            if (key.isNotEmpty() && !key.contains(" ")) {
                val category = GetpropCategory.categorize(key)
                val type = PropertyValueType.detect(value)
                return GetpropEntry(
                    key = key,
                    value = value,
                    source = sourceName,
                    lineNumber = lineNumber,
                    category = category,
                    valueType = type
                )
            }
        }

        return null
    }
}
