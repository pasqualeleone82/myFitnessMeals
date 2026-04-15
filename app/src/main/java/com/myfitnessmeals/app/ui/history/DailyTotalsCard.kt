package com.myfitnessmeals.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.domain.model.HistoryDaySnapshot
import java.util.Locale

@Composable
fun DailyTotalsCard(day: HistoryDaySnapshot) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_daily_totals_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.history_daily_totals),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stringResource(R.string.history_total_target, day.kcalTarget.format1()),
                modifier = Modifier.testTag("history_total_target"),
            )
            Text(
                text = stringResource(R.string.history_total_intake, day.kcalIntake.format1()),
                modifier = Modifier.testTag("history_total_intake"),
            )
            Text(
                text = stringResource(R.string.history_total_burned, day.kcalBurned.format1()),
                modifier = Modifier.testTag("history_total_burned"),
            )
            Text(
                text = stringResource(R.string.history_total_remaining, day.kcalRemaining.format1()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("history_selected_remaining"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.history_total_carbs, day.carbGrams.format1()),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.history_total_fat, day.fatGrams.format1()),
                    modifier = Modifier.weight(1f),
                )
            }

            Text(text = stringResource(R.string.history_total_protein, day.proteinGrams.format1()))

            Text(text = stringResource(R.string.history_total_saturated_fat, day.saturatedFatGrams.formatNullable()))
            Text(text = stringResource(R.string.history_total_sugar, day.sugarGrams.formatNullable()))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.history_total_iron, day.ironMg.formatNullable()), modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.history_total_calcium, day.calciumMg.formatNullable()), modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.history_total_magnesium, day.magnesiumMg.formatNullable()), modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.history_total_zinc, day.zincMg.formatNullable()), modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(R.string.history_total_vitamin_c, day.vitaminCMg.formatNullable()), modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.history_total_vitamin_d, day.vitaminDMcg.formatNullable()), modifier = Modifier.weight(1f))
            }

            Text(text = stringResource(R.string.history_total_vitamin_b12, day.vitaminB12Mcg.formatNullable()))
        }
    }
}

private fun Double.format1(): String = String.format(Locale.US, "%.1f", this)

private fun Double?.formatNullable(): String {
    return this?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
}