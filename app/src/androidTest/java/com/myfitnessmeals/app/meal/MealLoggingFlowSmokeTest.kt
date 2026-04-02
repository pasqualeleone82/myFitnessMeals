package com.myfitnessmeals.app.meal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeUp
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.FoodItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("meal_search_input").performTextClearance()
        composeRule.onNodeWithTag("meal_search_input").performTextInput("seed-local Chicken")
        composeRule.onNodeWithTag("meal_search_button").performClick()

        composeRule.onNodeWithTag("meal_result_$seededChickenId").assertIsDisplayed()
        composeRule.onNodeWithTag("meal_result_$seededChickenId").performClick()

        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("120")
        composeRule.onNodeWithTag("meal_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            mealEntryCount() == 1L
        }

        repeat(3) {
            composeRule.onNodeWithTag("meal_screen").performTouchInput { swipeUp() }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Delete")[0].performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            mealEntryCount() == 0L
        }
    }

    @Test
    fun mealFlow_canLogAtLeastOneEntryForEachMealType() {
        completeOnboardingIfVisible()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("meal_search_input").performTextClearance()
        composeRule.onNodeWithTag("meal_search_input").performTextInput("seed-local Chicken")
        composeRule.onNodeWithTag("meal_search_button").performClick()
        composeRule.onNodeWithTag("meal_result_$seededChickenId").performClick()

        composeRule.onNodeWithTag("meal_type_breakfast").performClick()
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        composeRule.onNodeWithTag("meal_save_button").performClick()

        composeRule.onNodeWithTag("meal_type_lunch").performClick()
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        composeRule.onNodeWithTag("meal_save_button").performClick()

        composeRule.onNodeWithTag("meal_type_dinner").performClick()
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        composeRule.onNodeWithTag("meal_save_button").performClick()

        composeRule.onNodeWithTag("meal_type_snack").performClick()
        composeRule.onNodeWithTag("meal_quantity_input").performTextClearance()
        composeRule.onNodeWithTag("meal_quantity_input").performTextInput("100")
        composeRule.onNodeWithTag("meal_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            mealEntryCount() == 4L
        }
    }

    @Test
    fun mealFlow_overrideShowsProvenanceAndCanBeCleared() {
        completeOnboardingIfVisible()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("meal_search_input").performTextClearance()
        composeRule.onNodeWithTag("meal_search_input").performTextInput("seed-local Chicken")
        composeRule.onNodeWithTag("meal_search_button").performClick()
        composeRule.onNodeWithTag("meal_result_$seededChickenId").performClick()

        repeat(2) {
            composeRule.onNodeWithTag("meal_screen").performTouchInput { swipeUp() }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("override_kcal_input").assertIsDisplayed()
                composeRule.onNodeWithTag("override_save_button").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("override_kcal_input").performTextClearance()
        composeRule.onNodeWithTag("override_kcal_input").performTextInput("200")
        composeRule.onNodeWithTag("override_save_button").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("meal_override_updated_at").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("override_clear_button").performClick()
        composeRule.onNodeWithTag("meal_resolved_source").assertIsDisplayed()
    }

    private fun mealEntryCount(): Long = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        try {
            database.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM meal_entry")
                .use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getLong(0)
                }
        } finally {
            database.close()
        }
    }

    private fun completeOnboardingIfVisible() {
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isEmpty()) {
            return
        }
        composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("meal_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
