package com.ihy2ln.weaverse.data.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodexScopesTest {
    @Test
    fun globalScopeMatchesNotesAppWideLibrary() {
        assertEquals("app", CodexScopes.TYPE)
        assertEquals("global", CodexScopes.ID)
    }
}
