package com.ihy2ln.weaverse.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class CodexScopesTest {
    @Test
    fun globalScopeMatchesNotesAppWideLibrary() {
        assertEquals("app", CodexScopes.TYPE)
        assertEquals("global", CodexScopes.ID)
    }
}
