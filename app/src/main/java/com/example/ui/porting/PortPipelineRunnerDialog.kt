package com.example.ui.porting

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.manager.TaskManager
import com.example.porting.engine.PortPipelineExecutionEngine
import com.example.porting.model.*
import com.example.ui.studio.workspace.RomProject
import kotlinx.coroutines.launch
import java.io.File

/**
 * Interactive Dialog for Orchestrating the End-to-End ROM Porting Pipeline:
 * PORT ANALYSIS -> PORT PLAN -> SELECT CANDIDATES -> SNAPSHOT -> MERGE/PATCH -> VALIDATE -> BUILD -> POST-BUILD ANALYSIS -> REPORT
 *
 * Supports:
 * - Real-time TaskManager progress & logs
 * - Pre-Merge Checks (conflict, ABI, dependency, SELinux, partition)
 * - 10 Post-Merge Forensic Analyzers
 * - Post-Build Artifact Forensics (magic, size, hash, architecture, metadata)
 * - Cancel, Rollback to Snapshot, and Error Recovery
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortPipelineRunnerDialog(
    sourceRom: SourceRomProfile,
    targetDevice: TargetDeviceProfile,
    selectedCandidates: List<MigrationCandidate>,
    onDismiss: () -> Unit,
    onViewReport: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val pipelineSummary by PortPipelineExecutionEngine.currentPipelineSummary.collectAsState()
    var runningTaskId by remember { mutableStateOf<String?>(null) }
    var isRollingBack by remember { mutableStateOf(false) }

    // Mock project if running from port assistant
    val project = remember {
        val root = File(context.filesDir, "rom_studio/default_project").apply { mkdirs() }
        RomProject(
            id = "porting_session_${System.currentTimeMillis()}",
            name = "Port: ${sourceRom.name}",
            createdAt = System.currentTimeMillis(),
            rootPath = root.absolutePath,
            device = targetDevice.model,
            androidVersion = sourceRom.androidVersion,
            architecture = targetDevice.cpuArch
        )
    }

    val isRunning = pipelineSummary?.status == PipelineStatus.RUNNING
    val isCompleted = pipelineSummary?.status == PipelineStatus.COMPLETED
    val isFailed = pipelineSummary?.status == PipelineStatus.FAILED
    val isCancelled = pipelineSummary?.status == PipelineStatus.CANCELLED
    val isRolledBack = pipelineSummary?.status == PipelineStatus.ROLLED_BACK

    AlertDialog(
        onDismissRequest = {
            if (!isRunning) onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.CheckCircle
                            isFailed -> Icons.Default.Error
                            isRunning -> Icons.Default.Sync
                            else -> Icons.Default.AccountTree
                        },
                        contentDescription = null,
                        tint = when {
                            isCompleted -> Color(0xFF2E7D32)
                            isFailed -> MaterialTheme.colorScheme.error
                            isRunning -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Text("ROM Port Execution Pipeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                if (!isRunning) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Info
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Source: ${sourceRom.name} (Android ${sourceRom.androidVersion}, ${sourceRom.architecture})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Target: ${targetDevice.model} (${targetDevice.platform}, 32-bit ARM)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Staged Candidates: ${selectedCandidates.size} components", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Overall Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pipelineSummary?.currentStage?.label ?: "Pipeline Staging Ready",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${((pipelineSummary?.progress ?: 0f) * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LinearProgressIndicator(
                        progress = { pipelineSummary?.progress ?: 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // 9-Stage Flow Indicators
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Execution Pipeline Flow:", fontWeight = FontWeight.Bold, fontSize = 11.sp)

                        PipelineStage.values().forEach { stage ->
                            val currentStage = pipelineSummary?.currentStage
                            val isCurrent = currentStage == stage && isRunning
                            val isDone = (currentStage != null && currentStage.order > stage.order) || isCompleted

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isDone -> Color(0xFF2E7D32)
                                                    isCurrent -> MaterialTheme.colorScheme.primary
                                                    else -> MaterialTheme.colorScheme.outlineVariant
                                                }
                                            )
                                    )
                                    Text(
                                        text = stage.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (isDone) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                } else if (isCurrent) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }

                // Stage 3 Detail: Pre-Merge Checks
                pipelineSummary?.preMergeResult?.let { pre ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (pre.allPassed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Pre-Merge Checks Result:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("• Conflict Check: ${pre.conflictCount} workspace file collisions", fontSize = 11.sp)
                            Text("• ABI Check: ${pre.abiPassCount} 32-bit ARM verified (${pre.abiFailures.size} 64-bit errors)", fontSize = 11.sp)
                            Text("• Dependencies: ${pre.dependencyMissingCount} missing", fontSize = 11.sp)
                            Text("• SELinux Contexts: ${pre.selinuxContextMissingCount} unmapped", fontSize = 11.sp)
                            Text("• Partition Budget: ${(pre.totalCandidateBytes / 1024)} KB / ${(pre.partitionBudgetBytes / (1024 * 1024))} MB", fontSize = 11.sp)
                        }
                    }
                }

                // Stage 6 Detail: Post-Merge 10 Analyzers
                pipelineSummary?.postMergeValidation?.let { post ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("10 Post-Merge Forensic Analyzers:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("• ROM Analyzer: ${post.romAnalyzer.status}", fontSize = 10.sp)
                            Text("• Boot Analyzer: ${post.bootAnalyzer.status}", fontSize = 10.sp)
                            Text("• Kernel Analyzer: ${post.kernelAnalyzer.status}", fontSize = 10.sp)
                            Text("• DTB Analyzer: ${post.dtbAnalyzer.status}", fontSize = 10.sp)
                            Text("• ELF Analyzer: ${post.elfAnalyzer.status}", fontSize = 10.sp)
                            Text("• HAL Analyzer: ${post.halAnalyzer.status}", fontSize = 10.sp)
                            Text("• RIL Analyzer: ${post.rilAnalyzer.status}", fontSize = 10.sp)
                            Text("• SELinux Analyzer: ${post.selinuxAnalyzer.status}", fontSize = 10.sp)
                            Text("• Partition Analyzer: ${post.partitionAnalyzer.status}", fontSize = 10.sp)
                            Text("• Project Health Score: ${post.healthScore}/100", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Stage 8 Detail: Post-Build Forensics
                pipelineSummary?.postBuildAnalysis?.let { postBuild ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Post-Build Artifact Forensics:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF2E7D32))
                            postBuild.artifacts.forEach { art ->
                                Text("• File: ${art.fileName} (${art.sizeBytes} B)", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("• Magic: ${art.detectedMagic} (Valid: ${art.magicValid})", fontSize = 10.sp)
                                Text("• Arch: ${art.architecture} (ARM32 Valid: ${art.isArm32Valid})", fontSize = 10.sp)
                                Text("• SHA-256: ${art.sha256.take(16)}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Live Pipeline Logs
                val logs = pipelineSummary?.logs ?: emptyList()
                if (logs.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Execution Logs (${logs.size} lines):", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E1E1E),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(logs) { line ->
                                    Text(
                                        text = line,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = if (line.contains("ERROR") || line.contains("BLOCKER")) Color(0xFFFF8A80)
                                        else if (line.contains("WARNING")) Color(0xFFFFD54F)
                                        else if (line.contains("SUCCESS") || line.contains("COMPLETED")) Color(0xFFB9F6CA)
                                        else Color(0xFFE0E0E0)
                                    )
                                }
                            }
                        }
                    }
                }

                // Error message if any
                pipelineSummary?.errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Pipeline Interruption / Failure:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(err, fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                if (isRolledBack) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE0F2F1),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF00796B), modifier = Modifier.size(16.dp))
                            Text("Workspace successfully restored to pre-merge snapshot baseline.", fontSize = 11.sp, color = Color(0xFF004D40))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // CANCEL BUTTON (if running)
                if (isRunning && runningTaskId != null) {
                    OutlinedButton(
                        onClick = {
                            runningTaskId?.let { TaskManager.cancelTask(it) }
                            Toast.makeText(context, "Cancelling port pipeline...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", fontSize = 11.sp)
                    }
                }

                // ROLLBACK BUTTON (if snapshot created & failed/cancelled)
                if ((isFailed || isCancelled || isCompleted) && pipelineSummary?.snapshotId != null && !isRolledBack) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isRollingBack = true
                                val snapshotId = pipelineSummary?.snapshotId ?: ""
                                val success = PortPipelineExecutionEngine.rollbackPipeline(project, snapshotId)
                                isRollingBack = false
                                if (success) {
                                    Toast.makeText(context, "Rollback to snapshot $snapshotId successful!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Rollback failed.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isRollingBack
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isRollingBack) "Rolling Back..." else "Rollback Snapshot", fontSize = 11.sp)
                    }
                }

                // VIEW REPORT BUTTON (if completed)
                if (isCompleted && pipelineSummary?.reportMarkdownPath != null) {
                    Button(
                        onClick = {
                            pipelineSummary?.reportMarkdownPath?.let { onViewReport(it) }
                        }
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View Report", fontSize = 11.sp)
                    }
                }

                // START / RETRY PIPELINE BUTTON
                if (!isRunning) {
                    Button(
                        onClick = {
                            runningTaskId = PortPipelineExecutionEngine.startPortingPipeline(
                                context = context,
                                sourceRom = sourceRom,
                                targetDevice = targetDevice,
                                project = project,
                                selectedCandidatesOverride = selectedCandidates
                            )
                            Toast.makeText(context, "ROM Porting Pipeline started in TaskManager!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("btn_start_port_pipeline")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isFailed || isCancelled) "Retry Pipeline" else "Start Pipeline (9 Stages)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            if (!isRunning) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
