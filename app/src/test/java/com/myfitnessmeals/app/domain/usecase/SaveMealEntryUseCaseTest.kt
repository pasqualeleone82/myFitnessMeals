package com.myfitnessmeals.app.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.ResolvedSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveMealEntryUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var foodRepository: LocalFoodRepository
    private lateinit var overrideRepository: LocalOverrideRepository
    private lateinit var diaryRepository: LocalDiaryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        foodRepository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = null,
            nowEpochMillis = { 1_700_000_000_000L },
        )
        overrideRepository = LocalOverrideRepository(database.nutritionOverrideDao())
        diaryRepository = LocalDiaryRepository(
            db = database,
            nowEpochMillis = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveMealEntry_computesAndPersistsEnrichedNutrientsIntoDailySummary() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "save-meal-entry-test-1",
                source = "CACHE",
                name = "Lentils",
                brand = "Brand",
                barcode = "1000000000999",
                kcal100 = 116.0,
                carb100 = 20.0,
                fat100 = 0.4,
                protein100 = 9.0,
                saturatedFat100 = 0.1,
                sugar100 = 1.8,
                iron100 = 3.3,
                calcium100 = 19.0,
                magnesium100 = 36.0,
                zinc100 = 1.3,
                vitaminC100 = 1.5,
                vitaminD100 = 0.0,
                vitaminB12100 = 0.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val saveMealEntryUseCase = SaveMealEntryUseCase(
            diaryRepository = diaryRepository,
            buildMealPreviewUseCase = BuildMealPreviewUseCase(overrideRepository),
            nowDateProvider = { java.time.LocalDate.of(2026, 4, 14) },
            nowOffsetProvider = { java.time.ZoneOffset.UTC },
        )

        saveMealEntryUseCase(
            SaveMealEntryCommand(
                mealType = MealType.DINNER,
                food = MealFoodCandidate(
                    id = foodId,
                    name = "Lentils",
                    brand = "Brand",
                    source = ResolvedSource.CACHE,
                    kcal100 = 116.0,
                    carb100 = 20.0,
                    fat100 = 0.4,
                    protein100 = 9.0,
                    saturatedFat100 = 0.1,
                    sugar100 = 1.8,
                    iron100 = 3.3,
                    calcium100 = 19.0,
                    magnesium100 = 36.0,
                    zinc100 = 1.3,
                    vitaminC100 = 1.5,
                    vitaminD100 = 0.0,
                    vitaminB12100 = 0.0,
                ),
                quantity = 200.0,
                unit = "g",
            )
        )

        val summary = diaryRepository.getDailySummary("2026-04-14")
        assertEquals(0.2, summary?.saturatedFatTotal ?: 0.0, 0.001)
        assertEquals(3.6, summary?.sugarTotal ?: 0.0, 0.001)
        assertEquals(6.6, summary?.ironTotal ?: 0.0, 0.001)
        assertEquals(38.0, summary?.calciumTotal ?: 0.0, 0.001)
        assertEquals(72.0, summary?.magnesiumTotal ?: 0.0, 0.001)
        assertEquals(2.6, summary?.zincTotal ?: 0.0, 0.001)
        assertEquals(3.0, summary?.vitaminCTotal ?: 0.0, 0.001)
    }
}
