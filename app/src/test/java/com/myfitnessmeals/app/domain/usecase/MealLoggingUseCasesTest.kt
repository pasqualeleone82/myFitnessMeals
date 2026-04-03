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
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.observability.ObservabilityTracker
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MealLoggingUseCasesTest {
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
    fun buildMealPreview_usesOverrideBeforeFoodAndFlagsMissingNutrients() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-1",
                source = "CACHE",
                name = "Soup",
                brand = "Brand",
                barcode = "1000000000001",
                kcal100 = 50.0,
                carb100 = null,
                fat100 = 1.0,
                protein100 = 2.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        overrideRepository.upsertOverride(
            NutritionOverrideEntity(
                foodId = foodId,
                kcal100 = 80.0,
                carb100 = 10.0,
                fat100 = null,
                protein100 = 3.0,
                note = null,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
            )
        )

        val preview = BuildMealPreviewUseCase(overrideRepository).invoke(
            food = MealFoodCandidate(
                id = foodId,
                name = "Soup",
                brand = "Brand",
                source = ResolvedSource.CACHE,
                kcal100 = 50.0,
                carb100 = null,
                fat100 = 1.0,
                protein100 = 2.0,
            ),
            quantity = 150.0,
            unit = "g",
        )

        assertEquals(120.0, preview.kcalTotal, 0.001)
        assertEquals(15.0, preview.carbTotal, 0.001)
        assertEquals(1.5, preview.fatTotal, 0.001)
        assertEquals(4.5, preview.proteinTotal, 0.001)
        assertTrue(preview.resolvedSource == ResolvedSource.OVERRIDE)
        assertTrue(!preview.kcalMissing)
        assertTrue(!preview.carbMissing)
        assertTrue(!preview.fatMissing)
        assertTrue(!preview.proteinMissing)
    }

    @Test
    fun saveMealEntry_withNonPositiveQuantity_throwsDeterministicError() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-2",
                source = "CACHE",
                name = "Chicken",
                brand = "Brand",
                barcode = "1000000000002",
                kcal100 = 165.0,
                carb100 = 0.0,
                fat100 = 3.0,
                protein100 = 31.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val tracker = RecordingObservabilityTracker()
        val saveUseCase = SaveMealEntryUseCase(
            diaryRepository = diaryRepository,
            buildMealPreviewUseCase = BuildMealPreviewUseCase(overrideRepository),
            observabilityTracker = tracker,
        )

        try {
            saveUseCase(
                SaveMealEntryCommand(
                    mealType = MealType.BREAKFAST,
                    food = MealFoodCandidate(
                        id = foodId,
                        name = "Chicken",
                        brand = "Brand",
                        source = ResolvedSource.CACHE,
                        kcal100 = 165.0,
                        carb100 = 0.0,
                        fat100 = 3.0,
                        protein100 = 31.0,
                    ),
                    quantity = 0.0,
                    unit = "g",
                )
            )
            throw AssertionError("Expected IllegalArgumentException for non-positive quantity")
        } catch (error: IllegalArgumentException) {
            assertEquals("Meal quantity must be positive", error.message)
            assertEquals(1, tracker.mealSaveEvents.size)
            assertFalse(tracker.mealSaveEvents.first().success)
            assertEquals("VALIDATION_FAILED", tracker.mealSaveEvents.first().errorCode)
        }
    }

    @Test
    fun saveMealEntry_whenRuntimeExceptionOccurs_tracksFailureAndRethrows() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-2b",
                source = "CACHE",
                name = "Turkey",
                brand = "Brand",
                barcode = "1000000000202",
                kcal100 = 140.0,
                carb100 = 0.0,
                fat100 = 2.0,
                protein100 = 29.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val tracker = RecordingObservabilityTracker()
        val saveUseCase = SaveMealEntryUseCase(
            diaryRepository = diaryRepository,
            buildMealPreviewUseCase = BuildMealPreviewUseCase(overrideRepository),
            observabilityTracker = tracker,
            nowOffsetProvider = { throw IllegalStateException("Clock runtime failure") },
        )

        try {
            saveUseCase(
                SaveMealEntryCommand(
                    mealType = MealType.LUNCH,
                    food = MealFoodCandidate(
                        id = foodId,
                        name = "Turkey",
                        brand = "Brand",
                        source = ResolvedSource.CACHE,
                        kcal100 = 140.0,
                        carb100 = 0.0,
                        fat100 = 2.0,
                        protein100 = 29.0,
                    ),
                    quantity = 100.0,
                    unit = "g",
                )
            )
            throw AssertionError("Expected IllegalStateException for runtime failure")
        } catch (error: IllegalStateException) {
            assertEquals("Clock runtime failure", error.message)
            assertEquals(1, tracker.mealSaveEvents.size)
            assertFalse(tracker.mealSaveEvents.first().success)
            assertEquals("RUNTIME_FAILED", tracker.mealSaveEvents.first().errorCode)
        }
    }

    @Test
    fun saveMealEntry_whenDatabaseExceptionOccurs_tracksFailureAndRethrows() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val localDiaryRepository = LocalDiaryRepository(
            db = db,
            nowEpochMillis = { 1_700_000_000_000L },
        )
        db.close()

        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-2c",
                source = "CACHE",
                name = "Oats",
                brand = "Brand",
                barcode = "1000000000203",
                kcal100 = 389.0,
                carb100 = 66.0,
                fat100 = 7.0,
                protein100 = 17.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val tracker = RecordingObservabilityTracker()
        val saveUseCase = SaveMealEntryUseCase(
            diaryRepository = localDiaryRepository,
            buildMealPreviewUseCase = BuildMealPreviewUseCase(overrideRepository),
            observabilityTracker = tracker,
            nowDateProvider = { java.time.LocalDate.of(2026, 3, 30) },
            nowOffsetProvider = { java.time.ZoneOffset.UTC },
        )

        try {
            saveUseCase(
                SaveMealEntryCommand(
                    mealType = MealType.DINNER,
                    food = MealFoodCandidate(
                        id = foodId,
                        name = "Oats",
                        brand = "Brand",
                        source = ResolvedSource.CACHE,
                        kcal100 = 389.0,
                        carb100 = 66.0,
                        fat100 = 7.0,
                        protein100 = 17.0,
                    ),
                    quantity = 50.0,
                    unit = "g",
                )
            )
            throw AssertionError("Expected runtime exception for closed database")
        } catch (error: Exception) {
            assertEquals(1, tracker.mealSaveEvents.size)
            assertFalse(tracker.mealSaveEvents.first().success)
            assertEquals("DB_WRITE_FAILED", tracker.mealSaveEvents.first().errorCode)
        }
    }

    @Test
    fun searchFoodsByText_success_tracksFoodSearchEventWithCacheSource() = runTest {
        foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-search-1",
                source = "CACHE",
                name = "Chicken Breast",
                brand = "Brand",
                barcode = "1999999999001",
                kcal100 = 165.0,
                carb100 = 0.0,
                fat100 = 3.6,
                protein100 = 31.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val tracker = RecordingObservabilityTracker()
        val useCase = SearchFoodsByTextUseCase(
            foodRepository = foodRepository,
            observabilityTracker = tracker,
        )

        val result = useCase("Chicken")
        assertTrue(result is MealSearchResult.Success)
        assertEquals(1, tracker.foodSearchEvents.size)
        val event = tracker.foodSearchEvents.first()
        assertEquals("text", event.origin)
        assertEquals("success", event.outcome)
        assertEquals("CACHE", event.source)
        assertEquals(1, event.resultCount)
    }

    @Test
    fun saveMealEntry_logsAllMealTypesInSameDay_snapshotContainsAllTypes() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-3",
                source = "CACHE",
                name = "Yogurt",
                brand = "Brand",
                barcode = "1000000000003",
                kcal100 = 100.0,
                carb100 = 8.0,
                fat100 = 4.0,
                protein100 = 9.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val saveUseCase = SaveMealEntryUseCase(
            diaryRepository = diaryRepository,
            buildMealPreviewUseCase = BuildMealPreviewUseCase(overrideRepository),
            nowDateProvider = { java.time.LocalDate.of(2026, 3, 30) },
            nowOffsetProvider = { java.time.ZoneOffset.UTC },
        )
        val snapshotUseCase = GetMealDaySnapshotUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            nowDateProvider = { java.time.LocalDate.of(2026, 3, 30) },
        )

        MealType.entries.forEach { mealType ->
            saveUseCase(
                SaveMealEntryCommand(
                    mealType = mealType,
                    food = MealFoodCandidate(
                        id = foodId,
                        name = "Yogurt",
                        brand = "Brand",
                        source = ResolvedSource.CACHE,
                        kcal100 = 100.0,
                        carb100 = 8.0,
                        fat100 = 4.0,
                        protein100 = 9.0,
                    ),
                    quantity = 100.0,
                    unit = "g",
                )
            )
        }

        val snapshot = snapshotUseCase()
        val loggedMealTypes = snapshot.entries.map { it.mealType }.toSet()

        assertEquals(MealType.entries.size, snapshot.entries.size)
        assertEquals(MealType.entries.toSet(), loggedMealTypes)
        assertEquals(400.0, snapshot.kcalIntake, 0.001)
        assertEquals(32.0, snapshot.carbTotal, 0.001)
        assertEquals(16.0, snapshot.fatTotal, 0.001)
        assertEquals(36.0, snapshot.proteinTotal, 0.001)
    }

    @Test
    fun deleteMealEntry_updatesSnapshotAggregatesAfterRemoval() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-4",
                source = "CACHE",
                name = "Salmon",
                brand = "Brand",
                barcode = "1000000000004",
                kcal100 = 200.0,
                carb100 = 0.0,
                fat100 = 12.0,
                protein100 = 22.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val saveUseCase = SaveMealEntryUseCase(
            diaryRepository = diaryRepository,
            buildMealPreviewUseCase = BuildMealPreviewUseCase(overrideRepository),
            nowDateProvider = { java.time.LocalDate.of(2026, 3, 30) },
            nowOffsetProvider = { java.time.ZoneOffset.UTC },
        )
        val deleteUseCase = DeleteMealEntryUseCase(diaryRepository)
        val snapshotUseCase = GetMealDaySnapshotUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
            nowDateProvider = { java.time.LocalDate.of(2026, 3, 30) },
        )

        val breakfastId = saveUseCase(
            SaveMealEntryCommand(
                mealType = MealType.BREAKFAST,
                food = MealFoodCandidate(
                    id = foodId,
                    name = "Salmon",
                    brand = "Brand",
                    source = ResolvedSource.CACHE,
                    kcal100 = 200.0,
                    carb100 = 0.0,
                    fat100 = 12.0,
                    protein100 = 22.0,
                ),
                quantity = 100.0,
                unit = "g",
            )
        )
        saveUseCase(
            SaveMealEntryCommand(
                mealType = MealType.LUNCH,
                food = MealFoodCandidate(
                    id = foodId,
                    name = "Salmon",
                    brand = "Brand",
                    source = ResolvedSource.CACHE,
                    kcal100 = 200.0,
                    carb100 = 0.0,
                    fat100 = 12.0,
                    protein100 = 22.0,
                ),
                quantity = 200.0,
                unit = "g",
            )
        )

        val snapshotBeforeDelete = snapshotUseCase()
        assertEquals(600.0, snapshotBeforeDelete.kcalIntake, 0.001)
        assertEquals(36.0, snapshotBeforeDelete.fatTotal, 0.001)
        assertEquals(66.0, snapshotBeforeDelete.proteinTotal, 0.001)

        val deleted = deleteUseCase(breakfastId)
        assertTrue(deleted)

        val snapshotAfterDelete = snapshotUseCase()
        assertEquals(1, snapshotAfterDelete.entries.size)
        assertFalse(snapshotAfterDelete.entries.any { it.id == breakfastId })
        assertEquals(400.0, snapshotAfterDelete.kcalIntake, 0.001)
        assertEquals(24.0, snapshotAfterDelete.fatTotal, 0.001)
        assertEquals(44.0, snapshotAfterDelete.proteinTotal, 0.001)
    }

    @Test
    fun saveNutritionOverride_persistsAndKeepsCreatedAtOnUpdate() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-5",
                source = "CACHE",
                name = "Beans",
                brand = "Brand",
                barcode = "1000000000005",
                kcal100 = 120.0,
                carb100 = 20.0,
                fat100 = 1.0,
                protein100 = 8.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        var clock = 1_800_000_000_000L
        val saveOverrideUseCase = SaveNutritionOverrideUseCase(
            overrideRepository = overrideRepository,
            nowEpochMillis = { clock },
        )

        saveOverrideUseCase(
            SaveNutritionOverrideCommand(
                foodId = foodId,
                kcal100 = 140.0,
                carb100 = null,
                fat100 = null,
                protein100 = 10.0,
                note = " label ",
            )
        )

        val firstOverride = overrideRepository.getOverrideByFoodId(foodId)
        assertNotNull(firstOverride)
        assertEquals(140.0, firstOverride?.kcal100 ?: 0.0, 0.001)
        assertEquals(10.0, firstOverride?.protein100 ?: 0.0, 0.001)
        assertEquals("label", firstOverride?.note)
        assertEquals(1_800_000_000_000L, firstOverride?.createdAt)
        assertEquals(1_800_000_000_000L, firstOverride?.updatedAt)

        clock = 1_800_000_100_000L
        saveOverrideUseCase(
            SaveNutritionOverrideCommand(
                foodId = foodId,
                kcal100 = 150.0,
                carb100 = 22.0,
                fat100 = 2.0,
                protein100 = 11.0,
                note = "updated",
            )
        )

        val updatedOverride = overrideRepository.getOverrideByFoodId(foodId)
        assertNotNull(updatedOverride)
        assertEquals(150.0, updatedOverride?.kcal100 ?: 0.0, 0.001)
        assertEquals(22.0, updatedOverride?.carb100 ?: 0.0, 0.001)
        assertEquals(2.0, updatedOverride?.fat100 ?: 0.0, 0.001)
        assertEquals(11.0, updatedOverride?.protein100 ?: 0.0, 0.001)
        assertEquals("updated", updatedOverride?.note)
        assertEquals(1_800_000_000_000L, updatedOverride?.createdAt)
        assertEquals(1_800_000_100_000L, updatedOverride?.updatedAt)
    }

    @Test
    fun saveNutritionOverride_requiresAtLeastOneNutrient() = runTest {
        val saveOverrideUseCase = SaveNutritionOverrideUseCase(overrideRepository)
        try {
            saveOverrideUseCase(
                SaveNutritionOverrideCommand(
                    foodId = 1L,
                    kcal100 = null,
                    carb100 = null,
                    fat100 = null,
                    protein100 = null,
                    note = null,
                )
            )
            throw AssertionError("Expected IllegalArgumentException when all nutrients are null")
        } catch (error: IllegalArgumentException) {
            assertEquals("At least one nutrient override is required", error.message)
        }
    }

    @Test
    fun deleteNutritionOverride_removesExistingOverride() = runTest {
        val foodId = foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "local-6",
                source = "CACHE",
                name = "Milk",
                brand = "Brand",
                barcode = "1000000000006",
                kcal100 = 60.0,
                carb100 = 5.0,
                fat100 = 3.2,
                protein100 = 3.1,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        overrideRepository.upsertOverride(
            NutritionOverrideEntity(
                foodId = foodId,
                kcal100 = 70.0,
                carb100 = 6.0,
                fat100 = 3.5,
                protein100 = 3.6,
                note = "manual",
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
            )
        )

        val deleteUseCase = DeleteNutritionOverrideUseCase(overrideRepository)
        val deleted = deleteUseCase(foodId)
        assertTrue(deleted)
        assertEquals(null, overrideRepository.getOverrideByFoodId(foodId))
    }

    private class RecordingObservabilityTracker : ObservabilityTracker {
        data class FoodSearchEvent(
            val origin: String,
            val outcome: String,
            val source: String?,
            val resultCount: Int,
            val errorCode: String?,
            val retryable: Boolean,
        )

        data class MealSaveEvent(
            val mealType: String,
            val success: Boolean,
            val errorCode: String?,
        )

        val foodSearchEvents = mutableListOf<FoodSearchEvent>()
        val mealSaveEvents = mutableListOf<MealSaveEvent>()

        override fun trackFoodSearch(
            origin: String,
            outcome: String,
            source: String?,
            resultCount: Int,
            errorCode: String?,
            retryable: Boolean,
        ) {
            foodSearchEvents += FoodSearchEvent(origin, outcome, source, resultCount, errorCode, retryable)
        }

        override fun trackMealSave(mealType: String, success: Boolean, errorCode: String?) {
            mealSaveEvents += MealSaveEvent(mealType, success, errorCode)
        }

        override fun trackProviderSync(mode: String, success: Boolean, errorCode: String?, retryable: Boolean) = Unit
    }
}
