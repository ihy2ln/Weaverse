package com.ihy2ln.weaverse.core.media

import java.io.InputStream
import java.security.MessageDigest

/** Content-based dedupe key for imported media (spec §7: "compute a checksum for dedupe"). */
fun computeSha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
    }
    // Byte is signed in Kotlin/JVM — format its raw value and a negative byte
    // (anything >= 0x80) would print wrong. Mask to Int first so it's always 0..255.
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}

private const val DEFAULT_BUFFER_SIZE = 8192
