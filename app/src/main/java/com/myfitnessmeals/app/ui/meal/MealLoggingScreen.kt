package com.myfitnessmeals.app.ui.meal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.ui.fooddetail.FoodDetailCard
import com.myfitnessmeals.app.ui.barcode.BarcodeCameraScannerDialog
import com.myfitnessmeals.app.ui.barcode.BarcodeLookupSection
import com.myfitnessmeals.app.ui.search.FoodSearchSection

@Composable
fun MealLoggingRoute(viewModel: MealLoggingViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var showPermissionFallback by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        showScanner = granted
        showPermissionFallback = !granted
    }

    MealLoggingScreen(
        state = state,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSearchClicked = viewModel::searchByText,
        showCameraPermissionFallback = showPermissionFallback,
        onBarcodeChanged = viewModel::onBarcodeChanged,
        onBarcodeLookupClicked = viewModel::searchByBarcode,
        onBarcodeScanClicked = {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                showPermissionFallback = false
                showScanner = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onMealTypeSelected = viewModel::onMealTypeSelected,
        onFoodSelected = viewModel::onFoodSelected,
        onQuantityChanged = viewModel::onQuantityChanged,
        onUnitChanged = viewModel::onUnitChanged,
        onOverrideKcalChanged = viewModel::onOverrideKcalChanged,
        onOverrideCarbChanged = viewModel::onOverrideCarbChanged,
        onOverrideFatChanged = viewModel::onOverrideFatChanged,
        onOverrideProteinChanged = viewModel::onOverrideProteinChanged,
        onOverrideNoteChanged = viewModel::onOverrideNoteChanged,
        onOverrideSaveClicked = viewModel::saveOverride,
        onOverrideClearClicked = viewModel::clearOverride,
        onSaveClicked = viewModel::saveSelectedFood,
        onRetryClicked = viewModel::retryLastSearch,
        onDeleteEntry = viewModel::deleteEntry,
    )

    if (showScanner) {
        BarcodeCameraScannerDialog(
            onDismissRequest = { showScanner = false },
            onBarcodeScanned = { scannedBarcode ->
                showScanner = false
                viewModel.onBarcodeScanned(scannedBarcode)
            },
            onScannerError = { message ->
                showScanner = false
                viewModel.onBarcodeScanError(message)
            },
        )
    }
}

@Composable
fun MealLoggingScreen(
    state: MealLoggingUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSearchClicked: () -> Unit,
    showCameraPermissionFallback: Boolean,
    onBarcodeChanged: (String) -> Unit,
    onBarcodeLookupClicked: () -> Unit,
    onBarcodeScanClicked: () -> Unit,
    onMealTypeSelected: (MealType) -> Unit,
    onFoodSelected: (com.myfitnessmeals.app.domain.usecase.MealFoodCandidate) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onUnitChanged: (String) -> Unit,
    onOverrideKcalChanged: (String) -> Unit,
    onOverrideCarbChanged: (String) -> Unit,
    onOverrideFatChanged: (String) -> Unit,
    onOverrideProteinChanged: (String) -> Unit,
    onOverrideNoteChanged: (String) -> Unit,
    onOverrideSaveClicked: () -> Unit,
    onOverrideClearClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onRetryClicked: () -> Unit,
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
                    showCameraPermissionFallback = showCameraPermissionFallback,
                    onBarcodeChanged = onBarcodeChanged,
                    onLookupClicked = onBarcodeLookupClicked,
                    onScanClicked = onBarcodeScanClicked,
                )
            }

            if (state.errorMessage != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("meal_error"),
                        )
                        if (state.showRetryAction) {
                            Button(
                                onClick = onRetryClicked,
                                modifier = Modifier.testTag("meal_error_retry"),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
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
                NutritionOverrideSection(
                    state = state,
                    onKcalChanged = onOverrideKcalChanged,
                    onCarbChanged = onOverrideCarbChanged,
                    onFatChanged = onOverrideFatChanged,
                    onProteinChanged = onOverrideProteinChanged,
                    onNoteChanged = onOverrideNoteChanged,
                    onSaveClicked = onOverrideSaveClicked,
                    onClearClicked = onOverrideClearClicked,
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
private fun NutritionOverrideSection(
    state: MealLoggingUiState,
    onKcalChanged: (String) -> Unit,
    onCarbChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onClearClicked: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Nutrition override", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.overrideKcalInput,
                onValueChange = onKcalChanged,
                label = { Text("kcal/100") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("override_kcal_input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.overrideCarbInput,
                onValueChange = onCarbChanged,
                label = { Text("carb/100") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("override_carb_input"),
                singleLine = true,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.overrideFatInput,
                onValueChange = onFatChanged,
                label = { Text("fat/100") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("override_fat_input"),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.overrideProteinInput,
                onValueChange = onProteinChanged,
                label = { Text("protein/100") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("override_protein_input"),
                singleLine = true,
            )
        }

        OutlinedTextField(
            value = state.overrideNoteInput,
            onValueChange = onNoteChanged,
            label = { Text("Note") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("override_note_input"),
            singleLine = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSaveClicked,
                modifier = Modifier
                    .weight(1f)
                    .testTag("override_save_button"),
            ) {
                Text("Save override")
            }
            Button(
                onClick = onClearClicked,
                modifier = Modifier
                    .weight(1f)
                    .testTag("override_clear_button"),
            ) {
                Text("Clear override")
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
            val baseSource = state.selectedFood?.source ?: ResolvedSource.CACHE
            FoodDetailCard(
                foodName = state.selectedFood?.name ?: "Unknown food",
                baseSource = baseSource,
                effectiveSource = preview.resolvedSource,
                overrideUpdatedAtEpochMs = state.overrideUpdatedAtEpochMs,
                kcalTotal = preview.kcalTotal,
                carbTotal = preview.carbTotal,
                fatTotal = preview.fatTotal,
                proteinTotal = preview.proteinTotal,
                kcalMissing = preview.kcalMissing,
                carbMissing = preview.carbMissing,
                fatMissing = preview.fatMissing,
                proteinMissing = preview.proteinMissing,
            )
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
