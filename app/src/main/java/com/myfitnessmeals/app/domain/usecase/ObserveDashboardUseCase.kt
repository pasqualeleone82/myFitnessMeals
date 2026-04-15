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
