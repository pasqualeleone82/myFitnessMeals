package com.myfitnessmeals.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalComputationService
import com.myfitnessmeals.app.domain.service.GoalProfileInput
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.service.Sex
import com.myfitnessmeals.app.ui.common.input.normalizePercentInput
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingUiState(
    val ageInput: String = "30",
    val heightInput: String = "175",
    val weightInput: String = "75",
    val sex: Sex = Sex.MALE,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalType: GoalType = GoalType.MAINTAIN,
    val carbPctInput: String = "40",
    val fatPctInput: String = "30",
    val proteinPctInput: String = "30",
    val computedTargetKcal: Double? = null,
    val onboardingCompleted: Boolean = false,
    val errorMessage: String? = null,
)

class OnboardingViewModel(
    private val settingsRepository: UserSettingsRepository,
    private val goalComputationService: GoalComputationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        val settings = settingsRepository.getSettings()
        _uiState.update {
            computeStateWithTarget(
                it.copy(
                onboardingCompleted = settings.onboardingCompleted,
                ageInput = settings.age.toString(),
                heightInput = settings.heightCm.toString(),
                weightInput = settings.weightKg.toString(),
                sex = settings.sex,
                activityLevel = settings.activityLevel,
                goalType = settings.goalType,
                carbPctInput = normalizePercentInput(settings.carbPct.toString()),
                fatPctInput = normalizePercentInput(settings.fatPct.toString()),
                proteinPctInput = normalizePercentInput(settings.proteinPct.toString()),
                computedTargetKcal = settings.targetKcal,
                )
            )
        }
    }

    fun onAgeChanged(value: String) = _uiState.update { computeStateWithTarget(it.copy(ageInput = value)) }
    fun onHeightChanged(value: String) = _uiState.update { computeStateWithTarget(it.copy(heightInput = value)) }
    fun onWeightChanged(value: String) = _uiState.update { computeStateWithTarget(it.copy(weightInput = value)) }
    fun onSexChanged(value: Sex) = _uiState.update { computeStateWithTarget(it.copy(sex = value)) }
    fun onActivityChanged(value: ActivityLevel) = _uiState.update { computeStateWithTarget(it.copy(activityLevel = value)) }
    fun onGoalChanged(value: GoalType) = _uiState.update { computeStateWithTarget(it.copy(goalType = value)) }
    fun onCarbChanged(value: String) = _uiState.update { it.copy(carbPctInput = normalizePercentInput(value)) }
    fun onFatChanged(value: String) = _uiState.update { it.copy(fatPctInput = normalizePercentInput(value)) }
    fun onProteinChanged(value: String) = _uiState.update { it.copy(proteinPctInput = normalizePercentInput(value)) }

    fun completeOnboarding() {
        val state = _uiState.value
        val age = state.ageInput.toIntOrNull()
        val height = state.heightInput.toDoubleOrNull()
        val weight = state.weightInput.toDoubleOrNull()
        val carb = normalizePercentInput(state.carbPctInput).toIntOrNull()
        val fat = normalizePercentInput(state.fatPctInput).toIntOrNull()
        val protein = normalizePercentInput(state.proteinPctInput).toIntOrNull()

        if (age == null || height == null || weight == null || carb == null || fat == null || protein == null) {
            _uiState.update { it.copy(errorMessage = "Invalid numeric values") }
            return
        }
        if (!goalComputationService.validateMacroSplit(carb, fat, protein)) {
            _uiState.update { it.copy(errorMessage = "Macro percentages must sum to 100") }
            return
        }

        val target = state.computedTargetKcal ?: computeTargetOrNull(state)
        if (target == null) {
            _uiState.update { it.copy(errorMessage = "Invalid profile") }
            return
        }

        try {
            settingsRepository.saveSettings(
                UserSettings(
                    onboardingCompleted = true,
                    age = age,
                    heightCm = height,
                    weightKg = weight,
                    sex = state.sex,
                    activityLevel = state.activityLevel,
                    goalType = state.goalType,
                    targetKcal = target,
                    carbPct = carb,
                    fatPct = fat,
                    proteinPct = protein,
                    themePreference = settingsRepository.getSettings().themePreference,
                )
            )
            _uiState.update { it.copy(onboardingCompleted = true, computedTargetKcal = target, errorMessage = null) }
        } catch (error: IllegalArgumentException) {
            _uiState.update { it.copy(errorMessage = error.message ?: "Invalid profile") }
        }
    }

    private fun computeStateWithTarget(state: OnboardingUiState): OnboardingUiState {
        return state.copy(computedTargetKcal = computeTargetOrNull(state))
    }

    private fun computeTargetOrNull(state: OnboardingUiState): Double? {
        val age = state.ageInput.toIntOrNull() ?: return null
        val height = state.heightInput.toDoubleOrNull() ?: return null
        val weight = state.weightInput.toDoubleOrNull() ?: return null

        return try {
            goalComputationService.computeTargetKcal(
                GoalProfileInput(
                    age = age,
                    heightCm = height,
                    weightKg = weight,
                    sex = state.sex,
                    activityLevel = state.activityLevel,
                    goalType = state.goalType,
                )
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        fun factory(appGraph: AppGraph): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OnboardingViewModel(
                        settingsRepository = appGraph.userSettingsRepository,
                        goalComputationService = appGraph.goalComputationService,
                    ) as T
                }
            }
        }
    }
}

@Composable
fun OnboardingRoute(viewModel: OnboardingViewModel) {
    val state by viewModel.uiState.collectAsState()
    OnboardingScreen(
        state = state,
        onAgeChanged = viewModel::onAgeChanged,
        onHeightChanged = viewModel::onHeightChanged,
        onWeightChanged = viewModel::onWeightChanged,
        onCarbChanged = viewModel::onCarbChanged,
        onFatChanged = viewModel::onFatChanged,
        onProteinChanged = viewModel::onProteinChanged,
        onComplete = viewModel::completeOnboarding,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAgeChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onCarbChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onComplete: () -> Unit,
) {
    val estimateValueText = state.computedTargetKcal?.let {
        roundEstimateKcalForDisplay(it)
    }?.let {
        stringResource(R.string.onboarding_estimated_target_value, it)
    } ?: stringResource(R.string.onboarding_estimated_target_placeholder)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("onboarding_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Welcome to myFitnessMeals", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(state.ageInput, onAgeChanged, label = { Text("Age") }, modifier = Modifier.fillMaxWidth().testTag("onboarding_age"))
        OutlinedTextField(state.heightInput, onHeightChanged, label = { Text("Height cm") }, modifier = Modifier.fillMaxWidth().testTag("onboarding_height"))
        OutlinedTextField(state.weightInput, onWeightChanged, label = { Text("Weight kg") }, modifier = Modifier.fillMaxWidth().testTag("onboarding_weight"))
        OutlinedTextField(
            value = normalizePercentInput(state.carbPctInput),
            onValueChange = onCarbChanged,
            label = { Text("Carb") },
            suffix = { if (state.carbPctInput.isNotEmpty()) Text("%") },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_carb"),
        )
        OutlinedTextField(
            value = normalizePercentInput(state.fatPctInput),
            onValueChange = onFatChanged,
            label = { Text("Fat") },
            suffix = { if (state.fatPctInput.isNotEmpty()) Text("%") },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_fat"),
        )
        OutlinedTextField(
            value = normalizePercentInput(state.proteinPctInput),
            onValueChange = onProteinChanged,
            label = { Text("Protein") },
            suffix = { if (state.proteinPctInput.isNotEmpty()) Text("%") },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_protein"),
        )
        Card(modifier = Modifier.fillMaxWidth().testTag("onboarding_target_card")) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_estimated_target_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = estimateValueText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("onboarding_target"),
                )
            }
        }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("onboarding_error")) }
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().testTag("onboarding_complete_button")) {
            Text("Complete onboarding")
        }
    }
}

internal fun roundEstimateKcalForDisplay(value: Double): Int = value.roundToInt()
