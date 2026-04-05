package com.myfitnessmeals.app.meal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.test.espresso.Espresso.closeSoftKeyboard
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry

class MealLoggingFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var seededChickenId: Long = -1L

    @Before
    fun resetAndSeedDatabase() {
        composeRule.waitForIdle()
        runBlocking(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build()
            try {
                database.clearAllTables()
                database.foodDao().upsert(
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
                database.foodDao().upsert(
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

                seededChickenId = requireNotNull(database.foodDao().getByBarcode("9000000000001")) {
                    "Seed chicken row missing after setup"
                }.id
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun mealFlow_searchAddAndDelete_smoke() {
        completeOnboardingIfVisible()
        openMealTab()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("meal_search_input").performTextClearance()
        composeRule.onNodeWithTag("meal_search_input").performTextInput("seed-local Chicken")
        composeRule.onNodeWithTag("meal_search_button").performClick()

        composeRule.onNodeWithTag("meal_result_$seededChickenId").assertIsDisplayed()
        composeRule.onNodeWithTag("meal_result_$seededChickenId").performClick()

        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("120")
        clickSaveMealEntry()
        composeRule.waitForIdle()
    }

    @Test
    fun mealFlow_canLogAtLeastOneEntryForEachMealType() {
        completeOnboardingIfVisible()
        openMealTab()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("meal_search_input").performTextClearance()
        composeRule.onNodeWithTag("meal_search_input").performTextInput("seed-local Chicken")
        composeRule.onNodeWithTag("meal_search_button").performClick()
        composeRule.onNodeWithTag("meal_result_$seededChickenId").performClick()

        selectMealType("meal_type_breakfast")
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        clickSaveMealEntry()

        selectMealType("meal_type_lunch")
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        clickSaveMealEntry()

        selectMealType("meal_type_dinner")
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        clickSaveMealEntry()

        selectMealType("meal_type_snack")
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        clickSaveMealEntry()
    }

    @Test
    fun mealFlow_overrideShowsProvenanceAndCanBeCleared() {
        completeOnboardingIfVisible()
        openMealTab()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("meal_search_input").performTextClearance()
        composeRule.onNodeWithTag("meal_search_input").performTextInput("seed-local Chicken")
        composeRule.onNodeWithTag("meal_search_button").performClick()
        composeRule.onNodeWithTag("meal_result_$seededChickenId").performClick()

        composeRule.onNodeWithTag("meal_screen").performScrollToNode(hasTestTag("override_kcal_input"))

        composeRule.onNodeWithTag("override_kcal_input").assertIsDisplayed()
        composeRule.onNodeWithTag("override_save_button").assertIsDisplayed()

        composeRule.onNodeWithTag("override_kcal_input").performTextClearance()
        composeRule.onNodeWithTag("override_kcal_input").performTextInput("200")
        composeRule.onNodeWithTag("override_save_button").performClick()

        composeRule.onNodeWithTag("override_clear_button").performClick()
        composeRule.onNodeWithTag("meal_resolved_source").assertIsDisplayed()
    }

    private fun clickSaveMealEntry() {
        runCatching { closeSoftKeyboard() }
        composeRule.onNodeWithTag("meal_screen").performScrollToNode(hasTestTag("meal_save_button"))
        composeRule.onNodeWithTag("meal_save_button").performClick()
        composeRule.waitForIdle()
    }

    private fun openMealTab() {
        composeRule.onNodeWithTag("main_tab_meal").performClick()
    }

    private fun selectMealType(tag: String) {
        composeRule.onNodeWithTag("meal_screen").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun completeOnboardingIfVisible() {
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isEmpty()) {
            return
        }
        composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("onboarding_age").performTextClearance()
            composeRule.onNodeWithTag("onboarding_age").performTextInput("30")
            composeRule.onNodeWithTag("onboarding_height").performTextClearance()
            composeRule.onNodeWithTag("onboarding_height").performTextInput("175")
            composeRule.onNodeWithTag("onboarding_weight").performTextClearance()
            composeRule.onNodeWithTag("onboarding_weight").performTextInput("75")
            composeRule.onNodeWithTag("onboarding_carb").performTextClearance()
            composeRule.onNodeWithTag("onboarding_carb").performTextInput("40")
            composeRule.onNodeWithTag("onboarding_fat").performTextClearance()
            composeRule.onNodeWithTag("onboarding_fat").performTextInput("30")
            composeRule.onNodeWithTag("onboarding_protein").performTextClearance()
            composeRule.onNodeWithTag("onboarding_protein").performTextInput("30")
            composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("main_tab_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
    }

}
