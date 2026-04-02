package com.myfitnessmeals.app.barcode

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.ui.meal.MealLoggingScreen
import com.myfitnessmeals.app.ui.meal.MealLoggingUiState
import org.junit.Rule
import org.junit.Test

class MealLoggingBarcodeErrorStateTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsBarcodeNotFoundErrorWhenLookupFails() {
        composeRule.setContent {
            MealLoggingScreen(
                state = MealLoggingUiState(
                    selectedMealType = MealType.BREAKFAST,
                    errorMessage = "Barcode not found",
                ),
                onSearchQueryChanged = {},
                onSearchClicked = {},
                showCameraPermissionFallback = false,
                onBarcodeChanged = {},
                onBarcodeLookupClicked = {},
                onBarcodeScanClicked = {},
                onMealTypeSelected = {},
                onFoodSelected = {},
                onQuantityChanged = {},
                onUnitChanged = {},
                onOverrideKcalChanged = {},
                onOverrideCarbChanged = {},
                onOverrideFatChanged = {},
                onOverrideProteinChanged = {},
                onOverrideNoteChanged = {},
                onOverrideSaveClicked = {},
                onOverrideClearClicked = {},
                onSaveClicked = {},
                onRetryClicked = {},
                onDeleteEntry = {},
            )
        }

        composeRule.onNodeWithTag("meal_error").assertIsDisplayed()
        composeRule.onNodeWithText("Barcode not found").assertIsDisplayed()
    }

    @Test
    fun showsScannerErrorWhenCameraScannerFails() {
        composeRule.setContent {
            MealLoggingScreen(
                state = MealLoggingUiState(
                    selectedMealType = MealType.BREAKFAST,
                    errorMessage = "Unable to start camera scanner",
                ),
                onSearchQueryChanged = {},
                onSearchClicked = {},
                showCameraPermissionFallback = false,
                onBarcodeChanged = {},
                onBarcodeLookupClicked = {},
                onBarcodeScanClicked = {},
                onMealTypeSelected = {},
                onFoodSelected = {},
                onQuantityChanged = {},
                onUnitChanged = {},
                onOverrideKcalChanged = {},
                onOverrideCarbChanged = {},
                onOverrideFatChanged = {},
                onOverrideProteinChanged = {},
                onOverrideNoteChanged = {},
                onOverrideSaveClicked = {},
                onOverrideClearClicked = {},
                onSaveClicked = {},
                onRetryClicked = {},
                onDeleteEntry = {},
            )
        }

        composeRule.onNodeWithTag("meal_error").assertIsDisplayed()
        composeRule.onNodeWithText("Unable to start camera scanner").assertIsDisplayed()
    }

    @Test
    fun showsRetryButtonWhenErrorIsRetryable() {
        composeRule.setContent {
            MealLoggingScreen(
                state = MealLoggingUiState(
                    selectedMealType = MealType.BREAKFAST,
                    errorMessage = "Network timeout. Check connection and retry.",
                    showRetryAction = true,
                ),
                onSearchQueryChanged = {},
                onSearchClicked = {},
                showCameraPermissionFallback = false,
                onBarcodeChanged = {},
                onBarcodeLookupClicked = {},
                onBarcodeScanClicked = {},
                onMealTypeSelected = {},
                onFoodSelected = {},
                onQuantityChanged = {},
                onUnitChanged = {},
                onOverrideKcalChanged = {},
                onOverrideCarbChanged = {},
                onOverrideFatChanged = {},
                onOverrideProteinChanged = {},
                onOverrideNoteChanged = {},
                onOverrideSaveClicked = {},
                onOverrideClearClicked = {},
                onSaveClicked = {},
                onRetryClicked = {},
                onDeleteEntry = {},
            )
        }

        composeRule.onNodeWithTag("meal_error_retry").assertIsDisplayed()
    }
}
