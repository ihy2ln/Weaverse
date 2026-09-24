package com.ihy2ln.weaverse.data.settings

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StreamingAppearanceMigrationTest {
    @Test fun upgradeChangesIdentityWithoutChangingLibraryOrWritingPreferences() = runBlocking {
        val original = mutablePreferencesOf(
            stringPreferencesKey("appearance_profile") to "Classic",
            stringPreferencesKey("theme_mode") to "Light",
            stringPreferencesKey("selected_book_id") to "existing-book",
            stringPreferencesKey("prompt_draft") to "Keep this draft",
            intPreferencesKey("font_size_sp") to 23,
        )
        assertTrue(StreamingAppearanceMigration.shouldMigrate(original))
        val migrated = StreamingAppearanceMigration.migrate(original)
        assertEquals("Streaming", migrated[stringPreferencesKey("appearance_profile")])
        assertEquals("Dark", migrated[stringPreferencesKey("theme_mode")])
        assertEquals("App", migrated[stringPreferencesKey("reader_theme")])
        original.asMap().filterKeys { it.name !in setOf("appearance_profile", "theme_mode") }.forEach { (key, value) ->
            assertEquals(value, migrated.asMap()[key])
        }
        assertEquals("Classic", original[stringPreferencesKey("appearance_profile")])
    }
    @Test fun explicitReaderPaletteAndLaterThemeChoicesSurviveRelaunch() = runBlocking {
        val migrated = StreamingAppearanceMigration.migrate(mutablePreferencesOf(stringPreferencesKey("reader_theme") to "Sepia")).toMutablePreferences()
        migrated[stringPreferencesKey("appearance_profile")] = "Fantasy"
        migrated[stringPreferencesKey("theme_mode")] = "Light"
        assertFalse(StreamingAppearanceMigration.shouldMigrate(migrated))
        assertEquals("Sepia", migrated[stringPreferencesKey("reader_theme")])
    }
}
