package com.myfitnessmeals.app.domain.usecase

import com.myfitnessmeals.app.data.local.FoodItemEntity
import com.myfitnessmeals.app.data.local.MealEntryEntity
import com.myfitnessmeals.app.data.local.NutritionOverrideEntity
import com.myfitnessmeals.app.data.repository.FoodLookupResult
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.OverrideRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.NewMealEntry
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.service.NutrientResolverService
import com.myfitnessmeals.app.integration.off.OffCatalogError
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

data class SaveNutritionOverrideCommand(
    val foodId: Long,
    val kcal100: Double?,
    val carb100: Double?,
    val fat100: Double?,
    val protein100: Double?,
    val note: String?,
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
    data class Error(
        val message: String,
        val retryable: Boolean,
        val code: String,
    ) : MealSearchResult()
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
            is FoodLookupResult.Error -> result.error.toMealSearchError()
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
            is FoodLookupResult.Error -> result.error.toMealSearchError()
        }
    }
}

private fun OffCatalogError.toMealSearchError(): MealSearchResult.Error {
    return when (this) {
        OffCatalogError.Timeout -> MealSearchResult.Error(
            message = "Network timeout. Check connection and retry.",
            retryable = true,
            code = "OFF_TIMEOUT",
        )

        OffCatalogError.RateLimit -> MealSearchResult.Error(
            message = "Open Food Facts is rate limited. Retry in a moment.",
            retryable = true,
            code = "OFF_RATE_LIMIT",
        )

        OffCatalogError.Unavailable -> MealSearchResult.Error(
            message = "Service unavailable. You can keep using cached foods and retry.",
            retryable = true,
            code = "OFF_UNAVAILABLE",
        )

        OffCatalogError.MalformedPayload -> MealSearchResult.Error(
            message = "Unexpected provider response. Please retry.",
            retryable = true,
            code = "OFF_MALFORMED_PAYLOAD",
        )
    }
}

class BuildMealPreviewUseCase(
    private val overrideRepository: OverrideRepository,
    private val nutrientResolverService: NutrientResolverService = NutrientResolverService(),
) {
    suspend operator fun invoke(
        food: MealFoodCandidate,
        quantity: Double,
        unit: String,
    ): MealPreview {
        require(quantity > 0) { "Meal quantity must be positive" }

        val override = overrideRepository.getOverrideByFoodId(food.id)
        val resolved = nutrientResolverService.resolve(food = food, override = override)

        val factor = quantityToFactor(quantity = quantity, unit = unit)
        return MealPreview(
            quantity = quantity,
            unit = unit,
            kcalTotal = (resolved.kcal100 ?: 0.0) * factor,
            carbTotal = (resolved.carb100 ?: 0.0) * factor,
            fatTotal = (resolved.fat100 ?: 0.0) * factor,
            proteinTotal = (resolved.protein100 ?: 0.0) * factor,
            kcalMissing = resolved.kcal100 == null,
            carbMissing = resolved.carb100 == null,
            fatMissing = resolved.fat100 == null,
            proteinMissing = resolved.protein100 == null,
            resolvedSource = resolved.source,
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

class SaveNutritionOverrideUseCase(
    private val overrideRepository: OverrideRepository,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend operator fun invoke(command: SaveNutritionOverrideCommand) {
        require(command.foodId > 0) { "Food id must be positive" }

        val nutrients = listOf(command.kcal100, command.carb100, command.fat100, command.protein100)
        require(nutrients.any { it != null }) { "At least one nutrient override is required" }
        nutrients.filterNotNull().forEach { value ->
            require(value >= 0.0) { "Nutrient override must be >= 0" }
        }

        val existing = overrideRepository.getOverrideByFoodId(command.foodId)
        val now = nowEpochMillis()
        overrideRepository.upsertOverride(
            NutritionOverrideEntity(
                foodId = command.foodId,
                kcal100 = command.kcal100,
                carb100 = command.carb100,
                fat100 = command.fat100,
                protein100 = command.protein100,
                note = command.note?.trim()?.ifBlank { null },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
    }
}

class DeleteNutritionOverrideUseCase(
    private val overrideRepository: OverrideRepository,
) {
    suspend operator fun invoke(foodId: Long): Boolean {
        require(foodId > 0) { "Food id must be positive" }
        return overrideRepository.deleteOverrideByFoodId(foodId) > 0
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
