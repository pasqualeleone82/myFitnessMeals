package com.myfitnessmeals.app.domain.service

enum class Sex {
    MALE,
    FEMALE,
}

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
}

enum class GoalType {
    LOSE,
    MAINTAIN,
    GAIN,
}

data class GoalProfileInput(
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val sex: Sex,
    val activityLevel: ActivityLevel,
    val goalType: GoalType,
)

class GoalComputationService {
    fun computeTargetKcal(input: GoalProfileInput): Double {
        require(input.age in 14..99) { "Age must be between 14 and 99" }
        require(input.heightCm in 120.0..240.0) { "Height must be between 120 and 240 cm" }
        require(input.weightKg in 30.0..300.0) { "Weight must be between 30 and 300 kg" }

        val bmr = if (input.sex == Sex.MALE) {
            10.0 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.age + 5.0
        } else {
            10.0 * input.weightKg + 6.25 * input.heightCm - 5.0 * input.age - 161.0
        }

        val maintenance = bmr * input.activityLevel.factor
        val delta = when (input.goalType) {
            GoalType.LOSE -> -400.0
            GoalType.MAINTAIN -> 0.0
            GoalType.GAIN -> 300.0
        }

        return (maintenance + delta).coerceAtLeast(1200.0)
    }

    fun validateMacroSplit(carbPct: Int, fatPct: Int, proteinPct: Int): Boolean {
        if (carbPct !in 0..100 || fatPct !in 0..100 || proteinPct !in 0..100) {
            return false
        }
        return carbPct + fatPct + proteinPct == 100
    }
}
