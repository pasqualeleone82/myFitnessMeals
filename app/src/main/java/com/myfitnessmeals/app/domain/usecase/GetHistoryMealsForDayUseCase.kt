package com.myfitnessmeals.app.domain.usecase

import com.myfitnessmeals.app.data.local.MealEntryEntity
import com.myfitnessmeals.app.data.repository.LocalDiaryRepository
import com.myfitnessmeals.app.data.repository.LocalFoodRepository
import com.myfitnessmeals.app.data.repository.OverrideRepository
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.model.ResolvedSource

class GetHistoryMealsForDayUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val foodRepository: LocalFoodRepository,
    private val overrideRepository: OverrideRepository,
) {
    suspend operator fun invoke(localDate: String): List<HistoryMealCard> {
        require(localDate.isNotBlank()) { "localDate must not be blank" }

        val entries = diaryRepository.getMealEntries(localDate)
        if (entries.isEmpty()) {
            return emptyList()
        }

        val foodIds = entries.map { it.foodId }.distinct()
        val foodsById = foodRepository.getFoodsByIds(foodIds)
        val overridesByFoodId = overrideRepository.getOverridesByFoodIds(foodIds)

        return entries.map { entry ->
            entry.toHistoryMealCard(
                food = foodsById[entry.foodId],
                hasOverride = overridesByFoodId.containsKey(entry.foodId),
            )
        }
    }

    private fun MealEntryEntity.toHistoryMealCard(
        food: com.myfitnessmeals.app.data.local.FoodItemEntity?,
        hasOverride: Boolean,
    ): HistoryMealCard {
        val source = resolvedSource.toResolvedSourceOrNull()
        val mealTypeValue = mealType.toMealTypeOrFallback()
        val isOverridden = when (source) {
            ResolvedSource.OVERRIDE -> true
            ResolvedSource.CACHE, ResolvedSource.OFF -> false
            null -> hasOverride
        }
        val sourceLabel = when (source) {
            ResolvedSource.OVERRIDE -> "Manual"
            ResolvedSource.CACHE, ResolvedSource.OFF -> source.name
            null -> if (hasOverride) "Manual" else "CACHE"
        }

        return HistoryMealCard(
            mealEntryId = id,
            foodId = foodId,
            foodName = food?.name ?: "Food #$foodId",
            brand = food?.brand,
            mealType = mealTypeValue,
            quantityValue = quantityValue,
            quantityUnit = quantityUnit,
            kcal = kcalTotal,
            protein = proteinTotal,
            carbs = carbTotal,
            fat = fatTotal,
            saturatedFat = saturatedFatTotal,
            sugar = sugarTotal,
            iron = ironTotal,
            calcium = calciumTotal,
            magnesium = magnesiumTotal,
            zinc = zincTotal,
            vitaminC = vitaminCTotal,
            vitaminD = vitaminDTotal,
            vitaminB12 = vitaminB12Total,
            isOverridden = isOverridden,
            sourceLabel = sourceLabel,
        )
    }

    private fun String.toMealTypeOrFallback(): MealType {
        return runCatching { MealType.valueOf(trim().uppercase()) }
            .getOrElse { MealType.SNACK }
    }

    private fun String.toResolvedSourceOrNull(): ResolvedSource? {
        return runCatching { ResolvedSource.valueOf(trim().uppercase()) }
            .getOrNull()
    }
}
