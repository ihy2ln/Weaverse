package com.ihy2ln.weaverse.sync

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
}
