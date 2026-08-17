package com.ihy2ln.weaverse.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API keys only, AES-256-GCM via Jetpack Security (spec ground rules:
 * "Never plaintext, never committed"). Keyed by
 * [com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity.id] — the
 * profile's non-secret metadata (label, base URL, provider type) lives in
 * Room; only the key itself lives here, in a *separate* sharedpref file
 * from the rest of the app's settings so `backup_rules.xml`/
 * `data_extraction_rules.xml` (Phase 1) can exclude exactly this file from
 * Auto Backup without also excluding unrelated preferences.
 */
@Singleton
class SecretsStore @Inject constructor(@ApplicationContext context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getApiKey(connectionProfileId: String): String? = prefs.getString(connectionProfileId, null)

    fun setApiKey(connectionProfileId: String, apiKey: String) {
        prefs.edit().putString(connectionProfileId, apiKey).apply()
    }

    fun removeApiKey(connectionProfileId: String) {
        prefs.edit().remove(connectionProfileId).apply()
    }

    private companion object {
        const val FILE_NAME = "weaverse_secrets"
    }
}
