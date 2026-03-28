package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.data.local.FitnessDailyDao
import com.myfitnessmeals.app.data.local.FitnessDailyEntity
import com.myfitnessmeals.app.domain.model.ProviderType

class LocalFitnessRepository(
    private val fitnessDailyDao: FitnessDailyDao,
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

        fitnessDailyDao.upsert(
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
    }

    suspend fun getDailyFitness(localDate: String): List<FitnessDailyEntity> =
        fitnessDailyDao.getForDate(localDate)
}
