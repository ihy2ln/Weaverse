package com.ihy2ln.weaverse.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncUrlsTest {
    @Test
    fun addsSchemeAndDefaultPort() {
        assertEquals("http://192.168.1.20:8787", normalizeSyncBaseUrl("192.168.1.20"))
    }

    @Test
    fun keepsExplicitPortAndHttps() {
        assertEquals("https://hub.example:443", normalizeSyncBaseUrl("https://hub.example:443/"))
    }
}
