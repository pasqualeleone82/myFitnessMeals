package com.myfitnessmeals.app.integration.garmin

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.domain.model.ProviderType
import com.myfitnessmeals.app.observability.ObservabilityTracker
import com.myfitnessmeals.app.security.OAuthToken
import com.myfitnessmeals.app.security.OAuthTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GarminIntegrationServiceTest {
    private lateinit var database: AppDatabase
    private lateinit var fitnessRepository: LocalFitnessRepository
    private lateinit var providerConnectionRepository: LocalProviderConnectionRepository
    private lateinit var tokenStore: InMemoryOAuthTokenStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        fitnessRepository = LocalFitnessRepository(database)
        providerConnectionRepository = LocalProviderConnectionRepository(database.providerConnectionDao())
        tokenStore = InMemoryOAuthTokenStore()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun connectAndManualSync_populatesFitnessAndStatus() = runTest {
        val tracker = RecordingObservabilityTracker()
        val service = GarminIntegrationService(
            providerConnectionRepository = providerConnectionRepository,
            fitnessRepository = fitnessRepository,
            tokenStore = tokenStore,
            observabilityTracker = tracker,
            garminClient = FakeGarminClient(),
            nowEpochMillis = { 1_700_000_000_000L },
            nowDate = { LocalDate.of(2026, 4, 2) },
        )

        val connect = service.connectProvider(authCode = "A1B2C3D4E5F6G7H8")
        assertTrue(connect is GarminActionResult.Success)

        val sync = service.syncFitness(GarminSyncMode.MANUAL)
        assertTrue(sync is GarminActionResult.Success)

        val status = service.getProviderStatus()
        assertEquals("CONNECTED", status.connectionState)
        assertEquals(1_700_000_000_000L, status.lastSyncAt)
        assertEquals(null, status.lastErrorCode)

        val daily = fitnessRepository.getDailyFitness("2026-04-02")
        assertEquals(1, daily.size)
        assertEquals(ProviderType.GARMIN.name, daily.first().provider)
        assertTrue(daily.first().steps > 0)
        assertEquals(1, tracker.providerSyncEvents.size)
        assertEquals("manual", tracker.providerSyncEvents.first().mode)
        assertTrue(tracker.providerSyncEvents.first().success)
    }

    @Test
    fun disconnect_clearsConnectionTokenRef() = runTest {
        val service = GarminIntegrationService(
            providerConnectionRepository = providerConnectionRepository,
            fitnessRepository = fitnessRepository,
            tokenStore = tokenStore,
            garminClient = FakeGarminClient(),
            nowEpochMillis = { 1_700_000_000_000L },
            nowDate = { LocalDate.of(2026, 4, 2) },
        )

        service.connectProvider(authCode = "A1B2C3D4E5F6G7H8")
        val before = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        assertNotNull(before?.tokenRef)

        val disconnect = service.disconnectProvider()
        assertTrue(disconnect is GarminActionResult.Success)

        val after = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        assertEquals("DISCONNECTED", after?.connectionState)
        assertEquals(null, after?.tokenRef)
    }

    @Test
    fun syncWhenNotConnected_tracksProviderSyncFailure() = runTest {
        val tracker = RecordingObservabilityTracker()
        val service = GarminIntegrationService(
            providerConnectionRepository = providerConnectionRepository,
            fitnessRepository = fitnessRepository,
            tokenStore = tokenStore,
            observabilityTracker = tracker,
            garminClient = FakeGarminClient(),
            nowEpochMillis = { 1_700_000_000_000L },
            nowDate = { LocalDate.of(2026, 4, 2) },
        )

        val sync = service.syncFitness(GarminSyncMode.APP_OPEN)
        assertTrue(sync is GarminActionResult.Error)
        assertEquals(1, tracker.providerSyncEvents.size)
        val event = tracker.providerSyncEvents.first()
        assertEquals("app_open", event.mode)
        assertEquals("NOT_CONNECTED", event.errorCode)
        assertTrue(!event.success)
    }

    private class InMemoryOAuthTokenStore : OAuthTokenStore {
        private val map = mutableMapOf<String, OAuthToken>()

        override fun putToken(tokenRef: String, token: OAuthToken) {
            map[tokenRef] = token
        }

        override fun getToken(tokenRef: String): OAuthToken? = map[tokenRef]

        override fun removeToken(tokenRef: String) {
            map.remove(tokenRef)
        }

        override fun removeAllTokens() {
            map.clear()
        }
    }

    private class RecordingObservabilityTracker : ObservabilityTracker {
        data class ProviderSyncEvent(
            val mode: String,
            val success: Boolean,
            val errorCode: String?,
            val retryable: Boolean,
        )

        val providerSyncEvents = mutableListOf<ProviderSyncEvent>()

        override fun trackFoodSearch(
            origin: String,
            outcome: String,
            source: String?,
            resultCount: Int,
            errorCode: String?,
            retryable: Boolean,
        ) = Unit

        override fun trackMealSave(mealType: String, success: Boolean, errorCode: String?) = Unit

        override fun trackProviderSync(mode: String, success: Boolean, errorCode: String?, retryable: Boolean) {
            providerSyncEvents += ProviderSyncEvent(mode, success, errorCode, retryable)
        }
    }
}
