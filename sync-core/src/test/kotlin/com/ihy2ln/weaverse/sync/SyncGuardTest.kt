package com.ihy2ln.weaverse.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncGuardTest {
    private var clock = 1_000_000L
    private val guard = SyncGuard(sessionTtlMs = 60_000L, now = { clock })

    @Test
    fun `sessions expire`() {
        val token = guard.newSession()
        assertTrue(guard.isSession(token))
        clock += 61_000L
        assertFalse(guard.isSession(token))
        assertFalse(guard.isSession(null))
        assertFalse(guard.isSession("made-up"))
    }

    @Test
    fun `repeated wrong passwords lock the client out, even for the right one`() {
        repeat(SyncGuard.FREE_ATTEMPTS) { assertFalse(guard.checkSecret("10.0.0.5", "000000", "123456")) }
        assertTrue(guard.isLockedOut("10.0.0.5"))
        assertFalse(guard.checkSecret("10.0.0.5", "123456", "123456"))
        // Other addresses are unaffected.
        assertTrue(guard.checkSecret("10.0.0.6", "123456", "123456"))
        clock += SyncGuard.BASE_LOCK_MS + 1
        assertTrue(guard.checkSecret("10.0.0.5", "123456", "123456"))
        assertFalse(guard.isLockedOut("10.0.0.5"))
    }

    @Test
    fun `blank passwords never match`() {
        assertFalse(guard.checkSecret("10.0.0.7", "", ""))
        assertFalse(guard.checkSecret("10.0.0.7", null, "123456"))
    }

    @Test
    fun `only the host itself counts as loopback`() {
        assertTrue(SyncGuard.isLoopback("127.0.0.1"))
        assertTrue(SyncGuard.isLoopback("::1"))
        assertTrue(SyncGuard.isLoopback("0:0:0:0:0:0:0:1"))
        assertFalse(SyncGuard.isLoopback("192.168.1.20"))
        assertFalse(SyncGuard.isLoopback(null))
    }
}
