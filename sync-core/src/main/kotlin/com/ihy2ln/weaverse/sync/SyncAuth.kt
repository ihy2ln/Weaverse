package com.ihy2ln.weaverse.sync

import java.net.InetAddress
import java.security.SecureRandom
import java.util.UUID

object SyncAuth {
    private val secure = SecureRandom()

    fun newDeviceId(): String = UUID.randomUUID().toString()

    fun newPairPin(): String = (100000 + secure.nextInt(900000)).toString()

    fun newSessionToken(): String {
        val bytes = ByteArray(24)
        secure.nextBytes(bytes)
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    fun constantTimeEquals(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    /** True for 127.0.0.0/8 and ::1 — the host's own browser, never a LAN peer. */
    fun isLoopback(remoteAddress: String?): Boolean {
        if (remoteAddress.isNullOrBlank()) return false
        val literal = remoteAddress.removePrefix("/").substringBefore('%')
        if (!literal.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' }) return false
        return runCatching { InetAddress.getByName(literal).isLoopbackAddress }.getOrDefault(false)
    }

    /** Media / record ids used to build file names: no path separators or dot-segments. */
    fun isSafeId(id: String): Boolean =
        id.isNotBlank() && id.length <= 200 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /**
     * Slows PIN guessing on the LAN pairing endpoint: after [maxFailures] wrong PINs,
     * every pair attempt is refused for [lockMs].
     */
    class PairThrottle(private val maxFailures: Int = 5, private val lockMs: Long = 60_000) {
        private var failures = 0
        private var lockedUntil = 0L

        @Synchronized
        fun isLocked(now: Long = System.currentTimeMillis()): Boolean = now < lockedUntil

        @Synchronized
        fun recordFailure(now: Long = System.currentTimeMillis()) {
            failures++
            if (failures >= maxFailures) {
                failures = 0
                lockedUntil = now + lockMs
            }
        }

        @Synchronized
        fun recordSuccess() {
            failures = 0
        }
    }
}
