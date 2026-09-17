package com.ihy2ln.weaverse.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Apply the new identity on upgrade, before the first preferences emission.
 * The marker lets later explicit theme choices survive every subsequent launch.
 * No manuscript, provider, history, layout or custom surface preference is changed.
 */
internal object StreamingAppearanceMigration : DataMigration<Preferences> {
    private val applied = booleanPreferencesKey("streaming_identity_applied")
    override suspend fun shouldMigrate(currentData: Preferences) = currentData[applied] != true
    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        this[stringPreferencesKey("appearance_profile")] = "Streaming"
        this[stringPreferencesKey("theme_mode")] = "Dark"
        // An explicitly selected reading palette remains a reading preference.
        val reader = stringPreferencesKey("reader_theme")
        if (this[reader] == null) this[reader] = "App"
        this[applied] = true
    }
    override suspend fun cleanUp() = Unit
}
