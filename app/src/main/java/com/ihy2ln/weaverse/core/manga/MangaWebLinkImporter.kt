package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class WebLinkPage(
    val index: Int,
    val url: String,
    val fileName: String,
)

data class WebLinkSnapshot(
    val url: String,
    val title: String,
    val pages: List<WebLinkPage>,
)

/**
 * Reads image URLs that a public reader page already exposes in its HTML.
 * This deliberately does not execute JavaScript or bypass login, paywall,
 * CAPTCHA, robots, or anti-bot controls.
 */
@Singleton
class MangaWebLinkImporter @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun inspect(url: String): WebLinkSnapshot = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Paste a complete http:// or https:// link."
        }
        val request = Request.Builder()
            .url(normalized)
            .header("User-Agent", "Weaverse/1.0")
            .header("Accept", "text/html,application/xhtml+xml,image/*;q=0.8,*/*;q=0.5")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Website returned HTTP ${response.code}")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if (contentType.startsWith("image/")) {
                val fileName = "web-0001.${extension(normalized)}"
                return@use WebLinkSnapshot(
                    url = normalized,
                    title = normalized.substringAfterLast('/').substringBefore('?').ifBlank { "Web image" },
                    pages = listOf(WebLinkPage(0, normalized, fileName)),
                )
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) error("The website returned an empty page.")
            val pages = extractImageUrls(normalized, body)
            if (pages.isEmpty()) {
                error("No public page images were found. The site may require JavaScript, login, or an authorized connector.")
            }
            WebLinkSnapshot(
                url = normalized,
                title = extractTitle(body).ifBlank { normalized.substringAfterLast('/').ifBlank { "Web import" } },
                pages = pages,
            )
        }
    }

    internal fun extractImageUrls(baseUrl: String, html: String): List<WebLinkPage> {
        val imageTag = Regex("<(?:img|source)\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attribute = Regex(
            "(?:src|srcset|data-src|data-srcset|data-original|data-lazy-src|data-image|data-url)\\s*=\\s*[\\\"']([^\\\"']+)",
            RegexOption.IGNORE_CASE,
        )
        val inlineImage = Regex(
            "https?:\\\\?/\\\\?/[^\\\"'<>\\s]+?\\.(?:jpe?g|png|webp|avif|gif)(?:\\?[^\\\"'<>\\s]*)?",
            RegexOption.IGNORE_CASE,
        )
        val found = LinkedHashSet<String>()
        imageTag.findAll(html).forEach { tagMatch ->
            val tag = tagMatch.value
            val candidate = attribute.find(tag)?.groupValues?.getOrNull(1)
                .orEmpty()
                .substringBefore(',')
                .trim()
                .substringBefore(' ')
            val resolved = resolve(baseUrl, candidate)
            if (resolved != null && looksLikePageImage(resolved, tag)) found += resolved
        }
        val nonTagHtml = imageTag.replace(html, " ")
        inlineImage.findAll(nonTagHtml).forEach { match ->
            val candidate = match.value.replace("\\/", "/")
            val resolved = resolve(baseUrl, candidate)
            if (resolved != null && looksLikePageImage(resolved, match.value)) found += resolved
        }
        val numberedPages = found.filter { url ->
            runCatching {
                URI(url).path.substringAfterLast('/').substringBeforeLast('.').toIntOrNull() != null
            }.getOrDefault(false)
        }
        val ordered = if (numberedPages.size >= 2) numberedPages else found.toList()
        return ordered.mapIndexed { index, url ->
            WebLinkPage(index, url, "web-${(index + 1).toString().padStart(4, '0')}.${extension(url)}")
        }
    }

    private fun looksLikePageImage(url: String, tag: String): Boolean {
        val lower = url.lowercase()
        if (BLOCKED_HINTS.any(lower::contains)) return false
        val hasImageExtension = IMAGE_EXTENSIONS.any { lower.substringBefore('?').endsWith(".$it") }
        val dataImage = tag.contains("data-", ignoreCase = true)
        return hasImageExtension || dataImage || lower.contains("image") || lower.contains("chapter") || lower.contains("page")
    }

    private fun resolve(baseUrl: String, raw: String): String? = runCatching {
        val clean = raw.replace("&amp;", "&").replace("\\/", "/").trim()
        if (clean.isBlank() || clean.startsWith("data:") || clean.startsWith("javascript:")) return null
        URI(baseUrl).resolve(clean).toString().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }.getOrNull()

    private fun extractTitle(html: String): String = Regex(
        "<title[^>]*>(.*?)</title>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
        .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

    private fun extension(url: String): String = url.substringBefore('?').substringAfterLast('.', "jpg")
        .lowercase().filter { it.isLetterOrDigit() }.ifBlank { "jpg" }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif")
        val BLOCKED_HINTS = setOf("favicon", "logo", "sprite", "avatar", "icon", "banner", "advert", "ads.", "/ads/", "analytics", "pixel", "tracking")
    }
}
