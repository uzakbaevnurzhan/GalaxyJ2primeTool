package com.example.ui.studio.workspace

data class WorkspaceFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val originalHash: String? = null,
    val currentHash: String? = null,
    val state: FileState = FileState.UNCHANGED
)

enum class FileState {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    UNCHANGED
}
