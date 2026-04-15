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
            saturatedFat100 = 1.0,
            sugar100 = 0.5,
            iron100 = 2.2,
            calcium100 = 15.0,
            magnesium100 = 20.0,
            zinc100 = 1.3,
            vitaminC100 = 0.0,
            vitaminD100 = 0.2,
            vitaminB12100 = 0.5,
        )
        val override = NutritionOverrideEntity(
            foodId = 1L,
            kcal100 = 200.0,
            carb100 = 2.0,
            fat100 = 5.0,
            protein100 = 35.0,
            saturatedFat100 = 2.0,
            sugar100 = null,
            iron100 = 3.0,
            calcium100 = 25.0,
            magnesium100 = 30.0,
            zinc100 = 2.0,
            vitaminC100 = 5.0,
            vitaminD100 = 0.4,
            vitaminB12100 = null,
            note = "label",
            createdAt = 1L,
            updatedAt = 2L,
        )

        val resolved = service.resolve(food, override)

        assertEquals(200.0, resolved.kcal100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.carb100 ?: 0.0, 0.001)
        assertEquals(5.0, resolved.fat100 ?: 0.0, 0.001)
        assertEquals(35.0, resolved.protein100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.saturatedFat100 ?: 0.0, 0.001)
        assertEquals(0.5, resolved.sugar100 ?: 0.0, 0.001)
        assertEquals(3.0, resolved.iron100 ?: 0.0, 0.001)
        assertEquals(25.0, resolved.calcium100 ?: 0.0, 0.001)
        assertEquals(30.0, resolved.magnesium100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.zinc100 ?: 0.0, 0.001)
        assertEquals(5.0, resolved.vitaminC100 ?: 0.0, 0.001)
        assertEquals(0.4, resolved.vitaminD100 ?: 0.0, 0.001)
        assertEquals(0.5, resolved.vitaminB12100 ?: 0.0, 0.001)
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
            saturatedFat100 = 0.2,
            sugar100 = 1.5,
            iron100 = 0.3,
            calcium100 = 8.0,
            magnesium100 = 4.0,
            zinc100 = 0.1,
            vitaminC100 = 2.0,
            vitaminD100 = null,
            vitaminB12100 = 0.0,
        )

        val resolved = service.resolve(food, null)

        assertEquals(40.0, resolved.kcal100 ?: 0.0, 0.001)
        assertEquals(null, resolved.carb100)
        assertEquals(1.0, resolved.fat100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.protein100 ?: 0.0, 0.001)
        assertEquals(0.2, resolved.saturatedFat100 ?: 0.0, 0.001)
        assertEquals(1.5, resolved.sugar100 ?: 0.0, 0.001)
        assertEquals(0.3, resolved.iron100 ?: 0.0, 0.001)
        assertEquals(8.0, resolved.calcium100 ?: 0.0, 0.001)
        assertEquals(4.0, resolved.magnesium100 ?: 0.0, 0.001)
        assertEquals(0.1, resolved.zinc100 ?: 0.0, 0.001)
        assertEquals(2.0, resolved.vitaminC100 ?: 0.0, 0.001)
        assertEquals(null, resolved.vitaminD100)
        assertEquals(0.0, resolved.vitaminB12100 ?: -1.0, 0.001)
        assertEquals(ResolvedSource.OFF, resolved.source)
    }
}
