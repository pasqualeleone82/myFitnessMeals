package com.myfitnessmeals.app.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.local.NutritionOverrideEntity
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ResolvedSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GetHistoryMealsForDayUseCaseTest {
    private lateinit var database: AppDatabase
    private lateinit var diaryRepository: LocalDiaryRepository
    private lateinit var foodRepository: LocalFoodRepository
    private lateinit var overrideRepository: LocalOverrideRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        diaryRepository = LocalDiaryRepository(database)
        foodRepository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = null,
        )
        overrideRepository = LocalOverrideRepository(database.nutritionOverrideDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun invoke_returnsEmptyListWhenNoMealsForDay() = runTest {
        val useCase = GetHistoryMealsForDayUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            overrideRepository = overrideRepository,
        )

        val meals = useCase("2026-04-14")

        assertTrue(meals.isEmpty())
    }

    @Test
    fun invoke_mapsMealCardsWithAllNutrientsAndOverrideMetadata() = runTest {
        val localDate = "2026-04-14"
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "card-food-1",
                source = "OFF",
                name = "Greek Yogurt",
                brand = "BrandX",
                barcode = "8990000000020",
                kcal100 = 80.0,
                carb100 = 4.0,
                fat100 = 3.0,
                protein100 = 10.0,
                saturatedFat100 = 1.2,
                sugar100 = 3.8,
                iron100 = 0.2,
                calcium100 = 120.0,
                magnesium100 = 14.0,
                zinc100 = 0.6,
                vitaminC100 = 0.0,
                vitaminD100 = 0.8,
                vitaminB12100 = 0.5,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )

        val mealId = diaryRepository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = 0,
                mealType = MealType.BREAKFAST,
                foodId = foodId,
                quantityValue = 200.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.OVERRIDE,
                kcalTotal = 160.0,
                carbTotal = 8.0,
                fatTotal = 6.0,
                proteinTotal = 20.0,
                saturatedFatTotal = 2.4,
                sugarTotal = 7.6,
                ironTotal = 0.4,
                calciumTotal = 240.0,
                magnesiumTotal = 28.0,
                zincTotal = 1.2,
                vitaminCTotal = 0.0,
                vitaminDTotal = 1.6,
                vitaminB12Total = 1.0,
            )
        )

        overrideRepository.upsertOverride(
            NutritionOverrideEntity(
                foodId = foodId,
                kcal100 = null,
                carb100 = null,
                fat100 = null,
                protein100 = null,
                saturatedFat100 = 1.1,
                sugar100 = 3.0,
                iron100 = null,
                calcium100 = null,
                magnesium100 = null,
                zinc100 = null,
                vitaminC100 = null,
                vitaminD100 = null,
                vitaminB12100 = null,
                note = "manual tweak",
                createdAt = 1L,
                updatedAt = 2L,
            )
        )

        val useCase = GetHistoryMealsForDayUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            overrideRepository = overrideRepository,
        )

        val cards = useCase(localDate)

        assertEquals(1, cards.size)
        val card = cards.single()
        assertEquals(mealId, card.mealEntryId)
        assertEquals("Greek Yogurt", card.foodName)
        assertEquals("BrandX", card.brand)
        assertEquals(MealType.BREAKFAST, card.mealType)
        assertEquals(200.0, card.quantityValue, 0.001)
        assertEquals("g", card.quantityUnit)
        assertEquals(160.0, card.kcal, 0.001)
        assertEquals(20.0, card.protein, 0.001)
        assertEquals(8.0, card.carbs, 0.001)
        assertEquals(6.0, card.fat, 0.001)
        assertEquals(2.4, card.saturatedFat ?: -1.0, 0.001)
        assertEquals(240.0, card.calcium ?: -1.0, 0.001)
        assertTrue(card.isOverridden)
        assertEquals("Manual", card.sourceLabel)
        assertFalse(card.shouldShowEnrichedPlaceholder)
    }

    @Test
    fun invoke_defaultsToCacheSourceAndPlaceholderWhenEnrichedValuesAreNull() = runTest {
        val localDate = "2026-04-15"
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "temp-food",
                source = "CACHE",
                name = "Temp",
                brand = null,
                barcode = null,
                kcal100 = 100.0,
                carb100 = 10.0,
                fat100 = 2.0,
                protein100 = 5.0,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )

        diaryRepository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = 0,
                mealType = MealType.SNACK,
                foodId = foodId,
                quantityValue = 100.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 100.0,
                carbTotal = 10.0,
                fatTotal = 2.0,
                proteinTotal = 5.0,
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

        val useCase = GetHistoryMealsForDayUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            overrideRepository = overrideRepository,
        )

        val card = useCase(localDate).single()

        assertEquals("Temp", card.foodName)
        assertNull(card.brand)
        assertFalse(card.isOverridden)
        assertEquals("CACHE", card.sourceLabel)
        assertTrue(card.shouldShowEnrichedPlaceholder)
    }

    @Test
    fun invoke_throwsWhenLocalDateIsBlank() = runTest {
        val useCase = GetHistoryMealsForDayUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            overrideRepository = overrideRepository,
        )

        var threw = false
        try {
            useCase("   ")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun invoke_prefersResolvedSourceForMetadataWhenOverrideRowExists() = runTest {
        val localDate = "2026-04-16"
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "consistency-food-1",
                source = "OFF",
                name = "Skyr",
                brand = "BrandY",
                barcode = "8990000000037",
                kcal100 = 60.0,
                carb100 = 3.0,
                fat100 = 0.2,
                protein100 = 11.0,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )

        diaryRepository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = 0,
                mealType = MealType.SNACK,
                foodId = foodId,
                quantityValue = 150.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 90.0,
                carbTotal = 4.5,
                fatTotal = 0.3,
                proteinTotal = 16.5,
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

        overrideRepository.upsertOverride(
            NutritionOverrideEntity(
                foodId = foodId,
                kcal100 = 65.0,
                carb100 = 3.2,
                fat100 = 0.3,
                protein100 = 11.5,
                saturatedFat100 = null,
                sugar100 = null,
                iron100 = null,
                calcium100 = null,
                magnesium100 = null,
                zinc100 = null,
                vitaminC100 = null,
                vitaminD100 = null,
                vitaminB12100 = null,
                note = "present but not used for this entry",
                createdAt = 3L,
                updatedAt = 4L,
            )
        )

        val useCase = GetHistoryMealsForDayUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            overrideRepository = overrideRepository,
        )

        val card = useCase(localDate).single()

        assertFalse(card.isOverridden)
        assertEquals("CACHE", card.sourceLabel)
    }
}
