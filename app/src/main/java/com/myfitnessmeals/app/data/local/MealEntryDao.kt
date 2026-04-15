package com.myfitnessmeals.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myfitnessmeals.app.data.local.projection.MealEntryTotals

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

    @Query("SELECT * FROM meal_entry ORDER BY id ASC")
    suspend fun getAll(): List<MealEntryEntity>

    @Query(
        """
        SELECT
            COALESCE(SUM(kcal_total), 0.0) AS kcalTotal,
            COALESCE(SUM(carb_total), 0.0) AS carbTotal,
            COALESCE(SUM(fat_total), 0.0) AS fatTotal,
            COALESCE(SUM(protein_total), 0.0) AS proteinTotal,
            SUM(saturated_fat_total) AS saturatedFatTotal,
            SUM(sugar_total) AS sugarTotal,
            SUM(iron_total) AS ironTotal,
            SUM(calcium_total) AS calciumTotal,
            SUM(magnesium_total) AS magnesiumTotal,
            SUM(zinc_total) AS zincTotal,
            SUM(vitamin_c_total) AS vitaminCTotal,
            SUM(vitamin_d_total) AS vitaminDTotal,
            SUM(vitamin_b12_total) AS vitaminB12Total
        FROM meal_entry
        WHERE local_date = :localDate
        """
    )
    suspend fun getTotalsForDate(localDate: String): MealEntryTotals
}
