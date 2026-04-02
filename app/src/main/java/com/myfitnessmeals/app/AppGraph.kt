package com.myfitnessmeals.app

import android.content.Context
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFitnessRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.data.repository.LocalProviderConnectionRepository
import com.myfitnessmeals.app.data.repository.LocalUserSettingsRepository
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.integration.garmin.GarminIntegrationService
import com.myfitnessmeals.app.domain.service.GoalComputationService
import com.myfitnessmeals.app.security.EncryptedOAuthTokenStore
import com.myfitnessmeals.app.domain.usecase.BuildMealPreviewUseCase
import com.myfitnessmeals.app.domain.usecase.DeleteMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.DeleteNutritionOverrideUseCase
import com.myfitnessmeals.app.domain.usecase.GetMealDaySnapshotUseCase
import com.myfitnessmeals.app.domain.usecase.ObserveDashboardUseCase
import com.myfitnessmeals.app.domain.usecase.ObserveHistoryUseCase
import com.myfitnessmeals.app.domain.usecase.SaveMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.SaveNutritionOverrideUseCase
import com.myfitnessmeals.app.domain.usecase.SearchFoodByBarcodeUseCase
import com.myfitnessmeals.app.domain.usecase.SearchFoodsByTextUseCase
import com.myfitnessmeals.app.worker.GarminSyncWorker

class AppGraph(private val context: Context) {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    private val foodRepository: LocalFoodRepository by lazy {
        LocalFoodRepository(database.foodDao())
    }

    private val diaryRepository: LocalDiaryRepository by lazy {
        LocalDiaryRepository(database)
    }

    private val fitnessRepository: LocalFitnessRepository by lazy {
        LocalFitnessRepository(database)
    }

    private val providerConnectionRepository: LocalProviderConnectionRepository by lazy {
        LocalProviderConnectionRepository(database.providerConnectionDao())
    }

    private val tokenStore by lazy {
        EncryptedOAuthTokenStore(context)
    }

    val overrideRepository: LocalOverrideRepository by lazy {
        LocalOverrideRepository(database.nutritionOverrideDao())
    }

    val userSettingsRepository: UserSettingsRepository by lazy {
        LocalUserSettingsRepository(context)
    }

    val goalComputationService: GoalComputationService by lazy {
        GoalComputationService()
    }

    val garminIntegrationService: GarminIntegrationService by lazy {
        GarminIntegrationService(
            providerConnectionRepository = providerConnectionRepository,
            fitnessRepository = fitnessRepository,
            tokenStore = tokenStore,
        )
    }

    val searchFoodsByTextUseCase: SearchFoodsByTextUseCase by lazy {
        SearchFoodsByTextUseCase(foodRepository)
    }

    val searchFoodByBarcodeUseCase: SearchFoodByBarcodeUseCase by lazy {
        SearchFoodByBarcodeUseCase(foodRepository)
    }

    val buildMealPreviewUseCase: BuildMealPreviewUseCase by lazy {
        BuildMealPreviewUseCase(overrideRepository)
    }

    val saveMealEntryUseCase: SaveMealEntryUseCase by lazy {
        SaveMealEntryUseCase(
            diaryRepository = diaryRepository,
            buildMealPreviewUseCase = buildMealPreviewUseCase,
        )
    }

    val saveNutritionOverrideUseCase: SaveNutritionOverrideUseCase by lazy {
        SaveNutritionOverrideUseCase(overrideRepository)
    }

    val deleteMealEntryUseCase: DeleteMealEntryUseCase by lazy {
        DeleteMealEntryUseCase(diaryRepository)
    }

    val deleteNutritionOverrideUseCase: DeleteNutritionOverrideUseCase by lazy {
        DeleteNutritionOverrideUseCase(overrideRepository)
    }

    val getMealDaySnapshotUseCase: GetMealDaySnapshotUseCase by lazy {
        GetMealDaySnapshotUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
        )
    }

    val observeDashboardUseCase: ObserveDashboardUseCase by lazy {
        ObserveDashboardUseCase(
            diaryRepository = diaryRepository,
            fitnessRepository = fitnessRepository,
            settingsRepository = userSettingsRepository,
        )
    }

    val observeHistoryUseCase: ObserveHistoryUseCase by lazy {
        ObserveHistoryUseCase(
            diaryRepository = diaryRepository,
            settingsRepository = userSettingsRepository,
        )
    }

    fun enqueueGarminAppOpenSync() {
        val request = OneTimeWorkRequestBuilder<GarminSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "garmin_app_open_sync",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    // Debug/test helper used to seed deterministic food rows for local runs and instrumentation tests.
    suspend fun ensureSeedFoods() {
        val existing = foodRepository.getFoodByBarcode("9000000000001")
        if (existing != null) {
            return
        }

        foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "seed-local-1",
                source = "CACHE",
                name = "seed-local Chicken Breast",
                brand = "Local",
                barcode = "9000000000001",
                kcal100 = 165.0,
                carb100 = 0.0,
                fat100 = 3.6,
                protein100 = 31.0,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )

        foodRepository.upsertFood(
            FoodItemEntity(
                sourceId = "seed-local-2",
                source = "CACHE",
                name = "seed-local Mystery Soup",
                brand = "Local",
                barcode = "9000000000002",
                kcal100 = null,
                carb100 = 7.0,
                fat100 = null,
                protein100 = 2.0,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
    }
}
