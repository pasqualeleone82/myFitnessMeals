package com.myfitnessmeals.app.ui.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalComputationService
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex
import com.myfitnessmeals.app.domain.usecase.DeleteAllUserDataUseCase
import com.myfitnessmeals.app.domain.usecase.ExportUserDataUseCase
import com.myfitnessmeals.app.integration.garmin.GarminIntegrationService
import com.myfitnessmeals.app.security.OAuthToken
import com.myfitnessmeals.app.security.OAuthTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun profileEdits_recomputeEstimateLive() = runTest {
        val repository = InMemorySettingsRepository(defaultSettings())
        val goalComputationService = GoalComputationService()
        val viewModel = buildViewModel(repository, goalComputationService)

        advanceUntilIdle()

        val initialTarget = viewModel.uiState.value.computedTargetKcal
        assertNotNull(initialTarget)

        viewModel.onWeightChanged("82")
        advanceUntilIdle()

        val updatedTarget = viewModel.uiState.value.computedTargetKcal
        assertNotNull(updatedTarget)
        assertTrue(updatedTarget != initialTarget)
    }

    @Test
    fun save_persistsEditedProfileAndReloadsWithSameTarget() = runTest {
        val repository = InMemorySettingsRepository(defaultSettings())
        val goalComputationService = GoalComputationService()
        val viewModel = buildViewModel(repository, goalComputationService)

        advanceUntilIdle()

        viewModel.onAgeChanged("35")
        viewModel.onWeightChanged("81.5")
        viewModel.onActivityChanged(ActivityLevel.ACTIVE)
        viewModel.onGoalChanged(GoalType.LOSE)
        advanceUntilIdle()

        val shownTarget = viewModel.uiState.value.computedTargetKcal
        assertNotNull(shownTarget)

        viewModel.saveSettings()
        advanceUntilIdle()

        val saved = repository.getSettings()
        assertEquals(35, saved.age)
        assertEquals(81.5, saved.weightKg, 0.001)
        assertEquals(ActivityLevel.ACTIVE, saved.activityLevel)
        assertEquals(GoalType.LOSE, saved.goalType)
        assertEquals(shownTarget!!, saved.targetKcal, 0.001)

        val reloadedViewModel = buildViewModel(repository, goalComputationService)
        advanceUntilIdle()

        val reloadedState = reloadedViewModel.uiState.value
        assertEquals("35", reloadedState.ageInput)
        assertEquals("81.5", reloadedState.weightInput)
        assertEquals(ActivityLevel.ACTIVE, reloadedState.activityLevel)
        assertEquals(GoalType.LOSE, reloadedState.goalType)
        assertEquals(saved.targetKcal, reloadedState.computedTargetKcal!!, 0.001)
    }

    private fun buildViewModel(
        repository: UserSettingsRepository,
        goalComputationService: GoalComputationService,
    ): SettingsViewModel {
        val providerRepository = LocalProviderConnectionRepository(database.providerConnectionDao())
        val fitnessRepository = LocalFitnessRepository(database)
        val tokenStore = InMemoryOAuthTokenStore()
        val garminIntegrationService = GarminIntegrationService(
            providerConnectionRepository = providerRepository,
            fitnessRepository = fitnessRepository,
            tokenStore = tokenStore,
        )
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val exportUseCase = ExportUserDataUseCase(
            appContext = appContext,
            database = database,
            settingsRepository = repository,
        )
        val deleteUseCase = DeleteAllUserDataUseCase(
            database = database,
            settingsRepository = repository,
            providerConnectionRepository = providerRepository,
            tokenStore = tokenStore,
        )

        return SettingsViewModel(
            settingsRepository = repository,
            goalComputationService = goalComputationService,
            garminIntegrationService = garminIntegrationService,
            exportUserDataUseCase = exportUseCase,
            deleteAllUserDataUseCase = deleteUseCase,
        )
    }

    private fun defaultSettings(): UserSettings {
        return UserSettings(
            onboardingCompleted = true,
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

    private class InMemoryOAuthTokenStore : OAuthTokenStore {
        private val store = mutableMapOf<String, OAuthToken>()

        override fun putToken(tokenRef: String, token: OAuthToken) {
            store[tokenRef] = token
        }

        override fun getToken(tokenRef: String): OAuthToken? = store[tokenRef]

        override fun removeToken(tokenRef: String) {
            store.remove(tokenRef)
        }

        override fun removeAllTokens() {
            store.clear()
        }
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
