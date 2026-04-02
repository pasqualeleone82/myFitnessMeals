package com.myfitnessmeals.app.data.repository

import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex

enum class AppThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserSettings(
    val onboardingCompleted: Boolean,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val sex: Sex,
    val activityLevel: ActivityLevel,
    val goalType: GoalType,
    val targetKcal: Double,
    val carbPct: Int,
    val fatPct: Int,
    val proteinPct: Int,
    val themePreference: AppThemePreference,
)

interface UserSettingsRepository {
    fun getSettings(): UserSettings

    fun saveSettings(settings: UserSettings)
}
