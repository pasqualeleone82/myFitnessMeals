package com.myfitnessmeals.app.data.repository

import androidx.room.withTransaction
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.DailySummaryEntity
import com.myfitnessmeals.app.data.local.FitnessDailyEntity
import com.myfitnessmeals.app.domain.model.ProviderType

class LocalFitnessRepository(
    private val db: AppDatabase,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun upsertDailyFitness(
        localDate: String,
        provider: ProviderType,
        steps: Int,
        activeKcal: Double,
        workoutMinutes: Int,
        syncStatus: String,
        lastSyncAt: Long,
    ) {
        require(steps >= 0) { "Fitness steps must be non-negative" }
        require(activeKcal >= 0.0) { "Fitness active kcal must be non-negative" }
        require(workoutMinutes >= 0) { "Fitness workout minutes must be non-negative" }

        db.withTransaction {
            db.fitnessDailyDao().upsert(
                FitnessDailyEntity(
                    localDate = localDate,
                    provider = provider.name,
                    steps = steps,
                    activeKcal = activeKcal,
                    workoutMinutes = workoutMinutes,
                    syncStatus = syncStatus,
                    lastSyncAt = lastSyncAt,
                )
            )
            recalculateDailySummaryLocked(localDate)
        }
    }

    suspend fun getDailyFitness(localDate: String): List<FitnessDailyEntity> =
        db.fitnessDailyDao().getForDate(localDate)

    private suspend fun recalculateDailySummaryLocked(localDate: String) {
        val now = nowEpochMillis()
        val totals = db.mealEntryDao().getTotalsForDate(localDate)
        val existing = db.dailySummaryDao().getByDate(localDate)

        val intake = totals.kcalTotal ?: 0.0
        val carbs = totals.carbTotal ?: 0.0
        val fats = totals.fatTotal ?: 0.0
        val proteins = totals.proteinTotal ?: 0.0
        val burned = db.fitnessDailyDao().getActiveKcalForDate(localDate)
        val target = existing?.kcalTarget ?: 0.0

        db.dailySummaryDao().upsert(
            DailySummaryEntity(
                localDate = localDate,
                kcalTarget = target,
                kcalIntake = intake,
                kcalBurned = burned,
                kcalRemaining = target - intake + burned,
                carbTotal = carbs,
                fatTotal = fats,
                proteinTotal = proteins,
                updatedAt = now,
            )
        )
    }
}
