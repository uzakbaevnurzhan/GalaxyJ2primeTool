package com.example.ui.analyzer.kernel.studio.models

import kotlinx.serialization.Serializable

@Serializable
enum class ConfigType {
    BOOL_Y,
    BOOL_N,
    MODULE_M,
    STRING,
    INTEGER,
    HEX,
    UNKNOWN
}

@Serializable
enum class ConfigCategory {
    ANDROID,
    FILESYSTEM,
    NETWORK,
    SECURITY,
    HARDWARE,
    CORE,
    OTHER
}

@Serializable
enum class ConfigState {
    ENABLED,
    MODULE,
    DISABLED,
    UNKNOWN
}

@Serializable
data class KernelConfig(
    val name: String,
    val value: String,
    val type: ConfigType,
    val state: ConfigState,
    val category: ConfigCategory = ConfigCategory.OTHER,
    val description: String = ""
)
