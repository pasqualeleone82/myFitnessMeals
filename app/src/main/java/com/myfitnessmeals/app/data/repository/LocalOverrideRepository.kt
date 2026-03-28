package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.NutritionOverrideDao
import com.myfitnessmeals.app.data.local.NutritionOverrideEntity

class LocalOverrideRepository(
    private val overrideDao: NutritionOverrideDao,
) {
    suspend fun upsertOverride(override: NutritionOverrideEntity) = overrideDao.upsert(override)

    suspend fun getOverrideByFoodId(foodId: Long): NutritionOverrideEntity? =
        overrideDao.getByFoodId(foodId)

    suspend fun deleteOverrideByFoodId(foodId: Long): Int = overrideDao.deleteByFoodId(foodId)
}
