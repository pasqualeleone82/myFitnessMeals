package com.myfitnessmeals.app.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.myfitnessmeals.app.domain.model.HistoryMealCard

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onAddMealTapped: (selectedDate: String) -> Unit,
    onEditMealTapped: (meal: HistoryMealCard, selectedDate: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val selectedDate = state.selectedDate ?: state.todayDate

    HistoryScreen(
        state = state,
        onPrevious = viewModel::showPreviousDay,
        onNext = viewModel::showNextDay,
        onSwipeLeft = viewModel::onSwipeLeft,
        onSwipeRight = viewModel::onSwipeRight,
        onAddMealTapped = { onAddMealTapped(selectedDate) },
        onEditMealTapped = { meal -> onEditMealTapped(meal, selectedDate) },
        onDeleteMealConfirmed = viewModel::onDeleteMealConfirmed,
        onSaveMealOverride = { meal, input ->
            viewModel.onSaveMealOverride(
                mealEntryId = meal.mealEntryId,
                foodId = meal.foodId,
                input = input,
            )
        },
    )
}
