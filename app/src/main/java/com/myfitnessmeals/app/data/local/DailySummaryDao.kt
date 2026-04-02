package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailySummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summary WHERE local_date = :localDate")
    suspend fun getByDate(localDate: String): DailySummaryEntity?

    @Query(
        """
        SELECT *
        FROM daily_summary
        WHERE local_date BETWEEN :startDateInclusive AND :endDateInclusive
        ORDER BY local_date DESC
        """
    )
    suspend fun getByDateRange(
        startDateInclusive: String,
        endDateInclusive: String,
    ): List<DailySummaryEntity>
}
