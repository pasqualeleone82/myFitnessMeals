package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: FoodItemEntity): Long

    @Query("SELECT * FROM food_item WHERE id = :id")
    suspend fun getById(id: Long): FoodItemEntity?

    @Query("SELECT * FROM food_item WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodItemEntity?
}
