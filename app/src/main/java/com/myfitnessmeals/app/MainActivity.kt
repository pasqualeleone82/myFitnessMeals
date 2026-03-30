package com.myfitnessmeals.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfitnessmeals.app.ui.meal.MealLoggingRoute
import com.myfitnessmeals.app.ui.meal.MealLoggingViewModel

class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph by lazy { AppGraph(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val viewModel: MealLoggingViewModel = viewModel(
                    factory = MealLoggingViewModel.factory(appGraph),
                )
                MealLoggingRoute(viewModel = viewModel)
            }
        }
    }
}