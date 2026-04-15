package com.myfitnessmeals.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ProviderType
import com.myfitnessmeals.app.domain.model.ResolvedSource
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
class LocalFitnessRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var diaryRepository: LocalDiaryRepository
    private lateinit var fitnessRepository: LocalFitnessRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        diaryRepository = LocalDiaryRepository(
            db = database,
            nowEpochMillis = { 1_700_000_000_000L },
        )
        fitnessRepository = LocalFitnessRepository(
            db = database,
            nowEpochMillis = { 1_700_000_000_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertDailyFitness_updatesDailySummaryToPreventStaleAggregates() = runTest {
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "off-6",
                source = "CACHE",
                name = "Salmon",
                brand = "Brand",
                barcode = "77777",
                kcal100 = 210.0,
                carb100 = 0.0,
                fat100 = 13.0,
                protein100 = 20.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val localDate = "2026-04-05"
        diaryRepository.setDailyTarget(localDate = localDate, kcalTarget = 2200.0)
        diaryRepository.addMealEntry(
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

        fitnessRepository.upsertDailyFitness(
            localDate = localDate,
            provider = ProviderType.GARMIN,
            steps = 9000,
            activeKcal = 300.0,
            workoutMinutes = 40,
            syncStatus = "SUCCESS",
            lastSyncAt = 1_700_000_000_000L,
        )

        val summary = diaryRepository.getDailySummary(localDate)
        assertEquals(500.0, summary?.kcalIntake ?: 0.0, 0.001)
        assertEquals(300.0, summary?.kcalBurned ?: 0.0, 0.001)
        assertEquals(2000.0, summary?.kcalRemaining ?: 0.0, 0.001)
    }

    @Test
    fun upsertDailyFitness_preservesEnrichedAggregateValuesAndNullSemantics() = runTest {
        val foodId = database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "off-9",
                source = "CACHE",
                name = "Beans",
                brand = "Brand",
                barcode = "123456",
                kcal100 = 140.0,
                carb100 = 19.0,
                fat100 = 1.0,
                protein100 = 9.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val localDate = "2026-04-08"
        database.mealEntryDao().insert(
            MealEntryEntity(
                localDate = localDate,
                timezoneOffsetMin = 60,
                mealType = "lunch",
                foodId = foodId,
                quantityValue = 100.0,
                quantityUnit = "g",
                resolvedSource = "CACHE",
                kcalTotal = 180.0,
                carbTotal = 24.0,
                fatTotal = 2.0,
                proteinTotal = 11.0,
                saturatedFatTotal = 0.4,
                sugarTotal = 1.0,
                ironTotal = 1.4,
                calciumTotal = 55.0,
                magnesiumTotal = 33.0,
                zincTotal = null,
                vitaminCTotal = 3.0,
                vitaminDTotal = null,
                vitaminB12Total = null,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
            )
        )

        fitnessRepository.upsertDailyFitness(
            localDate = localDate,
            provider = ProviderType.GARMIN,
            steps = 4000,
            activeKcal = 200.0,
            workoutMinutes = 30,
            syncStatus = "SUCCESS",
            lastSyncAt = 1_700_000_000_000L,
        )

        val summary = diaryRepository.getDailySummary(localDate)
        assertEquals(0.4, summary?.saturatedFatTotal ?: 0.0, 0.001)
        assertEquals(1.0, summary?.sugarTotal ?: 0.0, 0.001)
        assertEquals(1.4, summary?.ironTotal ?: 0.0, 0.001)
        assertEquals(55.0, summary?.calciumTotal ?: 0.0, 0.001)
        assertEquals(33.0, summary?.magnesiumTotal ?: 0.0, 0.001)
        assertEquals(3.0, summary?.vitaminCTotal ?: 0.0, 0.001)
        assertNull(summary?.zincTotal)
        assertNull(summary?.vitaminDTotal)
        assertNull(summary?.vitaminB12Total)
    }
}
