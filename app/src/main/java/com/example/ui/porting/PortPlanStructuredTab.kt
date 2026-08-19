package com.example.ui.porting

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.navigation.NavController
import com.example.porting.model.*

/**
 * Structured Port Plan across 11 core architectural sections:
 * KERNEL, BOOT, DTB, SYSTEM, VENDOR, HAL, RIL, SELINUX, PROPERTIES, INIT, PARTITIONS.
 *
 * Each task has title, description, dependencies, risk, status, and 4 explicit action buttons:
 * - ADD TO PATCH PLAN
 * - COMPARE
 * - ANALYZE
 * - IGNORE
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortPlanStructuredTab(
    result: PortAnalysisResult?,
    navController: NavController,
    onRunPipeline: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    val baseStructuredPlan = result.structuredPortPlan
    if (baseStructuredPlan == null) {
        PortPlanLegacyFallback(result, navController)
        return
    }

    // Local mutable plan state for real-time task status updates & patch plan additions
    var activePlan by remember(result) {
        mutableStateOf(baseStructuredPlan)
    }

    var selectedSectionType by remember { mutableStateOf<PortPlanSectionType?>(null) }
    var selectedTaskStatus by remember { mutableStateOf<PortTaskStatus?>(null) }
    var taskSearchQuery by remember { mutableStateOf("") }

    // Dialog state
    var taskToAnalyze by remember { mutableStateOf<PortPlanTask?>(null) }
    var taskToCompare by remember { mutableStateOf<PortPlanTask?>(null) }
    var taskToAddToPatchPlan by remember { mutableStateOf<PortPlanTask?>(null) }

    val filteredSections = remember(activePlan, selectedSectionType, selectedTaskStatus, taskSearchQuery) {
        activePlan.sections
            .filter { sec -> selectedSectionType == null || sec.sectionType == selectedSectionType }
            .map { sec ->
                val filteredTasks = sec.tasks.filter { task ->
                    val matchStatus = selectedTaskStatus == null || task.status == selectedTaskStatus
                    val matchQuery = taskSearchQuery.isBlank() ||
                            task.title.contains(taskSearchQuery, ignoreCase = true) ||
                            task.description.contains(taskSearchQuery, ignoreCase = true) ||
                            (task.targetPath?.contains(taskSearchQuery, ignoreCase = true) == true)
                    matchStatus && matchQuery
                }
                sec.copy(tasks = filteredTasks)
            }
            .filter { it.tasks.isNotEmpty() || (selectedTaskStatus == null && taskSearchQuery.isBlank()) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Plan Overview Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PORTING ROADMAP & TASKS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "11 ARCHITECTURAL SECTIONS",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = activePlan.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Text(
                        text = "Sequential port execution roadmap for Samsung Galaxy J2 Prime (MT6737T / grandpplte). Review dependencies and stage tasks without unverified automatic copying.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (onRunPipeline != null) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onRunPipeline,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Execute Porting Pipeline (9 Stages in TaskManager)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Summary Progress Metrics
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Task Completion Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${activePlan.completedTasks} of ${activePlan.totalTasks} Completed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Multi-segmented Progress Bar
                    val total = activePlan.totalTasks.toFloat().coerceAtLeast(1f)
                    LinearProgressIndicator(
                        progress = { activePlan.completedTasks.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFF2E7D32),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    // Stats Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskStatBadge("Total", activePlan.totalTasks, MaterialTheme.colorScheme.primary)
                        TaskStatBadge("Ready to Apply", activePlan.readyTasks, Color(0xFF1976D2))
                        TaskStatBadge("In Progress", activePlan.inProgressTasks, Color(0xFFF57F17))
                        TaskStatBadge("Completed", activePlan.completedTasks, Color(0xFF2E7D32))
                        TaskStatBadge("Blocked", activePlan.blockedTasks, Color(0xFFC62828))
                        TaskStatBadge("Ignored", activePlan.ignoredTasks, Color(0xFF757575))
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = taskSearchQuery,
                onValueChange = { taskSearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_search_field"),
                placeholder = { Text("Search tasks, files, commands...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (taskSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { taskSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Section Filter Chips (11 Core Sections)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Select Section:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedSectionType == null,
                            onClick = { selectedSectionType = null },
                            label = { Text("All (11)") }
                        )
                    }
                    items(PortPlanSectionType.values()) { secType ->
                        val taskCount = activePlan.sections.find { it.sectionType == secType }?.tasks?.size ?: 0
                        FilterChip(
                            selected = selectedSectionType == secType,
                            onClick = { selectedSectionType = if (selectedSectionType == secType) null else secType },
                            label = { Text("${secType.label} ($taskCount)") }
                        )
                    }
                }
            }
        }

        // Status Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedTaskStatus == null,
                    onClick = { selectedTaskStatus = null },
                    label = { Text("All Statuses") }
                )
                PortTaskStatus.values().forEach { st ->
                    FilterChip(
                        selected = selectedTaskStatus == st,
                        onClick = { selectedTaskStatus = if (selectedTaskStatus == st) null else st },
                        label = { Text(st.label) }
                    )
                }
            }
        }

        // Render each Section and its Tasks
        filteredSections.forEach { section ->
            item(key = "sec_header_${section.sectionType.name}") {
                PortPlanSectionHeader(section = section)
            }

            if (section.tasks.isEmpty()) {
                item(key = "sec_empty_${section.sectionType.name}") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No tasks match current filter in this section.",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(section.tasks, key = { it.id }) { task ->
                    PortPlanTaskCard(
                        task = task,
                        onAddToPatchPlan = { taskToAddToPatchPlan = task },
                        onCompare = { taskToCompare = task },
                        onAnalyze = { taskToAnalyze = task },
                        onToggleIgnore = {
                            val newStatus = if (task.status == PortTaskStatus.IGNORED) PortTaskStatus.PENDING else PortTaskStatus.IGNORED
                            activePlan = activePlan.copy(
                                sections = activePlan.sections.map { sec ->
                                    sec.copy(tasks = sec.tasks.map { t ->
                                        if (t.id == task.id) t.copy(status = newStatus) else t
                                    })
                                }
                            )
                            Toast.makeText(context, "Task '${task.title}' status: ${newStatus.label}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    taskToAnalyze?.let { task ->
        TaskAnalyzeDialog(
            task = task,
            onDismiss = { taskToAnalyze = null },
            onAddToPatchPlan = {
                taskToAnalyze = null
                taskToAddToPatchPlan = task
            }
        )
    }

    taskToCompare?.let { task ->
        TaskCompareDialog(
            task = task,
            onDismiss = { taskToCompare = null }
        )
    }

    taskToAddToPatchPlan?.let { task ->
        TaskAddToPatchPlanDialog(
            task = task,
            onDismiss = { taskToAddToPatchPlan = null },
            onConfirm = {
                activePlan = activePlan.copy(
                    sections = activePlan.sections.map { sec ->
                        sec.copy(tasks = sec.tasks.map { t ->
                            if (t.id == task.id) t.copy(
                                addedToPatchPlan = true,
                                status = PortTaskStatus.READY_TO_APPLY
                            ) else t
                        })
                    }
                )
                taskToAddToPatchPlan = null
                Toast.makeText(context, "Added '${task.title}' to Patch Plan!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
fun PortPlanSectionHeader(section: PortPlanSection) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = section.sectionType.label,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = section.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = "${section.tasks.size} tasks",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PortPlanTaskCard(
    task: PortPlanTask,
    onAddToPatchPlan: () -> Unit,
    onCompare: () -> Unit,
    onAnalyze: () -> Unit,
    onToggleIgnore: () -> Unit
) {
    val statusColor = getTaskStatusColor(task.status)
    val riskColor = getRiskColor(task.risk)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == PortTaskStatus.IGNORED) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (task.addedToPatchPlan) Color(0xFF2E7D32)
            else if (task.status == PortTaskStatus.IGNORED) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (task.status == PortTaskStatus.IGNORED) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (task.addedToPatchPlan) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                Text("IN PATCH PLAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }

                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // Task Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = task.status.label,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Description
            Text(
                text = task.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Target path or Command hint if available
            if (!task.targetPath.isNullOrBlank() || !task.actionCommandHint.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        task.targetPath?.let { path ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Target:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(path, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                        task.actionCommandHint?.let { cmd ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Command:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Dependencies list
            if (task.dependencies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Dependencies:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    task.dependencies.forEach { dep ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(dep, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // Risk indicator
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Risk:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = riskColor.copy(alpha = 0.15f)
                ) {
                    Text(task.risk.label, color = riskColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 4 REQUIRED ACTION BUTTONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. ADD TO PATCH PLAN
                FilledTonalButton(
                    onClick = onAddToPatchPlan,
                    modifier = Modifier.testTag("btn_task_add_to_patch_${task.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (task.addedToPatchPlan) "PATCHED" else "ADD TO PATCH PLAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // 2. COMPARE
                OutlinedButton(
                    onClick = onCompare,
                    modifier = Modifier.testTag("btn_task_compare_${task.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("COMPARE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // 3. ANALYZE
                OutlinedButton(
                    onClick = onAnalyze,
                    modifier = Modifier.testTag("btn_task_analyze_${task.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ANALYZE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // 4. IGNORE
                TextButton(
                    onClick = onToggleIgnore,
                    modifier = Modifier.testTag("btn_task_ignore_${task.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (task.status == PortTaskStatus.IGNORED) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (task.status == PortTaskStatus.IGNORED) "UNIGNORE" else "IGNORE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TaskAnalyzeDialog(
    task: PortPlanTask,
    onDismiss: () -> Unit,
    onAddToPatchPlan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Task Forensic Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Section: ${task.section.label} - ${task.section.description}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.targetPath?.let { Text("Target Path: $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        task.actionCommandHint?.let { Text("Action Command: $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                        Text("Risk Level: ${task.risk.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = getRiskColor(task.risk))
                        Text("Task Status: ${task.status.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = getTaskStatusColor(task.status))
                    }
                }

                Text("Description & Steps:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(task.description, fontSize = 13.sp)

                if (task.dependencies.isNotEmpty()) {
                    Text("Dependencies & Prerequisites:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    task.dependencies.forEach { dep ->
                        Text("• $dep", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onAddToPatchPlan) {
                Text("Add to Patch Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun TaskCompareDialog(
    task: PortPlanTask,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Task Subsystem Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Section: ${task.section.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                HorizontalDivider()

                Text("Architectural Comparison:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    text = "Verifying target hardware specs against donor subsystem components in ${task.section.label}. No automatic file writes will occur.",
                    fontSize = 12.sp
                )

                task.targetPath?.let {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Target: $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun TaskAddToPatchPlanDialog(
    task: PortPlanTask,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Add Task to Patch Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stage Task into Execution Pipeline:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Task: ${task.title}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Section: ${task.section.label}", fontSize = 11.sp)
                        Text("Risk: ${task.risk.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = getRiskColor(task.risk))
                    }
                }
                Text(
                    "This task will be queued into the ROM Patch Plan staging list for controlled manual execution.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TaskStatBadge(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text("$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun PortPlanLegacyFallback(result: PortAnalysisResult, navController: NavController) {
    val plan = result.generatedPortPlan
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plan.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Standard porting plan steps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        items(plan.steps) { step ->
            StepCard(step, navController)
        }
    }
}

fun getTaskStatusColor(status: PortTaskStatus): Color {
    return when (status) {
        PortTaskStatus.PENDING -> Color(0xFF757575)
        PortTaskStatus.IN_PROGRESS -> Color(0xFFF57F17)
        PortTaskStatus.READY_TO_APPLY -> Color(0xFF1976D2)
        PortTaskStatus.COMPLETED -> Color(0xFF2E7D32)
        PortTaskStatus.BLOCKED -> Color(0xFFC62828)
        PortTaskStatus.IGNORED -> Color(0xFF9E9E9E)
    }
}
