package com.myfitnessmeals.app.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.DailySummaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardHistoryFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetDatabase() {
        composeRule.waitForIdle()
        runBlocking(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build()
            try {
                database.clearAllTables()
                seedDashboardHistoryData(database)
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun dashboardAndHistoryTabs_renderCoreCardsAndWidgets() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_dashboard").performClick()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_kcal_target").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_kcal_intake").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_kcal_burned").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_kcal_remaining").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_macro_carb").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_macro_fat").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_macro_protein").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_widget_steps").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_widget_weight").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_widget_exercise_kcal").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_widget_workout_minutes").assertIsDisplayed()
        composeRule.onNodeWithText("Target: 2450", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Intake: 1800", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Burned: 320", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Remaining: 970", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Carbs: 210", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Fat: 70", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Protein: 170", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("main_tab_history").performClick()
        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("history_range_label").assertIsDisplayed()
        composeRule.onNodeWithTag("history_selected_date").assertIsDisplayed()
        composeRule.onNodeWithText(LocalDate.now().toString()).assertIsDisplayed()
        composeRule.onNodeWithText("Remaining: 970", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("history_prev_button").performClick()
        composeRule.onNodeWithTag("history_selected_remaining").assertIsDisplayed()
        composeRule.onNodeWithText(LocalDate.now().minusDays(1).toString()).assertIsDisplayed()
        composeRule.onNodeWithText("Remaining: 650", substring = true).assertIsDisplayed()
    }

    private suspend fun seedDashboardHistoryData(database: AppDatabase) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()

        database.dailySummaryDao().upsert(
            DailySummaryEntity(
                localDate = today,
                kcalTarget = 2450.0,
                kcalIntake = 1800.0,
                kcalBurned = 320.0,
                kcalRemaining = 970.0,
                carbTotal = 210.0,
                fatTotal = 70.0,
                proteinTotal = 170.0,
                updatedAt = now,
            )
        )

        database.dailySummaryDao().upsert(
            DailySummaryEntity(
                localDate = yesterday,
                kcalTarget = 2100.0,
                kcalIntake = 1600.0,
                kcalBurned = 150.0,
                kcalRemaining = 650.0,
                carbTotal = 160.0,
                fatTotal = 55.0,
                proteinTotal = 120.0,
                updatedAt = now,
            )
        )
    }

    private fun completeOnboardingIfVisible() {
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isEmpty()) {
            return
        }
        composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("main_tab_meal").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
