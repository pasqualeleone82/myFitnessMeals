package com.myfitnessmeals.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.domain.usecase.DashboardSnapshot
import com.myfitnessmeals.app.domain.usecase.ObserveDashboardUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val snapshot: DashboardSnapshot? = null,
    val errorMessage: String? = null,
)

class DashboardViewModel(
    private val observeDashboardUseCase: ObserveDashboardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val snapshot = observeDashboardUseCase()
                _uiState.update { it.copy(snapshot = snapshot, errorMessage = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Unable to load dashboard") }
            }
        }
    }

    companion object {
        fun factory(appGraph: AppGraph): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(
                        observeDashboardUseCase = appGraph.observeDashboardUseCase,
                    ) as T
                }
            }
        }
    }
}

@Composable
fun DashboardRoute(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()
    DashboardScreen(state = state)
}

@Composable
fun DashboardScreen(state: DashboardUiState) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("dashboard_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
            }

            state.snapshot?.let { snapshot ->
                item {
                    CalorieCard(snapshot)
                }
                item {
                    MacroCard(snapshot)
                }
                item {
                    FitnessWidgets(snapshot)
                }
            }

            state.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("dashboard_error"),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieCard(snapshot: DashboardSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Calories", style = MaterialTheme.typography.titleMedium)
            Text("Target: ${snapshot.kcalTarget.format1()}", modifier = Modifier.testTag("dashboard_kcal_target"))
            Text("Intake: ${snapshot.kcalIntake.format1()}", modifier = Modifier.testTag("dashboard_kcal_intake"))
            Text("Burned: ${snapshot.kcalBurned.format1()}", modifier = Modifier.testTag("dashboard_kcal_burned"))
            Text("Remaining: ${snapshot.kcalRemaining.format1()}", modifier = Modifier.testTag("dashboard_kcal_remaining"))
        }
    }
}

@Composable
private fun MacroCard(snapshot: DashboardSnapshot) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Macros", style = MaterialTheme.typography.titleMedium)
            Text(
                "Carbs: ${snapshot.carbGrams.format1()} g (${snapshot.carbPct}%)",
                modifier = Modifier.testTag("dashboard_macro_carb"),
            )
            Text(
                "Fat: ${snapshot.fatGrams.format1()} g (${snapshot.fatPct}%)",
                modifier = Modifier.testTag("dashboard_macro_fat"),
            )
            Text(
                "Protein: ${snapshot.proteinGrams.format1()} g (${snapshot.proteinPct}%)",
                modifier = Modifier.testTag("dashboard_macro_protein"),
            )
        }
    }
}

@Composable
private fun FitnessWidgets(snapshot: DashboardSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WidgetCard(
                title = "Steps",
                value = snapshot.steps.toString(),
                tag = "dashboard_widget_steps",
                modifier = Modifier.weight(1f),
            )
            WidgetCard(
                title = "Weight",
                value = "${snapshot.latestWeightKg.format1()} kg",
                tag = "dashboard_widget_weight",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WidgetCard(
                title = "Exercise kcal",
                value = snapshot.activeKcal.format1(),
                tag = "dashboard_widget_exercise_kcal",
                modifier = Modifier.weight(1f),
            )
            WidgetCard(
                title = "Workout min",
                value = snapshot.workoutMinutes.toString(),
                tag = "dashboard_widget_workout_minutes",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WidgetCard(
    title: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
        }
    }
}

private fun Double.format1(): String = String.format("%.1f", this)
