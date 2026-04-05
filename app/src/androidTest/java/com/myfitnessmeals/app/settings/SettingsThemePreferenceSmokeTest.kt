package com.myfitnessmeals.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
        scrollToBottomSettings()
        composeRule.onNodeWithTag("settings_save_button").performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("main_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_theme_current").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_theme_dark").assertIsDisplayed()
    }

    private fun scrollToBottomSettings() {
        repeat(2) {
            composeRule.onNodeWithTag("settings_screen").performTouchInput { swipeUp() }
        }
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
