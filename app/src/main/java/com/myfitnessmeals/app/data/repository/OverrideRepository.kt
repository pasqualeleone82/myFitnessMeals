package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.NutritionOverrideEntity

interface OverrideRepository {
    suspend fun upsertOverride(override: NutritionOverrideEntity)

    suspend fun getOverrideByFoodId(foodId: Long): NutritionOverrideEntity?

    suspend fun deleteOverrideByFoodId(foodId: Long): Int
}
