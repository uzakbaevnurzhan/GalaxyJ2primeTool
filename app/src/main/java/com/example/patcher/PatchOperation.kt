package com.example.patcher

import kotlinx.serialization.Serializable

enum class PatchRisk(val label: String, val level: Int) {
    LOW("Low Risk", 1),
    MEDIUM("Medium Risk", 2),
    HIGH("High Risk", 3),
    CRITICAL("Critical Risk", 4)
}

enum class PatchStatus {
    PENDING,
    VALIDATED,
    APPLIED,
    FAILED,
    ROLLED_BACK,
    SKIPPED
}

enum class PatchType {
    TEXT,
    PROPERTY,
    INIT_SCRIPT,
    XML,
    PERMISSION,
    SYMLINK,
    FILE_REPLACE,
    FILE_ADD,
    FILE_DELETE,
    BINARY,
    SELINUX_CONTEXT,
    VINTF_MANIFEST
}

@Serializable
data class PatchOperation(
    val id: String,
    val name: String,
    val description: String,
    val targetPath: String, // Relative path in workspace e.g. "system/build.prop"
    val type: PatchType,
    val risk: PatchRisk,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    var status: PatchStatus = PatchStatus.PENDING,
    var failureReason: String? = null,
    
    // Operation-specific payload fields
    val textDiffPayload: String? = null,
    val propertyKey: String? = null,
    val propertyOldValue: String? = null,
    val propertyNewValue: String? = null,
    val propertyAction: String? = null, // SET, ADD, REMOVE
    
    val permissionMode: String? = null, // e.g. "0755"
    val permissionUid: String? = null,
    val permissionGid: String? = null,
    val permissionContext: String? = null,
    
    val symlinkTarget: String? = null,
    val symlinkAction: String? = null, // CREATE, MODIFY, DELETE
    
    val xmlAction: String? = null, // ADD_NODE, REMOVE_NODE, MODIFY_ATTRIBUTE, MODIFY_TEXT, REPLACE_BLOCK
    val xmlXPathOrTag: String? = null,
    val xmlAttributeName: String? = null,
    val xmlAttributeValue: String? = null,
    val xmlContent: String? = null,
    
    val binaryOffset: Long? = null,
    val binaryExpectedOldHex: String? = null,
    val binaryNewHex: String? = null,
    
    val sourceFilePath: String? = null, // For Add/Replace file
    val sourceContentBase64: String? = null,
    val expectedTargetHash: String? = null,
    val expectedTargetSize: Long? = null
)
