package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * Ordinary HTTP for public catalog HTML/JSON.  Challenge pages are reported, never solved.
 */
internal class MangaCatalogHttp(
    private val client: OkHttpClient,
    private val sourceName: String,
) {
    suspend fun get(url: String, accept: String = ACCEPT): String = execute(
        Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en,ja;q=0.8")
            .build(),
    )

    suspend fun post(url: String, fields: Map<String, String>, accept: String = ACCEPT): String {
        val body = FormBody.Builder().also { builder ->
            fields.forEach { (key, value) -> builder.add(key, value) }
        }.build()
        return execute(
            Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", "en-US,en,ja;q=0.8")
                .post(body)
                .build(),
        )
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("$sourceName blocked the catalog request (HTTP ${response.code}).")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("$sourceName returned an empty catalog.")
            if (looksLikeChallenge(body)) {
                error("$sourceName requires a browser verification challenge and cannot be browsed right now.")
            }
            body
        }
    }

    companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 Weaverse/1.0"
        const val ACCEPT = "text/html,application/xhtml+xml;q=0.9,application/json;q=0.8,*/*;q=0.7"

        fun looksLikeChallenge(body: String): Boolean =
            body.contains("cf-chl-", true) ||
                body.contains("just a moment", true) ||
                body.contains("captcha", true)
    }
}

internal fun resolveHttpUrl(baseUrl: String, raw: String): String? = runCatching {
    val clean = raw.trim().replace("\\/", "/")
    if (clean.isBlank() || clean.startsWith("javascript:") || clean.startsWith("data:")) return null
    URI(baseUrl).resolve(clean).toString().takeIf { it.startsWith("http://") || it.startsWith("https://") }
}.getOrNull()

internal fun pathSegments(url: String): List<String> =
    runCatching { URI(url).path.orEmpty().trim('/').lowercase().split('/').filter { it.isNotEmpty() } }
        .getOrDefault(emptyList())
