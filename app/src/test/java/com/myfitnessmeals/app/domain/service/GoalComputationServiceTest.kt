package com.myfitnessmeals.app.domain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalComputationServiceTest {
    private val service = GoalComputationService()

    @Test
    fun computeTargetKcal_returnsReasonableValueForMaintain() {
        val target = service.computeTargetKcal(
            GoalProfileInput(
                age = 30,
                heightCm = 180.0,
                weightKg = 80.0,
                sex = Sex.MALE,
                activityLevel = ActivityLevel.MODERATE,
                goalType = GoalType.MAINTAIN,
            )
        )

        assertTrue(target > 2000.0)
        assertTrue(target < 3200.0)
    }

    @Test
    fun validateMacroSplit_acceptsOnlyHundredPercent() {
        assertTrue(service.validateMacroSplit(carbPct = 40, fatPct = 30, proteinPct = 30))
        assertFalse(service.validateMacroSplit(carbPct = 40, fatPct = 30, proteinPct = 20))
        assertFalse(service.validateMacroSplit(carbPct = -1, fatPct = 51, proteinPct = 50))
    }
}
