package com.myfitnessmeals.app.ui.meal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.ui.barcode.BarcodeLookupSection
import com.myfitnessmeals.app.ui.search.FoodSearchSection

@Composable
fun MealLoggingRoute(viewModel: MealLoggingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    MealLoggingScreen(
        state = state,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchClicked = viewModel::searchByText,
        onBarcodeChanged = viewModel::onBarcodeChanged,
        onBarcodeLookupClicked = viewModel::searchByBarcode,
        onMealTypeSelected = viewModel::onMealTypeSelected,
        onFoodSelected = viewModel::onFoodSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onUnitChanged = viewModel::onUnitChanged,
        onSaveClicked = viewModel::saveSelectedFood,
        onDeleteEntry = viewModel::deleteEntry,
    )
}

@Composable
fun MealLoggingScreen(
    state: MealLoggingUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSearchClicked: () -> Unit,
    onBarcodeChanged: (String) -> Unit,
    onBarcodeLookupClicked: () -> Unit,
    onMealTypeSelected: (MealType) -> Unit,
    onFoodSelected: (com.myfitnessmeals.app.domain.usecase.MealFoodCandidate) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onDeleteEntry: (Long) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("meal_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(text = "Meal logging", style = MaterialTheme.typography.headlineSmall)
            }

            item {
                MealTypeSelector(
                    selectedMealType = state.selectedMealType,
                    onMealTypeSelected = onMealTypeSelected,
                )
            }

            item {
                FoodSearchSection(
                    query = state.searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                    onSearchClicked = onSearchClicked,
                )
            }

            item {
                BarcodeLookupSection(
                    barcode = state.barcodeQuery,
                    onBarcodeChanged = onBarcodeChanged,
                    onLookupClicked = onBarcodeLookupClicked,
                )
            }

            if (state.errorMessage != null) {
                item {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("meal_error"),
                    )
                }
            }

            item {
                Text(text = "Results", style = MaterialTheme.typography.titleMedium)
            }

            items(state.searchResults, key = { it.id }) { food ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFoodSelected(food) }
                        .testTag("meal_result_${food.id}"),
                    border = if (state.selectedFood?.id == food.id) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(food.name, style = MaterialTheme.typography.titleSmall)
                        Text(food.brand ?: "Unknown brand", style = MaterialTheme.typography.bodyMedium)
                        Text("Source: ${food.source}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                PortionDetailSection(
                    state = state,
                    onQuantityChanged = onQuantityChanged,
                    onUnitChanged = onUnitChanged,
                    onSaveClicked = onSaveClicked,
                )
            }

            item {
                DailyTotalsCard(
                    kcal = state.kcalIntake,
                    carb = state.carbTotal,
                    fat = state.fatTotal,
                    protein = state.proteinTotal,
                )
            }

            item {
                Text(text = "Logged entries", style = MaterialTheme.typography.titleMedium)
            }

            items(state.entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meal_entry_${entry.id}"),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${entry.mealType}: ${entry.foodName}", style = MaterialTheme.typography.titleSmall)
                        Text("${entry.quantityValue} ${entry.quantityUnit}")
                        Text(
                            "kcal ${entry.kcalTotal.format1()} | C ${entry.carbTotal.format1()} | F ${entry.fatTotal.format1()} | P ${entry.proteinTotal.format1()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { onDeleteEntry(entry.id) },
                            modifier = Modifier.testTag("meal_delete_${entry.id}"),
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealTypeSelector(
    selectedMealType: MealType,
    onMealTypeSelected: (MealType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Meal type", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MealType.entries.forEach { mealType ->
                FilterChip(
                    selected = selectedMealType == mealType,
                    onClick = { onMealTypeSelected(mealType) },
                    label = { Text(mealType.name.lowercase().replaceFirstChar { it.titlecase() }) },
                    modifier = Modifier.testTag("meal_type_${mealType.name.lowercase()}"),
                )
            }
        }
    }
}

@Composable
private fun PortionDetailSection(
    state: MealLoggingUiState,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Portion detail", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.quantityInput,
                onValueChange = onQuantityChanged,
                label = { Text("Quantity") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("meal_quantity_input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.unitInput,
                onValueChange = onUnitChanged,
                label = { Text("Unit (g/ml/serving)") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("meal_unit_input"),
                singleLine = true,
            )
        }

        state.preview?.let { preview ->
            Text("Resolved source: ${preview.resolvedSource}", style = MaterialTheme.typography.bodySmall)
            Text("Calories: ${preview.kcalTotal.format1()}${if (preview.kcalMissing) " (N/D)" else ""}")
            Text("Carbs: ${preview.carbTotal.format1()}${if (preview.carbMissing) " (N/D)" else ""}")
            Text("Fat: ${preview.fatTotal.format1()}${if (preview.fatMissing) " (N/D)" else ""}")
            Text("Protein: ${preview.proteinTotal.format1()}${if (preview.proteinMissing) " (N/D)" else ""}")
        }

        Button(
            onClick = onSaveClicked,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("meal_save_button"),
        ) {
            Text("Save meal entry")
        }
    }
}

@Composable
private fun DailyTotalsCard(
    kcal: Double,
    carb: Double,
    fat: Double,
    protein: Double,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Day totals", style = MaterialTheme.typography.titleMedium)
            Text("Calories: ${kcal.format1()}", modifier = Modifier.testTag("meal_total_kcal"))
            Text("Carbs: ${carb.format1()}")
            Text("Fat: ${fat.format1()}")
            Text("Protein: ${protein.format1()}")
        }
    }
}

private fun Double.format1(): String = String.format("%.1f", this)
