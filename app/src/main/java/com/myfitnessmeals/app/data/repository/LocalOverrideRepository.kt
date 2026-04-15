package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.NutritionOverrideDao
import com.myfitnessmeals.app.data.local.NutritionOverrideEntity

class LocalOverrideRepository(
    private val overrideDao: NutritionOverrideDao,
) : OverrideRepository {
    override suspend fun upsertOverride(override: NutritionOverrideEntity) = overrideDao.upsert(override)

    override suspend fun getOverrideByFoodId(foodId: Long): NutritionOverrideEntity? =
        overrideDao.getByFoodId(foodId)

    override suspend fun getOverridesByFoodIds(foodIds: Collection<Long>): Map<Long, NutritionOverrideEntity> {
        if (foodIds.isEmpty()) return emptyMap()
        return overrideDao.getByFoodIds(foodIds.distinct()).associateBy { it.foodId }
    }

    override suspend fun deleteOverrideByFoodId(foodId: Long): Int = overrideDao.deleteByFoodId(foodId)
}
