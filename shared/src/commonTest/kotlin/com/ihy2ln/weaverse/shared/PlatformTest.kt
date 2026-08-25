package com.ihy2ln.weaverse.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun greeting_mentionsPlatformName() {
        val platform = Platform()
        assertTrue(platform.name.isNotBlank())
        assertTrue(greeting().contains(platform.name))
    }
}
