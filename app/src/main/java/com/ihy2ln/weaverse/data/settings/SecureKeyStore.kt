package com.ihy2ln.weaverse.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(providerId: String): String? = prefs.getString(keyFor(providerId), null)?.takeIf { it.isNotBlank() }

    fun set(providerId: String, value: String) {
        prefs.edit().putString(keyFor(providerId), value.trim()).apply()
    }

    fun clear(providerId: String) {
        prefs.edit().remove(keyFor(providerId)).apply()
    }

    private fun keyFor(providerId: String) = "api_key_$providerId"

    companion object {
        private const val PREFS_NAME = "weaverse_secrets"
        const val OPENROUTER = "openrouter"
        const val ANTHROPIC = "anthropic"
        const val OPENAI = "openai"
        const val GEMINI = "gemini"
    }
}
