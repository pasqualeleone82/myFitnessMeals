package com.myfitnessmeals.app.ui.onboarding

import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalComputationService
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
    private val goalComputationService = GoalComputationService()

    @Test
    fun estimateDisplay_roundsToNearestInteger_notTruncated() {
        assertEquals(2350, roundEstimateKcalForDisplay(2350.4))
        assertEquals(2351, roundEstimateKcalForDisplay(2350.5))
    }

    @Test
    fun estimate_isComputedFromProfileOnInit() {
        val repository = InMemorySettingsRepository(
            UserSettings(
                onboardingCompleted = false,
                age = 28,
                heightCm = 178.0,
                weightKg = 74.0,
                sex = Sex.MALE,
                activityLevel = ActivityLevel.MODERATE,
                goalType = GoalType.MAINTAIN,
                targetKcal = 9999.0,
                carbPct = 40,
                fatPct = 30,
                proteinPct = 30,
                themePreference = AppThemePreference.SYSTEM,
            )
        )

        val viewModel = OnboardingViewModel(repository, goalComputationService)
        val state = viewModel.uiState.value

        val expected = goalComputationService.computeTargetKcal(
            com.myfitnessmeals.app.domain.service.GoalProfileInput(
                age = 28,
                heightCm = 178.0,
                weightKg = 74.0,
                sex = Sex.MALE,
                activityLevel = ActivityLevel.MODERATE,
                goalType = GoalType.MAINTAIN,
            )
        )

        assertNotNull(state.computedTargetKcal)
        assertEquals(expected, state.computedTargetKcal!!, 0.001)
    }

    @Test
    fun estimate_updatesReactivelyAndClearsWhenProfileInvalid() {
        val repository = InMemorySettingsRepository(defaultSettings())
        val viewModel = OnboardingViewModel(repository, goalComputationService)

        val initialTarget = viewModel.uiState.value.computedTargetKcal
        assertNotNull(initialTarget)

        viewModel.onWeightChanged("80")
        val updatedTarget = viewModel.uiState.value.computedTargetKcal

        assertNotNull(updatedTarget)
        assertTrue(updatedTarget != initialTarget)

        viewModel.onAgeChanged("invalid")

        assertNull(viewModel.uiState.value.computedTargetKcal)
    }

    @Test
    fun completeOnboarding_persistsSameTargetShownInState() {
        val repository = InMemorySettingsRepository(defaultSettings())
        val viewModel = OnboardingViewModel(repository, goalComputationService)

        viewModel.onWeightChanged("85")
        val shownTarget = viewModel.uiState.value.computedTargetKcal

        assertNotNull(shownTarget)

        viewModel.completeOnboarding()

        val saved = repository.getSettings()
        assertEquals(shownTarget!!, saved.targetKcal, 0.001)
        assertTrue(saved.onboardingCompleted)
    }

    private fun defaultSettings(): UserSettings {
        return UserSettings(
            onboardingCompleted = false,
            age = 30,
            heightCm = 175.0,
            weightKg = 75.0,
            sex = Sex.MALE,
            activityLevel = ActivityLevel.MODERATE,
            goalType = GoalType.MAINTAIN,
            targetKcal = 2200.0,
            carbPct = 40,
            fatPct = 30,
            proteinPct = 30,
            themePreference = AppThemePreference.SYSTEM,
        )
    }

    private class InMemorySettingsRepository(
        private var settings: UserSettings,
    ) : UserSettingsRepository {
        override fun getSettings(): UserSettings = settings

        override fun saveSettings(settings: UserSettings) {
            this.settings = settings
        }

        override fun clearAll() {
            settings = settings.copy(onboardingCompleted = false)
        }
    }
}
