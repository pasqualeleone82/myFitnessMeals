package com.myfitnessmeals.app.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentFieldFormatterTest {
    @Test
    fun normalizePercentInput_keepsDigitsOnly() {
        assertEquals("40", normalizePercentInput("40"))
        assertEquals("40", normalizePercentInput("40%"))
        assertEquals("40", normalizePercentInput("40%%"))
        assertEquals("401", normalizePercentInput("4a0%1"))
    }

    @Test
    fun normalizePercentInput_handlesEmptyAndSymbols() {
        assertEquals("", normalizePercentInput(""))
        assertEquals("", normalizePercentInput("%"))
        assertEquals("", normalizePercentInput("%%"))
    }

    @Test
    fun formatPercentDisplay_isIdempotentAndSingleSymbol() {
        assertEquals("", formatPercentDisplay(""))
        assertEquals("0%", formatPercentDisplay("0"))
        assertEquals("40%", formatPercentDisplay("40"))
        assertEquals("40%", formatPercentDisplay("40%"))
        assertEquals("40%", formatPercentDisplay("40%%"))
    }
}
