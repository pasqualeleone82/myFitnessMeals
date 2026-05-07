package com.myfitnessmeals.app.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.myfitnessmeals.app.ui.history.NutrientOverrideDialog
import com.myfitnessmeals.app.ui.history.NutrientOverrideInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class NutrientOverrideDialogComponentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun save_withAtLeastOneField_callsOnSave() {
        var savedInput: NutrientOverrideInput? = null

        composeRule.setContent {
            MaterialTheme {
                NutrientOverrideDialog(
                    foodName = "Pasta",
                    onSave = { savedInput = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_override_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("history_override_kcal_input").performTextInput("210")
        composeRule.onNodeWithTag("history_override_protein_input").performTextInput("14.5")
        composeRule.onNodeWithTag("history_override_note_input").performTextInput("manual test")

        composeRule.onNodeWithText("Save override").performClick()

        assertNotNull(savedInput)
        assertEquals(210.0, savedInput!!.kcal100!!, 0.001)
        assertEquals(14.5, savedInput!!.protein100!!, 0.001)
        assertEquals("manual test", savedInput?.note)
    }

    @Test
    fun save_withInvalidNumber_showsErrorAndDoesNotSave() {
        var savedCount = 0

        composeRule.setContent {
            MaterialTheme {
                NutrientOverrideDialog(
                    foodName = "Pasta",
                    onSave = { savedCount += 1 },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("history_override_kcal_input").performTextInput("abc")
        composeRule.onNodeWithText("Save override").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Enter valid numbers (>= 0)").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, savedCount)
    }
}
