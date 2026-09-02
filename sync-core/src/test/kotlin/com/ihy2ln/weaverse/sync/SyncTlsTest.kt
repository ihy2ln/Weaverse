package com.ihy2ln.weaverse.sync

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncTlsTest {
    @Test
    fun `generated cert verifies and round-trips through PKCS12`() {
        val dir = File.createTempFile("weaverse-tls", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val file = File(dir, "tls.p12")
            val created = SyncTls.loadOrCreate(file)
            created.certificate.checkValidity()
            created.certificate.verify(created.keyPair.public)
            assertTrue(SyncTls.fingerprint(created).matches(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")))

            val loaded = SyncTls.loadOrCreate(file)
            assertEquals(SyncTls.fingerprint(created), SyncTls.fingerprint(loaded))
            val ks = SyncTls.toKeyStore(loaded)
            assertTrue(ks.containsAlias(SyncTls.KEY_ALIAS))
        } finally {
            dir.deleteRecursively()
        }
    }
}
