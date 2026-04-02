package com.myfitnessmeals.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.myfitnessmeals.app.MainActivity
import org.junit.Rule
import org.junit.Test

class SettingsThemePreferenceSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settings_themeSelectionPersistsAfterSaveAndRecreate() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_theme_dark").performClick()
        composeRule.onNodeWithTag("settings_save_button").performClick()
        composeRule.onNodeWithTag("settings_saved").assertIsDisplayed()
        composeRule.onNodeWithText("Current theme: DARK").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("main_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Current theme: DARK").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_theme_current").assertIsDisplayed()
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
