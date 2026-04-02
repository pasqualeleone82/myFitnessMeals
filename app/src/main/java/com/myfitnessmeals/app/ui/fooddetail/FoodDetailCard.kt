package com.myfitnessmeals.app.ui.fooddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.domain.model.ResolvedSource

@Composable
fun FoodDetailCard(
    foodName: String,
    baseSource: ResolvedSource,
    effectiveSource: ResolvedSource,
    overrideUpdatedAtEpochMs: Long?,
    kcalTotal: Double,
    carbTotal: Double,
    fatTotal: Double,
    proteinTotal: Double,
    kcalMissing: Boolean,
    carbMissing: Boolean,
    fatMissing: Boolean,
    proteinMissing: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("food_detail_card")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Food detail", style = MaterialTheme.typography.titleMedium)
            Text(foodName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("food_detail_name"))
            Text(
                "Resolved source: $effectiveSource",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("meal_resolved_source"),
            )
            Text(
                "Data provenance: base=$baseSource -> effective=$effectiveSource",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("meal_provenance"),
            )
            overrideUpdatedAtEpochMs?.let { updatedAt ->
                Text(
                    "Override updated at: ${updatedAt.formatEpochMillisUtc()}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("meal_override_updated_at"),
                )
            }
            Text("Calories: ${kcalTotal.format1()}${if (kcalMissing) " (N/D)" else ""}")
            Text("Carbs: ${carbTotal.format1()}${if (carbMissing) " (N/D)" else ""}")
            Text("Fat: ${fatTotal.format1()}${if (fatMissing) " (N/D)" else ""}")
            Text("Protein: ${proteinTotal.format1()}${if (proteinMissing) " (N/D)" else ""}")
        }
    }
}

private fun Double.format1(): String = String.format("%.1f", this)

private fun Long.formatEpochMillisUtc(): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", java.util.Locale.US)
    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return formatter.format(java.util.Date(this))
}
