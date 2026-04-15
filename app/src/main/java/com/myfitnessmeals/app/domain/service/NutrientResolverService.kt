package com.myfitnessmeals.app.domain.service

import com.myfitnessmeals.app.data.local.NutritionOverrideEntity
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.usecase.MealFoodCandidate

data class ResolvedNutrients(
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
    val source: ResolvedSource,
)

class NutrientResolverService {
    fun resolve(food: MealFoodCandidate, override: NutritionOverrideEntity?): ResolvedNutrients {
        return ResolvedNutrients(
            kcal100 = override?.kcal100 ?: food.kcal100,
            carb100 = override?.carb100 ?: food.carb100,
            fat100 = override?.fat100 ?: food.fat100,
            protein100 = override?.protein100 ?: food.protein100,
            saturatedFat100 = override?.saturatedFat100 ?: food.saturatedFat100,
            sugar100 = override?.sugar100 ?: food.sugar100,
            iron100 = override?.iron100 ?: food.iron100,
            calcium100 = override?.calcium100 ?: food.calcium100,
            magnesium100 = override?.magnesium100 ?: food.magnesium100,
            zinc100 = override?.zinc100 ?: food.zinc100,
            vitaminC100 = override?.vitaminC100 ?: food.vitaminC100,
            vitaminD100 = override?.vitaminD100 ?: food.vitaminD100,
            vitaminB12100 = override?.vitaminB12100 ?: food.vitaminB12100,
            source = if (override != null) ResolvedSource.OVERRIDE else food.source,
        )
    }
}
