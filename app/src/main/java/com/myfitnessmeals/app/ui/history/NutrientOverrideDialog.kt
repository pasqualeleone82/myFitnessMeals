package com.myfitnessmeals.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.R

data class NutrientOverrideInput(
    val kcal100: Double?,
    val carb100: Double?,
    val fat100: Double?,
    val protein100: Double?,
    val saturatedFat100: Double?,
    val sugar100: Double?,
    val iron100: Double?,
    val calcium100: Double?,
    val magnesium100: Double?,
    val zinc100: Double?,
    val vitaminC100: Double?,
    val vitaminD100: Double?,
    val vitaminB12100: Double?,
    val note: String?,
)

@Composable
fun NutrientOverrideDialog(
    foodName: String,
    onSave: (NutrientOverrideInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var kcal by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var saturatedFat by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var iron by remember { mutableStateOf("") }
    var calcium by remember { mutableStateOf("") }
    var magnesium by remember { mutableStateOf("") }
    var zinc by remember { mutableStateOf("") }
    var vitaminC by remember { mutableStateOf("") }
    var vitaminD by remember { mutableStateOf("") }
    var vitaminB12 by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val invalidNumberError = stringResource(R.string.history_override_invalid_number)
    val atLeastOneError = stringResource(R.string.history_override_at_least_one_required)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.history_override_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("history_override_dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = foodName,
                    style = MaterialTheme.typography.titleSmall,
                )
                NutrientField(kcal, { kcal = it }, R.string.history_override_kcal, "history_override_kcal_input")
                NutrientField(carb, { carb = it }, R.string.history_override_carb, "history_override_carb_input")
                NutrientField(fat, { fat = it }, R.string.history_override_fat, "history_override_fat_input")
                NutrientField(protein, { protein = it }, R.string.history_override_protein, "history_override_protein_input")
                NutrientField(saturatedFat, { saturatedFat = it }, R.string.history_override_saturated_fat, "history_override_saturated_fat_input")
                NutrientField(sugar, { sugar = it }, R.string.history_override_sugar, "history_override_sugar_input")
                NutrientField(iron, { iron = it }, R.string.history_override_iron, "history_override_iron_input")
                NutrientField(calcium, { calcium = it }, R.string.history_override_calcium, "history_override_calcium_input")
                NutrientField(magnesium, { magnesium = it }, R.string.history_override_magnesium, "history_override_magnesium_input")
                NutrientField(zinc, { zinc = it }, R.string.history_override_zinc, "history_override_zinc_input")
                NutrientField(vitaminC, { vitaminC = it }, R.string.history_override_vitamin_c, "history_override_vitamin_c_input")
                NutrientField(vitaminD, { vitaminD = it }, R.string.history_override_vitamin_d, "history_override_vitamin_d_input")
                NutrientField(vitaminB12, { vitaminB12 = it }, R.string.history_override_vitamin_b12, "history_override_vitamin_b12_input")

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.history_override_note)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_override_note_input"),
                    singleLine = true,
                )

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parseInput(
                        kcal = kcal,
                        carb = carb,
                        fat = fat,
                        protein = protein,
                        saturatedFat = saturatedFat,
                        sugar = sugar,
                        iron = iron,
                        calcium = calcium,
                        magnesium = magnesium,
                        zinc = zinc,
                        vitaminC = vitaminC,
                        vitaminD = vitaminD,
                        vitaminB12 = vitaminB12,
                        note = note,
                    )

                    if (parsed == null) {
                        errorText = invalidNumberError
                        return@TextButton
                    }

                    val nutrients = listOf(
                        parsed.kcal100,
                        parsed.carb100,
                        parsed.fat100,
                        parsed.protein100,
                        parsed.saturatedFat100,
                        parsed.sugar100,
                        parsed.iron100,
                        parsed.calcium100,
                        parsed.magnesium100,
                        parsed.zinc100,
                        parsed.vitaminC100,
                        parsed.vitaminD100,
                        parsed.vitaminB12100,
                    )
                    if (nutrients.none { it != null }) {
                        errorText = atLeastOneError
                        return@TextButton
                    }

                    errorText = null
                    onSave(parsed)
                },
            ) {
                Text(stringResource(R.string.history_override_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        },
    )
}

@Composable
private fun NutrientField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

private fun parseInput(
    kcal: String,
    carb: String,
    fat: String,
    protein: String,
    saturatedFat: String,
    sugar: String,
    iron: String,
    calcium: String,
    magnesium: String,
    zinc: String,
    vitaminC: String,
    vitaminD: String,
    vitaminB12: String,
    note: String,
): NutrientOverrideInput? {
    val parsed = listOf(
        kcal.toNullableDoubleStrict(),
        carb.toNullableDoubleStrict(),
        fat.toNullableDoubleStrict(),
        protein.toNullableDoubleStrict(),
        saturatedFat.toNullableDoubleStrict(),
        sugar.toNullableDoubleStrict(),
        iron.toNullableDoubleStrict(),
        calcium.toNullableDoubleStrict(),
        magnesium.toNullableDoubleStrict(),
        zinc.toNullableDoubleStrict(),
        vitaminC.toNullableDoubleStrict(),
        vitaminD.toNullableDoubleStrict(),
        vitaminB12.toNullableDoubleStrict(),
    )

    if (parsed.any { it == INVALID_NUMBER }) {
        return null
    }

    return NutrientOverrideInput(
        kcal100 = parsed[0] as Double?,
        carb100 = parsed[1] as Double?,
        fat100 = parsed[2] as Double?,
        protein100 = parsed[3] as Double?,
        saturatedFat100 = parsed[4] as Double?,
        sugar100 = parsed[5] as Double?,
        iron100 = parsed[6] as Double?,
        calcium100 = parsed[7] as Double?,
        magnesium100 = parsed[8] as Double?,
        zinc100 = parsed[9] as Double?,
        vitaminC100 = parsed[10] as Double?,
        vitaminD100 = parsed[11] as Double?,
        vitaminB12100 = parsed[12] as Double?,
        note = note.trim().ifBlank { null },
    )
}

private val INVALID_NUMBER = Any()

private fun String.toNullableDoubleStrict(): Any? {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return null
    }
    val parsed = trimmed.replace(',', '.').toDoubleOrNull() ?: return INVALID_NUMBER
    if (parsed < 0.0) {
        return INVALID_NUMBER
    }
    return parsed
}
