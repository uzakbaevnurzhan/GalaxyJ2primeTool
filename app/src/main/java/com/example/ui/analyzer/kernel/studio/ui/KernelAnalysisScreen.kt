package com.example.ui.analyzer.kernel.studio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.analyzer.kernel.studio.models.KernelInfo

@Composable
fun KernelAnalysisScreen(kernelInfo: KernelInfo?) {
    if (kernelInfo == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No Kernel Info Available", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Kernel Binary Identity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    KernelDetailRow("Detected Format", kernelInfo.formatInfo.format)
                    KernelDetailRow("Compression", kernelInfo.formatInfo.compression)
                    KernelDetailRow("Architecture", kernelInfo.architecture)
                    KernelDetailRow("Raw Binary Size", "${kernelInfo.rawSize} bytes")
                    KernelDetailRow("Decompressed Size", "${kernelInfo.decompressedSize} bytes")
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Version & Build Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    KernelDetailRow("Linux Version", kernelInfo.versionInfo.fullString)
                    KernelDetailRow("Major.Minor.Patch", "${kernelInfo.versionInfo.major}.${kernelInfo.versionInfo.minor}.${kernelInfo.versionInfo.patch}")
                    KernelDetailRow("Extra Version", kernelInfo.versionInfo.extraVersion.ifEmpty { "None" })
                    KernelDetailRow("Compiler", "${kernelInfo.versionInfo.compiler} ${kernelInfo.versionInfo.compilerVersion}")
                    KernelDetailRow("SMP Multiprocessing", if (kernelInfo.versionInfo.isSmp) "Enabled (SMP)" else "UP (Uni-Processor)")
                    KernelDetailRow("Preemption", if (kernelInfo.versionInfo.isPreempt) "PREEMPT" else "Non-preemptible")
                    KernelDetailRow("Module Support", if (kernelInfo.versionInfo.hasModuleSupport) "Enabled" else "Unknown / Built-in only")
                }
            }
        }

        if (kernelInfo.modules.isNotEmpty()) {
            item {
                Text(
                    "Detected Loadable Kernel Modules (*.ko)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(kernelInfo.modules) { mod ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                mod.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(mod.architecture, fontSize = 10.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Vermagic: ${mod.vermagic}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (mod.dependencies.isNotEmpty()) {
                            Text(
                                "Depends: ${mod.dependencies.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
