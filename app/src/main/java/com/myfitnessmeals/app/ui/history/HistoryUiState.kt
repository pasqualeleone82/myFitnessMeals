package com.myfitnessmeals.app.ui.history

import com.myfitnessmeals.app.domain.model.HistoryDaySnapshot
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import java.time.LocalDate

data class HistoryUiState(
    val days: List<HistoryDaySnapshot> = emptyList(),
    val selectedDate: String? = null,
    val mealsForSelectedDay: List<HistoryMealCard> = emptyList(),
    val isLoadingMeals: Boolean = false,
    val isSwipeInProgress: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedIndex: Int
        get() {
            val date = selectedDate ?: return 0
            return days.indexOfFirst { it.localDate == date }.takeIf { it >= 0 } ?: 0
        }

    val selectedDay: HistoryDaySnapshot?
        get() = days.getOrNull(selectedIndex)

    val canGoPrevious: Boolean
        get() = selectedIndex < days.lastIndex

    val canGoNext: Boolean
        get() = selectedIndex > 0

    val todayDate: String
        get() = LocalDate.now().toString()

    val oldestAvailableDate: String?
        get() = days.lastOrNull()?.localDate
}
