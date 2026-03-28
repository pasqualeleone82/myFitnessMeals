package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FitnessDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FitnessDailyEntity)

    @Query("SELECT * FROM fitness_daily WHERE local_date = :localDate")
    suspend fun getForDate(localDate: String): List<FitnessDailyEntity>

    @Query("SELECT COALESCE(SUM(active_kcal), 0) FROM fitness_daily WHERE local_date = :localDate")
    suspend fun getActiveKcalForDate(localDate: String): Double
}
