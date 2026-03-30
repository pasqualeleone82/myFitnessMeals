package com.myfitnessmeals.app.domain.usecase

import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.local.MealEntryEntity
import com.myfitnessmeals.app.data.local.NutritionOverrideEntity
import com.myfitnessmeals.app.data.repository.FoodLookupResult
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ResolvedSource
import java.time.LocalDate
import java.time.ZoneOffset

data class MealFoodCandidate(
    val id: Long,
    val name: String,
    val brand: String?,
    val source: ResolvedSource,
    val kcal100: Double?,
    val carb100: Double?,
    val fat100: Double?,
    val protein100: Double?,
)

data class MealPreview(
    val quantity: Double,
    val unit: String,
    val kcalTotal: Double,
    val carbTotal: Double,
    val fatTotal: Double,
    val proteinTotal: Double,
    val kcalMissing: Boolean,
    val carbMissing: Boolean,
    val fatMissing: Boolean,
    val proteinMissing: Boolean,
    val resolvedSource: ResolvedSource,
)

data class SaveMealEntryCommand(
    val mealType: MealType,
    val food: MealFoodCandidate,
    val quantity: Double,
    val unit: String,
)

data class MealLoggedEntry(
    val id: Long,
    val mealType: MealType,
    val foodName: String,
    val quantityValue: Double,
    val quantityUnit: String,
    val kcalTotal: Double,
    val carbTotal: Double,
    val fatTotal: Double,
    val proteinTotal: Double,
)

data class MealDaySnapshot(
    val entries: List<MealLoggedEntry>,
    val kcalIntake: Double,
    val carbTotal: Double,
    val fatTotal: Double,
    val proteinTotal: Double,
)

sealed class MealSearchResult {
    data class Success(val items: List<MealFoodCandidate>) : MealSearchResult()
    data object NotFound : MealSearchResult()
    data class Error(val message: String) : MealSearchResult()
}

class SearchFoodsByTextUseCase(
    private val foodRepository: LocalFoodRepository,
) {
    suspend operator fun invoke(query: String): MealSearchResult {
        return when (val result = foodRepository.searchFoodByText(query)) {
            is FoodLookupResult.Success -> MealSearchResult.Success(
                result.data.map { it.toCandidate(result.source) }
            )
            is FoodLookupResult.NotFound -> MealSearchResult.NotFound
            is FoodLookupResult.Error -> MealSearchResult.Error(result.error::class.simpleName ?: "Unknown")
        }
    }
}

class SearchFoodByBarcodeUseCase(
    private val foodRepository: LocalFoodRepository,
) {
    suspend operator fun invoke(barcode: String): MealSearchResult {
        return when (val result = foodRepository.searchFoodByBarcode(barcode)) {
            is FoodLookupResult.Success -> MealSearchResult.Success(
                listOf(result.data.toCandidate(result.source))
            )
            is FoodLookupResult.NotFound -> MealSearchResult.NotFound
            is FoodLookupResult.Error -> MealSearchResult.Error(result.error::class.simpleName ?: "Unknown")
        }
    }
}

class BuildMealPreviewUseCase(
    private val overrideRepository: LocalOverrideRepository,
) {
    suspend operator fun invoke(
        food: MealFoodCandidate,
        quantity: Double,
        unit: String,
    ): MealPreview {
        require(quantity > 0) { "Meal quantity must be positive" }

        val override = overrideRepository.getOverrideByFoodId(food.id)
        val resolvedSource = if (override != null) ResolvedSource.OVERRIDE else food.source

        val kcal100 = override?.kcal100 ?: food.kcal100
        val carb100 = override?.carb100 ?: food.carb100
        val fat100 = override?.fat100 ?: food.fat100
        val protein100 = override?.protein100 ?: food.protein100

        val factor = quantityToFactor(quantity = quantity, unit = unit)
        return MealPreview(
            quantity = quantity,
            unit = unit,
            kcalTotal = (kcal100 ?: 0.0) * factor,
            carbTotal = (carb100 ?: 0.0) * factor,
            fatTotal = (fat100 ?: 0.0) * factor,
            proteinTotal = (protein100 ?: 0.0) * factor,
            kcalMissing = kcal100 == null,
            carbMissing = carb100 == null,
            fatMissing = fat100 == null,
            proteinMissing = protein100 == null,
            resolvedSource = resolvedSource,
        )
    }

    private fun quantityToFactor(quantity: Double, unit: String): Double {
        return when (unit.trim().lowercase()) {
            "g", "ml" -> quantity / 100.0
            "serving" -> quantity
            else -> quantity / 100.0
        }
    }
}

class SaveMealEntryUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val buildMealPreviewUseCase: BuildMealPreviewUseCase,
    private val nowDateProvider: () -> LocalDate = { LocalDate.now() },
    private val nowOffsetProvider: () -> ZoneOffset = { ZoneOffset.systemDefault().rules.getOffset(java.time.Instant.now()) },
) {
    suspend operator fun invoke(command: SaveMealEntryCommand): Long {
        require(command.quantity > 0) { "Meal quantity must be positive" }

        val preview = buildMealPreviewUseCase(
            food = command.food,
            quantity = command.quantity,
            unit = command.unit,
        )

        val localDate = nowDateProvider().toString()
        val timezoneOffsetMin = nowOffsetProvider().totalSeconds / 60

        return diaryRepository.addMealEntry(
            NewMealEntry(
                localDate = localDate,
                timezoneOffsetMin = timezoneOffsetMin,
                mealType = command.mealType,
                foodId = command.food.id,
                quantityValue = command.quantity,
                quantityUnit = command.unit,
                resolvedSource = preview.resolvedSource,
                kcalTotal = preview.kcalTotal,
                carbTotal = preview.carbTotal,
                fatTotal = preview.fatTotal,
                proteinTotal = preview.proteinTotal,
            )
        )
    }
}

class DeleteMealEntryUseCase(
    private val diaryRepository: LocalDiaryRepository,
) {
    suspend operator fun invoke(entryId: Long): Boolean = diaryRepository.deleteMealEntry(entryId)
}

class GetMealDaySnapshotUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val foodRepository: LocalFoodRepository,
    private val nowDateProvider: () -> LocalDate = { LocalDate.now() },
) {
    suspend operator fun invoke(): MealDaySnapshot {
        val localDate = nowDateProvider().toString()
        val entries = diaryRepository.getMealEntries(localDate)
        val summary = diaryRepository.getDailySummary(localDate)

        return MealDaySnapshot(
            entries = entries.map { it.toLoggedEntry(foodRepository.getFoodById(it.foodId)) },
            kcalIntake = summary?.kcalIntake ?: 0.0,
            carbTotal = summary?.carbTotal ?: 0.0,
            fatTotal = summary?.fatTotal ?: 0.0,
            proteinTotal = summary?.proteinTotal ?: 0.0,
        )
    }

    private fun MealEntryEntity.toLoggedEntry(food: FoodItemEntity?): MealLoggedEntry {
        return MealLoggedEntry(
            id = id,
            mealType = MealType.valueOf(mealType.uppercase()),
            foodName = food?.name ?: "Food #$foodId",
            quantityValue = quantityValue,
            quantityUnit = quantityUnit,
            kcalTotal = kcalTotal,
            carbTotal = carbTotal,
            fatTotal = fatTotal,
            proteinTotal = proteinTotal,
        )
    }
}

private fun FoodItemEntity.toCandidate(source: ResolvedSource): MealFoodCandidate {
    return MealFoodCandidate(
        id = id,
        name = name,
        brand = brand,
        source = source,
        kcal100 = kcal100,
        carb100 = carb100,
        fat100 = fat100,
        protein100 = protein100,
    )
}
