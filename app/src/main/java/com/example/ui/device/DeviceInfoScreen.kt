package com.example.ui.device

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun DeviceInfoScreen(navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Device Information", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
        }
        item { InfoRow("Manufacturer", Build.MANUFACTURER) }
        item { InfoRow("Model", Build.MODEL) }
        item { InfoRow("Device", Build.DEVICE) }
        item { InfoRow("Product", Build.PRODUCT) }
        item { InfoRow("Board", Build.BOARD) }
        item { InfoRow("Hardware", Build.HARDWARE) }
        item { InfoRow("Android Version", Build.VERSION.RELEASE) }
        item { InfoRow("SDK", Build.VERSION.SDK_INT.toString()) }
        item { InfoRow("Supported ABIs", Build.SUPPORTED_ABIS.joinToString(", ")) }
        item { InfoRow("Tags", Build.TAGS) }
        item { InfoRow("Type", Build.TYPE) }
        item { InfoRow("User", Build.USER) }
        item { InfoRow("Display", Build.DISPLAY) }
        item { InfoRow("Fingerprint", Build.FINGERPRINT) }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(text = value.ifEmpty { "Unknown" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
