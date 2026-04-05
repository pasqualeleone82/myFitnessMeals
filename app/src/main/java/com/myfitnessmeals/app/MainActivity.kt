package com.myfitnessmeals.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfitnessmeals.app.R
import com.myfitnessmeals.app.data.repository.AppThemePreference
import com.myfitnessmeals.app.ui.dashboard.DashboardRoute
import com.myfitnessmeals.app.ui.dashboard.DashboardViewModel
import com.myfitnessmeals.app.ui.history.HistoryRoute
import com.myfitnessmeals.app.ui.history.HistoryViewModel
import com.myfitnessmeals.app.ui.meal.MealLoggingRoute
import com.myfitnessmeals.app.ui.meal.MealLoggingViewModel
import com.myfitnessmeals.app.ui.onboarding.OnboardingRoute
import com.myfitnessmeals.app.ui.onboarding.OnboardingViewModel
import com.myfitnessmeals.app.ui.settings.SettingsRoute
import com.myfitnessmeals.app.ui.settings.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph by lazy { AppGraph(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot(appGraph)
        }
    }
}

private enum class MainTab {
    DASHBOARD,
    MEAL,
    HISTORY,
    SETTINGS,
}

@Composable
private fun AppRoot(appGraph: AppGraph) {
    val systemDarkTheme = isSystemInDarkTheme()
    val onboardingViewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(appGraph))
    val onboardingState by onboardingViewModel.uiState.collectAsState()

    val savedThemePreference = remember {
        appGraph.userSettingsRepository.getSettings().themePreference
    }

    if (!onboardingState.onboardingCompleted) {
        MaterialTheme(
            colorScheme = if (resolveDarkTheme(savedThemePreference, systemDarkTheme)) darkColorScheme() else lightColorScheme(),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                OnboardingRoute(viewModel = onboardingViewModel)
            }
        }
        return
    }

    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(appGraph))
    val settingsState by settingsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        appGraph.enqueueGarminAppOpenSync()
        settingsViewModel.syncGarminOnAppOpen()
    }

    MaterialTheme(
        colorScheme = if (resolveDarkTheme(settingsState.themePreference, systemDarkTheme)) darkColorScheme() else lightColorScheme(),
    ) {
        val mealViewModel: MealLoggingViewModel = viewModel(factory = MealLoggingViewModel.factory(appGraph))
        val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(appGraph))
        val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(appGraph))
        val activity = LocalContext.current as? Activity

        var tab by remember { mutableStateOf(MainTab.DASHBOARD) }
        var showExitDialog by remember { mutableStateOf(false) }
        var scanRequestKey by remember { mutableStateOf(0L) }

        BackHandler {
            showExitDialog = true
        }

        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(stringResource(R.string.exit_dialog_title)) },
                text = { Text(stringResource(R.string.exit_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            activity?.finish()
                        }
                    ) {
                        Text(stringResource(R.string.exit_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text(stringResource(R.string.cancel_action))
                    }
                },
            )
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(modifier = Modifier.testTag("main_tab_bar")) {
                        NavigationBarItem(
                            selected = tab == MainTab.DASHBOARD,
                            onClick = { tab = MainTab.DASHBOARD },
                            icon = { Icon(Icons.Filled.SpaceDashboard, contentDescription = stringResource(R.string.main_tab_dashboard)) },
                            label = { Text(stringResource(R.string.main_tab_dashboard)) },
                            modifier = Modifier.testTag("main_tab_dashboard"),
                        )
                        NavigationBarItem(
                            selected = tab == MainTab.MEAL,
                            onClick = { tab = MainTab.MEAL },
                            icon = { Icon(Icons.Filled.RestaurantMenu, contentDescription = stringResource(R.string.main_tab_meal)) },
                            label = { Text(stringResource(R.string.main_tab_meal)) },
                            modifier = Modifier.testTag("main_tab_meal"),
                        )

                        Box(modifier = Modifier.width(64.dp))

                        NavigationBarItem(
                            selected = tab == MainTab.HISTORY,
                            onClick = { tab = MainTab.HISTORY },
                            icon = { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.main_tab_history)) },
                            label = { Text(stringResource(R.string.main_tab_history)) },
                            modifier = Modifier.testTag("main_tab_history"),
                        )
                        NavigationBarItem(
                            selected = tab == MainTab.SETTINGS,
                            onClick = { tab = MainTab.SETTINGS },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_tab_settings)) },
                            label = { Text(stringResource(R.string.main_tab_settings)) },
                            modifier = Modifier.testTag("main_tab_settings"),
                        )
                    }
                },
                floatingActionButtonPosition = FabPosition.Center,
                floatingActionButton = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FloatingActionButton(
                            onClick = { expanded = true },
                            modifier = Modifier.testTag("main_quick_add_fab"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quick_add_food))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quick_add_food)) },
                                onClick = {
                                    expanded = false
                                    tab = MainTab.MEAL
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.RestaurantMenu, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quick_scan_barcode)) },
                                onClick = {
                                    expanded = false
                                    tab = MainTab.MEAL
                                    scanRequestKey += 1L
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                                },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    when (tab) {
                        MainTab.MEAL -> MealLoggingRoute(viewModel = mealViewModel, scanRequestKey = scanRequestKey)
                        MainTab.DASHBOARD -> DashboardRoute(viewModel = dashboardViewModel)
                        MainTab.HISTORY -> HistoryRoute(viewModel = historyViewModel)
                        MainTab.SETTINGS -> SettingsRoute(viewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}

private fun resolveDarkTheme(preference: AppThemePreference, systemDarkTheme: Boolean): Boolean {
    return when (preference) {
        AppThemePreference.SYSTEM -> systemDarkTheme
        AppThemePreference.LIGHT -> false
        AppThemePreference.DARK -> true
    }
}