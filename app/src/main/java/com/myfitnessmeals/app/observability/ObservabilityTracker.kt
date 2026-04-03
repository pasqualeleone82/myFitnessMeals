package com.myfitnessmeals.app.observability

import com.myfitnessmeals.app.integration.analytics.TechnicalAnalytics

interface ObservabilityTracker {
    fun trackFoodSearch(
        origin: String,
        outcome: String,
        source: String?,
        resultCount: Int,
        errorCode: String? = null,
        retryable: Boolean = false,
    )

    fun trackMealSave(
        mealType: String,
        success: Boolean,
        errorCode: String? = null,
    )

    fun trackProviderSync(
        mode: String,
        success: Boolean,
        errorCode: String? = null,
        retryable: Boolean = false,
    )
}

object NoOpObservabilityTracker : ObservabilityTracker {
    override fun trackFoodSearch(
        origin: String,
        outcome: String,
        source: String?,
        resultCount: Int,
        errorCode: String?,
        retryable: Boolean,
    ) = Unit

    override fun trackMealSave(
        mealType: String,
        success: Boolean,
        errorCode: String?,
    ) = Unit

    override fun trackProviderSync(
        mode: String,
        success: Boolean,
        errorCode: String?,
        retryable: Boolean,
    ) = Unit
}

class DefaultObservabilityTracker(
    private val analytics: TechnicalAnalytics,
    private val metrics: OperationalMetricsRecorder,
    private val logger: StructuredLogger,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : ObservabilityTracker {
    override fun trackFoodSearch(
        origin: String,
        outcome: String,
        source: String?,
        resultCount: Int,
        errorCode: String?,
        retryable: Boolean,
    ) {
        val eventPayload = mutableMapOf<String, Any?>(
            "origin" to origin,
            "outcome" to outcome,
            "source" to (source ?: "unknown"),
            "result_count" to resultCount,
            "retryable" to retryable,
            "ts_ms" to nowEpochMillis(),
        )
        if (errorCode != null) {
            eventPayload["error_code"] = errorCode
        }

        analytics.track("food_search", eventPayload)
        logger.info("food_search", eventPayload)

        metrics.increment(COUNTER_FOOD_SEARCH_TOTAL)
        if (source == "CACHE" && outcome == OUTCOME_SUCCESS) {
            metrics.increment(COUNTER_FOOD_SEARCH_CACHE_HIT)
        }
    }

    override fun trackMealSave(mealType: String, success: Boolean, errorCode: String?) {
        val eventPayload = mutableMapOf<String, Any?>(
            "meal_type" to mealType,
            "success" to success,
            "ts_ms" to nowEpochMillis(),
        )
        if (errorCode != null) {
            eventPayload["error_code"] = errorCode
        }

        analytics.track("meal_save", eventPayload)
        logger.info("meal_save", eventPayload)

        metrics.increment(COUNTER_MEAL_SAVE_TOTAL)
        if (success) {
            metrics.increment(COUNTER_MEAL_SAVE_SUCCESS)
        }
    }

    override fun trackProviderSync(
        mode: String,
        success: Boolean,
        errorCode: String?,
        retryable: Boolean,
    ) {
        val eventPayload = mutableMapOf<String, Any?>(
            "provider" to "GARMIN",
            "mode" to mode,
            "success" to success,
            "retryable" to retryable,
            "ts_ms" to nowEpochMillis(),
        )
        if (errorCode != null) {
            eventPayload["error_code"] = errorCode
        }

        analytics.track("provider_sync", eventPayload)
        logger.info("provider_sync", eventPayload)

        metrics.increment(COUNTER_PROVIDER_SYNC_TOTAL)
        if (success) {
            metrics.increment(COUNTER_PROVIDER_SYNC_SUCCESS)
        } else {
            analytics.track("provider_sync_failed", eventPayload)
            logger.warn("provider_sync_failed", eventPayload)
            metrics.increment(COUNTER_PROVIDER_SYNC_FAILED)
            if (retryable) {
                metrics.increment(COUNTER_PROVIDER_SYNC_RETRYABLE_FAILED)
            }
        }
    }

    companion object {
        private const val OUTCOME_SUCCESS = "success"

        const val COUNTER_FOOD_SEARCH_TOTAL = "food_search_total"
        const val COUNTER_FOOD_SEARCH_CACHE_HIT = "food_search_cache_hit"
        const val COUNTER_MEAL_SAVE_TOTAL = "meal_save_total"
        const val COUNTER_MEAL_SAVE_SUCCESS = "meal_save_success"
        const val COUNTER_PROVIDER_SYNC_TOTAL = "provider_sync_total"
        const val COUNTER_PROVIDER_SYNC_SUCCESS = "provider_sync_success"
        const val COUNTER_PROVIDER_SYNC_FAILED = "provider_sync_failed"
        const val COUNTER_PROVIDER_SYNC_RETRYABLE_FAILED = "provider_sync_retryable_failed"
    }
}
