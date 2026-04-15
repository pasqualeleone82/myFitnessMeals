package com.myfitnessmeals.app.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.DailySummaryEntity
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HistoryScreenShellTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetDatabase() {
        composeRule.waitForIdle()
        runBlocking(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
            try {
                database.clearAllTables()
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun historyTab_isLabeledStorico() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_history").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.main_tab_history)).assertIsDisplayed()
        composeRule.onNodeWithText("Storico").assertIsDisplayed()
    }

    @Test
    fun emptyDay_showsMessageAndAddMealButton() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_history").performClick()
        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("history_empty_message").assertIsDisplayed()
        composeRule.onNodeWithTag("history_empty_message")
            .assertTextEquals(composeRule.activity.getString(R.string.history_empty_state_title))
        composeRule.onNodeWithTag("empty_state_add_meal_button").assertIsDisplayed()
    }

    @Test
    fun dateHeaderAndTotals_updateWhenSelectedDayChanges() {
        seedTwoDays()
        completeOnboardingIfVisible()

        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()

        composeRule.onNodeWithTag("main_tab_history").performClick()
        composeRule.onNodeWithTag("history_selected_date").assertTextEquals(today)
        composeRule.onNodeWithTag("history_daily_totals_card").assertIsDisplayed()
        composeRule.onNodeWithTag("history_total_intake").assertTextContains("1800.0")

        composeRule.onNodeWithTag("history_prev_button").performClick()
        composeRule.onNodeWithTag("history_selected_date").assertTextEquals(yesterday)
        composeRule.onNodeWithTag("history_total_intake").assertTextContains("1600.0")
    }

    private fun seedTwoDays() {
        runBlocking(Dispatchers.IO) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
            try {
                val now = System.currentTimeMillis()
                database.dailySummaryDao().upsert(
                    DailySummaryEntity(
                        localDate = LocalDate.now().toString(),
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
                        localDate = LocalDate.now().minusDays(1).toString(),
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
            } finally {
                database.close()
            }
        }
    }

    private fun completeOnboardingIfVisible() {
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isEmpty()) {
            return
        }
        composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("main_tab_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
    }
}