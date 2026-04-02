package com.myfitnessmeals.app.barcode

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.myfitnessmeals.app.ui.barcode.BarcodeLookupSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BarcodeLookupSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsManualFallbackMessageWhenCameraPermissionDenied() {
        var lookupClicks = 0

        composeRule.setContent {
            var barcodeState by mutableStateOf("")
            BarcodeLookupSection(
                barcode = barcodeState,
                showCameraPermissionFallback = true,
                onBarcodeChanged = {
                    barcodeState = it
                },
                onLookupClicked = { lookupClicks += 1 },
                onScanClicked = {},
            )
        }

        composeRule.onNodeWithTag("meal_barcode_permission_fallback").assertIsDisplayed()
        composeRule.onNodeWithTag("meal_barcode_input").performTextInput("1234567890123")
        composeRule.onNodeWithTag("meal_barcode_button").performClick()

        assertEquals(1, lookupClicks)
    }

    @Test
    fun scanButtonInvokesCallback() {
        var scanClicks = 0

        composeRule.setContent {
            BarcodeLookupSection(
                barcode = "",
                showCameraPermissionFallback = false,
                onBarcodeChanged = {},
                onLookupClicked = {},
                onScanClicked = { scanClicks += 1 },
            )
        }

        composeRule.onNodeWithTag("meal_barcode_scan_button").assertIsDisplayed()
        composeRule.onNodeWithTag("meal_barcode_scan_button").performClick()

        assertTrue(scanClicks == 1)
    }
}
