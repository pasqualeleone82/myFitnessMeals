package com.myfitnessmeals.app.data.repository

import androidx.room.withTransaction
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.DailySummaryEntity
import com.myfitnessmeals.app.data.local.FitnessDailyEntity
import com.myfitnessmeals.app.data.local.MealEntryEntity
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ProviderType

class LocalDiaryRepository(
    private val db: AppDatabase,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun addMealEntry(entry: NewMealEntry): Long {
        require(entry.quantityValue > 0) { "Meal quantity must be positive" }
        val now = nowEpochMillis()

        return db.withTransaction {
            val id = db.mealEntryDao().insert(
                MealEntryEntity(
                    localDate = entry.localDate,
                    timezoneOffsetMin = entry.timezoneOffsetMin,
                    mealType = entry.mealType.name,
                    foodId = entry.foodId,
                    quantityValue = entry.quantityValue,
                    quantityUnit = entry.quantityUnit,
                    resolvedSource = entry.resolvedSource.name,
                    kcalTotal = entry.kcalTotal,
                    carbTotal = entry.carbTotal,
                    fatTotal = entry.fatTotal,
                    proteinTotal = entry.proteinTotal,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            recalculateDailySummaryLocked(entry.localDate)
            id
        }
    }

    suspend fun updateMealEntry(entryId: Long, entry: NewMealEntry): Boolean {
        require(entry.quantityValue > 0) { "Meal quantity must be positive" }
        val now = nowEpochMillis()

        return db.withTransaction {
            val existing = db.mealEntryDao().getById(entryId) ?: return@withTransaction false
            val previousLocalDate = existing.localDate
            val updated = db.mealEntryDao().update(
                existing.copy(
                    localDate = entry.localDate,
                    timezoneOffsetMin = entry.timezoneOffsetMin,
                    mealType = entry.mealType.name,
                    foodId = entry.foodId,
                    quantityValue = entry.quantityValue,
                    quantityUnit = entry.quantityUnit,
                    resolvedSource = entry.resolvedSource.name,
                    kcalTotal = entry.kcalTotal,
                    carbTotal = entry.carbTotal,
                    fatTotal = entry.fatTotal,
                    proteinTotal = entry.proteinTotal,
                    updatedAt = now,
                )
            ) > 0

            if (updated) {
                recalculateDailySummaryLocked(previousLocalDate)
                if (entry.localDate != previousLocalDate) {
                    recalculateDailySummaryLocked(entry.localDate)
                }
            }

            updated
        }
    }

    suspend fun deleteMealEntry(entryId: Long): Boolean {
        return db.withTransaction {
            val actualLocalDate = db.mealEntryDao().getById(entryId)?.localDate
            val deleted = db.mealEntryDao().deleteById(entryId) > 0
            if (deleted && actualLocalDate != null) {
                recalculateDailySummaryLocked(actualLocalDate)
            }
            deleted
        }
    }

    @Deprecated(
        message = "localDate parameter is ignored. Use deleteMealEntry(entryId) instead.",
        replaceWith = ReplaceWith("deleteMealEntry(entryId)"),
    )
    suspend fun deleteMealEntry(entryId: Long, @Suppress("UNUSED_PARAMETER") localDate: String): Boolean =
        deleteMealEntry(entryId)

    suspend fun setDailyTarget(localDate: String, kcalTarget: Double) {
        db.withTransaction {
            val existing = db.dailySummaryDao().getByDate(localDate)
            val now = nowEpochMillis()
            val summary = if (existing == null) {
                DailySummaryEntity(
                    localDate = localDate,
                    kcalTarget = kcalTarget,
                    kcalIntake = 0.0,
                    kcalBurned = 0.0,
                    kcalRemaining = kcalTarget,
                    carbTotal = 0.0,
                    fatTotal = 0.0,
                    proteinTotal = 0.0,
                    updatedAt = now,
                )
            } else {
                existing.copy(
                    kcalTarget = kcalTarget,
                    kcalRemaining = kcalTarget - existing.kcalIntake + existing.kcalBurned,
                    updatedAt = now,
                )
            }
            db.dailySummaryDao().upsert(summary)
        }
    }

    suspend fun upsertFitnessDaily(
        localDate: String,
        provider: ProviderType,
        steps: Int,
        activeKcal: Double,
        workoutMinutes: Int,
        syncStatus: String,
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
                    lastSyncAt = nowEpochMillis(),
                    syncStatus = syncStatus,
                )
            )
            recalculateDailySummaryLocked(localDate)
        }
    }

    suspend fun getDailySummary(localDate: String): DailySummaryEntity? = db.dailySummaryDao().getByDate(localDate)

    suspend fun getMealEntries(localDate: String): List<MealEntryEntity> = db.mealEntryDao().getByDate(localDate)

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
