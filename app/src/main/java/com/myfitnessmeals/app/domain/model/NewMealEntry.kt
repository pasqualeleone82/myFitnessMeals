package com.myfitnessmeals.app.domain.model

data class NewMealEntry(
    val localDate: String,
    val timezoneOffsetMin: Int,
    val mealType: MealType,
    val foodId: Long,
    val quantityValue: Double,
    val quantityUnit: String,
    val resolvedSource: ResolvedSource,
    val kcalTotal: Double,
    val carbTotal: Double,
    val fatTotal: Double,
    val proteinTotal: Double,
)
