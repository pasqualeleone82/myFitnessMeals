package com.myfitnessmeals.app.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.DailySummaryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardHistoryFlowSmokeTest {
    private val historyDateValueFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
    private lateinit var anchorDate: LocalDate

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetDatabase() {
        composeRule.waitForIdle()
        runBlocking(Dispatchers.IO) {
            anchorDate = LocalDate.now()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
            try {
                database.clearAllTables()
                seedDashboardHistoryData(database, anchorDate)
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
        assertDashboardNodeVisible("dashboard_kcal_target")
        assertDashboardNodeVisible("dashboard_kcal_intake")
        assertDashboardNodeVisible("dashboard_kcal_burned")
        assertDashboardNodeVisible("dashboard_kcal_remaining")
        assertDashboardNodeVisible("dashboard_macro_card")
        assertDashboardNodeVisible("dashboard_widget_steps")
        assertDashboardNodeVisible("dashboard_widget_weight")
        assertDashboardNodeVisible("dashboard_widget_exercise_kcal")
        assertDashboardNodeVisible("dashboard_widget_workout_minutes")

        composeRule.onNodeWithTag("main_tab_history").performClick()
        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("history_selected_date")
            .assertTextContains(anchorDate.format(historyDateValueFormatter), substring = true)
        composeRule.onNodeWithTag("history_selected_remaining").assertIsDisplayed()

        composeRule.onNodeWithTag("history_prev_button").performClick()
        composeRule.onNodeWithTag("history_selected_remaining").assertIsDisplayed()
        composeRule.onNodeWithTag("history_selected_date")
            .assertTextContains(anchorDate.minusDays(1).format(historyDateValueFormatter), substring = true)
    }

    private fun assertDashboardNodeVisible(tag: String) {
        composeRule.onNodeWithTag("dashboard_screen").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private suspend fun seedDashboardHistoryData(database: AppDatabase, anchorDate: LocalDate) {
        val now = System.currentTimeMillis()
        val today = anchorDate.toString()
        val yesterday = anchorDate.minusDays(1).toString()

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
