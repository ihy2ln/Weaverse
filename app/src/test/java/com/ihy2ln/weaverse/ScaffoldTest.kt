package com.ihy2ln.weaverse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScaffoldTest {
    @Test
    fun appPackage_matchesSpec() {
        assertEquals(true, BuildConfig.APPLICATION_ID.contains("weaverse"))
    }
}
