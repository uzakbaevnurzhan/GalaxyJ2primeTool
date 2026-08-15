package com.example.ui.analyzer.getprop

/**
 * Automatically detected data types for Android property values.
 */
enum class PropertyValueType {
    STRING,
    INTEGER,
    LONG,
    BOOLEAN,
    HEX,
    VERSION,
    LIST,
    UNKNOWN;

    companion object {
        private val HEX_REGEX = Regex("^0[xX][0-9a-fA-F]+$")
        private val VERSION_REGEX = Regex("^\\d+(\\.\\d+)+(-[a-zA-Z0-9_.-]+)?$")
        private val INT_REGEX = Regex("^-?\\d+$")

        fun detect(value: String): PropertyValueType {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return UNKNOWN

            // Boolean detection
            if (trimmed.equals("true", ignoreCase = true) || trimmed.equals("false", ignoreCase = true)) {
                return BOOLEAN
            }

            // Hex detection (e.g. 0x00030002, 0x1)
            if (HEX_REGEX.matches(trimmed)) {
                return HEX
            }

            // Version string detection (e.g. 8.1.0, 1.0.0-rc1)
            if (VERSION_REGEX.matches(trimmed)) {
                return VERSION
            }

            // Integer vs Long
            if (INT_REGEX.matches(trimmed)) {
                return try {
                    trimmed.toInt()
                    INTEGER
                } catch (e: NumberFormatException) {
                    try {
                        trimmed.toLong()
                        LONG
                    } catch (e2: NumberFormatException) {
                        STRING
                    }
                }
            }

            // List detection (e.g. armeabi-v7a,armeabi or space separated list)
            if (trimmed.contains(",") && trimmed.split(",").all { it.isNotBlank() }) {
                return LIST
            }

            return STRING
        }
    }
}
