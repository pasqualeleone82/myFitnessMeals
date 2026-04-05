package com.myfitnessmeals.app.data.repository

import android.content.Context
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex

class LocalUserSettingsRepository(context: Context) : UserSettingsRepository {
    private val prefs = context.getSharedPreferences("user_settings", Context.MODE_PRIVATE)

    override fun getSettings(): UserSettings {
        return UserSettings(
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false),
            age = prefs.getInt(KEY_AGE, 30),
            heightCm = prefs.getFloat(KEY_HEIGHT_CM, 175f).toDouble(),
            weightKg = prefs.getFloat(KEY_WEIGHT_KG, 75f).toDouble(),
            sex = prefs.getEnumOrDefault(KEY_SEX, Sex.MALE),
            activityLevel = prefs.getEnumOrDefault(KEY_ACTIVITY_LEVEL, ActivityLevel.MODERATE),
            goalType = prefs.getEnumOrDefault(KEY_GOAL_TYPE, GoalType.MAINTAIN),
            targetKcal = prefs.getFloat(KEY_TARGET_KCAL, 2200f).toDouble(),
            carbPct = prefs.getInt(KEY_CARB_PCT, 40),
            fatPct = prefs.getInt(KEY_FAT_PCT, 30),
            proteinPct = prefs.getInt(KEY_PROTEIN_PCT, 30),
            themePreference = prefs.getEnumOrDefault(KEY_THEME_PREFERENCE, AppThemePreference.SYSTEM),
        )
    }

    override fun saveSettings(settings: UserSettings) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, settings.onboardingCompleted)
            .putInt(KEY_AGE, settings.age)
            .putFloat(KEY_HEIGHT_CM, settings.heightCm.toFloat())
            .putFloat(KEY_WEIGHT_KG, settings.weightKg.toFloat())
            .putString(KEY_SEX, settings.sex.name)
            .putString(KEY_ACTIVITY_LEVEL, settings.activityLevel.name)
            .putString(KEY_GOAL_TYPE, settings.goalType.name)
            .putFloat(KEY_TARGET_KCAL, settings.targetKcal.toFloat())
            .putInt(KEY_CARB_PCT, settings.carbPct)
            .putInt(KEY_FAT_PCT, settings.fatPct)
            .putInt(KEY_PROTEIN_PCT, settings.proteinPct)
                .putString(KEY_THEME_PREFERENCE, settings.themePreference.name)
            .apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_AGE = "age"
        const val KEY_HEIGHT_CM = "height_cm"
        const val KEY_WEIGHT_KG = "weight_kg"
        const val KEY_SEX = "sex"
        const val KEY_ACTIVITY_LEVEL = "activity_level"
        const val KEY_GOAL_TYPE = "goal_type"
        const val KEY_TARGET_KCAL = "target_kcal"
        const val KEY_CARB_PCT = "carb_pct"
        const val KEY_FAT_PCT = "fat_pct"
        const val KEY_PROTEIN_PCT = "protein_pct"
        const val KEY_THEME_PREFERENCE = "theme_preference"
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.getEnumOrDefault(
    key: String,
    default: T,
): T {
    val raw = getString(key, null)?.trim().orEmpty()
    if (raw.isEmpty()) {
        return default
    }
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}
