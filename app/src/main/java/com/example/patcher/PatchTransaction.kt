package com.example.patcher

import java.io.File

object PatchTransaction {

    suspend fun runTransaction(
        workspaceRoot: File,
        plan: PatchPlan,
        onProgress: (String) -> Unit
    ): PatchExecutionResult {
        onProgress("Starting transaction for ${plan.name}...")

        // 1. Validate
        onProgress("Validating patch plan...")
        val validation = PatchValidator.validate(workspaceRoot, plan)
        if (!validation.canApply) {
            val errors = validation.issues.filter { it.severity == ValidationSeverity.BLOCKER || it.severity == ValidationSeverity.ERROR }
            return PatchExecutionResult(
                transactionId = "FAILED_PRECHECK",
                snapshotId = null,
                success = false,
                status = "FAILED",
                appliedCount = 0,
                totalCount = plan.enabledOperations.size,
                results = emptyList(),
                errorMessage = "Validation failed: ${errors.firstOrNull()?.message}",
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis()
            )
        }

        // 2. Snapshot (Backup)
        onProgress("Creating pre-patch snapshot...")
        val snapshot = SnapshotManager.createSnapshot(
            workspaceRoot = workspaceRoot,
            projectId = plan.projectId,
            name = "Pre-patch: ${plan.name}",
            triggerReason = "Transaction Backup",
            filterRelativePaths = plan.enabledOperations.map { it.targetPath }.toSet()
        )

        // 3. Apply
        val workspaceDir = File(workspaceRoot, "workspace")
        val result = RomPatchEngine.executePatchPlan(workspaceDir, plan, snapshot.id) { current, total, msg ->
            onProgress("[$current/$total] $msg")
        }

        // 4. Verify & Commit or Rollback
        if (result.success) {
            onProgress("Transaction successful. Committing...")
            val historyEntry = HistoryEntry(
                transactionId = result.transactionId,
                planId = plan.id,
                planName = plan.name,
                snapshotId = snapshot.id,
                status = "APPLIED",
                affectedFilesCount = plan.enabledOperations.size,
                affectedFiles = plan.enabledOperations.map { it.targetPath }.distinct(),
                operationsExecuted = result.results,
                risk = plan.overallRisk
            )
            PatchHistoryManager.recordHistory(workspaceRoot, historyEntry)
            return result
        } else {
            onProgress("Transaction failed! Rolling back changes...")
            val rollbackSuccess = SnapshotManager.restoreSnapshot(workspaceRoot, snapshot.id)
            
            val historyEntry = HistoryEntry(
                transactionId = result.transactionId,
                planId = plan.id,
                planName = plan.name,
                snapshotId = snapshot.id,
                status = if (rollbackSuccess) "ROLLED_BACK" else "PARTIAL",
                affectedFilesCount = plan.enabledOperations.size,
                affectedFiles = plan.enabledOperations.map { it.targetPath }.distinct(),
                operationsExecuted = result.results,
                risk = plan.overallRisk,
                details = "Failed at operation. Rollback ${if (rollbackSuccess) "successful" else "FAILED"}."
            )
            PatchHistoryManager.recordHistory(workspaceRoot, historyEntry)
            
            return result.copy(
                rolledBack = rollbackSuccess,
                rollbackMessage = if (rollbackSuccess) "Changes rolled back successfully." else "CRITICAL: Rollback failed! Workspace may be inconsistent."
            )
        }
    }
}
