package com.example.ui.studio.rom

sealed class RomOperationResult {
    data class Success(val message: String, val outputPath: String? = null) : RomOperationResult()
    data class Error(val reason: String) : RomOperationResult()
    object Cancelled : RomOperationResult()
}
