package com.myfitnessmeals.app.domain.usecase

import android.content.Context
import androidx.room.withTransaction
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.security.OAuthTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class ExportUserDataResult(
    val filePath: String,
    val exportedAtEpochMs: Long,
)

class ExportUserDataUseCase(
    private val appContext: Context,
    private val database: AppDatabase,
    private val settingsRepository: UserSettingsRepository,
) {
    suspend operator fun invoke(): ExportUserDataResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", isoTimestamp(now))
            .put("exportedAtEpochMs", now)

        val settings = settingsRepository.getSettings()
        root.put(
            "settings",
            JSONObject()
                .put("onboardingCompleted", settings.onboardingCompleted)
                .put("age", settings.age)
                .put("heightCm", settings.heightCm)
                .put("weightKg", settings.weightKg)
                .put("sex", settings.sex.name)
                .put("activityLevel", settings.activityLevel.name)
                .put("goalType", settings.goalType.name)
                .put("targetKcal", settings.targetKcal)
                .put("carbPct", settings.carbPct)
                .put("fatPct", settings.fatPct)
                .put("proteinPct", settings.proteinPct)
                .put("themePreference", settings.themePreference.name)
        )

        root.put("foodItems", JSONArray().apply {
            database.foodDao().getAll().forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("sourceId", item.sourceId)
                        .put("source", item.source)
                        .put("name", item.name)
                        .put("brand", item.brand)
                        .put("barcode", item.barcode)
                        .put("kcal100", item.kcal100)
                        .put("carb100", item.carb100)
                        .put("fat100", item.fat100)
                        .put("protein100", item.protein100)
                        .put("lastSyncedAt", item.lastSyncedAt)
                        .put("canonicalExternalKey", item.canonicalExternalKey)
                )
            }
        })

        root.put("nutritionOverrides", JSONArray().apply {
            database.nutritionOverrideDao().getAll().forEach { row ->
                put(
                    JSONObject()
                        .put("foodId", row.foodId)
                        .put("kcal100", row.kcal100)
                        .put("carb100", row.carb100)
                        .put("fat100", row.fat100)
                        .put("protein100", row.protein100)
                        .put("note", row.note)
                        .put("createdAt", row.createdAt)
                        .put("updatedAt", row.updatedAt)
                )
            }
        })

        root.put("mealEntries", JSONArray().apply {
            database.mealEntryDao().getAll().forEach { row ->
                put(
                    JSONObject()
                        .put("id", row.id)
                        .put("localDate", row.localDate)
                        .put("timezoneOffsetMin", row.timezoneOffsetMin)
                        .put("mealType", row.mealType)
                        .put("foodId", row.foodId)
                        .put("quantityValue", row.quantityValue)
                        .put("quantityUnit", row.quantityUnit)
                        .put("resolvedSource", row.resolvedSource)
                        .put("kcalTotal", row.kcalTotal)
                        .put("carbTotal", row.carbTotal)
                        .put("fatTotal", row.fatTotal)
                        .put("proteinTotal", row.proteinTotal)
                        .put("createdAt", row.createdAt)
                        .put("updatedAt", row.updatedAt)
                )
            }
        })

        root.put("fitnessDaily", JSONArray().apply {
            database.fitnessDailyDao().getAll().forEach { row ->
                put(
                    JSONObject()
                        .put("localDate", row.localDate)
                        .put("provider", row.provider)
                        .put("steps", row.steps)
                        .put("activeKcal", row.activeKcal)
                        .put("workoutMinutes", row.workoutMinutes)
                        .put("lastSyncAt", row.lastSyncAt)
                        .put("syncStatus", row.syncStatus)
                )
            }
        })

        root.put("dailySummary", JSONArray().apply {
            database.dailySummaryDao().getAll().forEach { row ->
                put(
                    JSONObject()
                        .put("localDate", row.localDate)
                        .put("kcalTarget", row.kcalTarget)
                        .put("kcalIntake", row.kcalIntake)
                        .put("kcalBurned", row.kcalBurned)
                        .put("kcalRemaining", row.kcalRemaining)
                        .put("carbTotal", row.carbTotal)
                        .put("fatTotal", row.fatTotal)
                        .put("proteinTotal", row.proteinTotal)
                        .put("updatedAt", row.updatedAt)
                )
            }
        })

        root.put("providerConnections", JSONArray().apply {
            database.providerConnectionDao().getAll().forEach { row ->
                put(
                    JSONObject()
                        .put("provider", row.provider)
                        .put("connectionState", row.connectionState)
                        .put("tokenRef", row.tokenRef)
                        .put("scopes", row.scopes)
                        .put("lastSyncAt", row.lastSyncAt)
                        .put("lastErrorCode", row.lastErrorCode)
                        .put("updatedAt", row.updatedAt)
                )
            }
        })

        val exportDir = File(appContext.filesDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "myfitnessmeals-export-${now}.json")
        exportFile.writeText(root.toString(2))

        ExportUserDataResult(
            filePath = exportFile.absolutePath,
            exportedAtEpochMs = now,
        )
    }

    private fun isoTimestamp(epochMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC))
}

class DeleteAllUserDataUseCase(
    private val database: AppDatabase,
    private val settingsRepository: UserSettingsRepository,
    private val providerConnectionRepository: LocalProviderConnectionRepository,
    private val tokenStore: OAuthTokenStore,
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.clearAllTables()
            providerConnectionRepository.deleteAllConnections()
        }
        tokenStore.removeAllTokens()
        settingsRepository.clearAll()
    }
}
