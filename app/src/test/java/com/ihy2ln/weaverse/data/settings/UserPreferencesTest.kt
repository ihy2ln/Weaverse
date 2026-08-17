package com.ihy2ln.weaverse.data.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserPreferencesTest {
    @Test
    fun extraPromptSurfacesAreOffByDefault() {
        val extras = UserPreferences().extraPromptSurfaces
        assertFalse(extras.inlineWriting)
        assertFalse(extras.sceneBeatCard)
        assertFalse(extras.continuation)
        assertFalse(extras.chatComposer)
        assertFalse(extras.roleplayButtons)
    }

    @Test
    fun extraPromptSurfacesAreIndependent() {
        val extras = ExtraPromptSurfaces(sceneBeatCard = true)
        assertFalse(extras.inlineWriting)
        assertTrue(extras.sceneBeatCard)
        assertFalse(extras.continuation)
        assertFalse(extras.chatComposer)
        assertFalse(extras.roleplayButtons)
    }
}
