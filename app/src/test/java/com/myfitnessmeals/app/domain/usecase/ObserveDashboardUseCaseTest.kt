package com.myfitnessmeals.app.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ProviderType
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObserveDashboardUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var diaryRepository: LocalDiaryRepository
    private lateinit var fitnessRepository: LocalFitnessRepository
    private lateinit var settingsRepository: InMemorySettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        diaryRepository = LocalDiaryRepository(database)
        fitnessRepository = LocalFitnessRepository(database)
        settingsRepository = InMemorySettingsRepository(
            UserSettings(
                onboardingCompleted = true,
                age = 30,
                heightCm = 175.0,
                weightKg = 82.5,
                sex = Sex.MALE,
                activityLevel = ActivityLevel.MODERATE,
                goalType = GoalType.MAINTAIN,
                targetKcal = 2200.0,
                carbPct = 40,
                fatPct = 30,
                proteinPct = 30,
                themePreference = AppThemePreference.SYSTEM,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeDashboard_calculatesRemainingAndMacroPercentages() = runTest {
        val date = LocalDate.of(2026, 4, 1)
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "dash-food",
                source = "CACHE",
                name = "Rice",
                brand = "Test",
                barcode = "8990000000001",
                kcal100 = 130.0,
                carb100 = 28.0,
                fat100 = 0.3,
                protein100 = 2.5,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )

        diaryRepository.addMealEntry(
            NewMealEntry(
                localDate = date.toString(),
                timezoneOffsetMin = 0,
                mealType = MealType.LUNCH,
                foodId = foodId,
                quantityValue = 100.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 600.0,
                carbTotal = 80.0,
                fatTotal = 20.0,
                proteinTotal = 50.0,
            )
        )

        fitnessRepository.upsertDailyFitness(
            localDate = date.toString(),
            provider = ProviderType.GARMIN,
            steps = 9500,
            activeKcal = 350.0,
            workoutMinutes = 55,
            syncStatus = "SUCCESS",
            lastSyncAt = System.currentTimeMillis(),
        )

        val useCase = ObserveDashboardUseCase(
            diaryRepository = diaryRepository,
            fitnessRepository = fitnessRepository,
            settingsRepository = settingsRepository,
            nowDateProvider = { date },
        )

        val snapshot = useCase(date)

        assertEquals(2200.0, snapshot.kcalTarget, 0.001)
        assertEquals(600.0, snapshot.kcalIntake, 0.001)
        assertEquals(350.0, snapshot.kcalBurned, 0.001)
        assertEquals(1950.0, snapshot.kcalRemaining, 0.001)
        assertEquals(53, snapshot.carbPct)
        assertEquals(13, snapshot.fatPct)
        assertEquals(33, snapshot.proteinPct)
        assertEquals(9500, snapshot.steps)
        assertEquals(350.0, snapshot.activeKcal, 0.001)
        assertEquals(55, snapshot.workoutMinutes)
        assertEquals(82.5, snapshot.latestWeightKg, 0.001)
    }

    @Test
    fun observeHistory_returnsAtLeastNinetyDaysWithZeroFilledGaps() = runTest {
        val date = LocalDate.of(2026, 4, 1)
        diaryRepository.setDailyTarget(localDate = date.toString(), kcalTarget = 2000.0)

        val useCase = ObserveHistoryUseCase(
            diaryRepository = diaryRepository,
            settingsRepository = settingsRepository,
            nowDateProvider = { date },
        )

        val history = useCase(days = 90)

        assertEquals(90, history.size)
        assertEquals("2026-04-01", history.first().localDate)
        assertEquals("2026-01-02", history.last().localDate)
        assertEquals(2000.0, history.first().kcalTarget, 0.001)
        assertEquals(0.0, history[10].kcalIntake, 0.001)
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
