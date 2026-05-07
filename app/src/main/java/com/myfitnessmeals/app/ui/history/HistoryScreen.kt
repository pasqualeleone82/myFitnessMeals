package com.myfitnessmeals.app.ui.history

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dining
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.domain.model.HistoryMealCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

private const val HORIZONTAL_SWIPE_THRESHOLD_PX = 100f
private val HISTORY_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ITALIAN)

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onAddMealTapped: () -> Unit,
    onEditMealTapped: (meal: HistoryMealCard) -> Unit,
    onDeleteMealConfirmed: (mealEntryId: Long) -> Unit,
    onSaveMealOverride: (meal: HistoryMealCard, input: NutrientOverrideInput) -> Unit,
) {
    var accumulatedDrag by remember(state.selectedDate) { mutableFloatStateOf(0f) }
    var pendingDeleteMeal by remember { mutableStateOf<HistoryMealCard?>(null) }
    var pendingOverrideMeal by remember { mutableStateOf<HistoryMealCard?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .pointerInput(state.selectedDate) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            accumulatedDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (abs(accumulatedDrag) >= HORIZONTAL_SWIPE_THRESHOLD_PX) {
                                if (accumulatedDrag > 0f) {
                                    onSwipeRight()
                                } else {
                                    onSwipeLeft()
                                }
                            }
                            accumulatedDrag = 0f
                        },
                        onDragCancel = {
                            accumulatedDrag = 0f
                        },
                    )
                }
                .testTag("history_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)

            HistoryDateHeader(
                state = state,
                onPrevious = onPrevious,
                onNext = onNext,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.selectedDay?.let { day ->
                    item {
                        DailyTotalsCard(day = day)
                    }

                    if (state.mealsForSelectedDay.isEmpty()) {
                        item {
                            EmptyHistoryState(onAddMealTapped = onAddMealTapped)
                        }
                    } else {
                        item {
                            Text(
                                text = stringResource(R.string.history_meals_count, state.mealsForSelectedDay.size),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("history_meals_count"),
                            )
                        }

                        items(
                            items = state.mealsForSelectedDay,
                            key = { meal -> meal.mealEntryId },
                        ) { meal ->
                            MealCard(
                                meal = meal,
                                onEditTapped = { onEditMealTapped(meal) },
                                onDeleteTapped = { pendingDeleteMeal = meal },
                                onOverrideTapped = { pendingOverrideMeal = meal },
                            )
                        }

                        item {
                            Button(
                                onClick = onAddMealTapped,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_add_meal_button"),
                            ) {
                                Text(stringResource(R.string.history_add_meal))
                            }
                        }
                    }
                }

                if (state.selectedDay == null) {
                    item {
                        EmptyHistoryState(onAddMealTapped = onAddMealTapped)
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.history_showing_range),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("history_range_label"),
                    )
                }

                state.errorMessage?.let { message ->
                    item {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("history_error"),
                        )
                    }
                }
            }
        }

        pendingDeleteMeal?.let { meal ->
            DeleteMealConfirmDialog(
                foodName = meal.foodName,
                onConfirmDelete = {
                    onDeleteMealConfirmed(meal.mealEntryId)
                    pendingDeleteMeal = null
                },
                onDismiss = {
                    pendingDeleteMeal = null
                },
            )
        }

        pendingOverrideMeal?.let { meal ->
            NutrientOverrideDialog(
                foodName = meal.foodName,
                onSave = { input ->
                    onSaveMealOverride(meal, input)
                    pendingOverrideMeal = null
                },
                onDismiss = { pendingOverrideMeal = null },
            )
        }
    }
}

@Composable
private fun HistoryDateHeader(
    state: HistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val selectedDateText = formatHistoryDate(state.selectedDay?.localDate ?: state.todayDate)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = state.canGoPrevious,
                modifier = Modifier.testTag("history_prev_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.history_previous),
                )
            }

            Text(
                text = selectedDateText,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_selected_date"),
            )

            IconButton(
                onClick = onNext,
                enabled = state.canGoNext,
                modifier = Modifier.testTag("history_next_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.history_next),
                )
            }
        }
    }
}

private fun formatHistoryDate(localDate: String): String {
    return try {
        LocalDate.parse(localDate).format(HISTORY_DATE_FORMATTER)
    } catch (_: DateTimeParseException) {
        localDate
    }
}

@Composable
private fun EmptyHistoryState(onAddMealTapped: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_empty_state"),
    ) {
        Icon(
            imageVector = Icons.Filled.Dining,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.history_empty_state_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("history_empty_message"),
        )
        Text(
            text = stringResource(R.string.history_empty_state_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onAddMealTapped,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("empty_state_add_meal_button"),
        ) {
            Text(stringResource(R.string.history_add_meal))
        }
    }
}