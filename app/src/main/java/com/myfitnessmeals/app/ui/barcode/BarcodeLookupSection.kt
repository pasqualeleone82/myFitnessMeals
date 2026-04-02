package com.myfitnessmeals.app.ui.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun BarcodeLookupSection(
    barcode: String,
    showCameraPermissionFallback: Boolean,
    onBarcodeChanged: (String) -> Unit,
    onLookupClicked: () -> Unit,
    onScanClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Search by barcode", style = MaterialTheme.typography.titleMedium)
        if (showCameraPermissionFallback) {
            Text(
                text = "Camera permission denied. You can still type the barcode manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("meal_barcode_permission_fallback"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = barcode,
                onValueChange = onBarcodeChanged,
                label = { Text("Barcode") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("meal_barcode_input"),
                singleLine = true,
            )
            Button(
                onClick = onLookupClicked,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("meal_barcode_button"),
            ) {
                Text("Lookup")
            }
            Button(
                onClick = onScanClicked,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("meal_barcode_scan_button"),
            ) {
                Text("Scan")
            }
        }
    }
}
