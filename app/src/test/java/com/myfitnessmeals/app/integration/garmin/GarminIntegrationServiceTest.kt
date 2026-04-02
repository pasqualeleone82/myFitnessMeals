package com.myfitnessmeals.app.integration.garmin

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.domain.model.ProviderType
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
        val service = GarminIntegrationService(
            providerConnectionRepository = providerConnectionRepository,
            fitnessRepository = fitnessRepository,
            tokenStore = tokenStore,
            garminClient = FakeGarminClient(),
            nowEpochMillis = { 1_700_000_000_000L },
            nowDate = { LocalDate.of(2026, 4, 2) },
        )

        val connect = service.connectProvider(authCode = "test")
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

        service.connectProvider(authCode = "test")
        val before = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        assertNotNull(before?.tokenRef)

        val disconnect = service.disconnectProvider()
        assertTrue(disconnect is GarminActionResult.Success)

        val after = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        assertEquals("DISCONNECTED", after?.connectionState)
        assertEquals(null, after?.tokenRef)
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
    }
}
