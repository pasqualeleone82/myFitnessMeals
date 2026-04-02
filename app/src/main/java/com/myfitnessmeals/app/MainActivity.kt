package com.myfitnessmeals.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
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
    MEAL,
    DASHBOARD,
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
            OnboardingRoute(viewModel = onboardingViewModel)
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

        var tab by remember { mutableStateOf(MainTab.MEAL) }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .testTag("main_tab_bar"),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { tab = MainTab.MEAL }, modifier = Modifier.testTag("main_tab_meal")) { Text("Meal") }
                TextButton(onClick = { tab = MainTab.DASHBOARD }, modifier = Modifier.testTag("main_tab_dashboard")) { Text("Dashboard") }
                TextButton(onClick = { tab = MainTab.HISTORY }, modifier = Modifier.testTag("main_tab_history")) { Text("History") }
                TextButton(onClick = { tab = MainTab.SETTINGS }, modifier = Modifier.testTag("main_tab_settings")) { Text("Settings") }
            }

            when (tab) {
                MainTab.MEAL -> MealLoggingRoute(viewModel = mealViewModel)
                MainTab.DASHBOARD -> DashboardRoute(viewModel = dashboardViewModel)
                MainTab.HISTORY -> HistoryRoute(viewModel = historyViewModel)
                MainTab.SETTINGS -> SettingsRoute(viewModel = settingsViewModel)
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