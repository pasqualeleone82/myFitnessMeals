package com.myfitnessmeals.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.data.repository.UserSettings
import com.myfitnessmeals.app.data.repository.UserSettingsRepository
import com.myfitnessmeals.app.domain.service.ActivityLevel
import com.myfitnessmeals.app.domain.service.GoalComputationService
import com.myfitnessmeals.app.domain.service.GoalProfileInput
import com.myfitnessmeals.app.domain.service.GoalType
import com.myfitnessmeals.app.domain.usecase.DeleteAllUserDataUseCase
import com.myfitnessmeals.app.domain.usecase.ExportUserDataUseCase
import com.myfitnessmeals.app.integration.garmin.GarminActionResult
import com.myfitnessmeals.app.integration.garmin.GarminIntegrationService
import com.myfitnessmeals.app.integration.garmin.GarminSyncMode
import com.myfitnessmeals.app.ui.common.input.normalizePercentInput
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val ageInput: String = "30",
    val weightInput: String = "75",
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goalType: GoalType = GoalType.MAINTAIN,
    val computedTargetKcal: Double? = null,
    val carbPctInput: String = "40",
    val fatPctInput: String = "30",
    val proteinPctInput: String = "30",
    val themePreference: AppThemePreference = AppThemePreference.SYSTEM,
    val garminConnectionState: String = "DISCONNECTED",
    val garminAuthCodeInput: String = "",
    val garminLastSyncLabel: String = "Never",
    val garminLastError: String? = null,
    val garminNotice: String? = null,
    val privacyNotice: String? = null,
    val privacyDeleteArmed: Boolean = false,
    val saveMessage: String? = null,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: UserSettingsRepository,
    private val goalComputationService: GoalComputationService,
    private val garminIntegrationService: GarminIntegrationService,
    private val exportUserDataUseCase: ExportUserDataUseCase,
    private val deleteAllUserDataUseCase: DeleteAllUserDataUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val settings = settingsRepository.getSettings()
        _uiState.update {
            computeStateWithTarget(
                it.copy(
                    ageInput = settings.age.toString(),
                    weightInput = settings.weightKg.toString(),
                    activityLevel = settings.activityLevel,
                    goalType = settings.goalType,
                    carbPctInput = normalizePercentInput(settings.carbPct.toString()),
                    fatPctInput = normalizePercentInput(settings.fatPct.toString()),
                    proteinPctInput = normalizePercentInput(settings.proteinPct.toString()),
                    themePreference = settings.themePreference,
                )
            )
        }
        viewModelScope.launch {
            refreshGarminStatus()
        }
    }

    fun onAgeChanged(value: String) = _uiState.update { computeStateWithTarget(it.copy(ageInput = value)) }
    fun onWeightChanged(value: String) = _uiState.update { computeStateWithTarget(it.copy(weightInput = value)) }
    fun onActivityChanged(value: ActivityLevel) = _uiState.update { computeStateWithTarget(it.copy(activityLevel = value)) }
    fun onGoalChanged(value: GoalType) = _uiState.update { computeStateWithTarget(it.copy(goalType = value)) }
    fun onCarbChanged(value: String) = _uiState.update { it.copy(carbPctInput = normalizePercentInput(value)) }
    fun onFatChanged(value: String) = _uiState.update { it.copy(fatPctInput = normalizePercentInput(value)) }
    fun onProteinChanged(value: String) = _uiState.update { it.copy(proteinPctInput = normalizePercentInput(value)) }
    fun onThemeChanged(value: AppThemePreference) = _uiState.update { it.copy(themePreference = value) }
    fun onGarminAuthCodeChanged(value: String) = _uiState.update { it.copy(garminAuthCodeInput = value) }

    fun connectGarmin() {
        viewModelScope.launch {
            val result = garminIntegrationService.connectProvider(_uiState.value.garminAuthCodeInput)
            applyGarminResult(result)
            refreshGarminStatus()
        }
    }

    fun disconnectGarmin() {
        viewModelScope.launch {
            val result = garminIntegrationService.disconnectProvider()
            applyGarminResult(result)
            refreshGarminStatus()
        }
    }

    fun syncGarminNow() {
        viewModelScope.launch {
            val result = garminIntegrationService.syncFitness(GarminSyncMode.MANUAL)
            applyGarminResult(result)
            refreshGarminStatus()
        }
    }

    fun syncGarminOnAppOpen() {
        viewModelScope.launch {
            garminIntegrationService.syncFitness(GarminSyncMode.APP_OPEN)
            refreshGarminStatus()
        }
    }

    fun exportAllData() {
        viewModelScope.launch {
            runCatching { exportUserDataUseCase() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            privacyNotice = "Export completed: ${result.filePath}",
                            errorMessage = null,
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            privacyNotice = null,
                            errorMessage = "Export failed",
                        )
                    }
                }
        }
    }

    fun armDeleteAllData() {
        _uiState.update { it.copy(privacyDeleteArmed = true, privacyNotice = "Confirm delete to erase all local data") }
    }

    fun confirmDeleteAllData() {
        viewModelScope.launch {
            runCatching { deleteAllUserDataUseCase() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            privacyDeleteArmed = false,
                            privacyNotice = "All local data deleted",
                            garminAuthCodeInput = "",
                            garminNotice = null,
                            garminLastError = null,
                        )
                    }
                    val settings = settingsRepository.getSettings()
                    _uiState.update {
                        computeStateWithTarget(
                            it.copy(
                                ageInput = settings.age.toString(),
                                weightInput = settings.weightKg.toString(),
                                activityLevel = settings.activityLevel,
                                goalType = settings.goalType,
                                carbPctInput = normalizePercentInput(settings.carbPct.toString()),
                                fatPctInput = normalizePercentInput(settings.fatPct.toString()),
                                proteinPctInput = normalizePercentInput(settings.proteinPct.toString()),
                                themePreference = settings.themePreference,
                            )
                        )
                    }
                    refreshGarminStatus()
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            privacyDeleteArmed = false,
                            privacyNotice = null,
                            errorMessage = "Delete data failed",
                        )
                    }
                }
        }
    }

    fun saveSettings() {
        val state = _uiState.value
        val age = state.ageInput.toIntOrNull()
        val weight = parseWeightInput(state.weightInput)
        val target = state.computedTargetKcal ?: computeTargetOrNull(state)
        val carb = normalizePercentInput(state.carbPctInput).toIntOrNull()
        val fat = normalizePercentInput(state.fatPctInput).toIntOrNull()
        val protein = normalizePercentInput(state.proteinPctInput).toIntOrNull()

        if (age == null || weight == null || carb == null || fat == null || protein == null || target == null) {
            _uiState.update { it.copy(errorMessage = "Invalid profile", saveMessage = null) }
            return
        }
        if (!goalComputationService.validateMacroSplit(carb, fat, protein)) {
            _uiState.update { it.copy(errorMessage = "Macro percentages must sum to 100", saveMessage = null) }
            return
        }

        val existing = settingsRepository.getSettings()
        settingsRepository.saveSettings(
            UserSettings(
                onboardingCompleted = existing.onboardingCompleted,
                age = age,
                heightCm = existing.heightCm,
                weightKg = weight,
                sex = existing.sex,
                activityLevel = state.activityLevel,
                goalType = state.goalType,
                targetKcal = target,
                carbPct = carb,
                fatPct = fat,
                proteinPct = protein,
                themePreference = state.themePreference,
            )
        )
        _uiState.update { computeStateWithTarget(it.copy(errorMessage = null, saveMessage = "Settings saved")) }
    }

    private fun computeStateWithTarget(state: SettingsUiState): SettingsUiState {
        return state.copy(computedTargetKcal = computeTargetOrNull(state))
    }

    private fun computeTargetOrNull(state: SettingsUiState): Double? {
        val age = state.ageInput.toIntOrNull() ?: return null
        val weight = parseWeightInput(state.weightInput) ?: return null
        val existing = settingsRepository.getSettings()

        return try {
            goalComputationService.computeTargetKcal(
                GoalProfileInput(
                    age = age,
                    heightCm = existing.heightCm,
                    weightKg = weight,
                    sex = existing.sex,
                    activityLevel = state.activityLevel,
                    goalType = state.goalType,
                )
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parseWeightInput(value: String): Double? {
        return value.replace(',', '.').toDoubleOrNull()
    }

    private suspend fun refreshGarminStatus() {
        val status = garminIntegrationService.getProviderStatus()
        _uiState.update {
            it.copy(
                garminConnectionState = status.connectionState,
                garminLastSyncLabel = status.lastSyncAt?.let(::formatTimestamp) ?: "Never",
                garminLastError = status.lastErrorCode,
            )
        }
    }

    private fun applyGarminResult(result: GarminActionResult) {
        when (result) {
            is GarminActionResult.Success -> _uiState.update {
                it.copy(
                    garminNotice = result.message,
                    errorMessage = null,
                    garminAuthCodeInput = "",
                )
            }

            is GarminActionResult.Error -> _uiState.update {
                it.copy(
                    garminNotice = result.message,
                    errorMessage = if (result.code == "NOT_CONNECTED") null else result.message,
                )
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    companion object {
        fun factory(appGraph: AppGraph): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(
                        settingsRepository = appGraph.userSettingsRepository,
                        goalComputationService = appGraph.goalComputationService,
                        garminIntegrationService = appGraph.garminIntegrationService,
                        exportUserDataUseCase = appGraph.exportUserDataUseCase,
                        deleteAllUserDataUseCase = appGraph.deleteAllUserDataUseCase,
                    ) as T
                }
            }
        }
    }
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    SettingsScreen(
        state = state,
        onAgeChanged = viewModel::onAgeChanged,
        onWeightChanged = viewModel::onWeightChanged,
        onActivityChanged = viewModel::onActivityChanged,
        onGoalChanged = viewModel::onGoalChanged,
        onCarbChanged = viewModel::onCarbChanged,
        onFatChanged = viewModel::onFatChanged,
        onProteinChanged = viewModel::onProteinChanged,
        onThemeChanged = viewModel::onThemeChanged,
        onGarminAuthCodeChanged = viewModel::onGarminAuthCodeChanged,
        onConnectGarmin = viewModel::connectGarmin,
        onDisconnectGarmin = viewModel::disconnectGarmin,
        onSyncGarmin = viewModel::syncGarminNow,
        onExportData = viewModel::exportAllData,
        onArmDeleteData = viewModel::armDeleteAllData,
        onConfirmDeleteData = viewModel::confirmDeleteAllData,
        onSave = viewModel::saveSettings,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAgeChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onActivityChanged: (ActivityLevel) -> Unit,
    onGoalChanged: (GoalType) -> Unit,
    onCarbChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onThemeChanged: (AppThemePreference) -> Unit,
    onGarminAuthCodeChanged: (String) -> Unit,
    onConnectGarmin: () -> Unit,
    onDisconnectGarmin: () -> Unit,
    onSyncGarmin: () -> Unit,
    onExportData: () -> Unit,
    onArmDeleteData: () -> Unit,
    onConfirmDeleteData: () -> Unit,
    onSave: () -> Unit,
) {
    val estimateValueText = state.computedTargetKcal?.let {
        roundSettingsEstimateKcalForDisplay(it)
    }?.let {
        stringResource(R.string.onboarding_estimated_target_value, it)
    } ?: stringResource(R.string.onboarding_estimated_target_placeholder)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.settings_profile_section), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.ageInput,
                onValueChange = onAgeChanged,
                label = { Text(stringResource(R.string.settings_age)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_age"),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.weightInput,
                onValueChange = onWeightChanged,
                label = { Text(stringResource(R.string.settings_weight_kg)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_weight"),
                singleLine = true,
            )

            Text(stringResource(R.string.settings_activity_level), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_activity_sedentary),
                    selected = state.activityLevel == ActivityLevel.SEDENTARY,
                    tag = "settings_activity_sedentary",
                    onClick = { onActivityChanged(ActivityLevel.SEDENTARY) },
                )
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_activity_light),
                    selected = state.activityLevel == ActivityLevel.LIGHT,
                    tag = "settings_activity_light",
                    onClick = { onActivityChanged(ActivityLevel.LIGHT) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_activity_moderate),
                    selected = state.activityLevel == ActivityLevel.MODERATE,
                    tag = "settings_activity_moderate",
                    onClick = { onActivityChanged(ActivityLevel.MODERATE) },
                )
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_activity_active),
                    selected = state.activityLevel == ActivityLevel.ACTIVE,
                    tag = "settings_activity_active",
                    onClick = { onActivityChanged(ActivityLevel.ACTIVE) },
                )
            }

            Text(stringResource(R.string.settings_goal_type), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_goal_lose),
                    selected = state.goalType == GoalType.LOSE,
                    tag = "settings_goal_lose",
                    onClick = { onGoalChanged(GoalType.LOSE) },
                )
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_goal_maintain),
                    selected = state.goalType == GoalType.MAINTAIN,
                    tag = "settings_goal_maintain",
                    onClick = { onGoalChanged(GoalType.MAINTAIN) },
                )
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_goal_gain),
                    selected = state.goalType == GoalType.GAIN,
                    tag = "settings_goal_gain",
                    onClick = { onGoalChanged(GoalType.GAIN) },
                )
            }

            Card(modifier = Modifier.fillMaxWidth().testTag("settings_target_card")) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_estimated_daily_calories),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = estimateValueText,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("settings_target"),
                    )
                }
            }

            OutlinedTextField(
                value = normalizePercentInput(state.carbPctInput),
                onValueChange = onCarbChanged,
                label = { Text(stringResource(R.string.settings_carb_pct)) },
                suffix = { if (state.carbPctInput.isNotEmpty()) Text("%") },
                modifier = Modifier.fillMaxWidth().testTag("settings_carb"),
            )
            OutlinedTextField(
                value = normalizePercentInput(state.fatPctInput),
                onValueChange = onFatChanged,
                label = { Text(stringResource(R.string.settings_fat_pct)) },
                suffix = { if (state.fatPctInput.isNotEmpty()) Text("%") },
                modifier = Modifier.fillMaxWidth().testTag("settings_fat"),
            )
            OutlinedTextField(
                value = normalizePercentInput(state.proteinPctInput),
                onValueChange = onProteinChanged,
                label = { Text(stringResource(R.string.settings_protein_pct)) },
                suffix = { if (state.proteinPctInput.isNotEmpty()) Text("%") },
                modifier = Modifier.fillMaxWidth().testTag("settings_protein"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_theme_system),
                    selected = state.themePreference == AppThemePreference.SYSTEM,
                    tag = "settings_theme_system",
                    onClick = { onThemeChanged(AppThemePreference.SYSTEM) },
                )
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_theme_light),
                    selected = state.themePreference == AppThemePreference.LIGHT,
                    tag = "settings_theme_light",
                    onClick = { onThemeChanged(AppThemePreference.LIGHT) },
                )
                ThemeChoiceButton(
                    label = stringResource(R.string.settings_theme_dark),
                    selected = state.themePreference == AppThemePreference.DARK,
                    tag = "settings_theme_dark",
                    onClick = { onThemeChanged(AppThemePreference.DARK) },
                )
            }
            Text(
                text = stringResource(R.string.settings_theme_current, state.themePreference.name),
                modifier = Modifier.testTag("settings_theme_current"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Watch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_garmin), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.settings_connection, state.garminConnectionState), modifier = Modifier.testTag("settings_garmin_status"))
            Text(stringResource(R.string.settings_last_sync, state.garminLastSyncLabel), modifier = Modifier.testTag("settings_garmin_last_sync"))
            OutlinedTextField(
                value = state.garminAuthCodeInput,
                onValueChange = onGarminAuthCodeChanged,
                label = { Text(stringResource(R.string.settings_garmin_auth_code)) },
                modifier = Modifier.fillMaxWidth().testTag("settings_garmin_auth_code"),
                singleLine = true,
            )
            state.garminLastError?.let {
                Text(
                    text = stringResource(R.string.settings_last_error, it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings_garmin_error"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onConnectGarmin, modifier = Modifier.testTag("settings_garmin_connect")) {
                    Text(stringResource(R.string.settings_connect))
                }
                OutlinedButton(onClick = onDisconnectGarmin, modifier = Modifier.testTag("settings_garmin_disconnect")) {
                    Text(stringResource(R.string.settings_disconnect))
                }
                OutlinedButton(onClick = onSyncGarmin, modifier = Modifier.testTag("settings_garmin_sync")) {
                    Text(stringResource(R.string.settings_sync_now))
                }
            }
            state.garminNotice?.let {
                Text(it, modifier = Modifier.testTag("settings_garmin_notice"))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onExportData, modifier = Modifier.testTag("settings_privacy_export")) {
                    Text(stringResource(R.string.settings_export_data))
                }
                if (state.privacyDeleteArmed) {
                    Button(onClick = onConfirmDeleteData, modifier = Modifier.testTag("settings_privacy_delete_confirm")) {
                        Text(stringResource(R.string.settings_confirm_delete))
                    }
                } else {
                    OutlinedButton(onClick = onArmDeleteData, modifier = Modifier.testTag("settings_privacy_delete")) {
                        Text(stringResource(R.string.settings_delete_all_data))
                    }
                }
            }
            state.privacyNotice?.let {
                Text(it, modifier = Modifier.testTag("settings_privacy_notice"))
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings_error")) }
            state.saveMessage?.let { Text(it, modifier = Modifier.testTag("settings_saved")) }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().testTag("settings_save_button")) {
                Text(stringResource(R.string.settings_save))
            }
        }
    }
}

@Composable
private fun ThemeChoiceButton(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .testTag(tag),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .testTag(tag),
        ) {
            Text(label)
        }
    }
}

internal fun roundSettingsEstimateKcalForDisplay(value: Double): Int = value.roundToInt()
