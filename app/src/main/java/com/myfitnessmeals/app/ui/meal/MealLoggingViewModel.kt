package com.myfitnessmeals.app.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.usecase.BuildMealPreviewUseCase
import com.myfitnessmeals.app.domain.usecase.DeleteMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.GetMealDaySnapshotUseCase
import com.myfitnessmeals.app.domain.usecase.MealFoodCandidate
import com.myfitnessmeals.app.domain.usecase.MealLoggedEntry
import com.myfitnessmeals.app.domain.usecase.MealPreview
import com.myfitnessmeals.app.domain.usecase.MealSearchResult
import com.myfitnessmeals.app.domain.usecase.SaveMealEntryCommand
import com.myfitnessmeals.app.domain.usecase.SaveMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.SearchFoodByBarcodeUseCase
import com.myfitnessmeals.app.domain.usecase.SearchFoodsByTextUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealLoggingUiState(
    val selectedMealType: MealType = MealType.BREAKFAST,
    val searchQuery: String = "",
    val barcodeQuery: String = "",
    val searchResults: List<MealFoodCandidate> = emptyList(),
    val selectedFood: MealFoodCandidate? = null,
    val quantityInput: String = "100",
    val unitInput: String = "g",
    val preview: MealPreview? = null,
    val entries: List<MealLoggedEntry> = emptyList(),
    val kcalIntake: Double = 0.0,
    val carbTotal: Double = 0.0,
    val fatTotal: Double = 0.0,
    val proteinTotal: Double = 0.0,
    val errorMessage: String? = null,
)

class MealLoggingViewModel(
    private val searchFoodsByTextUseCase: SearchFoodsByTextUseCase,
    private val searchFoodByBarcodeUseCase: SearchFoodByBarcodeUseCase,
    private val buildMealPreviewUseCase: BuildMealPreviewUseCase,
    private val saveMealEntryUseCase: SaveMealEntryUseCase,
    private val deleteMealEntryUseCase: DeleteMealEntryUseCase,
    private val getMealDaySnapshotUseCase: GetMealDaySnapshotUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealLoggingUiState())
    val uiState: StateFlow<MealLoggingUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var searchRequestVersion: Long = 0
    private var previewJob: Job? = null
    private var previewRequestVersion: Long = 0

    init {
        viewModelScope.launch {
            refreshDay()
        }
    }

    fun onSearchQueryChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun onBarcodeChanged(value: String) {
        _uiState.update { it.copy(barcodeQuery = value) }
    }

    fun onMealTypeSelected(mealType: MealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
    }

    fun onQuantityChanged(value: String) {
        _uiState.update { it.copy(quantityInput = value) }
        recalculatePreview()
    }

    fun onUnitChanged(value: String) {
        _uiState.update { it.copy(unitInput = value) }
        recalculatePreview()
    }

    fun searchByText() {
        val query = _uiState.value.searchQuery
        executeSearch(notFoundMessage = "No food found") {
            searchFoodsByTextUseCase(query)
        }
    }

    fun searchByBarcode() {
        val barcode = _uiState.value.barcodeQuery
        executeSearch(notFoundMessage = "Barcode not found") {
            searchFoodByBarcodeUseCase(barcode)
        }
    }

    fun onFoodSelected(food: MealFoodCandidate) {
        _uiState.update { it.copy(selectedFood = food, errorMessage = null) }
        recalculatePreview()
    }

    fun saveSelectedFood() {
        val state = _uiState.value
        val selectedFood = state.selectedFood ?: run {
            _uiState.update { it.copy(errorMessage = "Select a food first") }
            return
        }
        val quantity = state.quantityInput.toDoubleOrNull()
        if (quantity == null || quantity <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Quantity must be greater than zero") }
            return
        }

        viewModelScope.launch {
            try {
                saveMealEntryUseCase(
                    SaveMealEntryCommand(
                        mealType = state.selectedMealType,
                        food = selectedFood,
                        quantity = quantity,
                        unit = state.unitInput,
                    )
                )
                _uiState.update { it.copy(errorMessage = null) }
                refreshDay()
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Invalid meal data") }
            }
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            deleteMealEntryUseCase(entryId)
            refreshDay()
        }
    }

    private fun recalculatePreview() {
        val state = _uiState.value
        val selectedFood = state.selectedFood ?: run {
            previewJob?.cancel()
            _uiState.update { it.copy(preview = null) }
            return
        }
        val quantity = state.quantityInput.toDoubleOrNull()
        if (quantity == null || quantity <= 0.0) {
            previewJob?.cancel()
            _uiState.update {
                it.copy(
                    preview = null,
                    errorMessage = "Quantity must be greater than zero",
                )
            }
            return
        }

        previewJob?.cancel()
        val requestVersion = ++previewRequestVersion

        previewJob = viewModelScope.launch {
            try {
                val preview = buildMealPreviewUseCase(
                    food = selectedFood,
                    quantity = quantity,
                    unit = state.unitInput,
                )
                if (requestVersion != previewRequestVersion) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        preview = preview,
                        errorMessage = null,
                    )
                }
            } catch (_: CancellationException) {
                // A newer preview request superseded this one.
            } catch (error: IllegalArgumentException) {
                if (requestVersion != previewRequestVersion) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        preview = null,
                        errorMessage = error.message ?: "Invalid meal data",
                    )
                }
            }
        }
    }

    private fun executeSearch(
        notFoundMessage: String,
        searchCall: suspend () -> MealSearchResult,
    ) {
        searchJob?.cancel()
        val requestVersion = ++searchRequestVersion
        searchJob = viewModelScope.launch {
            try {
                val result = searchCall()
                if (requestVersion != searchRequestVersion) {
                    return@launch
                }
                applySearchResult(result, notFoundMessage)
            } catch (_: CancellationException) {
                // A newer search request superseded this one.
            }
        }
    }

    private fun applySearchResult(result: MealSearchResult, notFoundMessage: String) {
        when (result) {
            is MealSearchResult.Success -> {
                _uiState.update {
                    it.copy(
                        searchResults = result.items,
                        errorMessage = null,
                    )
                }
            }
            is MealSearchResult.NotFound -> {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        errorMessage = notFoundMessage,
                    )
                }
            }
            is MealSearchResult.Error -> {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    private suspend fun refreshDay() {
        val snapshot = getMealDaySnapshotUseCase()
        _uiState.update {
            it.copy(
                entries = snapshot.entries,
                kcalIntake = snapshot.kcalIntake,
                carbTotal = snapshot.carbTotal,
                fatTotal = snapshot.fatTotal,
                proteinTotal = snapshot.proteinTotal,
            )
        }
    }

    companion object {
        fun factory(appGraph: AppGraph): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MealLoggingViewModel(
                        searchFoodsByTextUseCase = appGraph.searchFoodsByTextUseCase,
                        searchFoodByBarcodeUseCase = appGraph.searchFoodByBarcodeUseCase,
                        buildMealPreviewUseCase = appGraph.buildMealPreviewUseCase,
                        saveMealEntryUseCase = appGraph.saveMealEntryUseCase,
                        deleteMealEntryUseCase = appGraph.deleteMealEntryUseCase,
                        getMealDaySnapshotUseCase = appGraph.getMealDaySnapshotUseCase,
                    ) as T
                }
            }
        }
    }
}
