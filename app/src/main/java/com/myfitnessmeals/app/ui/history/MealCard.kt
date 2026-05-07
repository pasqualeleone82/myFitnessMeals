package com.myfitnessmeals.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import java.util.Locale

@Composable
fun MealCard(
    meal: HistoryMealCard,
    onEditTapped: () -> Unit,
    onDeleteTapped: () -> Unit,
    onOverrideTapped: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_meal_card_${meal.mealEntryId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${meal.mealType}: ${meal.foodName}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("history_meal_title_${meal.mealEntryId}"),
            )

            meal.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                Text(
                    text = brand,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("history_meal_brand_${meal.mealEntryId}"),
                )
            }

            Text(
                text = "${meal.quantityValue.format1()} ${meal.quantityUnit}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("history_meal_portion_${meal.mealEntryId}"),
            )

            Text(
                text = stringResource(
                    R.string.meal_entry_line,
                    meal.kcal,
                    meal.carbs,
                    meal.fat,
                    meal.protein,
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("history_meal_macros_${meal.mealEntryId}"),
            )

            Text(
                text = stringResource(R.string.history_meal_source, meal.sourceLabel),
                style = MaterialTheme.typography.bodySmall,
                color = if (meal.isOverridden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("history_meal_source_${meal.mealEntryId}"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onEditTapped,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_meal_edit_${meal.mealEntryId}"),
                ) {
                    Text(stringResource(R.string.history_meal_edit))
                }

                Button(
                    onClick = onDeleteTapped,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_meal_delete_${meal.mealEntryId}"),
                ) {
                    Text(stringResource(R.string.history_meal_delete))
                }
            }

            OutlinedButton(
                onClick = onOverrideTapped,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_meal_override_${meal.mealEntryId}"),
            ) {
                Text(stringResource(R.string.history_meal_override))
            }
        }
    }
}

private fun Double.format1(): String = String.format(Locale.US, "%.1f", this)
