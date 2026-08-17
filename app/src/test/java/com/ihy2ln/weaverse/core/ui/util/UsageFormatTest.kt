package com.ihy2ln.weaverse.core.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UsageFormatTest {
    @Test
    fun formatCost_avoidsScientificNotation() {
        assertEquals("$0.00007182", UsageFormat.formatCost(7.182E-5))
    }

    @Test
    fun formatCost_zero() {
        assertEquals("$0.00", UsageFormat.formatCost(0.0))
    }

    @Test
    fun formatUsage_includesReadableCost() {
        val result = UsageFormat.formatUsage(1200, 350, 1550, 7.182E-5)
        assertTrue(result.contains("1,200"))
        assertTrue(result.contains("350"))
        assertTrue(result.contains("$0.00007182"))
    }
}
