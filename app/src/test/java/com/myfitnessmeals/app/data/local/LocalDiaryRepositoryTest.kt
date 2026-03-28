package com.myfitnessmeals.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ProviderType
import com.myfitnessmeals.app.domain.model.ResolvedSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDiaryRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalDiaryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = LocalDiaryRepository(
            db = database,
            nowEpochMillis = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addAndDeleteMealEntry_recalculatesDailySummaryAtomically() = runTest {
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "off-1",
                source = "CACHE",
                name = "Yogurt",
                brand = "Brand",
                barcode = "12345",
                kcal100 = 100.0,
                carb100 = 10.0,
                fat100 = 3.0,
                protein100 = 8.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val localDate = "2026-03-28"
        repository.setDailyTarget(localDate = localDate, kcalTarget = 2000.0)

        val firstEntryId = repository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = 60,
                mealType = MealType.BREAKFAST,
                foodId = foodId,
                quantityValue = 150.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 300.0,
                carbTotal = 30.0,
                fatTotal = 10.0,
                proteinTotal = 15.0,
            )
        )

        repository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = 60,
                mealType = MealType.LUNCH,
                foodId = foodId,
                quantityValue = 200.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 450.0,
                carbTotal = 40.0,
                fatTotal = 15.0,
                proteinTotal = 20.0,
            )
        )

        var summary = repository.getDailySummary(localDate)
        assertEquals(750.0, summary?.kcalIntake ?: 0.0, 0.001)
        assertEquals(70.0, summary?.carbTotal ?: 0.0, 0.001)
        assertEquals(25.0, summary?.fatTotal ?: 0.0, 0.001)
        assertEquals(35.0, summary?.proteinTotal ?: 0.0, 0.001)
        assertEquals(1250.0, summary?.kcalRemaining ?: 0.0, 0.001)

        val deleted = repository.deleteMealEntry(firstEntryId)
        assertTrue(deleted)

        summary = repository.getDailySummary(localDate)
        assertEquals(450.0, summary?.kcalIntake ?: 0.0, 0.001)
        assertEquals(40.0, summary?.carbTotal ?: 0.0, 0.001)
        assertEquals(15.0, summary?.fatTotal ?: 0.0, 0.001)
        assertEquals(20.0, summary?.proteinTotal ?: 0.0, 0.001)
        assertEquals(1550.0, summary?.kcalRemaining ?: 0.0, 0.001)
    }

    @Test
    fun deleteMealEntry_legacySignatureIgnoresPassedDateAndRecalculatesUsingStoredEntryDate() = runTest {
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "off-4",
                source = "CACHE",
                name = "Pasta",
                brand = "Brand",
                barcode = "55555",
                kcal100 = 350.0,
                carb100 = 70.0,
                fat100 = 2.0,
                protein100 = 12.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val entryDate = "2026-03-31"
        val wrongDate = "2026-04-01"

        repository.setDailyTarget(localDate = entryDate, kcalTarget = 2000.0)
        repository.setDailyTarget(localDate = wrongDate, kcalTarget = 1800.0)

        val entryId = repository.addMealEntry(
            NewMealEntry(
                localDate = entryDate,
                timezoneOffsetMin = 60,
                mealType = MealType.LUNCH,
                foodId = foodId,
                quantityValue = 100.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 600.0,
                carbTotal = 80.0,
                fatTotal = 20.0,
                proteinTotal = 25.0,
            )
        )

        @Suppress("DEPRECATION")
        val deleted = repository.deleteMealEntry(entryId, wrongDate)
        assertTrue(deleted)

        val summaryForEntryDate = repository.getDailySummary(entryDate)
        assertEquals(0.0, summaryForEntryDate?.kcalIntake ?: -1.0, 0.001)
        assertEquals(2000.0, summaryForEntryDate?.kcalRemaining ?: 0.0, 0.001)

        val summaryForWrongDate = repository.getDailySummary(wrongDate)
        assertEquals(0.0, summaryForWrongDate?.kcalIntake ?: -1.0, 0.001)
        assertEquals(1800.0, summaryForWrongDate?.kcalRemaining ?: 0.0, 0.001)
    }

    @Test
    fun upsertFitness_recalculatesBurnedAndRemaining() = runTest {
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "off-2",
                source = "CACHE",
                name = "Chicken",
                brand = "Brand",
                barcode = "98765",
                kcal100 = 200.0,
                carb100 = 0.0,
                fat100 = 5.0,
                protein100 = 31.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val localDate = "2026-03-29"
        repository.setDailyTarget(localDate = localDate, kcalTarget = 2200.0)

        repository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = 60,
                mealType = MealType.DINNER,
                foodId = foodId,
                quantityValue = 200.0,
                quantityUnit = "g",
                resolvedSource = ResolvedSource.CACHE,
                kcalTotal = 500.0,
                carbTotal = 0.0,
                fatTotal = 10.0,
                proteinTotal = 62.0,
            )
        )

        repository.upsertFitnessDaily(
            localDate = localDate,
            provider = ProviderType.GARMIN,
            steps = 8000,
            activeKcal = 350.0,
            workoutMinutes = 45,
            syncStatus = "SUCCESS",
        )

        val summary = repository.getDailySummary(localDate)
        assertEquals(500.0, summary?.kcalIntake ?: 0.0, 0.001)
        assertEquals(350.0, summary?.kcalBurned ?: 0.0, 0.001)
        assertEquals(2050.0, summary?.kcalRemaining ?: 0.0, 0.001)
    }

    @Test
    fun addMealEntry_withNonPositiveQuantity_throwsDeterministicError() = runTest {
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "off-3",
                source = "CACHE",
                name = "Rice",
                brand = "Brand",
                barcode = "11111",
                kcal100 = 130.0,
                carb100 = 28.0,
                fat100 = 0.3,
                protein100 = 2.7,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        try {
            repository.addMealEntry(
                NewMealEntry(
                    localDate = "2026-03-30",
                    timezoneOffsetMin = 60,
                    mealType = MealType.SNACK,
                    foodId = foodId,
                    quantityValue = 0.0,
                    quantityUnit = "g",
                    resolvedSource = ResolvedSource.CACHE,
                    kcalTotal = 0.0,
                    carbTotal = 0.0,
                    fatTotal = 0.0,
                    proteinTotal = 0.0,
                )
            )
            fail("Expected IllegalArgumentException for non-positive quantity")
        } catch (error: IllegalArgumentException) {
            assertEquals("Meal quantity must be positive", error.message)
        }
    }

    @Test
    fun upsertFitness_withNegativeValues_throwsDeterministicError() = runTest {
        val localDate = "2026-04-02"

        try {
            repository.upsertFitnessDaily(
                localDate = localDate,
                provider = ProviderType.GARMIN,
                steps = -1,
                activeKcal = 100.0,
                workoutMinutes = 10,
                syncStatus = "SUCCESS",
            )
            fail("Expected IllegalArgumentException for negative steps")
        } catch (error: IllegalArgumentException) {
            assertEquals("Fitness steps must be non-negative", error.message)
        }

        try {
            repository.upsertFitnessDaily(
                localDate = localDate,
                provider = ProviderType.GARMIN,
                steps = 100,
                activeKcal = -0.1,
                workoutMinutes = 10,
                syncStatus = "SUCCESS",
            )
            fail("Expected IllegalArgumentException for negative active kcal")
        } catch (error: IllegalArgumentException) {
            assertEquals("Fitness active kcal must be non-negative", error.message)
        }

        try {
            repository.upsertFitnessDaily(
                localDate = localDate,
                provider = ProviderType.GARMIN,
                steps = 100,
                activeKcal = 100.0,
                workoutMinutes = -1,
                syncStatus = "SUCCESS",
            )
            fail("Expected IllegalArgumentException for negative workout minutes")
        } catch (error: IllegalArgumentException) {
            assertEquals("Fitness workout minutes must be non-negative", error.message)
        }

        val fitnessRows = database.fitnessDailyDao().getForDate(localDate)
        assertFalse(fitnessRows.isNotEmpty())
    }
}
