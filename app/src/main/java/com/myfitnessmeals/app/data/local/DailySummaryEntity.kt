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
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
