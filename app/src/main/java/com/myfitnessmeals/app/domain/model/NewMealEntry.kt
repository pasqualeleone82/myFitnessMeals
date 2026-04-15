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
    val saturatedFatTotal: Double? = null,
    val sugarTotal: Double? = null,
    val ironTotal: Double? = null,
    val calciumTotal: Double? = null,
    val magnesiumTotal: Double? = null,
    val zincTotal: Double? = null,
    val vitaminCTotal: Double? = null,
    val vitaminDTotal: Double? = null,
    val vitaminB12Total: Double? = null,
)
