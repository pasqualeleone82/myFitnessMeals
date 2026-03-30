package com.myfitnessmeals.app

import android.content.Context
import androidx.room.Room
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.domain.usecase.BuildMealPreviewUseCase
import com.myfitnessmeals.app.domain.usecase.DeleteMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.GetMealDaySnapshotUseCase
import com.myfitnessmeals.app.domain.usecase.SaveMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.SearchFoodByBarcodeUseCase
import com.myfitnessmeals.app.domain.usecase.SearchFoodsByTextUseCase

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

    private val overrideRepository: LocalOverrideRepository by lazy {
        LocalOverrideRepository(database.nutritionOverrideDao())
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

    val deleteMealEntryUseCase: DeleteMealEntryUseCase by lazy {
        DeleteMealEntryUseCase(diaryRepository)
    }

    val getMealDaySnapshotUseCase: GetMealDaySnapshotUseCase by lazy {
        GetMealDaySnapshotUseCase(
            diaryRepository = diaryRepository,
            foodRepository = foodRepository,
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
