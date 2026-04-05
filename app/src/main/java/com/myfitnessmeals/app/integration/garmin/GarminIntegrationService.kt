package com.myfitnessmeals.app.integration.garmin

import com.myfitnessmeals.app.data.local.ProviderConnectionEntity
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.domain.model.ProviderType
import com.myfitnessmeals.app.observability.NoOpObservabilityTracker
import com.myfitnessmeals.app.observability.ObservabilityTracker
import com.myfitnessmeals.app.security.OAuthToken
import com.myfitnessmeals.app.security.OAuthTokenStore
import com.myfitnessmeals.app.security.isExpired
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

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
    val expiresInSec: Long,
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

    suspend fun refreshAccessToken(refreshToken: String): GarminTokenPayload

    suspend fun fetchDailyMetrics(accessToken: String, date: LocalDate): GarminDailyMetrics
}

class FakeGarminClient : GarminClient {
    override suspend fun exchangeAuthCodeForToken(authCode: String): GarminTokenPayload {
        return GarminTokenPayload(
            accessToken = "garmin-access-$authCode",
            refreshToken = "garmin-refresh-$authCode",
            scopes = listOf("activity", "profile"),
            expiresInSec = TimeUnit.HOURS.toSeconds(1),
        )
    }

    override suspend fun refreshAccessToken(refreshToken: String): GarminTokenPayload {
        val seed = refreshToken.takeLast(12)
        return GarminTokenPayload(
            accessToken = "garmin-access-refresh-$seed",
            refreshToken = refreshToken,
            scopes = listOf("activity", "profile"),
            expiresInSec = TimeUnit.HOURS.toSeconds(1),
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
    private val observabilityTracker: ObservabilityTracker = NoOpObservabilityTracker,
    private val garminClient: GarminClient = FakeGarminClient(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val nowDate: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun connectProvider(authCode: String): GarminActionResult {
        val sanitizedAuthCode = authCode.trim()
        if (!isValidAuthCode(sanitizedAuthCode)) {
            return GarminActionResult.Error(
                message = "Invalid Garmin authorization code",
                code = "INVALID_AUTH_CODE",
                retryable = false,
            )
        }

        return try {
            val tokenPayload = garminClient.exchangeAuthCodeForToken(sanitizedAuthCode)
            if (!isValidTokenPayload(tokenPayload)) {
                return GarminActionResult.Error(
                    message = "Garmin returned invalid token payload",
                    code = "INVALID_TOKEN_PAYLOAD",
                    retryable = false,
                )
            }
            val tokenRef = UUID.randomUUID().toString()
            val previousConnection = providerConnectionRepository.getConnection(ProviderType.GARMIN)
            val now = nowEpochMillis()
            tokenStore.putToken(
                tokenRef = tokenRef,
                token = OAuthToken(
                    accessToken = tokenPayload.accessToken,
                    refreshToken = tokenPayload.refreshToken,
                    expiresAtEpochSeconds = now.toEpochSeconds() + tokenPayload.expiresInSec,
                ),
            )

            try {
                providerConnectionRepository.upsertConnection(
                    ProviderConnectionEntity(
                        provider = ProviderType.GARMIN.name,
                        connectionState = STATE_CONNECTED,
                        tokenRef = tokenRef,
                        scopes = tokenPayload.scopes.joinToString(","),
                        lastSyncAt = null,
                        lastErrorCode = null,
                        updatedAt = now,
                    )
                )
                previousConnection?.tokenRef
                    ?.takeIf { it != tokenRef }
                    ?.let(tokenStore::removeToken)
            } catch (error: Exception) {
                tokenStore.removeToken(tokenRef)
                throw error
            }

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
        val modeLabel = if (mode == GarminSyncMode.APP_OPEN) "app_open" else "manual"
        val existing = providerConnectionRepository.getConnection(ProviderType.GARMIN)
        if (existing?.connectionState != STATE_CONNECTED || existing.tokenRef.isNullOrBlank()) {
            observabilityTracker.trackProviderSync(
                mode = modeLabel,
                success = false,
                errorCode = "NOT_CONNECTED",
                retryable = false,
            )
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
            observabilityTracker.trackProviderSync(
                mode = modeLabel,
                success = false,
                errorCode = "TOKEN_MISSING",
                retryable = false,
            )
            return GarminActionResult.Error(
                message = "Garmin token missing, reconnect required",
                code = "TOKEN_MISSING",
                retryable = false,
            )
        }

        val activeToken = if (token.isExpired(nowEpochMillis().toEpochSeconds())) {
            val refreshed = try {
                garminClient.refreshAccessToken(token.refreshToken)
            } catch (_: Exception) {
                null
            }

            if (refreshed == null || !isValidTokenPayload(refreshed)) {
                providerConnectionRepository.upsertConnection(
                    existing.copy(
                        connectionState = STATE_REAUTH_REQUIRED,
                        lastErrorCode = "TOKEN_EXPIRED",
                        updatedAt = nowEpochMillis(),
                    )
                )
                return GarminActionResult.Error(
                    message = "Garmin token expired, reconnect required",
                    code = "TOKEN_EXPIRED",
                    retryable = false,
                )
            }

            val refreshedToken = OAuthToken(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
                expiresAtEpochSeconds = nowEpochMillis().toEpochSeconds() + refreshed.expiresInSec,
            )
            tokenStore.putToken(existing.tokenRef, refreshedToken)
            refreshedToken
        } else {
            token
        }

        return try {
            val date = nowDate()
            val metrics = garminClient.fetchDailyMetrics(activeToken.accessToken, date)
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

            observabilityTracker.trackProviderSync(
                mode = modeLabel,
                success = true,
            )

            val modeText = if (mode == GarminSyncMode.APP_OPEN) "app-open" else "manual"
            GarminActionResult.Success("Garmin sync completed ($modeText)")
        } catch (_: Exception) {
            providerConnectionRepository.upsertConnection(
                existing.copy(
                    lastErrorCode = "SYNC_FAILED",
                    updatedAt = nowEpochMillis(),
                )
            )
            observabilityTracker.trackProviderSync(
                mode = modeLabel,
                success = false,
                errorCode = "SYNC_FAILED",
                retryable = true,
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
        val REQUIRED_SCOPES = setOf("activity", "profile")
        val AUTH_CODE_REGEX = Regex("^[A-Za-z0-9._~-]{16,512}$")
    }

    private fun isValidAuthCode(authCode: String): Boolean = AUTH_CODE_REGEX.matches(authCode)

    private fun isValidTokenPayload(payload: GarminTokenPayload): Boolean {
        if (payload.accessToken.isBlank() || payload.refreshToken.isBlank()) return false
        if (payload.expiresInSec <= 0L) return false
        val scopes = payload.scopes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return scopes.containsAll(REQUIRED_SCOPES)
    }

    private fun Long.toEpochSeconds(): Long = this / 1000L
}
