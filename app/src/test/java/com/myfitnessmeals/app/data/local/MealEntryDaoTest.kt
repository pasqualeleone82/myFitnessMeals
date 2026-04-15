package com.myfitnessmeals.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MealEntryDao] using an in-memory Room database.
 *
 * Covers:
 *  - AC-013: existing DB schema loads without crash (in-memory build succeeds)
 *  - AC-014: null-safe reads for enriched columns (Double? fields read as null)
 *  - AC-004: SUM returns NULL when all rows have NULL enriched values
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MealEntryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: MealEntryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.mealEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private suspend fun insertFood(
        id: Long = 1L,
        name: String = "TestFood",
        saturatedFat: Double? = null,
        sugar: Double? = null,
        iron: Double? = null,
        calcium: Double? = null,
        magnesium: Double? = null,
        zinc: Double? = null,
        vitaminC: Double? = null,
        vitaminD: Double? = null,
        vitaminB12: Double? = null,
    ): Long {
        return database.foodDao().upsert(
            FoodItemEntity(
                id = id,
                sourceId = "src-$id",
                source = "TEST",
                name = name,
                brand = null,
                barcode = null,
                kcal100 = 100.0,
                carb100 = 10.0,
                fat100 = 3.0,
                protein100 = 8.0,
                saturatedFat100 = saturatedFat,
                sugar100 = sugar,
                iron100 = iron,
                calcium100 = calcium,
                magnesium100 = magnesium,
                zinc100 = zinc,
                vitaminC100 = vitaminC,
                vitaminD100 = vitaminD,
                vitaminB12100 = vitaminB12,
                lastSyncedAt = 1_000L,
            )
        )
    }

    private suspend fun insertMealEntry(
        localDate: String,
        foodId: Long,
        saturatedFatTotal: Double? = null,
        sugarTotal: Double? = null,
        ironTotal: Double? = null,
        calciumTotal: Double? = null,
        magnesiumTotal: Double? = null,
        zincTotal: Double? = null,
        vitaminCTotal: Double? = null,
        vitaminDTotal: Double? = null,
        vitaminB12Total: Double? = null,
    ): Long {
        return dao.insert(
            MealEntryEntity(
                localDate = localDate,
                timezoneOffsetMin = 60,
                mealType = "lunch",
                foodId = foodId,
                quantityValue = 100.0,
                quantityUnit = "g",
                resolvedSource = "TEST",
                kcalTotal = 100.0,
                carbTotal = 10.0,
                fatTotal = 3.0,
                proteinTotal = 8.0,
                saturatedFatTotal = saturatedFatTotal,
                sugarTotal = sugarTotal,
                ironTotal = ironTotal,
                calciumTotal = calciumTotal,
                magnesiumTotal = magnesiumTotal,
                zincTotal = zincTotal,
                vitaminCTotal = vitaminCTotal,
                vitaminDTotal = vitaminDTotal,
                vitaminB12Total = vitaminB12Total,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )
        )
    }

    // ---------------------------------------------------------------
    // AC-013: DB initializes without crash
    // ---------------------------------------------------------------

    @Test
    fun database_inMemoryBuild_doesNotCrash() {
        // If setUp succeeded, the DB version-3 schema including all enriched columns loaded fine.
        assertNotNull(database)
        assertNotNull(dao)
    }

    // ---------------------------------------------------------------
    // AC-014: null-safe reads for enriched columns
    // ---------------------------------------------------------------

    @Test
    fun insert_legacyMealEntry_noEnrichedColumns_readsAsNull() = runTest {
        val foodId = insertFood(id = 10L)
        val entryId = insertMealEntry(localDate = "2026-04-01", foodId = foodId)

        val entry = dao.getById(entryId)
        assertNotNull(entry)
        assertNull("saturatedFatTotal must be null for legacy row", entry!!.saturatedFatTotal)
        assertNull("sugarTotal must be null for legacy row", entry.sugarTotal)
        assertNull("ironTotal must be null for legacy row", entry.ironTotal)
        assertNull("calciumTotal must be null for legacy row", entry.calciumTotal)
        assertNull("magnesiumTotal must be null for legacy row", entry.magnesiumTotal)
        assertNull("zincTotal must be null for legacy row", entry.zincTotal)
        assertNull("vitaminCTotal must be null for legacy row", entry.vitaminCTotal)
        assertNull("vitaminDTotal must be null for legacy row", entry.vitaminDTotal)
        assertNull("vitaminB12Total must be null for legacy row", entry.vitaminB12Total)
    }

    @Test
    fun insert_enrichedMealEntry_readsBackCorrectly() = runTest {
        val foodId = insertFood(id = 20L)
        val entryId = insertMealEntry(
            localDate = "2026-04-01",
            foodId = foodId,
            saturatedFatTotal = 1.2,
            sugarTotal = 5.0,
            ironTotal = 0.8,
            calciumTotal = 120.0,
            magnesiumTotal = 30.0,
            zincTotal = 1.5,
            vitaminCTotal = 15.0,
            vitaminDTotal = 2.0,
            vitaminB12Total = 0.5,
        )

        val entry = dao.getById(entryId)
        assertNotNull(entry)
        assertEquals(1.2, entry!!.saturatedFatTotal!!, 0.001)
        assertEquals(5.0, entry.sugarTotal!!, 0.001)
        assertEquals(0.8, entry.ironTotal!!, 0.001)
        assertEquals(120.0, entry.calciumTotal!!, 0.001)
        assertEquals(30.0, entry.magnesiumTotal!!, 0.001)
        assertEquals(1.5, entry.zincTotal!!, 0.001)
        assertEquals(15.0, entry.vitaminCTotal!!, 0.001)
        assertEquals(2.0, entry.vitaminDTotal!!, 0.001)
        assertEquals(0.5, entry.vitaminB12Total!!, 0.001)
    }

    // ---------------------------------------------------------------
    // AC-004: SUM returns NULL when all rows have NULL enriched values
    // ---------------------------------------------------------------

    @Test
    fun getTotalsForDate_noRows_baseNutrientsAreZeroEnrichedAreNull() = runTest {
        val totals = dao.getTotalsForDate("2099-01-01")

        // Base macros: COALESCE(SUM(...), 0.0) — no rows → 0
        assertEquals(0.0, totals.kcalTotal ?: 0.0, 0.001)
        assertEquals(0.0, totals.carbTotal ?: 0.0, 0.001)
        assertEquals(0.0, totals.fatTotal ?: 0.0, 0.001)
        assertEquals(0.0, totals.proteinTotal ?: 0.0, 0.001)

        // Enriched: SUM with no rows → NULL
        assertNull("saturatedFatTotal must be null with no rows", totals.saturatedFatTotal)
        assertNull("sugarTotal must be null with no rows", totals.sugarTotal)
        assertNull("ironTotal must be null with no rows", totals.ironTotal)
        assertNull("calciumTotal must be null with no rows", totals.calciumTotal)
        assertNull("magnesiumTotal must be null with no rows", totals.magnesiumTotal)
        assertNull("zincTotal must be null with no rows", totals.zincTotal)
        assertNull("vitaminCTotal must be null with no rows", totals.vitaminCTotal)
        assertNull("vitaminDTotal must be null with no rows", totals.vitaminDTotal)
        assertNull("vitaminB12Total must be null with no rows", totals.vitaminB12Total)
    }

    @Test
    fun getTotalsForDate_allRowsHaveNullEnriched_sumReturnsNull() = runTest {
        // Seed two legacy rows (no enriched values) — simulates pre-migration data
        val foodId = insertFood(id = 30L)
        insertMealEntry(localDate = "2026-04-05", foodId = foodId) // all enriched = null
        insertMealEntry(localDate = "2026-04-05", foodId = foodId) // all enriched = null

        val totals = dao.getTotalsForDate("2026-04-05")

        // Base macros sum correctly (100 + 100 = 200)
        assertEquals(200.0, totals.kcalTotal ?: 0.0, 0.001)

        // AC-004: SUM of all-NULL enriched columns must be NULL, not 0
        assertNull("saturatedFatTotal must be NULL when all rows are NULL", totals.saturatedFatTotal)
        assertNull("sugarTotal must be NULL when all rows are NULL", totals.sugarTotal)
        assertNull("ironTotal must be NULL when all rows are NULL", totals.ironTotal)
        assertNull("calciumTotal must be NULL when all rows are NULL", totals.calciumTotal)
        assertNull("magnesiumTotal must be NULL when all rows are NULL", totals.magnesiumTotal)
        assertNull("zincTotal must be NULL when all rows are NULL", totals.zincTotal)
        assertNull("vitaminCTotal must be NULL when all rows are NULL", totals.vitaminCTotal)
        assertNull("vitaminDTotal must be NULL when all rows are NULL", totals.vitaminDTotal)
        assertNull("vitaminB12Total must be NULL when all rows are NULL", totals.vitaminB12Total)
    }

    @Test
    fun getTotalsForDate_enrichedRowsPresent_summedCorrectly() = runTest {
        val foodId = insertFood(id = 40L)
        insertMealEntry(
            localDate = "2026-04-06",
            foodId = foodId,
            saturatedFatTotal = 1.0,
            sugarTotal = 2.0,
            ironTotal = 0.5,
            calciumTotal = 100.0,
            magnesiumTotal = 20.0,
            zincTotal = 1.0,
            vitaminCTotal = 10.0,
            vitaminDTotal = 1.0,
            vitaminB12Total = 0.2,
        )
        insertMealEntry(
            localDate = "2026-04-06",
            foodId = foodId,
            saturatedFatTotal = 0.5,
            sugarTotal = 3.0,
            ironTotal = 0.3,
            calciumTotal = 50.0,
            magnesiumTotal = 10.0,
            zincTotal = 0.5,
            vitaminCTotal = 5.0,
            vitaminDTotal = 0.5,
            vitaminB12Total = 0.1,
        )

        val totals = dao.getTotalsForDate("2026-04-06")

        assertEquals(1.5, totals.saturatedFatTotal!!, 0.001)
        assertEquals(5.0, totals.sugarTotal!!, 0.001)
        assertEquals(0.8, totals.ironTotal!!, 0.001)
        assertEquals(150.0, totals.calciumTotal!!, 0.001)
        assertEquals(30.0, totals.magnesiumTotal!!, 0.001)
        assertEquals(1.5, totals.zincTotal!!, 0.001)
        assertEquals(15.0, totals.vitaminCTotal!!, 0.001)
        assertEquals(1.5, totals.vitaminDTotal!!, 0.001)
        assertEquals(0.3, totals.vitaminB12Total!!, 0.001)
    }

    @Test
    fun getTotalsForDate_mixedNullAndNonNullEnriched_partialSumIgnoresNulls() = runTest {
        // One enriched row, one legacy row — SQL SUM ignores NULLs, returns the non-null sum
        val foodId = insertFood(id = 50L)
        insertMealEntry(
            localDate = "2026-04-07",
            foodId = foodId,
            saturatedFatTotal = 2.0,
            calciumTotal = 80.0,
            vitaminCTotal = 12.0,
        )
        insertMealEntry(
            localDate = "2026-04-07",
            foodId = foodId,
            // all enriched = null (legacy)
        )

        val totals = dao.getTotalsForDate("2026-04-07")

        // SQL SUM ignores NULLs; result is the one non-null value
        assertEquals(2.0, totals.saturatedFatTotal!!, 0.001)
        assertEquals(80.0, totals.calciumTotal!!, 0.001)
        assertEquals(12.0, totals.vitaminCTotal!!, 0.001)

        // Columns that are ALL null remain null
        assertNull("sugarTotal should be null (all rows NULL)", totals.sugarTotal)
        assertNull("ironTotal should be null (all rows NULL)", totals.ironTotal)
    }

    // ---------------------------------------------------------------
    // Column completeness: all 9 enriched columns writable & readable
    // ---------------------------------------------------------------

    @Test
    fun allNineEnrichedColumns_storableAndReadable() = runTest {
        val foodId = insertFood(id = 60L)
        insertMealEntry(
            localDate = "2026-04-08",
            foodId = foodId,
            saturatedFatTotal = 1.1,
            sugarTotal = 2.2,
            ironTotal = 3.3,
            calciumTotal = 4.4,
            magnesiumTotal = 5.5,
            zincTotal = 6.6,
            vitaminCTotal = 7.7,
            vitaminDTotal = 8.8,
            vitaminB12Total = 9.9,
        )

        val totals = dao.getTotalsForDate("2026-04-08")

        // Verify all 9 enriched column names are covered
        assertEquals(1.1, totals.saturatedFatTotal!!, 0.001)   // saturated fat
        assertEquals(2.2, totals.sugarTotal!!, 0.001)           // sugar
        assertEquals(3.3, totals.ironTotal!!, 0.001)            // Fe (iron)
        assertEquals(4.4, totals.calciumTotal!!, 0.001)         // Ca (calcium)
        assertEquals(5.5, totals.magnesiumTotal!!, 0.001)       // Mg (magnesium)
        assertEquals(6.6, totals.zincTotal!!, 0.001)            // Zn (zinc)
        assertEquals(7.7, totals.vitaminCTotal!!, 0.001)        // Vit C
        assertEquals(8.8, totals.vitaminDTotal!!, 0.001)        // Vit D
        assertEquals(9.9, totals.vitaminB12Total!!, 0.001)      // B12
    }
}
