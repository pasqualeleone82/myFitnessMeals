package com.myfitnessmeals.app.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObserveHistoryUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var diaryRepository: LocalDiaryRepository
    private lateinit var settingsRepository: InMemorySettingsRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        diaryRepository = LocalDiaryRepository(database)
        settingsRepository = InMemorySettingsRepository(
            UserSettings(
                onboardingCompleted = true,
                age = 30,
                heightCm = 175.0,
                weightKg = 80.0,
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
    fun invoke_returnsNinetyDaysNewestFirst_withZeroFilledEmptyDays() = runTest {
        val date = LocalDate.of(2026, 4, 14)
        diaryRepository.setDailyTarget(localDate = date.toString(), kcalTarget = 2000.0)

        val useCase = ObserveHistoryUseCase(
            diaryRepository = diaryRepository,
            settingsRepository = settingsRepository,
            nowDateProvider = { date },
        )

        val history = useCase(days = 90).first()

        assertEquals(90, history.size)
        assertEquals("2026-04-14", history.first().localDate)
        assertEquals("2026-01-15", history.last().localDate)
        assertEquals(2000.0, history.first().kcalTarget, 0.001)
        assertEquals(0.0, history[10].kcalIntake, 0.001)
        assertEquals(0.0, history[10].saturatedFatGrams ?: -1.0, 0.001)
        assertEquals(0.0, history[10].vitaminB12Mcg ?: -1.0, 0.001)
        assertEquals(0, history[10].meals.size)
    }

    @Test
    fun invoke_keepsNullEnrichedNutrients_whenDaySummaryExistsButEnrichedDataUnavailable() = runTest {
        val date = LocalDate.of(2026, 4, 14)
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "hist-food",
                source = "CACHE",
                name = "Pasta",
                brand = "Test",
                barcode = "8990000000010",
                kcal100 = 350.0,
                carb100 = 70.0,
                fat100 = 1.5,
                protein100 = 12.0,
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
                kcalTotal = 350.0,
                carbTotal = 70.0,
                fatTotal = 1.5,
                proteinTotal = 12.0,
                saturatedFatTotal = null,
                sugarTotal = null,
                ironTotal = null,
                calciumTotal = null,
                magnesiumTotal = null,
                zincTotal = null,
                vitaminCTotal = null,
                vitaminDTotal = null,
                vitaminB12Total = null,
            )
        )

        val useCase = ObserveHistoryUseCase(
            diaryRepository = diaryRepository,
            settingsRepository = settingsRepository,
            nowDateProvider = { date },
        )

        val today = useCase(days = 1).first().single()

        assertEquals(350.0, today.kcalIntake, 0.001)
        assertNull(today.saturatedFatGrams)
        assertNull(today.sugarGrams)
        assertNull(today.vitaminB12Mcg)
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
