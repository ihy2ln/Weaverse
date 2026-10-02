package com.ihy2ln.weaverse.feature.chatting.social

import android.net.Uri
import android.util.Base64
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import com.ihy2ln.weaverse.feature.chatting.media.WebPictureSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RedditAccountState(
    val clientId: String = "",
    val username: String = "",
    val signedIn: Boolean = false,
    val message: String = "",
)

/**
 * The writer's own Reddit account, through Reddit's official API (OAuth, "installed app").
 * Anonymous RSS is throttled to a request or two a minute, which left most Reddit and
 * adult feeds empty; signed in, the app reads like any Reddit client, including the
 * writer's own front page and NSFW communities their account allows.
 *
 * Setup: the writer creates an "installed app" at reddit.com/prefs/apps with redirect URI
 * [REDIRECT_URI] and pastes its client ID. Tokens live in [SecureKeyStore]; only read
 * scopes are requested, nothing is ever posted.
 */
@Singleton
class RedditAccount @Inject constructor(
    private val secrets: SecureKeyStore,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenLock = Mutex()
    private var accessToken: String = ""
    private var expiresAt: Long = 0L
    private var pendingState: String = ""

    private val _state = MutableStateFlow(
        RedditAccountState(
            clientId = secrets.get(KEY_CLIENT).orEmpty(),
            username = secrets.get(KEY_USER).orEmpty(),
            signedIn = !secrets.get(KEY_REFRESH).isNullOrBlank(),
        ),
    )
    val state: StateFlow<RedditAccountState> = _state.asStateFlow()

    val signedIn: Boolean get() = _state.value.signedIn

    fun setClientId(clientId: String) {
        val id = clientId.trim()
        if (id == _state.value.clientId) return
        secrets.set(KEY_CLIENT, id)
        signOut()
        _state.update { it.copy(clientId = id) }
    }

    /** The page the writer approves access on; null until a client ID is set. */
    fun authorizeUri(): Uri? {
        val clientId = _state.value.clientId.ifBlank { return null }
        pendingState = UUID.randomUUID().toString()
        return Uri.parse("https://www.reddit.com/api/v1/authorize.compact").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", pendingState)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("duration", "permanent")
            .appendQueryParameter("scope", "read identity mysubreddits")
            .build()
    }

    /** Finishes sign-in from Reddit's redirect back into the app. */
    suspend fun complete(redirect: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            redirect.getQueryParameter("error")?.let { error(if (it == "access_denied") "Reddit sign-in was cancelled" else "Reddit said: $it") }
            val code = redirect.getQueryParameter("code") ?: error("Reddit didn't send a sign-in code")
            if (pendingState.isNotBlank() && redirect.getQueryParameter("state") != pendingState) error("Sign-in link expired — try again")
            val token = tokenRequest(FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .build())
            val refresh = token.str("refresh_token") ?: error("Reddit didn't grant lasting access")
            secrets.set(KEY_REFRESH, refresh)
            accessToken = token.str("access_token").orEmpty()
            expiresAt = System.currentTimeMillis() + (token.str("expires_in")?.toLongOrNull() ?: 3600L) * 1000 - 60_000
            val me = getJson("/api/v1/me") as? JsonObject
            val name = me?.str("name").orEmpty()
            secrets.set(KEY_USER, name)
            _state.update { it.copy(signedIn = true, username = name, message = "Signed in as u/$name") }
            name
        }.onFailure { err -> _state.update { it.copy(message = err.message ?: "Reddit sign-in failed") } }
    }

    fun signOut() {
        secrets.clear(KEY_REFRESH)
        secrets.clear(KEY_USER)
        accessToken = ""
        expiresAt = 0L
        _state.update { it.copy(signedIn = false, username = "", message = "") }
    }

    /** GET on oauth.reddit.com, e.g. `/r/anime/hot?limit=25`. */
    suspend fun getJson(path: String): JsonElement = withContext(Dispatchers.IO) {
        val token = token()
        val request = Request.Builder()
            .url("https://oauth.reddit.com$path" + (if ('?' in path) "&" else "?") + "raw_json=1")
            .header("Authorization", "bearer $token")
            .header("User-Agent", WebPictureSearch.USER_AGENT)
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401) {
                accessToken = ""
                error("Reddit session expired")
            }
            require(response.isSuccessful) { "Reddit HTTP ${response.code}" }
            json.parseToJsonElement(response.body?.string().orEmpty())
        }
    }

    private suspend fun token(): String = tokenLock.withLock {
        if (accessToken.isNotBlank() && System.currentTimeMillis() < expiresAt) return accessToken
        val refresh = secrets.get(KEY_REFRESH) ?: error("Not signed in to Reddit")
        val token = tokenRequest(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refresh).build())
        accessToken = token.str("access_token") ?: run {
            signOut()
            error("Reddit sign-in was revoked — sign in again")
        }
        expiresAt = System.currentTimeMillis() + (token.str("expires_in")?.toLongOrNull() ?: 3600L) * 1000 - 60_000
        accessToken
    }

    private fun tokenRequest(body: FormBody): JsonObject {
        val basic = Base64.encodeToString("${_state.value.clientId}:".toByteArray(), Base64.NO_WRAP)
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("Authorization", "Basic $basic")
            .header("User-Agent", WebPictureSearch.USER_AGENT)
            .post(body)
            .build()
        return http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            require(response.isSuccessful) { "Reddit sign-in failed (HTTP ${response.code}) — check the client ID and redirect URI" }
            json.parseToJsonElement(text) as? JsonObject ?: error("Reddit sent an unreadable reply")
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        const val REDIRECT_URI = "weaverse://reddit-auth"
        private const val KEY_CLIENT = "reddit_client_id"
        private const val KEY_REFRESH = "reddit_refresh_token"
        private const val KEY_USER = "reddit_username"
    }
}
