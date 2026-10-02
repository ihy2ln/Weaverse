package com.ihy2ln.weaverse.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Who may talk to a sync host: paired sessions that expire, and a lockout for any address
 * that keeps guessing the six-digit sync password. Shared by the Android and desktop hosts.
 */
class SyncGuard(
    private val sessionTtlMs: Long = SESSION_TTL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val sessions = ConcurrentHashMap<String, Long>()

    private class Strikes(var count: Int = 0, var lockedUntil: Long = 0L)

    private val strikes = ConcurrentHashMap<String, Strikes>()

    /** A fresh session token, valid for [sessionTtlMs]. */
    fun newSession(): String {
        val time = now()
        sessions.entries.removeIf { it.value < time }
        return SyncAuth.newSessionToken().also { sessions[it] = time + sessionTtlMs }
    }

    fun isSession(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val expires = sessions[token] ?: return false
        if (expires < now()) {
            sessions.remove(token)
            return false
        }
        return true
    }

    fun isLockedOut(client: String): Boolean = (strikes[client]?.lockedUntil ?: 0L) > now()

    /**
     * Checks a password [given] by [client] against [secret]. After [FREE_ATTEMPTS] wrong
     * guesses the client is locked out, twice as long after each further miss; a lockout
     * rejects even the right password until it ends.
     */
    fun checkSecret(client: String, given: String?, secret: String): Boolean {
        if (isLockedOut(client)) return false
        if (!given.isNullOrBlank() && SyncAuth.constantTimeEquals(given, secret)) {
            strikes.remove(client)
            return true
        }
        val record = strikes.getOrPut(client) { Strikes() }
        synchronized(record) {
            record.count++
            if (record.count >= FREE_ATTEMPTS) {
                val doublings = (record.count - FREE_ATTEMPTS).coerceAtMost(10)
                record.lockedUntil = now() + minOf(MAX_LOCK_MS, BASE_LOCK_MS shl doublings)
            }
        }
        return false
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_LOCK_MS = 60 * 60_000L
        const val SESSION_TTL_MS = 12 * 60 * 60_000L

        /** Requests from the host itself may read its password; the rest of the network may not. */
        fun isLoopback(remoteHost: String?): Boolean {
            val host = remoteHost?.trim()?.removePrefix("[")?.removeSuffix("]")?.lowercase() ?: return false
            return host == "localhost" || host == "::1" || host == "0:0:0:0:0:0:0:1" || host.startsWith("127.") ||
                host == "::ffff:127.0.0.1"
        }
    }
}
