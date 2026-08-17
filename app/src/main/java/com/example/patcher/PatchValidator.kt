package com.example.patcher

import java.io.File

object PatchValidator {

    fun validate(workspaceRoot: File, plan: PatchPlan): PatchValidationSummary {
        val workspaceDir = File(workspaceRoot, "workspace")
        val issues = mutableListOf<PatchValidationIssue>()
        val conflicts = mutableListOf<PatchConflict>()
        
        val enabledOps = plan.enabledOperations
        
        // 1. Conflict detection (Multiple operations on same target)
        val targetMap = enabledOps.groupBy { it.targetPath }
        for ((target, ops) in targetMap) {
            if (ops.size > 1) {
                // If there's a delete operation mixed with others, that's a conflict
                val hasDelete = ops.any { it.type == PatchType.FILE_DELETE }
                if (hasDelete) {
                    conflicts.add(PatchConflict(ops[0].id, ops.last().id, target, ConflictType.DELETED_FILE_MODIFIED_LATER, "File is deleted and modified in the same plan."))
                }
                
                // If multiple property patches on the same key
                val propOps = ops.filter { it.type == PatchType.PROPERTY }
                val keyGroups = propOps.groupBy { it.propertyKey }
                for ((key, kOps) in keyGroups) {
                    if (key != null && kOps.size > 1) {
                        conflicts.add(PatchConflict(kOps[0].id, kOps.last().id, target, ConflictType.SAME_PROPERTY_MODIFIED_TWICE, "Property '$key' is modified multiple times."))
                    }
                }
                
                // Multiple file replacements
                val replaceOps = ops.filter { it.type == PatchType.FILE_REPLACE }
                if (replaceOps.size > 1) {
                    conflicts.add(PatchConflict(replaceOps[0].id, replaceOps.last().id, target, ConflictType.MULTIPLE_PATCHES_SAME_FILE, "File is replaced multiple times."))
                }
            }
        }

        // 2. File existence validation
        for (op in enabledOps) {
            val targetFile = File(workspaceDir, op.targetPath)
            
            if (op.type != PatchType.FILE_ADD) {
                if (!targetFile.exists()) {
                    issues.add(PatchValidationIssue(ValidationSeverity.ERROR, op.id, op.targetPath, "Target file does not exist."))
                }
            } else {
                if (targetFile.exists()) {
                    issues.add(PatchValidationIssue(ValidationSeverity.ERROR, op.id, op.targetPath, "File already exists. Use REPLACE instead of ADD."))
                }
            }
            
            // Check property key
            if (op.type == PatchType.PROPERTY && op.propertyKey.isNullOrBlank()) {
                issues.add(PatchValidationIssue(ValidationSeverity.ERROR, op.id, op.targetPath, "Property key cannot be empty."))
            }
            
            // Check binary offset
            if (op.type == PatchType.BINARY) {
                if (op.binaryOffset == null || op.binaryOffset < 0) {
                    issues.add(PatchValidationIssue(ValidationSeverity.ERROR, op.id, op.targetPath, "Invalid binary offset."))
                }
            }
        }
        
        val blockers = issues.count { it.severity == ValidationSeverity.BLOCKER }
        val errors = issues.count { it.severity == ValidationSeverity.ERROR }
        val warnings = issues.count { it.severity == ValidationSeverity.WARNING }
        
        return PatchValidationSummary(
            isValid = blockers == 0 && errors == 0 && conflicts.isEmpty(),
            blockersCount = blockers,
            errorsCount = errors,
            warningsCount = warnings,
            conflicts = conflicts,
            issues = issues,
            overallRisk = plan.overallRisk
        )
    }

    fun dryRun(workspaceRoot: File, plan: PatchPlan): DryRunReport {
        val validation = validate(workspaceRoot, plan)
        val changes = mutableListOf<DryRunChange>()
        
        for (op in plan.enabledOperations) {
            val diff = when (op.type) {
                PatchType.PROPERTY -> "Key: ${op.propertyKey}\nAction: ${op.propertyAction}\nNew Value: ${op.propertyNewValue}"
                PatchType.FILE_DELETE -> "File will be removed."
                PatchType.FILE_ADD -> "File will be created/added."
                PatchType.FILE_REPLACE -> "File will be overwritten with new content."
                PatchType.BINARY -> "Hex modified at offset ${op.binaryOffset}"
                PatchType.PERMISSION -> "Permissions changed to ${op.permissionMode}"
                PatchType.SYMLINK -> "Symlink to ${op.symlinkTarget}"
                else -> "Content modification."
            }
            
            changes.add(
                DryRunChange(
                    operationId = op.id,
                    operationName = op.name,
                    targetPath = op.targetPath,
                    type = op.type,
                    oldValueSummary = op.propertyOldValue,
                    newValueSummary = op.propertyNewValue,
                    diffText = diff,
                    risk = op.risk,
                    isExecutable = true
                )
            )
        }
        
        return DryRunReport(
            planId = plan.id,
            planName = plan.name,
            totalChanges = changes.size,
            affectedFiles = changes.map { it.targetPath }.distinct(),
            changes = changes,
            validationSummary = validation,
            estimatedBytesImpact = 0L // Placeholder
        )
    }
}
