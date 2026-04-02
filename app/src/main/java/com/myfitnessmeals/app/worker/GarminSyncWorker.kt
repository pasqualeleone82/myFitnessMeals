package com.myfitnessmeals.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myfitnessmeals.app.AppGraph
import com.myfitnessmeals.app.integration.garmin.GarminActionResult
import com.myfitnessmeals.app.integration.garmin.GarminSyncMode

class GarminSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val graph = AppGraph(applicationContext)
        return when (val result = graph.garminIntegrationService.syncFitness(GarminSyncMode.APP_OPEN)) {
            is GarminActionResult.Success -> Result.success()
            is GarminActionResult.Error -> if (result.retryable) Result.retry() else Result.failure()
        }
    }
}
