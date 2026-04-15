package com.myfitnessmeals.app.main

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.MainActivity
import com.myfitnessmeals.app.R
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class MainTabNavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomTabs_navigateToExpectedScreens() {
        completeOnboardingIfVisible()

        composeRule.onNodeWithTag("main_tab_dashboard").performClick()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("main_tab_meal").performClick()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("main_tab_history").performClick()
        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("main_tab_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun quickAdd_inlineActions_openMenuAndNavigateToMeal() {
        completeOnboardingIfVisible()

        assertQuickAddTabLivesInsideBottomBar()

        composeRule.onNodeWithTag("main_tab_quick_add").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.quick_add_food)).performClick()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("main_tab_dashboard").performClick()
        composeRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()

        grantCameraPermission()
        composeRule.onNodeWithTag("main_tab_quick_add").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.quick_scan_barcode)).performClick()
        composeRule.onNodeWithTag("meal_barcode_scanner_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("meal_barcode_scanner_close").performClick()
        composeRule.onNodeWithTag("meal_screen").assertIsDisplayed()
    }

    @Test
    fun settingsTabLabel_staysSingleLine_onCompactWidth_withItalianLocale() {
        completeOnboardingIfVisible()

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val widthDp = with(composeRule.density) { rootBounds.width.toDp() }
        assumeTrue(
            "Compact-width matrix case applies only to <=411dp widths; actual=$widthDp",
            widthDp <= 411.dp
        )

        val italianSettingsLabel = localizedString(R.string.main_tab_settings, Locale.ITALIAN)
        setAppLocaleToItalian()

        composeRule.onNodeWithTag("main_tab_settings_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(italianSettingsLabel)

        val settingsLabelBounds = composeRule
            .onNodeWithTag("main_tab_settings_label", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val settingsLabelHeightDp = with(composeRule.density) { settingsLabelBounds.height.toDp() }
        assertTrue(
            "Expected settings label to remain single-line in compact Italian locale; height=$settingsLabelHeightDp",
            settingsLabelHeightDp <= 24.dp
        )
    }

    private fun assertQuickAddTabLivesInsideBottomBar() {
        val quickAddNode = composeRule.onNodeWithTag("main_tab_quick_add", useUnmergedTree = true).fetchSemanticsNode()
        var current: SemanticsNode? = quickAddNode
        while (current != null) {
            if (current.config.contains(SemanticsProperties.TestTag) &&
                current.config[SemanticsProperties.TestTag] == "main_tab_bar") {
                return
            }
            current = current.parent
        }
        fail("Expected quick-add tab to be rendered inside main_tab_bar")
    }

    private fun localizedString(stringRes: Int, locale: Locale): String {
        val config = Configuration(composeRule.activity.resources.configuration)
        config.setLocale(locale)
        val localizedContext = composeRule.activity.createConfigurationContext(config)
        return localizedContext.resources.getString(stringRes)
    }

    private fun setAppLocaleToItalian() {
        assumeTrue(
            "LocaleManager-based locale switching requires API 33+; actual=${Build.VERSION.SDK_INT}",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
        composeRule.runOnIdle {
            val localeManager = composeRule.activity.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = LocaleList.forLanguageTags("it")
            composeRule.activity.recreate()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("main_tab_settings_label", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun grantCameraPermission() {
        val packageName = composeRule.activity.packageName
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("pm grant $packageName android.permission.CAMERA").close()
        composeRule.waitForIdle()
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
