package com.myfitnessmeals.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.domain.usecase.HistoryDaySnapshot
import com.myfitnessmeals.app.domain.usecase.ObserveHistoryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val days: List<HistoryDaySnapshot> = emptyList(),
    val selectedIndex: Int = 0,
    val errorMessage: String? = null,
) {
    val selectedDay: HistoryDaySnapshot?
        get() = days.getOrNull(selectedIndex)

    val canGoPrevious: Boolean
        get() = selectedIndex < days.lastIndex

    val canGoNext: Boolean
        get() = selectedIndex > 0
}

class HistoryViewModel(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val days = observeHistoryUseCase(90)
                _uiState.update {
                    it.copy(
                        days = days,
                        selectedIndex = it.selectedIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0)),
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Unable to load history") }
            }
        }
    }

    fun showPreviousDay() {
        _uiState.update { state ->
            if (!state.canGoPrevious) {
                state
            } else {
                state.copy(selectedIndex = state.selectedIndex + 1)
            }
        }
    }

    fun showNextDay() {
        _uiState.update { state ->
            if (!state.canGoNext) {
                state
            } else {
                state.copy(selectedIndex = state.selectedIndex - 1)
            }
        }
    }

    companion object {
        fun factory(appGraph: AppGraph): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HistoryViewModel(
                        observeHistoryUseCase = appGraph.observeHistoryUseCase,
                    ) as T
                }
            }
        }
    }
}

@Composable
fun HistoryRoute(viewModel: HistoryViewModel) {
    val state by viewModel.uiState.collectAsState()
    HistoryScreen(
        state = state,
        onPrevious = viewModel::showPreviousDay,
        onNext = viewModel::showNextDay,
    )
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("history_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPrevious,
                    enabled = state.canGoPrevious,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_prev_button"),
                ) {
                    Text(stringResource(R.string.history_previous))
                }
                Button(
                    onClick = onNext,
                    enabled = state.canGoNext,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_next_button"),
                ) {
                    Text(stringResource(R.string.history_next))
                }
            }

            state.selectedDay?.let { day ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.localDate, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("history_selected_date"))
                        Text("Target: ${day.kcalTarget.format1()}")
                        Text("Intake: ${day.kcalIntake.format1()}")
                        Text("Burned: ${day.kcalBurned.format1()}")
                        Text("Remaining: ${day.kcalRemaining.format1()}", modifier = Modifier.testTag("history_selected_remaining"))
                        Text("Carbs: ${day.carbGrams.format1()} g")
                        Text("Fat: ${day.fatGrams.format1()} g")
                        Text("Protein: ${day.proteinGrams.format1()} g")
                    }
                }
            }

            Text(stringResource(R.string.history_showing_range), modifier = Modifier.testTag("history_range_label"))

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("history_error"),
                )
            }
        }
    }
}

private fun Double.format1(): String = String.format("%.1f", this)
