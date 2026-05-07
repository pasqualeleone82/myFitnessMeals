package com.myfitnessmeals.app.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.ui.history.MealCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MealCardComponentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mealCard_rendersCoreFieldsAndActions() {
        val meal = fakeMeal(mealEntryId = 42L)

        composeRule.setContent {
            MaterialTheme {
                MealCard(
                    meal = meal,
                    onEditTapped = {},
                    onDeleteTapped = {},
                    onOverrideTapped = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_meal_card_42").assertIsDisplayed()
        composeRule.onNodeWithTag("history_meal_title_42").assertIsDisplayed()
        composeRule.onNodeWithTag("history_meal_portion_42").assertIsDisplayed()
        composeRule.onNodeWithTag("history_meal_macros_42").assertIsDisplayed()
        composeRule.onNodeWithTag("history_meal_edit_42").assertIsDisplayed()
        composeRule.onNodeWithTag("history_meal_delete_42").assertIsDisplayed()
        composeRule.onNodeWithTag("history_meal_override_42").assertIsDisplayed()
    }

    @Test
    fun mealCard_invokesEditAndDeleteCallbacks() {
        val meal = fakeMeal(mealEntryId = 7L)
        var editTapped = 0
        var deleteTapped = 0
        var overrideTapped = 0

        composeRule.setContent {
            MaterialTheme {
                MealCard(
                    meal = meal,
                    onEditTapped = { editTapped += 1 },
                    onDeleteTapped = { deleteTapped += 1 },
                    onOverrideTapped = { overrideTapped += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("history_meal_edit_7").performClick()
        composeRule.onNodeWithTag("history_meal_delete_7").performClick()
        composeRule.onNodeWithTag("history_meal_override_7").performClick()

        assertEquals(1, editTapped)
        assertEquals(1, deleteTapped)
        assertEquals(1, overrideTapped)
    }

    private fun fakeMeal(mealEntryId: Long): HistoryMealCard {
        return HistoryMealCard(
            mealEntryId = mealEntryId,
            foodId = 11L,
            foodName = "Pasta",
            brand = "Test",
            mealType = MealType.LUNCH,
            quantityValue = 150.0,
            quantityUnit = "g",
            kcal = 525.0,
            protein = 18.0,
            carbs = 105.0,
            fat = 2.3,
            saturatedFat = 0.4,
            sugar = 3.6,
            iron = 3.0,
            calcium = 30.0,
            magnesium = 45.0,
            zinc = 1.6,
            vitaminC = 0.0,
            vitaminD = 0.0,
            vitaminB12 = 0.0,
            isOverridden = false,
            sourceLabel = "CACHE",
        )
    }
}
