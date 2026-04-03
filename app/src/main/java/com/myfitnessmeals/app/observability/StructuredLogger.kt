package com.myfitnessmeals.app.observability

import android.util.Log

interface StructuredLogger {
    fun info(event: String, fields: Map<String, Any?> = emptyMap())

    fun warn(event: String, fields: Map<String, Any?> = emptyMap())

    fun error(event: String, fields: Map<String, Any?> = emptyMap())
}

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
}

interface LogSink {
    fun write(level: LogLevel, payload: String)
}

class AndroidLogSink(
    private val tag: String = "mfm-observability",
) : LogSink {
    override fun write(level: LogLevel, payload: String) {
        when (level) {
            LogLevel.INFO -> Log.i(tag, payload)
            LogLevel.WARN -> Log.w(tag, payload)
            LogLevel.ERROR -> Log.e(tag, payload)
        }
    }
}

class RedactingStructuredLogger(
    private val sink: LogSink,
) : StructuredLogger {
    override fun info(event: String, fields: Map<String, Any?>) {
        sink.write(LogLevel.INFO, encodePayload(LogLevel.INFO, event, fields))
    }

    override fun warn(event: String, fields: Map<String, Any?>) {
        sink.write(LogLevel.WARN, encodePayload(LogLevel.WARN, event, fields))
    }

    override fun error(event: String, fields: Map<String, Any?>) {
        sink.write(LogLevel.ERROR, encodePayload(LogLevel.ERROR, event, fields))
    }

    private fun encodePayload(level: LogLevel, event: String, fields: Map<String, Any?>): String {
        val normalized = linkedMapOf<String, String>()
        normalized["level"] = level.name
        normalized["event"] = event

        fields.toSortedMap().forEach { (key, rawValue) ->
            normalized[key] = redactIfSensitive(key, rawValue)
        }

        val body = normalized.entries.joinToString(separator = ",") { (key, value) ->
            "\"${escapeJson(key)}\":\"${escapeJson(value)}\""
        }
        return "{$body}"
    }

    private fun redactIfSensitive(key: String, value: Any?): String {
        val normalizedKey = normalizeKey(key)
        if (SENSITIVE_KEYS.any { normalizedKey.contains(it) }) {
            return REDACTED
        }
        return value?.toString() ?: "null"
    }

    private fun normalizeKey(key: String): String {
        return key
            .lowercase()
            .replace(NON_ALPHANUMERIC, "")
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private companion object {
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")
        private val SENSITIVE_KEYS = setOf(
            "query",
            "barcode",
            "token",
            "authcode",
            "oauthcode",
            "refresh",
            "secret",
            "password",
            "email",
            "note",
            "name",
        )
        private const val REDACTED = "[REDACTED]"
    }
}
