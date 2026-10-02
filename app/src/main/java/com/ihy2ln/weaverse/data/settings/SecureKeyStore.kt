package com.ihy2ln.weaverse.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    /**
     * Some devices corrupt the Android Keystore entry behind encrypted prefs (after a
     * restore, an OS update or a lock-screen change), and opening them then throws. That
     * used to crash the app at startup. The unreadable store is reset (keys must be entered
     * again); if even that fails, keys live in memory for this session.
     */
    private val prefs: SharedPreferences? = openOrReset(context)
    private val memory = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** False when the keystore was unusable and keys aren't being saved. */
    val persistent: Boolean get() = prefs != null

    fun get(providerId: String): String? =
        (runCatching { prefs?.getString(keyFor(providerId), null) }.getOrNull() ?: memory[keyFor(providerId)])
            ?.takeIf { it.isNotBlank() }

    fun set(providerId: String, value: String) {
        memory[keyFor(providerId)] = value.trim()
        runCatching { prefs?.edit()?.putString(keyFor(providerId), value.trim())?.apply() }
    }

    fun clear(providerId: String) {
        memory.remove(keyFor(providerId))
        runCatching { prefs?.edit()?.remove(keyFor(providerId))?.apply() }
    }

    private fun openOrReset(context: Context): SharedPreferences? {
        fun open(): SharedPreferences = EncryptedSharedPreferences.create(
            PREFS_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return runCatching { open() }.recoverCatching { first ->
            android.util.Log.e("Weaverse", "Encrypted key store unreadable; resetting it", first)
            context.deleteSharedPreferences(PREFS_NAME)
            open()
        }.onFailure { android.util.Log.e("Weaverse", "Encrypted key store unavailable; keys kept in memory only", it) }
            .getOrNull()
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
