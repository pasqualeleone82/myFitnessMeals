package com.myfitnessmeals.app.domain.usecase

import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import java.time.LocalDate

data class DashboardSnapshot(
    val localDate: String,
    val kcalTarget: Double,
    val kcalIntake: Double,
    val kcalBurned: Double,
    val kcalRemaining: Double,
    val carbGrams: Double,
    val fatGrams: Double,
    val proteinGrams: Double,
    val carbPct: Int,
    val fatPct: Int,
    val proteinPct: Int,
    val steps: Int,
    val activeKcal: Double,
    val workoutMinutes: Int,
    val latestWeightKg: Double,
)

data class HistoryDaySnapshot(
    val localDate: String,
    val kcalTarget: Double,
    val kcalIntake: Double,
    val kcalBurned: Double,
    val kcalRemaining: Double,
    val carbGrams: Double,
    val fatGrams: Double,
    val proteinGrams: Double,
)

class ObserveDashboardUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val fitnessRepository: LocalFitnessRepository,
    private val settingsRepository: UserSettingsRepository,
    private val nowDateProvider: () -> LocalDate = { LocalDate.now() },
) {
    suspend operator fun invoke(date: LocalDate = nowDateProvider()): DashboardSnapshot {
        val localDate = date.toString()
        val settings = settingsRepository.getSettings()
        val summary = diaryRepository.getDailySummary(localDate)
        val fitness = fitnessRepository.getDailyFitness(localDate)

        val target = summary?.kcalTarget?.takeIf { it > 0.0 } ?: settings.targetKcal
        val intake = summary?.kcalIntake ?: 0.0
        val burned = summary?.kcalBurned ?: fitness.sumOf { it.activeKcal }
        val carbs = summary?.carbTotal ?: 0.0
        val fats = summary?.fatTotal ?: 0.0
        val proteins = summary?.proteinTotal ?: 0.0

        val macroTotal = carbs + fats + proteins
        val carbPct = macroPercent(carbs, macroTotal)
        val fatPct = macroPercent(fats, macroTotal)
        val proteinPct = macroPercent(proteins, macroTotal)

        return DashboardSnapshot(
            localDate = localDate,
            kcalTarget = target,
            kcalIntake = intake,
            kcalBurned = burned,
            kcalRemaining = target - intake + burned,
            carbGrams = carbs,
            fatGrams = fats,
            proteinGrams = proteins,
            carbPct = carbPct,
            fatPct = fatPct,
            proteinPct = proteinPct,
            steps = fitness.sumOf { it.steps },
            activeKcal = fitness.sumOf { it.activeKcal },
            workoutMinutes = fitness.sumOf { it.workoutMinutes },
            latestWeightKg = settings.weightKg,
        )
    }

    private fun macroPercent(value: Double, total: Double): Int {
        if (total <= 0.0) {
            return 0
        }
        return ((value / total) * 100.0).toInt()
    }
}

class ObserveHistoryUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val settingsRepository: UserSettingsRepository,
    private val nowDateProvider: () -> LocalDate = { LocalDate.now() },
) {
    suspend operator fun invoke(days: Int = 90): List<HistoryDaySnapshot> {
        require(days >= 1) { "History days must be at least 1" }

        val endDate = nowDateProvider()
        val startDate = endDate.minusDays(days.toLong() - 1L)
        val settingsTarget = settingsRepository.getSettings().targetKcal

        val summaries = diaryRepository
            .getDailySummariesInRange(startDate.toString(), endDate.toString())
            .associateBy { it.localDate }

        return (0 until days).map { index ->
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
            )
        }
    }
}
