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
 * Migration Candidates UI for discovering, triaging, and inspecting potentially
 * transplantable ROM components across 12 subsystem categories.
 *
 * Strict constraint: Candidates are NEVER copied automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationCandidatesTab(
    result: PortAnalysisResult?,
    onNavigateToTab: (PortAssistantTab) -> Unit = {},
    onRunPipeline: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (result == null) {
        EmptyAnalysisPlaceholder()
        return
    }

    // Local mutable state for interactive candidate updates
    var candidatesList by remember(result) {
        mutableStateOf(result.migrationCandidates)
    }

    var selectedCategory by remember { mutableStateOf<CandidateCategory?>(null) }
    var selectedStatus by remember { mutableStateOf<CandidateStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var candidateToAnalyze by remember { mutableStateOf<MigrationCandidate?>(null) }
    var candidateToCompare by remember { mutableStateOf<MigrationCandidate?>(null) }
    var candidateToAddToPlan by remember { mutableStateOf<MigrationCandidate?>(null) }

    val filteredCandidates = remember(candidatesList, selectedCategory, selectedStatus, searchQuery) {
        candidatesList.filter { candidate ->
            val matchCategory = selectedCategory == null || candidate.category == selectedCategory
            val matchStatus = selectedStatus == null || candidate.status == selectedStatus
            val matchQuery = searchQuery.isBlank() ||
                    candidate.name.contains(searchQuery, ignoreCase = true) ||
                    candidate.path.contains(searchQuery, ignoreCase = true) ||
                    candidate.reason.contains(searchQuery, ignoreCase = true) ||
                    candidate.target.contains(searchQuery, ignoreCase = true)
            matchCategory && matchStatus && matchQuery
        }
    }

    val totalCount = candidatesList.size
    val candidateCount = candidatesList.count { it.status == CandidateStatus.CANDIDATE }
    val safeCount = candidatesList.count { it.status == CandidateStatus.SAFE_TO_INVESTIGATE }
    val highRiskCount = candidatesList.count { it.status == CandidateStatus.HIGH_RISK }
    val blockedCount = candidatesList.count { it.status == CandidateStatus.BLOCKED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Strict Safety Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "NO AUTOMATIC COPYING ENFORCED",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "All candidates are isolated for forensic inspection. Add them to your Patch Plan or review differences before applying to workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Pipeline Action Card
        if (onRunPipeline != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("ROM Porting Pipeline Engine", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Execute full 9-stage verified porting workflow in TaskManager with snapshot rollback.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(
                            onClick = onRunPipeline,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run Pipeline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Summary Stats Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CandidateStatBadge("Total", totalCount, MaterialTheme.colorScheme.primary)
                CandidateStatBadge("Candidate", candidateCount, Color(0xFF1976D2))
                CandidateStatBadge("Safe to Investigate", safeCount, Color(0xFF2E7D32))
                CandidateStatBadge("High Risk", highRiskCount, Color(0xFFE65100))
                CandidateStatBadge("Blocked", blockedCount, Color(0xFFC62828))
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("candidate_search_field"),
                placeholder = { Text("Search by path, library, HAL, or reason...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Status Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Filter by Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All (${candidatesList.size})") }
                    )
                    CandidateStatus.values().forEach { status ->
                        val count = candidatesList.count { it.status == status }
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = if (selectedStatus == status) null else status },
                            label = { Text("${status.label} ($count)") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(getStatusColor(status))
                                )
                            }
                        )
                    }
                }
            }
        }

        // Subsystem Category Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Filter by Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All Categories") }
                        )
                    }
                    items(CandidateCategory.values()) { cat ->
                        val count = candidatesList.count { it.category == cat }
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text("${cat.label} ($count)") }
                        )
                    }
                }
            }
        }

        // Section header with candidate count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Candidates (${filteredCandidates.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectedCategory != null || selectedStatus != null || searchQuery.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedCategory = null
                        selectedStatus = null
                        searchQuery = ""
                    }) {
                        Text("Reset Filters", fontSize = 12.sp)
                    }
                }
            }
        }

        if (filteredCandidates.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.FilterListOff, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No migration candidates match current filters.", fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            items(filteredCandidates, key = { it.id }) { candidate ->
                MigrationCandidateCard(
                    candidate = candidate,
                    onAddToPatchPlan = { candidateToAddToPlan = candidate },
                    onCompare = { candidateToCompare = candidate },
                    onAnalyze = { candidateToAnalyze = candidate },
                    onToggleIgnore = {
                        candidatesList = candidatesList.map {
                            if (it.id == candidate.id) it.copy(isIgnored = !it.isIgnored) else it
                        }
                        val msg = if (!candidate.isIgnored) "Marked '${candidate.name}' as Ignored" else "Restored '${candidate.name}'"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Dialog: Deep Analyze Candidate
    candidateToAnalyze?.let { cand ->
        CandidateAnalyzeDialog(
            candidate = cand,
            onDismiss = { candidateToAnalyze = null },
            onAddToPatchPlan = {
                candidateToAnalyze = null
                candidateToAddToPlan = cand
            }
        )
    }

    // Dialog: Compare Candidate vs Target Base
    candidateToCompare?.let { cand ->
        CandidateCompareDialog(
            candidate = cand,
            onDismiss = { candidateToCompare = null }
        )
    }

    // Dialog: Add To Patch Plan Confirmation
    candidateToAddToPlan?.let { cand ->
        AddToPatchPlanDialog(
            candidate = cand,
            onDismiss = { candidateToAddToPlan = null },
            onConfirm = {
                candidatesList = candidatesList.map {
                    if (it.id == cand.id) it.copy(addedToPatchPlan = true) else it
                }
                candidateToAddToPlan = null
                Toast.makeText(context, "Added '${cand.name}' to Patch Plan!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
fun CandidateStatBadge(label: String, count: Int, color: Color) {
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
fun MigrationCandidateCard(
    candidate: MigrationCandidate,
    onAddToPatchPlan: () -> Unit,
    onCompare: () -> Unit,
    onAnalyze: () -> Unit,
    onToggleIgnore: () -> Unit
) {
    val statusColor = getStatusColor(candidate.status)
    val riskColor = getRiskColor(candidate.risk)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_card_${candidate.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (candidate.isIgnored) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (candidate.addedToPatchPlan) Color(0xFF2E7D32)
            else if (candidate.isIgnored) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (candidate.isIgnored) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Category badge, Name, Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = candidate.category.label.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (candidate.addedToPatchPlan) {
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

                        if (candidate.isIgnored) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text("IGNORED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Text(
                        text = candidate.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = candidate.status.label,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Path & Target in Monospace
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PATH:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(candidate.path, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ARCH:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(candidate.architecture, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SRC:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(candidate.source, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Reason
            Text(
                text = candidate.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Dependencies chips if any
            if (candidate.dependencies.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Deps:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    candidate.dependencies.forEach { dep ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(dep, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // Risk & Confidence Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Risk:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = riskColor.copy(alpha = 0.15f)
                    ) {
                        Text(candidate.risk.label, color = riskColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Confidence: ${(candidate.confidence * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { candidate.confidence },
                        modifier = Modifier
                            .width(60.dp)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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
                    modifier = Modifier.testTag("btn_add_to_patch_${candidate.id}"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (candidate.addedToPatchPlan) "PATCHED" else "ADD TO PATCH PLAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // 2. COMPARE
                OutlinedButton(
                    onClick = onCompare,
                    modifier = Modifier.testTag("btn_compare_${candidate.id}"),
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
                    modifier = Modifier.testTag("btn_analyze_${candidate.id}"),
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
                    modifier = Modifier.testTag("btn_ignore_${candidate.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (candidate.isIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (candidate.isIgnored) "UNIGNORE" else "IGNORE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun CandidateAnalyzeDialog(
    candidate: MigrationCandidate,
    onDismiss: () -> Unit,
    onAddToPatchPlan: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Candidate Forensic Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(candidate.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                // Monospace path inspection
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Target Path: ${candidate.path}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Architecture: ${candidate.architecture}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("Subsystem Category: ${candidate.category.label}", fontSize = 11.sp)
                        Text("Source Provenance: ${candidate.source}", fontSize = 11.sp)
                    }
                }

                Text("Forensic Reason:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(candidate.reason, fontSize = 13.sp)

                if (candidate.details.isNotBlank()) {
                    Text("Technical Details:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(candidate.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (candidate.dependencies.isNotEmpty()) {
                    Text("Required Dependencies:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    candidate.dependencies.forEach { dep ->
                        Text("• $dep", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFF3E0),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                        Text(
                            text = "Automated file cloning is disabled. To include this file in the port, click 'Add to Patch Plan'.",
                            fontSize = 11.sp,
                            color = Color(0xFFBF360C)
                        )
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
fun CandidateCompareDialog(
    candidate: MigrationCandidate,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Subsystem Comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(candidate.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Path: ${candidate.path}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)

                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("SOURCE ROM", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            Text(candidate.source, fontSize = 11.sp)
                            Text("Arch: ${candidate.architecture}", fontSize = 10.sp)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TARGET BASE", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF2E7D32))
                            Text("SM-G532F (MT6737T)", fontSize = 11.sp)
                            Text("Arch: 32-bit ARMv7-A", fontSize = 10.sp)
                        }
                    }
                }

                Text("Compatibility Assessment:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    text = when (candidate.status) {
                        CandidateStatus.SAFE_TO_INVESTIGATE -> "Component is structurally compatible with Galaxy J2 Prime hardware specifications. Requires manual validation."
                        CandidateStatus.CANDIDATE -> "Component is a strong transplant candidate. Dependencies must be resolved during ROM assembly."
                        CandidateStatus.HIGH_RISK -> "Component touches critical hardware/security subsystem. Test extensively on permissive kernel first."
                        CandidateStatus.BLOCKED -> "Component cannot be transplanted in current state (e.g. 64-bit ABI mismatch)."
                        CandidateStatus.UNKNOWN -> "Telemetry is inconclusive. Inspect binary symbols and headers manually."
                    },
                    fontSize = 12.sp
                )
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
fun AddToPatchPlanDialog(
    candidate: MigrationCandidate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Add to Patch Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Staging Patch Plan Action:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Task: ${candidate.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Target Path: ${candidate.path}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        Text("Category: ${candidate.category.label}", fontSize = 11.sp)
                        Text("Risk: ${candidate.risk.label}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = getRiskColor(candidate.risk))
                    }
                }
                Text(
                    "This will register an intentional patch operation into the active ROM Porting Plan without executing any unverified automated file copies.",
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

fun getStatusColor(status: CandidateStatus): Color {
    return when (status) {
        CandidateStatus.CANDIDATE -> Color(0xFF1976D2)
        CandidateStatus.SAFE_TO_INVESTIGATE -> Color(0xFF2E7D32)
        CandidateStatus.HIGH_RISK -> Color(0xFFE65100)
        CandidateStatus.BLOCKED -> Color(0xFFC62828)
        CandidateStatus.UNKNOWN -> Color(0xFF757575)
    }
}

fun getRiskColor(risk: MigrationRisk): Color {
    return when (risk) {
        MigrationRisk.LOW -> Color(0xFF2E7D32)
        MigrationRisk.MEDIUM -> Color(0xFFF57F17)
        MigrationRisk.HIGH -> Color(0xFFE65100)
        MigrationRisk.CRITICAL -> Color(0xFFC62828)
    }
}
