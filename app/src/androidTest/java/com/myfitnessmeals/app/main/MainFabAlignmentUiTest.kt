package com.myfitnessmeals.app.main

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.MainActivity
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MainFabAlignmentUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun centerQuickAddTab_isAlignedToViewportAndHasMinimumTouchTarget() {
        completeOnboardingIfVisible()
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        assertQuickAddAlignmentAndTouchTarget()
    }

    @Test
    fun centerQuickAddTab_isAlignedToViewport_onCompactPortraitWidths() {
        completeOnboardingIfVisible()
        setOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val widthDp = with(composeRule.density) { rootBounds.width.toDp() }
        assumeTrue(
            "Compact-width matrix case applies only to <=411dp portrait widths; actual=$widthDp",
            widthDp <= 411.dp
        )

        assertQuickAddAlignmentAndTouchTarget()
    }

    @Test
    fun centerQuickAddTab_isAlignedToViewport_inLandscape() {
        completeOnboardingIfVisible()
        try {
            setOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assertQuickAddAlignmentAndTouchTarget()
        } finally {
            setOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    private fun assertQuickAddAlignmentAndTouchTarget() {
        completeOnboardingIfVisible()

        val quickAddBounds = findVisibleBoundsForTag("main_tab_quick_add")
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        val oneDpTolerancePx = with(composeRule.density) { 1.dp.toPx() }

        val quickAddCenterX = quickAddBounds.center.x
        val rootCenterX = rootBounds.center.x
        val quickAddToRootDelta = abs(quickAddCenterX - rootCenterX)

        assertTrue(
            "Quick-add center must stay within 1dp of viewport center; quickAdd=$quickAddCenterX root=$rootCenterX delta=$quickAddToRootDelta tol=$oneDpTolerancePx",
            quickAddToRootDelta <= oneDpTolerancePx
        )

        composeRule.onNodeWithTag("main_tab_quick_add", useUnmergedTree = true)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun findVisibleBoundsForTag(tag: String): Rect {
        val taggedNode = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return nearestNonZeroBounds(taggedNode)
    }

    private fun nearestNonZeroBounds(node: SemanticsNode): Rect {
        var current: SemanticsNode? = node
        while (current != null) {
            val bounds = current.boundsInRoot
            if (bounds.width > 0f && bounds.height > 0f) {
                return bounds
            }
            current = current.parent
        }
        return node.boundsInRoot
    }

    private fun setOrientation(orientation: Int) {
        composeRule.runOnIdle {
            composeRule.activity.requestedOrientation = orientation
        }

        val expectedConfiguration = when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            else -> null
        }
        if (expectedConfiguration != null) {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.activity.resources.configuration.orientation == expectedConfiguration
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("main_tab_quick_add", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val quickAddNodes = composeRule.onAllNodesWithTag("main_tab_quick_add", useUnmergedTree = true)
                .fetchSemanticsNodes()
            quickAddNodes.isNotEmpty() && nearestNonZeroBounds(quickAddNodes.first()).width > 0f
        }
    }

    private fun completeOnboardingIfVisible() {
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isEmpty()) {
            return
        }
        composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        if (composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("onboarding_age").performTextClearance()
            composeRule.onNodeWithTag("onboarding_age").performTextInput("30")
            composeRule.onNodeWithTag("onboarding_height").performTextClearance()
            composeRule.onNodeWithTag("onboarding_height").performTextInput("175")
            composeRule.onNodeWithTag("onboarding_weight").performTextClearance()
            composeRule.onNodeWithTag("onboarding_weight").performTextInput("75")
            composeRule.onNodeWithTag("onboarding_carb").performTextClearance()
            composeRule.onNodeWithTag("onboarding_carb").performTextInput("40")
            composeRule.onNodeWithTag("onboarding_fat").performTextClearance()
            composeRule.onNodeWithTag("onboarding_fat").performTextInput("30")
            composeRule.onNodeWithTag("onboarding_protein").performTextClearance()
            composeRule.onNodeWithTag("onboarding_protein").performTextInput("30")
            composeRule.onNodeWithTag("onboarding_complete_button").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("main_tab_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
