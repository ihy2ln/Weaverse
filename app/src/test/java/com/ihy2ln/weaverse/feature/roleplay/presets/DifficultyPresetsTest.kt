package com.ihy2ln.weaverse.feature.roleplay.presets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DifficultyPresetsTest {
    @Test
    fun presetsRunSliceOfLifeToRuthless() {
        assertEquals(
            listOf("Slice of life", "Normal", "Hard", "Ruthless"),
            defaultPresets.map { it.name },
        )
    }

    @Test
    fun harderSettingsAreMoreDisciplined() {
        val temps = defaultPresets.map { it.temperature }
        assertEquals(temps.sortedDescending(), temps)
    }

    @Test
    fun everyPresetCarriesADirectiveForTheSystemPrompt() {
        defaultPresets.forEach {
            assertTrue(it.directive.isNotBlank(), "${it.name} has no directive")
            assertTrue(it.description.isNotBlank(), "${it.name} has no description")
        }
    }

    @Test
    fun theStoredDefaultStillResolves() {
        // UserPreferences defaults to preset-balanced; renaming must not orphan it.
        assertNotNull(defaultPresets.firstOrNull { it.id == "preset-balanced" })
        assertEquals("Normal", defaultPresets.first { it.id == "preset-balanced" }.name)
    }
}
