package com.myfitnessmeals.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.domain.model.HistoryDaySnapshot
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import com.myfitnessmeals.app.domain.usecase.DeleteMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.GetHistoryMealsForDayUseCase
import com.myfitnessmeals.app.domain.usecase.ObserveHistoryUseCase
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val getHistoryMealsForDayUseCase: GetHistoryMealsForDayUseCase,
    private val deleteMealEntryUseCase: DeleteMealEntryUseCase,
    private val historyLoader: suspend (Int) -> List<HistoryDaySnapshot> = { days ->
        observeHistoryUseCase(days).first()
    },
    private val mealsLoader: suspend (String) -> List<HistoryMealCard> = { localDate ->
        getHistoryMealsForDayUseCase(localDate)
    },
    private val deleteMealAction: suspend (Long) -> Unit = { mealEntryId ->
        deleteMealEntryUseCase(mealEntryId)
    },
    private val swipeDebounceMs: Long = DEFAULT_SWIPE_DEBOUNCE_MS,
    private val nowEpochMillis: () -> Long = { Instant.now().toEpochMilli() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var lastSwipeAtMs: Long = Long.MIN_VALUE
    private var latestMealsLoadId: Long = 0L

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val days = historyLoader(90)
                val previousDate = _uiState.value.selectedDate
                val selectedDate = when {
                    days.isEmpty() -> null
                    previousDate != null && days.any { it.localDate == previousDate } -> previousDate
                    else -> days.first().localDate
                }

                _uiState.update {
                    it.copy(
                        days = days,
                        selectedDate = selectedDate,
                        mealsForSelectedDay = if (selectedDate == null) emptyList() else it.mealsForSelectedDay,
                        errorMessage = null,
                    )
                }

                scheduleMealsLoadForSelectedDay()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Unable to load history") }
            }
        }
    }

    fun showPreviousDay() {
        moveSelection(direction = SwipeDirection.RIGHT)
    }

    fun showNextDay() {
        moveSelection(direction = SwipeDirection.LEFT)
    }

    fun onSwipeRight() {
        moveSelection(direction = SwipeDirection.RIGHT)
    }

    fun onSwipeLeft() {
        moveSelection(direction = SwipeDirection.LEFT)
    }

    fun onMealAdded() {
        refresh()
    }

    fun onMealEdited() {
        refresh()
    }

    fun onMealOverrideSaved() {
        refresh()
    }

    fun onDeleteMealConfirmed(mealEntryId: Long) {
        viewModelScope.launch {
            try {
                deleteMealAction(mealEntryId)
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Unable to delete meal") }
            }
        }
    }

    private fun moveSelection(direction: SwipeDirection) {
        val currentState = _uiState.value
        val nextDate = when (direction) {
            SwipeDirection.RIGHT -> currentState.nextPreviousDate()
            SwipeDirection.LEFT -> currentState.nextForwardDate()
        } ?: return

        val now = nowEpochMillis()
        if (lastSwipeAtMs != Long.MIN_VALUE && now - lastSwipeAtMs < swipeDebounceMs) {
            return
        }
        lastSwipeAtMs = now

        _uiState.update {
            it.copy(
                selectedDate = nextDate,
                errorMessage = null,
                isSwipeInProgress = true,
            )
        }

        scheduleMealsLoadForSelectedDay()
    }

    private fun scheduleMealsLoadForSelectedDay() {
        val selectedDate = _uiState.value.selectedDate ?: return
        val loadId = nextMealsLoadId()
        _uiState.update { it.copy(isLoadingMeals = true) }
        viewModelScope.launch {
            loadMealsForSelectedDay(selectedDate = selectedDate, loadId = loadId)
        }
    }

    private suspend fun loadMealsForSelectedDay(selectedDate: String, loadId: Long) {
        try {
            val meals = mealsLoader(selectedDate)
            if (loadId != latestMealsLoadId || _uiState.value.selectedDate != selectedDate) {
                return
            }
            _uiState.update {
                it.copy(
                    mealsForSelectedDay = meals,
                    isLoadingMeals = false,
                    isSwipeInProgress = false,
                    errorMessage = null,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (loadId != latestMealsLoadId || _uiState.value.selectedDate != selectedDate) {
                return
            }
            _uiState.update {
                it.copy(
                    isLoadingMeals = false,
                    isSwipeInProgress = false,
                    errorMessage = error.message ?: "Unable to load meals",
                )
            }
        }
    }

    private fun nextMealsLoadId(): Long {
        latestMealsLoadId += 1
        return latestMealsLoadId
    }

    private fun HistoryUiState.nextPreviousDate(): String? {
        if (!canGoPrevious) {
            return null
        }
        return days.getOrNull(selectedIndex + 1)?.localDate
    }

    private fun HistoryUiState.nextForwardDate(): String? {
        if (!canGoNext) {
            return null
        }
        return days.getOrNull(selectedIndex - 1)?.localDate
    }

    companion object {
        private const val DEFAULT_SWIPE_DEBOUNCE_MS = 500L

        fun factory(appGraph: AppGraph): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(
                        observeHistoryUseCase = appGraph.observeHistoryUseCase,
                        getHistoryMealsForDayUseCase = appGraph.getHistoryMealsForDayUseCase,
                        deleteMealEntryUseCase = appGraph.deleteMealEntryUseCase,
                    ) as T
                }
            }
        }
    }
}

enum class SwipeDirection {
    LEFT,
    RIGHT,
}
