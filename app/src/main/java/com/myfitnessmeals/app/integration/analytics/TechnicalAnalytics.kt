package com.myfitnessmeals.app.integration.analytics

import com.myfitnessmeals.app.observability.StructuredLogger

interface TechnicalAnalytics {
    fun track(eventName: String, attributes: Map<String, Any?> = emptyMap())
}

object NoOpTechnicalAnalytics : TechnicalAnalytics {
    override fun track(eventName: String, attributes: Map<String, Any?>) = Unit
}

class LoggingTechnicalAnalytics(
    private val logger: StructuredLogger,
) : TechnicalAnalytics {
    override fun track(eventName: String, attributes: Map<String, Any?>) {
        logger.info(
            event = "technical_event",
            fields = mapOf("event_name" to eventName) + attributes,
        )
    }
}
