package com.ihy2ln.weaverse.feature.roleplay.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelativeStampTest {
    private val now = 1_700_000_000_000L

    private fun stampAgo(millis: Long) = relativeStamp(now - millis, now)

    @Test
    fun formatsAcrossTheUnitBoundaries() {
        assertEquals("now", stampAgo(0))
        assertEquals("now", stampAgo(59_000))
        assertEquals("1m", stampAgo(60_000))
        assertEquals("59m", stampAgo(59 * 60_000L))
        assertEquals("1h", stampAgo(60 * 60_000L))
        assertEquals("23h", stampAgo(23 * 3_600_000L))
        assertEquals("1d", stampAgo(24 * 3_600_000L))
        assertEquals("6d", stampAgo(6 * 86_400_000L))
        assertEquals("1w", stampAgo(7 * 86_400_000L))
        assertEquals("4w", stampAgo(30 * 86_400_000L))
    }

    @Test
    fun handlesMissingAndFutureTimestamps() {
        // Chats seeded without a timestamp must not render a stamp at all.
        assertEquals("", relativeStamp(0L, now))
        assertEquals("", relativeStamp(-1L, now))
        // Clock skew must never produce a negative age.
        assertEquals("now", relativeStamp(now + 60_000, now))
    }
}
