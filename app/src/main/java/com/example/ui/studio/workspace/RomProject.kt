package com.example.ui.studio.workspace

import kotlinx.serialization.Serializable

@Serializable
data class RomProject(
    val id: String,
    val name: String,
    val createdAt: Long,
    val rootPath: String,
    val device: String = "Samsung Galaxy J2 Prime (SM-G532F)",
    val androidVersion: String = "6.0.1",
    val architecture: String = "arm32 (armv7-a)",
    val operations: List<RomOperation> = emptyList()
)

@Serializable
data class RomOperation(
    val id: String,
    val timestamp: Long,
    val type: String,
    val description: String,
    val status: String
)
