package com.myfitnessmeals.app.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingStructuredLoggerTest {
    @Test
    fun info_redactsSensitiveFieldsAndKeepsStructuredPayload() {
        val sink = CapturingSink()
        val logger = RedactingStructuredLogger(sink)

        logger.info(
            event = "provider_sync",
            fields = mapOf(
                "barcode" to "1234567890123",
                "query" to "chicken breast",
                "accessToken" to "secret-token",
                "mode" to "manual",
                "success" to true,
            ),
        )

        assertEquals(LogLevel.INFO, sink.level)
        val payload = sink.payload
        assertTrue(payload.contains("\"event\":\"provider_sync\""))
        assertTrue(payload.contains("\"mode\":\"manual\""))
        assertTrue(payload.contains("\"success\":\"true\""))
        assertTrue(payload.contains("\"barcode\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"query\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"accessToken\":\"[REDACTED]\""))
    }

    @Test
    fun info_redactsSensitiveFieldsAcrossSnakeKebabCamelAndSpaceKeys() {
        val sink = CapturingSink()
        val logger = RedactingStructuredLogger(sink)

        logger.info(
            event = "provider_sync",
            fields = mapOf(
                "auth_code" to "a1",
                "oauth-code" to "a2",
                "refresh token" to "a3",
                "clientSecret" to "a4",
                "user password" to "a5",
                "mode" to "manual",
            ),
        )

        assertEquals(LogLevel.INFO, sink.level)
        val payload = sink.payload
        assertTrue(payload.contains("\"auth_code\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"oauth-code\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"refresh token\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"clientSecret\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"user password\":\"[REDACTED]\""))
        assertTrue(payload.contains("\"mode\":\"manual\""))
    }

    private class CapturingSink : LogSink {
        var level: LogLevel? = null
        var payload: String = ""

        override fun write(level: LogLevel, payload: String) {
            this.level = level
            this.payload = payload
        }
    }
}
