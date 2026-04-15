package com.myfitnessmeals.app.domain.model

data class HistoryDaySnapshot(
    val localDate: String,
    val kcalTarget: Double,
    val kcalIntake: Double,
    val kcalBurned: Double,
    val kcalRemaining: Double,
    val carbGrams: Double,
    val fatGrams: Double,
    val proteinGrams: Double,
    val saturatedFatGrams: Double?,
    val sugarGrams: Double?,
    val ironMg: Double?,
    val calciumMg: Double?,
    val magnesiumMg: Double?,
    val zincMg: Double?,
    val vitaminCMg: Double?,
    val vitaminDMcg: Double?,
    val vitaminB12Mcg: Double?,
    val meals: List<HistoryMealCard> = emptyList(),
) {
    val isEmptyDay: Boolean
        get() = meals.isEmpty() &&
            kcalIntake == 0.0 &&
            carbGrams == 0.0 &&
            fatGrams == 0.0 &&
            proteinGrams == 0.0

    val shouldShowEnrichedPlaceholder: Boolean
        get() = listOf(
            saturatedFatGrams,
            sugarGrams,
            ironMg,
            calciumMg,
            magnesiumMg,
            zincMg,
            vitaminCMg,
            vitaminDMcg,
            vitaminB12Mcg,
        ).all { it == null }
}
