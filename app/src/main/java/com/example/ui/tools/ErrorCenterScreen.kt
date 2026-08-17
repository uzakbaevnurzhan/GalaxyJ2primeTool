package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.data.manager.ErrorCenterManager
import com.example.data.model.AppErrorLog
import com.example.ui.theme.ColorWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorCenterScreen(navController: NavController) {
    val context = LocalContext.current
    val errors by ErrorCenterManager.errors.collectAsState()
    var selectedErrorForDetails by remember { mutableStateOf<AppErrorLog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Error Diagnostic Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (errors.isNotEmpty()) {
                        IconButton(onClick = { ErrorCenterManager.clearErrors() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Errors")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (errors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = com.example.ui.theme.ColorGood
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Recorded Errors",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "All system operations and engines are running smoothly.",
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
                items(errors, key = { it.id }) { errorItem ->
                    ErrorLogCard(
                        errorItem = errorItem,
                        onViewDetails = { selectedErrorForDetails = errorItem },
                        onCopy = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Error Report", errorItem.toFormattedString())
                            clipMgr.setPrimaryClip(clip)
                            Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        selectedErrorForDetails?.let { err ->
            AlertDialog(
                onDismissRequest = { selectedErrorForDetails = null },
                title = { Text("[${err.module}] ${err.operation}") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        Text("Stage: ${err.stage}", fontWeight = FontWeight.SemiBold)
                        Text("Message: ${err.message}", color = MaterialTheme.colorScheme.error)
                        err.cause?.let { Text("Root Cause: $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Suggested Action:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text(err.suggestedAction, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (!err.stackTrace.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Stack Trace:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    item {
                                        Text(
                                            err.stackTrace,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedErrorForDetails = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun ErrorLogCard(
    errorItem: AppErrorLog,
    onViewDetails: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorItem.module,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(errorItem.timestamp))
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(errorItem.operation, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorItem.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = ColorWarning, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorItem.suggestedAction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Error Details")
                }
                TextButton(onClick = onViewDetails) {
                    Text("Full Details")
                }
            }
        }
    }
}

private fun AppErrorLog.toFormattedString(): String = buildString {
    appendLine("MODULE: $module")
    appendLine("OPERATION: $operation")
    appendLine("STAGE: $stage")
    appendLine("MESSAGE: $message")
    cause?.let { appendLine("CAUSE: $it") }
    appendLine("SUGGESTED ACTION: $suggestedAction")
    stackTrace?.let { appendLine("STACK TRACE:\n$it") }
}
