package com.myfitnessmeals.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_date")
    val localDate: String,
    @ColumnInfo(name = "kcal_target")
    val kcalTarget: Double,
    @ColumnInfo(name = "kcal_intake")
    val kcalIntake: Double,
    @ColumnInfo(name = "kcal_burned")
    val kcalBurned: Double,
    @ColumnInfo(name = "kcal_remaining")
    val kcalRemaining: Double,
    @ColumnInfo(name = "carb_total")
    val carbTotal: Double,
    @ColumnInfo(name = "fat_total")
    val fatTotal: Double,
    @ColumnInfo(name = "protein_total")
    val proteinTotal: Double,
    @ColumnInfo(name = "saturated_fat_total")
    val saturatedFatTotal: Double? = null,
    @ColumnInfo(name = "sugar_total")
    val sugarTotal: Double? = null,
    @ColumnInfo(name = "iron_total")
    val ironTotal: Double? = null,
    @ColumnInfo(name = "calcium_total")
    val calciumTotal: Double? = null,
    @ColumnInfo(name = "magnesium_total")
    val magnesiumTotal: Double? = null,
    @ColumnInfo(name = "zinc_total")
    val zincTotal: Double? = null,
    @ColumnInfo(name = "vitamin_c_total")
    val vitaminCTotal: Double? = null,
    @ColumnInfo(name = "vitamin_d_total")
    val vitaminDTotal: Double? = null,
    @ColumnInfo(name = "vitamin_b12_total")
    val vitaminB12Total: Double? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
