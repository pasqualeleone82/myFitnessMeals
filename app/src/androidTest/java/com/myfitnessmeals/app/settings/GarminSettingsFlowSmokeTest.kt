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

class GarminSettingsFlowSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settings_canConnectSyncAndDisconnectGarmin() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_garmin_connect").performClick()
        composeRule.onNodeWithText("Garmin connected").assertIsDisplayed()
        composeRule.onNodeWithText("Connection: CONNECTED").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_garmin_sync").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("settings_garmin_last_sync").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("settings_garmin_last_sync").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_garmin_disconnect").performClick()
        composeRule.onNodeWithText("Garmin disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("Connection: DISCONNECTED").assertIsDisplayed()
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
