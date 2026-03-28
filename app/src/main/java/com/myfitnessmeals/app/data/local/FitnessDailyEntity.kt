package com.myfitnessmeals.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fitness_daily",
    primaryKeys = ["local_date", "provider"],
    indices = [Index(value = ["local_date"], unique = false)],
)
data class FitnessDailyEntity(
    @ColumnInfo(name = "local_date")
    val localDate: String,
    val provider: String,
    val steps: Int,
    @ColumnInfo(name = "active_kcal")
    val activeKcal: Double,
    @ColumnInfo(name = "workout_minutes")
    val workoutMinutes: Int,
    @ColumnInfo(name = "last_sync_at")
    val lastSyncAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String,
)
