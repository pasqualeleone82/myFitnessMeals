package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NutritionOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: NutritionOverrideEntity)

    @Query("SELECT * FROM nutrition_override WHERE food_id = :foodId")
    suspend fun getByFoodId(foodId: Long): NutritionOverrideEntity?

    @Query("DELETE FROM nutrition_override WHERE food_id = :foodId")
    suspend fun deleteByFoodId(foodId: Long): Int
}
