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
import com.myfitnessmeals.app.domain.service.GoalComputationService
import com.myfitnessmeals.app.domain.usecase.DeleteAllUserDataUseCase
import com.myfitnessmeals.app.domain.usecase.ExportUserDataUseCase
import com.myfitnessmeals.app.integration.garmin.GarminActionResult
import com.myfitnessmeals.app.integration.garmin.GarminIntegrationService
import com.myfitnessmeals.app.integration.garmin.GarminSyncMode
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val targetKcalInput: String = "",
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
            it.copy(
                targetKcalInput = settings.targetKcal.toInt().toString(),
                carbPctInput = settings.carbPct.toString(),
                fatPctInput = settings.fatPct.toString(),
                proteinPctInput = settings.proteinPct.toString(),
                themePreference = settings.themePreference,
            )
        }
        viewModelScope.launch {
            refreshGarminStatus()
        }
    }

    fun onTargetChanged(value: String) = _uiState.update { it.copy(targetKcalInput = value) }
    fun onCarbChanged(value: String) = _uiState.update { it.copy(carbPctInput = value) }
    fun onFatChanged(value: String) = _uiState.update { it.copy(fatPctInput = value) }
    fun onProteinChanged(value: String) = _uiState.update { it.copy(proteinPctInput = value) }
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
                        it.copy(
                            targetKcalInput = settings.targetKcal.toInt().toString(),
                            carbPctInput = settings.carbPct.toString(),
                            fatPctInput = settings.fatPct.toString(),
                            proteinPctInput = settings.proteinPct.toString(),
                            themePreference = settings.themePreference,
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
        val target = state.targetKcalInput.toDoubleOrNull()
        val carb = state.carbPctInput.toIntOrNull()
        val fat = state.fatPctInput.toIntOrNull()
        val protein = state.proteinPctInput.toIntOrNull()

        if (target == null || carb == null || fat == null || protein == null) {
            _uiState.update { it.copy(errorMessage = "Invalid numeric values", saveMessage = null) }
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
                age = existing.age,
                heightCm = existing.heightCm,
                weightKg = existing.weightKg,
                sex = existing.sex,
                activityLevel = existing.activityLevel,
                goalType = existing.goalType,
                targetKcal = target,
                carbPct = carb,
                fatPct = fat,
                proteinPct = protein,
                themePreference = state.themePreference,
            )
        )
        _uiState.update { it.copy(errorMessage = null, saveMessage = "Settings saved") }
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
        onTargetChanged = viewModel::onTargetChanged,
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
    onTargetChanged: (String) -> Unit,
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
            OutlinedTextField(state.targetKcalInput, onTargetChanged, label = { Text(stringResource(R.string.settings_target_kcal)) }, modifier = Modifier.fillMaxWidth().testTag("settings_target"))
            OutlinedTextField(state.carbPctInput, onCarbChanged, label = { Text(stringResource(R.string.settings_carb_pct)) }, modifier = Modifier.fillMaxWidth().testTag("settings_carb"))
            OutlinedTextField(state.fatPctInput, onFatChanged, label = { Text(stringResource(R.string.settings_fat_pct)) }, modifier = Modifier.fillMaxWidth().testTag("settings_fat"))
            OutlinedTextField(state.proteinPctInput, onProteinChanged, label = { Text(stringResource(R.string.settings_protein_pct)) }, modifier = Modifier.fillMaxWidth().testTag("settings_protein"))

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
