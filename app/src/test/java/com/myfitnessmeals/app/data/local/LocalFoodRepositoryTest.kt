package com.myfitnessmeals.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.repository.FoodLookupResult
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.integration.off.OffCatalogClient
import com.myfitnessmeals.app.integration.off.OffCatalogClientResult
import com.myfitnessmeals.app.integration.off.OffCatalogError
import com.myfitnessmeals.app.integration.off.OffReferenceUnit
import com.myfitnessmeals.app.integration.off.OffRemoteNutrients
import com.myfitnessmeals.app.integration.off.OffRemoteProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalFoodRepositoryTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchFoodByText_cacheHit_usesCacheWithoutOffCall() = runTest {
        database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "local-1",
                source = "CACHE",
                name = "Greek Yogurt",
                brand = "Brand",
                barcode = "123456",
                kcal100 = 100.0,
                carb100 = 6.0,
                fat100 = 2.0,
                protein100 = 10.0,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(emptyList()),
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_100_000L },
        )

        val result = repository.searchFoodByText("yogurt")
        assertTrue(result is FoodLookupResult.Success)
        val success = result as FoodLookupResult.Success
        assertEquals(1, success.data.size)
        assertEquals("CACHE", success.source.name)
        assertEquals(0, fakeClient.textCalls)
    }

    @Test
    fun searchFoodByText_limitLessOrEqualZero_clampsToOneForLocalLookup() = runTest {
        database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "local-apple-1",
                source = "CACHE",
                name = "Apple A",
                brand = "Brand",
                barcode = "111111",
                kcal100 = 52.0,
                carb100 = 14.0,
                fat100 = 0.2,
                protein100 = 0.3,
                lastSyncedAt = 1_700_000_000_000L,
            )
        )
        database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "local-apple-2",
                source = "CACHE",
                name = "Apple B",
                brand = "Brand",
                barcode = "222222",
                kcal100 = 53.0,
                carb100 = 13.0,
                fat100 = 0.3,
                protein100 = 0.4,
                lastSyncedAt = 1_700_000_000_001L,
            )
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.NotFound,
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_100_000L },
        )

        val result = repository.searchFoodByText("apple", limit = 0)
        assertTrue(result is FoodLookupResult.Success)
        val success = result as FoodLookupResult.Success
        assertEquals("CACHE", success.source.name)
        assertEquals(1, success.data.size)
        assertEquals(0, fakeClient.textCalls)
    }

    @Test
    fun searchFoodByText_cacheMiss_fallsBackToOffAndPersistsMappedFood() = runTest {
        val offFood = OffRemoteProduct(
            externalId = "off-42",
            name = "Tomato Soup",
            brand = "OFF Brand",
            barcode = "8001234567890",
            nutrients = OffRemoteNutrients(
                kcalPer100g = 40.0,
                carbPer100g = 7.5,
                fatPer100g = 1.0,
                proteinPer100g = 1.2,
            ),
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(listOf(offFood)),
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_200_000L },
        )

        val result = repository.searchFoodByText("tomato")
        assertTrue(result is FoodLookupResult.Success)
        val success = result as FoodLookupResult.Success
        assertEquals("OFF", success.source.name)
        assertEquals("Tomato Soup", success.data.first().name)
        assertEquals(1, fakeClient.textCalls)

        val cached = database.foodDao().searchByName("%Tomato%", 10)
        assertEquals(1, cached.size)
        assertEquals("OFF", cached.first().source)
    }

    @Test
    fun searchFoodByText_limitTooLarge_clampsAndReusesNormalizedLimitForOffCall() = runTest {
        val offFood = OffRemoteProduct(
            externalId = "off-100",
            name = "Kidney Beans",
            brand = "OFF Brand",
            barcode = "8000000000100",
            nutrients = OffRemoteNutrients(
                kcalPer100g = 120.0,
                carbPer100g = 20.0,
                fatPer100g = 0.5,
                proteinPer100g = 8.0,
            ),
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(listOf(offFood)),
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_250_000L },
        )

        val result = repository.searchFoodByText("beans", limit = 1_000)
        assertTrue(result is FoodLookupResult.Success)
        assertEquals(1, fakeClient.textCalls)
        assertEquals(100, fakeClient.lastTextLimit)
    }

    @Test
    fun searchFoodByText_escapesLikeMetacharacters_forLiteralMatching() = runTest {
        database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "local-literal",
                source = "CACHE",
                name = "100% Whey",
                brand = "Brand",
                barcode = "333333",
                kcal100 = 380.0,
                carb100 = 8.0,
                fat100 = 6.0,
                protein100 = 72.0,
                lastSyncedAt = 1_700_000_000_100L,
            )
        )
        database.foodDao().upsert(
            FoodItemEntity(
                sourceId = "local-wildcard",
                source = "CACHE",
                name = "100X Whey",
                brand = "Brand",
                barcode = "444444",
                kcal100 = 375.0,
                carb100 = 9.0,
                fat100 = 7.0,
                protein100 = 70.0,
                lastSyncedAt = 1_700_000_000_101L,
            )
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.NotFound,
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_300_000L },
        )

        val result = repository.searchFoodByText("100%")
        assertTrue(result is FoodLookupResult.Success)
        val success = result as FoodLookupResult.Success
        assertEquals("CACHE", success.source.name)
        assertEquals(1, success.data.size)
        assertEquals("100% Whey", success.data.first().name)
        assertEquals(0, fakeClient.textCalls)
    }

    @Test
    fun searchFoodByText_repeatedRemoteFetch_persistsOffIdempotently() = runTest {
        val offFood = OffRemoteProduct(
            externalId = "off-repeat-1",
            name = "Remote Lentils",
            brand = "OFF Brand",
            barcode = "8880001112223",
            nutrients = OffRemoteNutrients(
                kcalPer100g = 116.0,
                carbPer100g = 20.0,
                fatPer100g = 0.4,
                proteinPer100g = 9.0,
            ),
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(listOf(offFood)),
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_700_000L },
        )

        val first = repository.searchFoodByText("lentils query one")
        val second = repository.searchFoodByText("lentils query two")

        assertTrue(first is FoodLookupResult.Success)
        assertTrue(second is FoodLookupResult.Success)

        val firstId = (first as FoodLookupResult.Success).data.first().id
        val secondId = (second as FoodLookupResult.Success).data.first().id
        assertEquals(firstId, secondId)
        assertEquals(2, fakeClient.textCalls)

        val persisted = database.foodDao().searchByName("%Remote Lentils%", 10)
        assertEquals(1, persisted.size)
        assertEquals("off-repeat-1", persisted.first().sourceId)
    }

    @Test
    fun searchFoodByText_parallelRemoteFetch_sameProduct_isAtomicallyDeduplicated() = runTest {
        val offFood = OffRemoteProduct(
            externalId = "off-concurrent-1",
            name = "Concurrent Chickpeas",
            brand = "OFF Brand",
            barcode = "8800000011112",
            nutrients = OffRemoteNutrients(
                kcalPer100g = 164.0,
                carbPer100g = 27.0,
                fatPer100g = 2.6,
                proteinPer100g = 9.0,
            ),
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(listOf(offFood)),
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_800_000L },
        )

        val ids = withContext(Dispatchers.Default) {
            coroutineScope {
                listOf(
                    async { repository.searchFoodByText("chickpeas query one") },
                    async { repository.searchFoodByText("chickpeas query two") },
                ).awaitAll().map { result ->
                    val success = result as FoodLookupResult.Success
                    success.data.first().id
                }
            }
        }

        assertEquals(2, fakeClient.textCalls)
        assertEquals(1, ids.toSet().size)

        val canonicalKey = "off::sid:off-concurrent-1"
        assertEquals(1, database.foodDao().countByCanonicalExternalKey(canonicalKey))

        val persisted = database.foodDao().searchByName("%Concurrent Chickpeas%", 10)
        assertEquals(1, persisted.size)
    }

    @Test
    fun searchFoodByBarcode_notFound_returnsNotFound() = runTest {
        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(emptyList()),
            searchByBarcodeResult = OffCatalogClientResult.NotFound,
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_300_000L },
        )

        val result = repository.searchFoodByBarcode("0000000000000")
        assertTrue(result is FoodLookupResult.NotFound)
        assertEquals(1, fakeClient.barcodeCalls)
    }

    @Test
    fun searchFoodByBarcode_timeout_returnsTypedOffError() = runTest {
        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(emptyList()),
            searchByBarcodeResult = OffCatalogClientResult.Error(OffCatalogError.Timeout),
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_400_000L },
        )

        val result = repository.searchFoodByBarcode("8059300850018")
        assertTrue(result is FoodLookupResult.Error)
        val error = result as FoodLookupResult.Error
        assertTrue(error.error is OffCatalogError.Timeout)
    }

    @Test
    fun searchFoodByBarcode_cacheMiss_mapsMlReferenceToCanonicalPer100() = runTest {
        val offFood = OffRemoteProduct(
            externalId = "off-ml-1",
            name = "Orange Juice",
            brand = "OFF Juice",
            barcode = "9991234567000",
            nutrients = OffRemoteNutrients(
                kcalPer100g = null,
                carbPer100g = null,
                fatPer100g = null,
                proteinPer100g = null,
                kcalPer100ml = 45.0,
                carbPer100ml = 10.0,
                fatPer100ml = 0.2,
                proteinPer100ml = 0.6,
                referenceUnit = OffReferenceUnit.MILLILITER,
            ),
        )

        val fakeClient = FakeOffCatalogClient(
            searchByTextResult = OffCatalogClientResult.Success(emptyList()),
            searchByBarcodeResult = OffCatalogClientResult.Success(offFood),
        )

        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = fakeClient,
            nowEpochMillis = { 1_700_000_500_000L },
        )

        val result = repository.searchFoodByBarcode("9991234567000")
        assertTrue(result is FoodLookupResult.Success)
        val success = result as FoodLookupResult.Success
        assertEquals("OFF", success.source.name)
        assertEquals(45.0, success.data.kcal100 ?: -1.0, 0.001)
        assertEquals(10.0, success.data.carb100 ?: -1.0, 0.001)
        assertEquals(0.2, success.data.fat100 ?: -1.0, 0.001)
        assertEquals(0.6, success.data.protein100 ?: -1.0, 0.001)
    }

    @Test
    fun searchFoodByText_cacheMiss_withoutOffClient_returnsUnavailableError() = runTest {
        val repository = LocalFoodRepository(
            foodDao = database.foodDao(),
            offCatalogClient = null,
            nowEpochMillis = { 1_700_000_600_000L },
        )

        val result = repository.searchFoodByText("oat")
        assertTrue(result is FoodLookupResult.Error)
        val error = result as FoodLookupResult.Error
        assertTrue(error.error is OffCatalogError.Unavailable)
    }

    private class FakeOffCatalogClient(
        private val searchByTextResult: OffCatalogClientResult<List<OffRemoteProduct>>,
        private val searchByBarcodeResult: OffCatalogClientResult<OffRemoteProduct>,
    ) : OffCatalogClient {
        var textCalls: Int = 0
            private set

        var lastTextLimit: Int? = null
            private set

        var barcodeCalls: Int = 0
            private set

        override suspend fun searchByText(
            query: String,
            limit: Int,
        ): OffCatalogClientResult<List<OffRemoteProduct>> {
            textCalls += 1
            lastTextLimit = limit
            return searchByTextResult
        }

        override suspend fun searchByBarcode(barcode: String): OffCatalogClientResult<OffRemoteProduct> {
            barcodeCalls += 1
            return searchByBarcodeResult
        }
    }
}
