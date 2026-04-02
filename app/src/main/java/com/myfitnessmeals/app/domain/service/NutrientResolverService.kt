package com.myfitnessmeals.app.domain.service

import com.myfitnessmeals.app.data.local.NutritionOverrideEntity
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.usecase.MealFoodCandidate

data class ResolvedNutrients(
    val kcal100: Double?,
    val carb100: Double?,
    val fat100: Double?,
    val protein100: Double?,
    val source: ResolvedSource,
)

class NutrientResolverService {
    fun resolve(food: MealFoodCandidate, override: NutritionOverrideEntity?): ResolvedNutrients {
        return ResolvedNutrients(
            kcal100 = override?.kcal100 ?: food.kcal100,
            carb100 = override?.carb100 ?: food.carb100,
            fat100 = override?.fat100 ?: food.fat100,
            protein100 = override?.protein100 ?: food.protein100,
            source = if (override != null) ResolvedSource.OVERRIDE else food.source,
        )
    }
}
