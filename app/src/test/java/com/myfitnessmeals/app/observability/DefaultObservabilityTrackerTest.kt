package com.myfitnessmeals.app.observability

import com.myfitnessmeals.app.integration.analytics.TechnicalAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultObservabilityTrackerTest {
    @Test
    fun tracker_emitsRequiredEventsAndUpdatesOperationalCounters() {
        val analytics = CapturingAnalytics()
        val metrics = InMemoryOperationalMetricsRecorder()
        val logger = RedactingStructuredLogger(NoOpSink())
        val tracker = DefaultObservabilityTracker(
            analytics = analytics,
            metrics = metrics,
            logger = logger,
            nowEpochMillis = { 1_700_000_000_000L },
        )

        tracker.trackFoodSearch(
            origin = "text",
            outcome = "success",
            source = "CACHE",
            resultCount = 3,
        )
        tracker.trackMealSave(
            mealType = "LUNCH",
            success = true,
        )
        tracker.trackProviderSync(
            mode = "manual",
            success = false,
            errorCode = "SYNC_FAILED",
            retryable = true,
        )

        assertTrue(analytics.events.contains("food_search"))
        assertTrue(analytics.events.contains("meal_save"))
        assertTrue(analytics.events.contains("provider_sync"))
        assertTrue(analytics.events.contains("provider_sync_failed"))

        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_FOOD_SEARCH_TOTAL))
        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_FOOD_SEARCH_CACHE_HIT))
        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_MEAL_SAVE_TOTAL))
        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_MEAL_SAVE_SUCCESS))
        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_PROVIDER_SYNC_TOTAL))
        assertEquals(0L, metrics.value(DefaultObservabilityTracker.COUNTER_PROVIDER_SYNC_SUCCESS))
        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_PROVIDER_SYNC_FAILED))
        assertEquals(1L, metrics.value(DefaultObservabilityTracker.COUNTER_PROVIDER_SYNC_RETRYABLE_FAILED))
    }

    @Test
    fun tracker_emitsRequiredPayloadFieldsForTelemetryEvents() {
        val analytics = CapturingAnalytics()
        val metrics = InMemoryOperationalMetricsRecorder()
        val logger = RedactingStructuredLogger(NoOpSink())
        val tracker = DefaultObservabilityTracker(
            analytics = analytics,
            metrics = metrics,
            logger = logger,
            nowEpochMillis = { 1_700_000_000_000L },
        )

        tracker.trackFoodSearch(
            origin = "text",
            outcome = "error",
            source = null,
            resultCount = 0,
            errorCode = "OFF_TIMEOUT",
            retryable = true,
        )
        tracker.trackMealSave(
            mealType = "DINNER",
            success = false,
            errorCode = "VALIDATION_FAILED",
        )
        tracker.trackProviderSync(
            mode = "app_open",
            success = false,
            errorCode = "AUTH_EXPIRED",
            retryable = true,
        )

        val foodSearchPayload = analytics.lastPayload("food_search")
        assertNotNull(foodSearchPayload)
        assertEquals("text", foodSearchPayload?.get("origin"))
        assertEquals("error", foodSearchPayload?.get("outcome"))
        assertEquals("unknown", foodSearchPayload?.get("source"))
        assertEquals(0, foodSearchPayload?.get("result_count"))
        assertEquals("OFF_TIMEOUT", foodSearchPayload?.get("error_code"))
        assertEquals(true, foodSearchPayload?.get("retryable"))
        assertEquals(1_700_000_000_000L, foodSearchPayload?.get("ts_ms"))

        val mealSavePayload = analytics.lastPayload("meal_save")
        assertNotNull(mealSavePayload)
        assertEquals("DINNER", mealSavePayload?.get("meal_type"))
        assertEquals(false, mealSavePayload?.get("success"))
        assertEquals("VALIDATION_FAILED", mealSavePayload?.get("error_code"))
        assertEquals(1_700_000_000_000L, mealSavePayload?.get("ts_ms"))

        val providerSyncPayload = analytics.lastPayload("provider_sync")
        assertNotNull(providerSyncPayload)
        assertEquals("GARMIN", providerSyncPayload?.get("provider"))
        assertEquals("app_open", providerSyncPayload?.get("mode"))
        assertEquals(false, providerSyncPayload?.get("success"))
        assertEquals("AUTH_EXPIRED", providerSyncPayload?.get("error_code"))
        assertEquals(true, providerSyncPayload?.get("retryable"))
        assertEquals(1_700_000_000_000L, providerSyncPayload?.get("ts_ms"))

        val providerSyncFailedPayload = analytics.lastPayload("provider_sync_failed")
        assertNotNull(providerSyncFailedPayload)
        assertEquals("GARMIN", providerSyncFailedPayload?.get("provider"))
        assertEquals("app_open", providerSyncFailedPayload?.get("mode"))
        assertEquals(false, providerSyncFailedPayload?.get("success"))
        assertEquals("AUTH_EXPIRED", providerSyncFailedPayload?.get("error_code"))
        assertEquals(true, providerSyncFailedPayload?.get("retryable"))
        assertEquals(1_700_000_000_000L, providerSyncFailedPayload?.get("ts_ms"))
    }

    @Test
    fun tracker_doesNotEmitProviderSyncFailedWhenSyncSucceeds() {
        val analytics = CapturingAnalytics()
        val metrics = InMemoryOperationalMetricsRecorder()
        val logger = RedactingStructuredLogger(NoOpSink())
        val tracker = DefaultObservabilityTracker(
            analytics = analytics,
            metrics = metrics,
            logger = logger,
            nowEpochMillis = { 1_700_000_000_000L },
        )

        tracker.trackProviderSync(
            mode = "manual",
            success = true,
            errorCode = null,
            retryable = false,
        )

        assertTrue(analytics.events.contains("provider_sync"))
        assertFalse(analytics.events.contains("provider_sync_failed"))
    }

    private class CapturingAnalytics : TechnicalAnalytics {
        val events = mutableListOf<String>()
        private val payloadsByEvent = mutableMapOf<String, MutableList<Map<String, Any?>>>()

        override fun track(eventName: String, attributes: Map<String, Any?>) {
            events.add(eventName)
            payloadsByEvent.getOrPut(eventName) { mutableListOf() }.add(attributes)
        }

        fun lastPayload(eventName: String): Map<String, Any?>? {
            return payloadsByEvent[eventName]?.lastOrNull()
        }
    }

    private class NoOpSink : LogSink {
        override fun write(level: LogLevel, payload: String) = Unit
    }
}
