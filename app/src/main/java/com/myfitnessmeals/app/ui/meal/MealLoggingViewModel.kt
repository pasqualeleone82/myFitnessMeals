package com.myfitnessmeals.app.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.data.repository.LocalOverrideRepository
import com.myfitnessmeals.app.domain.model.MealType
import com.myfitnessmeals.app.domain.usecase.BuildMealPreviewUseCase
import com.myfitnessmeals.app.domain.usecase.DeleteMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.DeleteNutritionOverrideUseCase
import com.myfitnessmeals.app.domain.usecase.GetMealDaySnapshotUseCase
import com.myfitnessmeals.app.domain.usecase.MealFoodCandidate
import com.myfitnessmeals.app.domain.usecase.MealLoggedEntry
import com.myfitnessmeals.app.domain.usecase.MealPreview
import com.myfitnessmeals.app.domain.usecase.MealSearchResult
import com.myfitnessmeals.app.domain.usecase.SaveMealEntryCommand
import com.myfitnessmeals.app.domain.usecase.SaveMealEntryUseCase
import com.myfitnessmeals.app.domain.usecase.SaveNutritionOverrideCommand
import com.myfitnessmeals.app.domain.usecase.SaveNutritionOverrideUseCase
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
    val overrideKcalInput: String = "",
    val overrideCarbInput: String = "",
    val overrideFatInput: String = "",
    val overrideProteinInput: String = "",
    val overrideNoteInput: String = "",
    val overrideUpdatedAtEpochMs: Long? = null,
    val preview: MealPreview? = null,
    val entries: List<MealLoggedEntry> = emptyList(),
    val kcalIntake: Double = 0.0,
    val carbTotal: Double = 0.0,
    val fatTotal: Double = 0.0,
    val proteinTotal: Double = 0.0,
    val errorMessage: String? = null,
    val showRetryAction: Boolean = false,
)

private enum class SearchOrigin {
    TEXT,
    BARCODE,
}

class MealLoggingViewModel(
    private val searchFoodsByTextUseCase: SearchFoodsByTextUseCase,
    private val searchFoodByBarcodeUseCase: SearchFoodByBarcodeUseCase,
    private val buildMealPreviewUseCase: BuildMealPreviewUseCase,
    private val saveMealEntryUseCase: SaveMealEntryUseCase,
    private val saveNutritionOverrideUseCase: SaveNutritionOverrideUseCase,
    private val deleteNutritionOverrideUseCase: DeleteNutritionOverrideUseCase,
    private val overrideRepository: LocalOverrideRepository,
    private val deleteMealEntryUseCase: DeleteMealEntryUseCase,
    private val getMealDaySnapshotUseCase: GetMealDaySnapshotUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MealLoggingUiState())
    val uiState: StateFlow<MealLoggingUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var searchRequestVersion: Long = 0
    private var previewJob: Job? = null
    private var previewRequestVersion: Long = 0
    private var lastSearchOrigin: SearchOrigin? = null

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

    fun onBarcodeScanned(value: String) {
        val barcode = value.trim()
        if (barcode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Unable to read barcode") }
            return
        }

        _uiState.update {
            it.copy(
                barcodeQuery = barcode,
                errorMessage = null,
            )
        }
        searchByBarcode()
    }

    fun onBarcodeScanError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
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
        lastSearchOrigin = SearchOrigin.TEXT
        val query = _uiState.value.searchQuery
        executeSearch(notFoundMessage = "No food found") {
            searchFoodsByTextUseCase(query)
        }
    }

    fun searchByBarcode() {
        lastSearchOrigin = SearchOrigin.BARCODE
        val barcode = _uiState.value.barcodeQuery
        executeSearch(notFoundMessage = "Barcode not found") {
            searchFoodByBarcodeUseCase(barcode)
        }
    }

    fun retryLastSearch() {
        when (lastSearchOrigin) {
            SearchOrigin.TEXT -> searchByText()
            SearchOrigin.BARCODE -> searchByBarcode()
            null -> Unit
        }
    }

    fun onFoodSelected(food: MealFoodCandidate) {
        _uiState.update {
            it.copy(
                selectedFood = food,
                errorMessage = null,
                    showRetryAction = false,
                overrideKcalInput = "",
                overrideCarbInput = "",
                overrideFatInput = "",
                overrideProteinInput = "",
                overrideNoteInput = "",
                overrideUpdatedAtEpochMs = null,
            )
        }
        viewModelScope.launch {
            val existing = overrideRepository.getOverrideByFoodId(food.id)
            if (_uiState.value.selectedFood?.id != food.id) {
                return@launch
            }
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        overrideKcalInput = existing.kcal100?.formatOverrideInput().orEmpty(),
                        overrideCarbInput = existing.carb100?.formatOverrideInput().orEmpty(),
                        overrideFatInput = existing.fat100?.formatOverrideInput().orEmpty(),
                        overrideProteinInput = existing.protein100?.formatOverrideInput().orEmpty(),
                        overrideNoteInput = existing.note.orEmpty(),
                        overrideUpdatedAtEpochMs = existing.updatedAt,
                    )
                }
            }
            recalculatePreview()
        }
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

    fun onOverrideKcalChanged(value: String) {
        _uiState.update { it.copy(overrideKcalInput = value) }
    }

    fun onOverrideCarbChanged(value: String) {
        _uiState.update { it.copy(overrideCarbInput = value) }
    }

    fun onOverrideFatChanged(value: String) {
        _uiState.update { it.copy(overrideFatInput = value) }
    }

    fun onOverrideProteinChanged(value: String) {
        _uiState.update { it.copy(overrideProteinInput = value) }
    }

    fun onOverrideNoteChanged(value: String) {
        _uiState.update { it.copy(overrideNoteInput = value) }
    }

    fun saveOverride() {
        val state = _uiState.value
        val selectedFood = state.selectedFood ?: run {
            _uiState.update { it.copy(errorMessage = "Select a food first") }
            return
        }

        val kcalParsed = parseNullableNumber(state.overrideKcalInput, "kcal")
        if (!kcalParsed.isValid) return
        val carbParsed = parseNullableNumber(state.overrideCarbInput, "carb")
        if (!carbParsed.isValid) return
        val fatParsed = parseNullableNumber(state.overrideFatInput, "fat")
        if (!fatParsed.isValid) return
        val proteinParsed = parseNullableNumber(state.overrideProteinInput, "protein")
        if (!proteinParsed.isValid) return

        val kcal = kcalParsed.value
        val carb = carbParsed.value
        val fat = fatParsed.value
        val protein = proteinParsed.value

        if (listOf(kcal, carb, fat, protein).all { it == null }) {
            _uiState.update { it.copy(errorMessage = "Set at least one nutrient override") }
            return
        }

        viewModelScope.launch {
            try {
                saveNutritionOverrideUseCase(
                    SaveNutritionOverrideCommand(
                        foodId = selectedFood.id,
                        kcal100 = kcal,
                        carb100 = carb,
                        fat100 = fat,
                        protein100 = protein,
                        note = state.overrideNoteInput,
                    )
                )
                val persisted = overrideRepository.getOverrideByFoodId(selectedFood.id)
                _uiState.update { it.copy(errorMessage = null) }
                if (persisted != null) {
                    _uiState.update { it.copy(overrideUpdatedAtEpochMs = persisted.updatedAt) }
                }
                recalculatePreview()
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Invalid override") }
            }
        }
    }

    fun clearOverride() {
        val selectedFoodId = _uiState.value.selectedFood?.id ?: run {
            _uiState.update { it.copy(errorMessage = "Select a food first") }
            return
        }

        viewModelScope.launch {
            try {
                deleteNutritionOverrideUseCase(selectedFoodId)
                _uiState.update {
                    it.copy(
                        overrideKcalInput = "",
                        overrideCarbInput = "",
                        overrideFatInput = "",
                        overrideProteinInput = "",
                        overrideNoteInput = "",
                        overrideUpdatedAtEpochMs = null,
                        errorMessage = null,
                    )
                }
                recalculatePreview()
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = error.message ?: "Invalid override request") }
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
                        showRetryAction = false,
                    )
                }
            }
            is MealSearchResult.NotFound -> {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        errorMessage = notFoundMessage,
                        showRetryAction = false,
                    )
                }
            }
            is MealSearchResult.Error -> {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        errorMessage = result.message,
                        showRetryAction = result.retryable,
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

    private fun parseNullableNumber(raw: String, label: String): ParsedNumber {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParsedNumber(isValid = true, value = null)
        }
        val parsed = trimmed.toDoubleOrNull()
        if (parsed == null) {
            _uiState.update { it.copy(errorMessage = "Invalid $label value") }
            return ParsedNumber(isValid = false, value = null)
        }
        if (parsed < 0.0) {
            _uiState.update { it.copy(errorMessage = "$label must be >= 0") }
            return ParsedNumber(isValid = false, value = null)
        }
        return ParsedNumber(isValid = true, value = parsed)
    }

    private data class ParsedNumber(
        val isValid: Boolean,
        val value: Double?,
    )

    private fun Double.formatOverrideInput(): String {
        val longValue = toLong()
        return if (this == longValue.toDouble()) {
            longValue.toString()
        } else {
            toString()
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
                        saveNutritionOverrideUseCase = appGraph.saveNutritionOverrideUseCase,
                        deleteNutritionOverrideUseCase = appGraph.deleteNutritionOverrideUseCase,
                        overrideRepository = appGraph.overrideRepository,
                        deleteMealEntryUseCase = appGraph.deleteMealEntryUseCase,
                        getMealDaySnapshotUseCase = appGraph.getMealDaySnapshotUseCase,
                    ) as T
                }
            }
        }
    }
}
