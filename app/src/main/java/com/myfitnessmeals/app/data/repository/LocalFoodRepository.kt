package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.FoodDao
import com.myfitnessmeals.app.data.local.FoodItemEntity

class LocalFoodRepository(
    private val foodDao: FoodDao,
) {
    suspend fun upsertFood(food: FoodItemEntity): Long = foodDao.upsert(food)

    suspend fun getFoodById(id: Long): FoodItemEntity? = foodDao.getById(id)

    suspend fun getFoodByBarcode(barcode: String): FoodItemEntity? = foodDao.getByBarcode(barcode)
}
