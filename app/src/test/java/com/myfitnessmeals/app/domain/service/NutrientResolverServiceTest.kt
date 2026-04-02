package com.myfitnessmeals.app.domain.service

import com.myfitnessmeals.app.data.local.NutritionOverrideEntity
import com.myfitnessmeals.app.domain.model.ResolvedSource
import com.myfitnessmeals.app.domain.usecase.MealFoodCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class NutrientResolverServiceTest {
    private val service = NutrientResolverService()

    @Test
    fun resolve_prioritizesOverrideValuesAndSource() {
        val food = MealFoodCandidate(
            id = 1L,
            name = "Chicken",
            brand = "Brand",
            source = ResolvedSource.CACHE,
            kcal100 = 165.0,
            carb100 = 0.0,
            fat100 = 3.6,
            protein100 = 31.0,
        )
        val override = NutritionOverrideEntity(
            foodId = 1L,
            kcal100 = 200.0,
            carb100 = 2.0,
            fat100 = 5.0,
            protein100 = 35.0,
            note = "label",
            createdAt = 1L,
            updatedAt = 2L,
        )

        val resolved = service.resolve(food, override)

        assertEquals(200.0, resolved.kcal100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.carb100 ?: 0.0, 0.001)
        assertEquals(5.0, resolved.fat100 ?: 0.0, 0.001)
        assertEquals(35.0, resolved.protein100 ?: 0.0, 0.001)
        assertEquals(ResolvedSource.OVERRIDE, resolved.source)
    }

    @Test
    fun resolve_usesBaseFoodWhenNoOverride() {
        val food = MealFoodCandidate(
            id = 2L,
            name = "Soup",
            brand = "Brand",
            source = ResolvedSource.OFF,
            kcal100 = 40.0,
            carb100 = null,
            fat100 = 1.0,
            protein100 = 2.0,
        )

        val resolved = service.resolve(food, null)

        assertEquals(40.0, resolved.kcal100 ?: 0.0, 0.001)
        assertEquals(null, resolved.carb100)
        assertEquals(1.0, resolved.fat100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.protein100 ?: 0.0, 0.001)
        assertEquals(ResolvedSource.OFF, resolved.source)
    }
}
