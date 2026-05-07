package com.myfitnessmeals.app.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.data.local.AppDatabase
import com.myfitnessmeals.app.data.local.DailySummaryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DashboardNavigationToHistoryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val historyDateValueFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
    private lateinit var anchorDate: LocalDate

    @Before
    fun seedData() {
        composeRule.waitForIdle()
        runBlocking(Dispatchers.IO) {
            anchorDate = LocalDate.now()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.databaseBuilder(context, AppDatabase::class.java, "myfitnessmeals.db")
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
                .build()
            try {
                database.clearAllTables()
                val now = System.currentTimeMillis()
                database.dailySummaryDao().upsert(
                    DailySummaryEntity(
                        localDate = anchorDate.toString(),
                        kcalTarget = 2400.0,
                        kcalIntake = 1800.0,
                        kcalBurned = 200.0,
                        kcalRemaining = 800.0,
                        carbTotal = 200.0,
                        fatTotal = 60.0,
                        proteinTotal = 150.0,
                        updatedAt = now,
                    )
                )
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun tappingMacroCard_opensHistoryOnToday() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_dashboard").performClick()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_macro_card").performClick()

        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("history_selected_date")
            .assertTextContains(anchorDate.format(historyDateValueFormatter), substring = true)
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
