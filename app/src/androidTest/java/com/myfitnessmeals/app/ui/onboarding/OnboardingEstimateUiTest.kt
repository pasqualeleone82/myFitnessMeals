package com.myfitnessmeals.app.ui.onboarding

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.intl.Locale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingEstimateUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun estimateCard_isVisibleAndAbovePrimaryCta() {
        composeRule.setContent {
            MaterialTheme {
                OnboardingScreen(
                    state = OnboardingUiState(computedTargetKcal = 2300.6),
                    onAgeChanged = {},
                    onHeightChanged = {},
                    onWeightChanged = {},
                    onCarbChanged = {},
                    onFatChanged = {},
                    onProteinChanged = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_screen")
            .performScrollToNode(hasTestTag("onboarding_target_card"))
        composeRule.onNodeWithTag("onboarding_target").assertTextContains("2301", substring = true)
        composeRule.onNodeWithTag("onboarding_screen")
            .performScrollToNode(hasTestTag("onboarding_complete_button"))
        composeRule.onNodeWithTag("onboarding_complete_button").assertIsDisplayed()

        val estimateBounds = composeRule.onNodeWithTag("onboarding_target_card").fetchSemanticsNode().boundsInRoot
        val ctaBounds = composeRule.onNodeWithTag("onboarding_complete_button").fetchSemanticsNode().boundsInRoot

        assertTrue("Expected estimate card to be positioned before CTA", estimateBounds.bottom <= ctaBounds.top)
    }

    @Test
    fun estimateValue_usesEnglishUnitForEnLocale() {
        setLocalizedContent(localeTag = "en")

        composeRule.onNodeWithText("2301 kcal/day").assertIsDisplayed()
    }

    @Test
    fun estimateValue_usesItalianUnitForItLocale() {
        setLocalizedContent(localeTag = "it")

        composeRule.onNodeWithText("2301 kcal/giorno").assertIsDisplayed()
    }

    private fun setLocalizedContent(localeTag: String) {
        val targetContext = composeRule.activity.localizedContext(localeTag)

        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides targetContext) {
                MaterialTheme {
                    OnboardingScreen(
                        state = OnboardingUiState(computedTargetKcal = 2300.6),
                        onAgeChanged = {},
                        onHeightChanged = {},
                        onWeightChanged = {},
                        onCarbChanged = {},
                        onFatChanged = {},
                        onProteinChanged = {},
                        onComplete = {},
                    )
                }
            }
        }
    }

    private fun Context.localizedContext(localeTag: String): Context {
        val locale = Locale(localeTag)
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(android.os.LocaleList.forLanguageTags(locale.toLanguageTag()))
        return createConfigurationContext(configuration)
    }
}
