package com.ihy2ln.weaverse.core.manga

import android.content.Context
import android.webkit.CookieManager
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class GalleryAccountState(val enabled: Boolean = false, val restricted: Boolean = false, val sessionSaved: Boolean = false)

/** Only these two exact HTTPS origins may receive gallery session credentials. */
internal object GallerySessionPolicy {
    val hosts = setOf("e-hentai.org", "exhentai.org")
    val loginHosts = hosts + "forums.e-hentai.org"
    val authNames = setOf("ipb_member_id", "ipb_pass_hash", "igneous")
    fun allowed(url: String, login: Boolean = false): Boolean = url.toHttpUrlOrNull()?.let {
        it.scheme == "https" && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() &&
            it.host in if (login) loginHosts else hosts
    } == true
    fun session(header: String?): Map<String, String> = header.orEmpty().split(';').mapNotNull { part ->
        val name = part.substringBefore('=').trim()
        val value = part.substringAfter('=', "").trim()
        if (name !in authNames || value.isBlank() || value.any { it == '\r' || it == '\n' }) null else name to value
    }.toMap()
    fun loggedIn(header: String?): Boolean = session(header).let { cookies ->
        cookies["ipb_member_id"]?.toLongOrNull()?.let { it > 0 } == true && !cookies["ipb_pass_hash"].isNullOrBlank()
    }
}

@Singleton
class GalleryAccountManager @Inject constructor(
    @ApplicationContext context: Context,
    private val secrets: SecureKeyStore,
) {
    private val prefs = context.getSharedPreferences("gallery-account-options", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readState())
    val state = _state.asStateFlow()
    private fun readState(): GalleryAccountState {
        val saved = GallerySessionPolicy.loggedIn(secrets.get("gallery-session"))
        return GalleryAccountState(prefs.getBoolean("enabled", false), prefs.getBoolean("restricted", false) && saved, saved)
    }
    fun enable(value: Boolean) {
        check(prefs.edit().putBoolean("enabled", value).commit()) { "Could not save source preference" }
        _state.value = readState()
    }
    fun enableRestricted(value: Boolean) {
        check(!value || state.value.sessionSaved) { "Sign in before enabling ExHentai. Access is controlled by the website." }
        check(prefs.edit().putBoolean("restricted", value).commit()) { "Could not save source preference" }
        _state.value = readState()
    }
    /** Called only after a user visits the official login in this app. No passwords are read. */
    fun saveWebSession(): Boolean {
        val cookies = CookieManager.getInstance()
        val primary = sequenceOf("https://e-hentai.org/", "https://forums.e-hentai.org/")
            .map { cookies.getCookie(it) }.firstOrNull { GallerySessionPolicy.loggedIn(it) } ?: return false
        val primaryId = GallerySessionPolicy.session(primary)["ipb_member_id"]
        if (GallerySessionPolicy.session(secrets.get("gallery-session"))["ipb_member_id"] != primaryId) {
            secrets.clear("gallery-igneous")
            prefs.edit().putBoolean("restricted", false).commit()
        }
        secrets.set("gallery-session", GallerySessionPolicy.session(primary).entries.joinToString("; ") { "${it.key}=${it.value}" })
        val restricted = GallerySessionPolicy.session(cookies.getCookie("https://exhentai.org/"))
        if (restricted["ipb_member_id"] == primaryId) restricted["igneous"]?.takeIf { it != "mystery" }?.let { secrets.set("gallery-igneous", it) }
        cookies.flush()
        _state.value = readState()
        return true
    }
    /** The two official sites share account credentials; the server still decides eligibility. */
    fun prepareRestrictedWebSession(): Boolean {
        if (!saveWebSession()) return false
        val cookies = CookieManager.getInstance()
        GallerySessionPolicy.session(secrets.get("gallery-session")).filterKeys { it != "igneous" }.forEach { (name, value) ->
            cookies.setCookie("https://exhentai.org/", "$name=$value; Path=/; Secure; HttpOnly")
        }
        cookies.flush()
        return true
    }
    internal fun cookieHeader(url: String): String? {
        if (!state.value.enabled || !GallerySessionPolicy.allowed(url)) return null
        val values = GallerySessionPolicy.session(secrets.get("gallery-session")).toMutableMap()
        if (url.toHttpUrlOrNull()?.host == "exhentai.org") secrets.get("gallery-igneous")?.let { values["igneous"] = it }
        return values.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifBlank { null }
    }
    fun logout() {
        secrets.clear("gallery-session"); secrets.clear("gallery-igneous")
        prefs.edit().putBoolean("restricted", false).commit()
        // Scope deletion to this account. Never clear other source/browser logins.
        val cookies = CookieManager.getInstance()
        GallerySessionPolicy.loginHosts.forEach { host ->
            GallerySessionPolicy.authNames.forEach { name ->
                cookies.setCookie("https://$host/", "$name=; Path=/; Max-Age=0; Secure")
                cookies.setCookie("https://$host/", "$name=; Domain=.$host; Path=/; Max-Age=0; Secure")
            }
        }
        cookies.flush()
        _state.value = readState()
    }
}
