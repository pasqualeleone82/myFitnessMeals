package com.myfitnessmeals.app.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun HistoryRoute(viewModel: HistoryViewModel) {
    val state by viewModel.uiState.collectAsState()
    HistoryScreen(
        state = state,
        onPrevious = viewModel::showPreviousDay,
        onNext = viewModel::showNextDay,
        onSwipeLeft = viewModel::onSwipeLeft,
        onSwipeRight = viewModel::onSwipeRight,
        onAddMealTapped = viewModel::onMealAdded,
    )
}
