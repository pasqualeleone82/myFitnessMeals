package com.myfitnessmeals.app.integration.garmin

import com.myfitnessmeals.app.data.local.ProviderConnectionEntity
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.domain.model.ProviderType
import com.myfitnessmeals.app.security.OAuthToken
import com.myfitnessmeals.app.security.OAuthTokenStore
import java.time.LocalDate
import java.util.UUID

enum class GarminSyncMode {
    APP_OPEN,
    MANUAL,
}

data class GarminProviderStatus(
    val connectionState: String,
    val lastSyncAt: Long?,
    val lastErrorCode: String?,
)

data class GarminDailyMetrics(
    val steps: Int,
    val activeKcal: Double,
    val workoutMinutes: Int,
)

data class GarminTokenPayload(
    val accessToken: String,
    val refreshToken: String,
    val scopes: List<String>,
)

sealed class GarminActionResult {
    data class Success(val message: String) : GarminActionResult()

    data class Error(
        val message: String,
        val code: String,
        val retryable: Boolean,
    ) : GarminActionResult()
}

interface GarminClient {
    suspend fun exchangeAuthCodeForToken(authCode: String): GarminTokenPayload

    suspend fun fetchDailyMetrics(accessToken: String, date: LocalDate): GarminDailyMetrics
}

class FakeGarminClient : GarminClient {
    override suspend fun exchangeAuthCodeForToken(authCode: String): GarminTokenPayload {
        return GarminTokenPayload(
            accessToken = "garmin-access-$authCode",
            refreshToken = "garmin-refresh-$authCode",
            scopes = listOf("activity", "profile"),
        )
    }

    override suspend fun fetchDailyMetrics(accessToken: String, date: LocalDate): GarminDailyMetrics {
        val daySeed = date.dayOfMonth
        return GarminDailyMetrics(
            steps = 7000 + daySeed * 100,
            activeKcal = 220.0 + daySeed,
            workoutMinutes = 25 + (daySeed % 6) * 5,
        )
    }
}

class GarminIntegrationService(
    private val providerConnectionRepository: LocalProviderConnectionRepository,
    private val fitnessRepository: LocalFitnessRepository,
    private val tokenStore: OAuthTokenStore,
    private val garminClient: GarminClient = FakeGarminClient(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val nowDate: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun connectProvider(authCode: String = "local-mvp-code"): GarminActionResult {
        return try {
            val tokenPayload = garminClient.exchangeAuthCodeForToken(authCode)
            val tokenRef = UUID.randomUUID().toString()
            tokenStore.putToken(
                tokenRef = tokenRef,
                token = OAuthToken(
                    accessToken = tokenPayload.accessToken,
                    refreshToken = tokenPayload.refreshToken,
                ),
            )

            providerConnectionRepository.upsertConnection(
                ProviderConnectionEntity(
                    provider = ProviderType.GARMIN.name,
                    connectionState = STATE_CONNECTED,
                    tokenRef = tokenRef,
                    scopes = tokenPayload.scopes.joinToString(","),
                    lastSyncAt = null,
                    lastErrorCode = null,
                    updatedAt = nowEpochMillis(),
                )
            )
            GarminActionResult.Success("Garmin connected")
        } catch (_: Exception) {
            GarminActionResult.Error(
                message = "Unable to connect Garmin",
                code = "CONNECT_FAILED",
                retryable = false,
            )
        }
    }

    suspend fun disconnectProvider(): GarminActionResult {
        val existing = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        existing?.tokenRef?.let { tokenStore.removeToken(it) }

        providerConnectionRepository.upsertConnection(
            ProviderConnectionEntity(
                provider = ProviderType.GARMIN.name,
                connectionState = STATE_DISCONNECTED,
                tokenRef = null,
                scopes = "",
                lastSyncAt = existing?.lastSyncAt,
                lastErrorCode = null,
                updatedAt = nowEpochMillis(),
            )
        )

        return GarminActionResult.Success("Garmin disconnected")
    }

    suspend fun syncFitness(mode: GarminSyncMode): GarminActionResult {
        val existing = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        if (existing?.connectionState != STATE_CONNECTED || existing.tokenRef.isNullOrBlank()) {
            return GarminActionResult.Error(
                message = "Garmin is not connected",
                code = "NOT_CONNECTED",
                retryable = false,
            )
        }

        val token = tokenStore.getToken(existing.tokenRef)
        if (token == null) {
            providerConnectionRepository.upsertConnection(
                existing.copy(
                    connectionState = STATE_REAUTH_REQUIRED,
                    lastErrorCode = "TOKEN_MISSING",
                    updatedAt = nowEpochMillis(),
                )
            )
            return GarminActionResult.Error(
                message = "Garmin token missing, reconnect required",
                code = "TOKEN_MISSING",
                retryable = false,
            )
        }

        return try {
            val date = nowDate()
            val metrics = garminClient.fetchDailyMetrics(token.accessToken, date)
            val now = nowEpochMillis()

            fitnessRepository.upsertDailyFitness(
                localDate = date.toString(),
                provider = ProviderType.GARMIN,
                steps = metrics.steps,
                activeKcal = metrics.activeKcal,
                workoutMinutes = metrics.workoutMinutes,
                syncStatus = "SUCCESS",
                lastSyncAt = now,
            )

            providerConnectionRepository.upsertConnection(
                existing.copy(
                    connectionState = STATE_CONNECTED,
                    lastSyncAt = now,
                    lastErrorCode = null,
                    updatedAt = now,
                )
            )

            val modeLabel = if (mode == GarminSyncMode.APP_OPEN) "app-open" else "manual"
            GarminActionResult.Success("Garmin sync completed ($modeLabel)")
        } catch (_: Exception) {
            providerConnectionRepository.upsertConnection(
                existing.copy(
                    lastErrorCode = "SYNC_FAILED",
                    updatedAt = nowEpochMillis(),
                )
            )
            GarminActionResult.Error(
                message = "Garmin sync failed",
                code = "SYNC_FAILED",
                retryable = true,
            )
        }
    }

    suspend fun getProviderStatus(): GarminProviderStatus {
        val connection = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        return GarminProviderStatus(
            connectionState = connection?.connectionState ?: STATE_DISCONNECTED,
            lastSyncAt = connection?.lastSyncAt,
            lastErrorCode = connection?.lastErrorCode,
        )
    }

    private companion object {
        const val STATE_CONNECTED = "CONNECTED"
        const val STATE_DISCONNECTED = "DISCONNECTED"
        const val STATE_REAUTH_REQUIRED = "REAUTH_REQUIRED"
    }
}
