package com.myfitnessmeals.app.ui.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.model.HistoryDaySnapshot
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex
import com.myfitnessmeals.app.domain.usecase.DeleteMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.GetHistoryMealsForDayUseCase
import com.myfitnessmeals.app.domain.usecase.ObserveHistoryUseCase
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var diaryRepository: LocalDiaryRepository
    private lateinit var foodRepository: LocalFoodRepository
    private lateinit var overrideRepository: LocalOverrideRepository
    private lateinit var fixedToday: LocalDate

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        diaryRepository = LocalDiaryRepository(database)
        foodRepository = LocalFoodRepository(foodDao = database.foodDao(), offCatalogClient = null)
        overrideRepository = LocalOverrideRepository(database.nutritionOverrideDao())
        fixedToday = LocalDate.of(2026, 4, 14)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    @Test
    fun swipeRightAndLeft_obeyBoundaries_withDeterministicOldestAndToday() = runTest {
        val fixedDays = buildFixedDays(fixedToday)
        val viewModel = buildViewModel(
            swipeDebounceMs = 0L,
            historyLoader = { fixedDays },
        )
        advanceUntilIdle()

        assertEquals("2026-04-14", viewModel.uiState.value.selectedDate)

        viewModel.onSwipeRight()
        advanceUntilIdle()
        assertEquals("2026-04-13", viewModel.uiState.value.selectedDate)

        repeat(200) {
            viewModel.onSwipeRight()
        }
        advanceUntilIdle()

        val oldestExpected = fixedToday.minusDays(89).toString()
        assertEquals(oldestExpected, viewModel.uiState.value.selectedDate)

        viewModel.onSwipeRight()
        advanceUntilIdle()
        assertEquals(oldestExpected, viewModel.uiState.value.selectedDate)

        repeat(200) {
            viewModel.onSwipeLeft()
        }
        advanceUntilIdle()
        assertEquals("2026-04-14", viewModel.uiState.value.selectedDate)

        viewModel.onSwipeLeft()
        advanceUntilIdle()
        assertEquals("2026-04-14", viewModel.uiState.value.selectedDate)
    }

    @Test
    fun rapidSwipes_areDebounced_toAvoidStateCorruption() = runTest {
        var nowMs = 1_000L
        val fixedDays = buildFixedDays(fixedToday)
        val viewModel = buildViewModel(
            swipeDebounceMs = 500L,
            historyLoader = { fixedDays },
            nowEpochMillis = { nowMs },
        )
        advanceUntilIdle()

        viewModel.onSwipeRight()
        advanceUntilIdle()
        assertEquals("2026-04-13", viewModel.uiState.value.selectedDate)

        viewModel.onSwipeRight()
        advanceUntilIdle()
        assertEquals("2026-04-13", viewModel.uiState.value.selectedDate)

        nowMs += 500L
        viewModel.onSwipeRight()
        advanceUntilIdle()
        assertEquals("2026-04-12", viewModel.uiState.value.selectedDate)
    }

    @Test
    fun boundaryNoopSwipe_doesNotConsumeDebounceWindow() = runTest {
        var nowMs = 1_000L
        val fixedDays = buildFixedDays(fixedToday)
        val viewModel = buildViewModel(
            swipeDebounceMs = 500L,
            historyLoader = { fixedDays },
            nowEpochMillis = { nowMs },
        )
        advanceUntilIdle()

        // Left swipe at today boundary is a no-op and must not block the next valid swipe.
        viewModel.onSwipeLeft()
        advanceUntilIdle()
        assertEquals("2026-04-14", viewModel.uiState.value.selectedDate)

        viewModel.onSwipeRight()
        advanceUntilIdle()
        assertEquals("2026-04-13", viewModel.uiState.value.selectedDate)
    }

    @Test
    fun overlappingLoads_onlyLatestSelectionMutatesMealsState() = runTest {
        val previousDayGate = CompletableDeferred<Unit>()
        val fixedDays = buildFixedDays(fixedToday)
        val viewModel = buildViewModel(
            swipeDebounceMs = 0L,
            historyLoader = { fixedDays },
            mealsLoader = { localDate ->
                when (localDate) {
                    "2026-04-13" -> {
                        previousDayGate.await()
                        listOf(fakeMeal(mealEntryId = 13L, foodName = "Previous Day Meal"))
                    }

                    "2026-04-14" -> listOf(fakeMeal(mealEntryId = 14L, foodName = "Today Meal"))
                    else -> emptyList()
                }
            },
        )
        advanceUntilIdle()

        viewModel.onSwipeRight()
        viewModel.onSwipeLeft()
        advanceUntilIdle()

        assertEquals("2026-04-14", viewModel.uiState.value.selectedDate)
        assertEquals(14L, viewModel.uiState.value.mealsForSelectedDay.single().mealEntryId)

        previousDayGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("2026-04-14", viewModel.uiState.value.selectedDate)
        assertEquals(14L, viewModel.uiState.value.mealsForSelectedDay.single().mealEntryId)
    }

    @Test
    fun addEditDeleteEvents_refreshMealsAndTotals_forSelectedDay() = runTest {
        val today = fixedToday.toString()
        var kcalIntakeToday = 0.0
        var mealsToday = emptyList<HistoryMealCard>()
        val viewModel = buildViewModel(
            swipeDebounceMs = 0L,
            historyLoader = {
                buildFixedDays(
                    today = fixedToday,
                    kcalByDate = mapOf(today to kcalIntakeToday),
                )
            },
            mealsLoader = { localDate -> if (localDate == today) mealsToday else emptyList() },
            deleteMealAction = { mealEntryId ->
                mealsToday = mealsToday.filterNot { it.mealEntryId == mealEntryId }
                kcalIntakeToday = mealsToday.sumOf { it.kcal }
            },
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.mealsForSelectedDay.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.selectedDay?.kcalIntake ?: -1.0, 0.001)

        mealsToday = listOf(fakeMeal(mealEntryId = 1L, foodName = "Rice", kcal = 100.0))
        kcalIntakeToday = 100.0

        viewModel.onMealAdded()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.mealsForSelectedDay.size)
        assertEquals(100.0, viewModel.uiState.value.selectedDay?.kcalIntake ?: -1.0, 0.001)

        mealsToday = listOf(fakeMeal(mealEntryId = 1L, foodName = "Rice", kcal = 250.0))
        kcalIntakeToday = 250.0

        viewModel.onMealEdited()
        advanceUntilIdle()
        assertEquals(250.0, viewModel.uiState.value.selectedDay?.kcalIntake ?: -1.0, 0.001)

        viewModel.onDeleteMealConfirmed(1L)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.mealsForSelectedDay.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.selectedDay?.kcalIntake ?: -1.0, 0.001)
    }

    private fun buildViewModel(
        swipeDebounceMs: Long,
        historyLoader: (suspend (Int) -> List<HistoryDaySnapshot>)? = null,
        nowEpochMillis: () -> Long = { 0L },
        mealsLoader: (suspend (String) -> List<HistoryMealCard>)? = null,
        deleteMealAction: (suspend (Long) -> Unit)? = null,
    ): HistoryViewModel {
        val observeHistoryUseCase = ObserveHistoryUseCase(
            diaryRepository = diaryRepository,
            settingsRepository = InMemorySettingsRepository(defaultSettings()),
            nowDateProvider = { fixedToday },
        )
        val getHistoryMealsForDayUseCase = GetHistoryMealsForDayUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            overrideRepository = overrideRepository,
        )

        return HistoryViewModel(
            observeHistoryUseCase = observeHistoryUseCase,
            getHistoryMealsForDayUseCase = getHistoryMealsForDayUseCase,
            deleteMealEntryUseCase = DeleteMealEntryUseCase(diaryRepository),
            historyLoader = historyLoader ?: { days -> observeHistoryUseCase(days).first() },
            mealsLoader = mealsLoader ?: { localDate -> getHistoryMealsForDayUseCase(localDate) },
            deleteMealAction = deleteMealAction ?: { mealEntryId ->
                DeleteMealEntryUseCase(diaryRepository)(mealEntryId)
                Unit
            },
            swipeDebounceMs = swipeDebounceMs,
            nowEpochMillis = nowEpochMillis,
        )
    }

    private fun fakeMeal(mealEntryId: Long, foodName: String, kcal: Double = 100.0): HistoryMealCard {
        return HistoryMealCard(
            mealEntryId = mealEntryId,
            foodId = 1L,
            foodName = foodName,
            brand = null,
            mealType = MealType.LUNCH,
            quantityValue = 100.0,
            quantityUnit = "g",
            kcal = kcal,
            protein = 10.0,
            carbs = 10.0,
            fat = 10.0,
            saturatedFat = null,
            sugar = null,
            iron = null,
            calcium = null,
            magnesium = null,
            zinc = null,
            vitaminC = null,
            vitaminD = null,
            vitaminB12 = null,
            isOverridden = false,
            sourceLabel = "OFF",
        )
    }

    private fun buildFixedDays(
        today: LocalDate,
        kcalByDate: Map<String, Double> = emptyMap(),
        days: Int = 90,
    ): List<HistoryDaySnapshot> {
        return (0 until days).map { offset ->
            val date = today.minusDays(offset.toLong()).toString()
            val intake = kcalByDate[date] ?: 0.0
            HistoryDaySnapshot(
                localDate = date,
                kcalTarget = 2200.0,
                kcalIntake = intake,
                kcalBurned = 0.0,
                kcalRemaining = 2200.0 - intake,
                carbGrams = 0.0,
                fatGrams = 0.0,
                proteinGrams = 0.0,
                saturatedFatGrams = 0.0,
                sugarGrams = 0.0,
                ironMg = 0.0,
                calciumMg = 0.0,
                magnesiumMg = 0.0,
                zincMg = 0.0,
                vitaminCMg = 0.0,
                vitaminDMcg = 0.0,
                vitaminB12Mcg = 0.0,
            )
        }
    }

    private suspend fun seedFood(): Long {
        return foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "history-vm-food-1",
                source = "CACHE",
                name = "Rice",
                brand = "BrandA",
                barcode = "8990000000501",
                kcal100 = 100.0,
                carb100 = 10.0,
                fat100 = 3.0,
                protein100 = 5.0,
                lastSyncedAt = System.currentTimeMillis(),
            )
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
