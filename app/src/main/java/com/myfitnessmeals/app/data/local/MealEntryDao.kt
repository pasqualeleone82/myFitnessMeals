package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MealEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MealEntryEntity): Long

    @Query("SELECT * FROM meal_entry WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MealEntryEntity?

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entry: MealEntryEntity): Int

    @Query("DELETE FROM meal_entry WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM meal_entry WHERE local_date = :localDate ORDER BY id ASC")
    suspend fun getByDate(localDate: String): List<MealEntryEntity>

    @Query(
        """
        SELECT
            SUM(kcal_total) AS kcalTotal,
            SUM(carb_total) AS carbTotal,
            SUM(fat_total) AS fatTotal,
            SUM(protein_total) AS proteinTotal
        FROM meal_entry
        WHERE local_date = :localDate
        """
    )
    suspend fun getTotalsForDate(localDate: String): MealEntryTotals
}

data class MealEntryTotals(
    val kcalTotal: Double?,
    val carbTotal: Double?,
    val fatTotal: Double?,
    val proteinTotal: Double?,
)
