package com.myfitnessmeals.app.integration.off

import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class OffHttpCatalogClientTest {
    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchByBarcode_success_mapsProductAndNutrients() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                {
                  "status": 1,
                  "product": {
                    "_id": "8059300850018",
                    "code": "8059300850018",
                    "product_name": "Protein Bar",
                    "brands": "Gym Co",
                    "nutriments": {
                      "energy-kcal_100g": 360,
                      "carbohydrates_100g": 25.5,
                      "fat_100g": 10,
                      "proteins_100g": 30.2
                    }
                  }
                }
                """.trimIndent(),
                                )
                )

        val client = OffHttpCatalogClient(baseUrl = baseUrl)
        val result = client.searchByBarcode("8059300850018")

        assertTrue(result is OffCatalogClientResult.Success)
        val success = result as OffCatalogClientResult.Success
        assertEquals("Protein Bar", success.data.name)
        assertEquals(360.0, success.data.nutrients.kcalPer100g ?: -1.0, 0.001)
        assertEquals(25.5, success.data.nutrients.carbPer100g ?: -1.0, 0.001)
        assertEquals(10.0, success.data.nutrients.fatPer100g ?: -1.0, 0.001)
        assertEquals(30.2, success.data.nutrients.proteinPer100g ?: -1.0, 0.001)
    }

    @Test
    fun searchByBarcode_statusZero_returnsNotFound() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                {
                  "status": 0
                }
                """.trimIndent(),
                )
        )

        val client = OffHttpCatalogClient(baseUrl = baseUrl)
        val result = client.searchByBarcode("0000000000000")

        assertTrue(result is OffCatalogClientResult.NotFound)
    }

    @Test
    fun searchByText_rateLimit_returnsRateLimitError() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))

        val client = OffHttpCatalogClient(baseUrl = baseUrl)
        val result = client.searchByText("pasta", 5)

        assertTrue(result is OffCatalogClientResult.Error)
        val error = result as OffCatalogClientResult.Error
        assertTrue(error.error is OffCatalogError.RateLimit)
    }

    @Test
    fun searchByText_unavailable_returnsUnavailableError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))

        val client = OffHttpCatalogClient(baseUrl = baseUrl)
        val result = client.searchByText("bread", 5)

        assertTrue(result is OffCatalogClientResult.Error)
        val error = result as OffCatalogClientResult.Error
        assertTrue(error.error is OffCatalogError.Unavailable)
    }

    @Test
    fun searchByText_malformedPayload_returnsMalformedPayloadError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"products\": 7}"))

        val client = OffHttpCatalogClient(baseUrl = baseUrl)
        val result = client.searchByText("juice", 10)

        assertTrue(result is OffCatalogClientResult.Error)
        val error = result as OffCatalogClientResult.Error
        assertTrue(error.error is OffCatalogError.MalformedPayload)
    }

    @Test
    fun searchByBarcode_slowResponse_returnsTimeoutError() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("{}")
                .setBodyDelay(250, TimeUnit.MILLISECONDS)
        )

        val client = OffHttpCatalogClient(
            baseUrl = baseUrl,
            connectTimeoutMillis = 50,
            readTimeoutMillis = 50,
        )
        val result = client.searchByBarcode("1231231231231")

        assertTrue(result is OffCatalogClientResult.Error)
        val error = result as OffCatalogClientResult.Error
        assertTrue(error.error is OffCatalogError.Timeout)
    }

    @Test
    fun searchByBarcode_invalidInput_doesNotCallNetworkAndReturnsNotFound() = runTest {
        val client = OffHttpCatalogClient(baseUrl = baseUrl)

        val result = client.searchByBarcode("../8059300850018")

        assertTrue(result is OffCatalogClientResult.NotFound)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun searchByText_executesNetworkBoundaryOnProvidedIoDispatcher() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"products\":[]}")
        )

        val threadName = AtomicReference<String?>(null)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "off-io-dispatcher-test")
        }
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            val client = OffHttpCatalogClient(
                baseUrl = baseUrl,
                ioDispatcher = dispatcher,
                connectionFactory = { url ->
                    threadName.set(Thread.currentThread().name)
                    url.openConnection() as HttpURLConnection
                },
            )

            client.searchByText("milk", 5)

            assertTrue(threadName.get()?.contains("off-io-dispatcher-test") == true)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }
}
