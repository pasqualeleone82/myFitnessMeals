package com.myfitnessmeals.app.ui.common.input

/**
 * Canonical formatter for percent input fields.
 * Input/storage stays numeric-only while UI can present a single percent suffix.
 */
fun normalizePercentInput(raw: String): String = raw.filter(Char::isDigit)

fun formatPercentDisplay(value: String): String {
    val normalized = normalizePercentInput(value)
    return if (normalized.isEmpty()) "" else "$normalized%"
}
