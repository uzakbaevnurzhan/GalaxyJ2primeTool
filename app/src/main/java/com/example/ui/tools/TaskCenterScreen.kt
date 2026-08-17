package com.example.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.manager.TaskManager
import com.example.data.model.TaskItem
import com.example.data.model.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCenterScreen(navController: NavController) {
    val tasks by TaskManager.tasks.collectAsState()
    var selectedTaskForLogs by remember { mutableStateOf<TaskItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { TaskManager.clearCompleted() }) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = "Clear Completed")
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.TaskAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Background Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "All operations, builds, and patch transactions run and report here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onCancel = { TaskManager.cancelTask(task.id) },
                        onViewLogs = { selectedTaskForLogs = task }
                    )
                }
            }
        }

        selectedTaskForLogs?.let { task ->
            AlertDialog(
                onDismissRequest = { selectedTaskForLogs = null },
                title = { Text("Logs: ${task.title}") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        Text("Stage: ${task.currentStage}", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (task.logs.isEmpty()) {
                            Text("No verbose logs recorded for this stage.")
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(task.logs) { log ->
                                    Text(
                                        log,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedTaskForLogs = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun TaskCard(
    task: TaskItem,
    onCancel: () -> Unit,
    onViewLogs: () -> Unit
) {
    val statusColor = when (task.status) {
        TaskStatus.QUEUED -> MaterialTheme.colorScheme.outline
        TaskStatus.PREPARING -> MaterialTheme.colorScheme.secondary
        TaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TaskStatus.VALIDATING -> Color(0xFFFF9800)
        TaskStatus.SUCCESS -> com.example.ui.theme.ColorGood
        TaskStatus.WARNING -> com.example.ui.theme.ColorWarning
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = task.status.name,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PREPARING) {
                    if (task.canCancel) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Stage: ${task.currentStage}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

            if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.PREPARING || task.status == TaskStatus.VALIDATING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${(task.progress * 100).toInt()}% completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            task.errorDetails?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            task.resultSummary?.let { res ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = com.example.ui.theme.ColorGood.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = res,
                        color = com.example.ui.theme.ColorGood,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(task.startTime))
                Text("Started at $timeStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                TextButton(onClick = onViewLogs) {
                    Icon(Icons.Filled.Notes, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Logs (${task.logs.size})", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
