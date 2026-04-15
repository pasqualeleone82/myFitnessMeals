package com.myfitnessmeals.app.domain.model

data class HistoryMealCard(
    val mealEntryId: Long,
    val foodId: Long,
    val foodName: String,
    val brand: String?,
    val mealType: MealType,
    val quantityValue: Double,
    val quantityUnit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val saturatedFat: Double?,
    val sugar: Double?,
    val iron: Double?,
    val calcium: Double?,
    val magnesium: Double?,
    val zinc: Double?,
    val vitaminC: Double?,
    val vitaminD: Double?,
    val vitaminB12: Double?,
    val isOverridden: Boolean,
    val sourceLabel: String,
) {
    val hasAnyEnrichedValue: Boolean
        get() = listOf(
            saturatedFat,
            sugar,
            iron,
            calcium,
            magnesium,
            zinc,
            vitaminC,
            vitaminD,
            vitaminB12,
        ).any { it != null }

    val shouldShowEnrichedPlaceholder: Boolean
        get() = !hasAnyEnrichedValue
}
