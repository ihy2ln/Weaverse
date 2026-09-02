package com.ihy2ln.weaverse.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiRetryTest {
    @Test
    fun waitSecondsUsesRetryAfterWhenPresent() {
        assertEquals(12L, AiRetry.waitSeconds(12, 0))
    }

    @Test
    fun waitSecondsFallsBackToExponentialBackoff() {
        val first = AiRetry.waitSeconds(null, 0)
        val second = AiRetry.waitSeconds(null, 1)
        assertTrue(first in 1L..90L)
        assertTrue(second > first)
    }

    @Test
    fun waitSecondsIsCapped() {
        assertEquals(90L, AiRetry.waitSeconds(9_999, 0))
        assertEquals(1L, AiRetry.waitSeconds(0, 0))
    }
}
