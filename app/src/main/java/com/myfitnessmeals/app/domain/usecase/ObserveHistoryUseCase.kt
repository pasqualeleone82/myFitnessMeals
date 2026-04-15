package com.myfitnessmeals.app.domain.usecase

import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.model.HistoryDaySnapshot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ObserveHistoryUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val settingsRepository: UserSettingsRepository,
    private val nowDateProvider: () -> LocalDate = { LocalDate.now() },
) {
    operator fun invoke(days: Int = 90): Flow<List<HistoryDaySnapshot>> = flow {
        require(days >= 1) { "History days must be at least 1" }

        val endDate = nowDateProvider()
        val startDate = endDate.minusDays(days.toLong() - 1L)
        val settingsTarget = settingsRepository.getSettings().targetKcal

        val summaries = diaryRepository
            .getDailySummariesInRange(startDate.toString(), endDate.toString())
            .associateBy { it.localDate }

        val snapshots = (0 until days).map { index ->
            val date = endDate.minusDays(index.toLong())
            val key = date.toString()
            val summary = summaries[key]
            val target = summary?.kcalTarget?.takeIf { it > 0.0 } ?: settingsTarget
            val intake = summary?.kcalIntake ?: 0.0
            val burned = summary?.kcalBurned ?: 0.0

            HistoryDaySnapshot(
                localDate = key,
                kcalTarget = target,
                kcalIntake = intake,
                kcalBurned = burned,
                kcalRemaining = target - intake + burned,
                carbGrams = summary?.carbTotal ?: 0.0,
                fatGrams = summary?.fatTotal ?: 0.0,
                proteinGrams = summary?.proteinTotal ?: 0.0,
                saturatedFatGrams = summary?.saturatedFatTotal ?: if (summary == null) 0.0 else null,
                sugarGrams = summary?.sugarTotal ?: if (summary == null) 0.0 else null,
                ironMg = summary?.ironTotal ?: if (summary == null) 0.0 else null,
                calciumMg = summary?.calciumTotal ?: if (summary == null) 0.0 else null,
                magnesiumMg = summary?.magnesiumTotal ?: if (summary == null) 0.0 else null,
                zincMg = summary?.zincTotal ?: if (summary == null) 0.0 else null,
                vitaminCMg = summary?.vitaminCTotal ?: if (summary == null) 0.0 else null,
                vitaminDMcg = summary?.vitaminDTotal ?: if (summary == null) 0.0 else null,
                vitaminB12Mcg = summary?.vitaminB12Total ?: if (summary == null) 0.0 else null,
                meals = emptyList(),
            )
        }

        emit(snapshots)
    }
}
