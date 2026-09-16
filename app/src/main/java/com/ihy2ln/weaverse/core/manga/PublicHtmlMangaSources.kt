package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import org.jsoup.Jsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Built-in public website adapters. JavaScript-only catalogs use the site's normal WebView UI;
 * the host does not fabricate access tokens, log in, or solve verification challenges.
 */
@Singleton
class PublicHtmlMangaSources @Inject constructor(
    client: OkHttpClient,
    webLinkImporter: MangaWebLinkImporter,
    renderedCatalog: RenderedCatalogClient,
) {
    val sources: List<MangaSourceAdapter> = listOf(
        PublicHtmlMangaSourceAdapter(
            config = PublicHtmlSourceConfig(
                id = "comix",
                name = "Comix",
                baseUrl = "https://comix.to/",
                popularPaths = listOf("/"),
                latestPaths = listOf("/browse"),
                searchPaths = listOf("/browse?keyword=%s"),
                seriesPathHints = listOf("/title/", "/comic/", "/manga/", "/series/"),
            ),
            client = client,
            webLinkImporter = webLinkImporter,
            renderedLoader = renderedCatalog::load,
        ),
        AtsumaruSource(client),
        PublicHtmlMangaSourceAdapter(
            config = PublicHtmlSourceConfig(
                id = "mangafire",
                name = "MangaFire",
                baseUrl = "https://mangafire.to/",
                popularPaths = listOf("/"),
                latestPaths = listOf("/browse"),
                searchPaths = listOf("/browse?keyword=%s"),
                seriesPathHints = listOf("/manga/", "/title/", "/series/"),
            ),
            client = client,
            webLinkImporter = webLinkImporter,
            renderedLoader = renderedCatalog::load,
        ),
        PublicHtmlMangaSourceAdapter(
            config = PublicHtmlSourceConfig(
                id = "mangadot",
                name = "MangaDot",
                baseUrl = "https://mangadot.net/",
                popularPaths = listOf("/view-all/most-tracked", "/search?page=1&sortBy=views", "/"),
                latestPaths = listOf("/search?page=1&sortBy=updatedAt", "/search?page=1&sortBy=updated", "/latest", "/"),
                searchPaths = listOf("/search?query=%s", "/search?keyword=%s", "/search?q=%s", "/?s=%s"),
                seriesPathHints = listOf("/manga/", "/series/", "/title/"),
            ),
            client = client,
            webLinkImporter = webLinkImporter,
            renderedLoader = renderedCatalog::load,
        ),
        PublicHtmlMangaSourceAdapter(
            config = PublicHtmlSourceConfig(
                id = "rawkuma",
                name = "Rawkuma",
                baseUrl = "https://rawkuma.net/",
                popularPaths = listOf("/manga/?order=popular", "/library/?sort=popular", "/"),
                latestPaths = listOf("/manga/?order=update", "/library/?sort=updated", "/"),
                searchPaths = listOf("/?s=%s&post_type=wp-manga", "/manga/?s=%s", "/?s=%s"),
                seriesPathHints = listOf("/manga/", "/series/"),
                language = "ja",
                readingOrder = "rtl",
            ),
            client = client,
            webLinkImporter = webLinkImporter,
        ),
    )
}

internal data class PublicHtmlSourceConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val popularPaths: List<String>,
    val latestPaths: List<String>,
    val searchPaths: List<String>,
    val seriesPathHints: List<String>,
    val language: String = "en",
    val readingOrder: String = "ltr",
)

private data class PublicHtmlMetadata(
    val description: String = "",
    val coverUrl: String? = null,
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val status: String = "",
    val type: String = "",
    val year: String = "",
    val rating: String = "",
)

internal class PublicHtmlMangaSourceAdapter(
    private val config: PublicHtmlSourceConfig,
    private val client: OkHttpClient,
    private val webLinkImporter: MangaWebLinkImporter,
    private val renderedLoader: (suspend (String) -> String)? = null,
) : MangaSourceAdapter {
    override val descriptor = MangaSourceDescriptor(
        id = config.id,
        name = config.name,
        baseUrl = config.baseUrl,
        description = "Browsable public catalog with series metadata, chapters, and page downloads.",
        authorized = false,
        language = config.language,
    )

    override suspend fun search(query: String): List<MangaSearchResult> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        if (config.id == "rawkuma") return searchPage(clean, 0)
        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        val results = fetchCatalog(config.searchPaths.map { it.replace("%s", encoded) })
        return results.filter { it.title.contains(clean, ignoreCase = true) }.ifEmpty { results }
    }

    override suspend fun browse(mode: MangaBrowseMode): List<MangaSearchResult> = if (config.id == "rawkuma") browsePage(mode, 0) else fetchCatalog(
        when (mode) {
            MangaBrowseMode.Popular -> config.popularPaths
            MangaBrowseMode.Latest -> config.latestPaths
        },
    )

    override suspend fun browsePage(mode: MangaBrowseMode, page: Int): List<MangaSearchResult> {
        if (config.id != "rawkuma") return super.browsePage(mode, page)
        return rawkumaPage("browse:$mode", page, "manga/?order=${if (mode == MangaBrowseMode.Popular) "popular" else "update"}")
    }

    override suspend fun searchPage(query: String, page: Int): List<MangaSearchResult> {
        if (config.id != "rawkuma") return super.searchPage(query, page)
        return rawkumaPage("search:$query", page, "?s=${URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())}&post_type=wp-manga")
    }

    private val rawkumaNext = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun rawkumaPage(key: String, page: Int, firstPath: String): List<MangaSearchResult> {
        require(page >= 0)
        val url = if (page == 0) resolve(config.baseUrl, firstPath)!! else {
            rawkumaNext["$key:$page"] ?: return emptyList()
        }
        val html = fetch(url)
        val document = Jsoup.parse(html, url)
        val nextNumber = page + 2
        val next = document.select("a[href]").firstOrNull {
            val href = it.absUrl("href")
            URI(href).host == URI(config.baseUrl).host &&
                (URI(href).path.orEmpty().endsWith("/page/$nextNumber/") ||
                    Regex("[?&]paged=$nextNumber(?:&|$)").containsMatchIn(href))
        }?.absUrl("href")
        if (next != null) rawkumaNext["$key:${page + 1}"] = next
        else rawkumaNext.remove("$key:${page + 1}")
        return parseCatalog(url, html)
    }

    override suspend fun details(manga: MangaSearchResult): MangaSearchResult {
        var html = fetch(manga.canonicalUrl)
        if (Jsoup.parse(html).selectFirst("script#initial-data") != null) {
            parseCatalog(manga.canonicalUrl, html).firstOrNull { it.remoteId == manga.remoteId || it.title.equals(manga.title, true) }
                ?.let { return it.copy(remoteId = manga.remoteId) }
        }
        if (heading(html).isBlank() && renderedLoader != null) html = renderedLoader.invoke(manga.canonicalUrl)
        val metadata = extractMetadata(manga.canonicalUrl, html)
        return manga.copy(
            title = meta(html, "og:title").ifBlank { heading(html).ifBlank { manga.title } },
            description = metadata.description.ifBlank { manga.description },
            coverUrl = metadata.coverUrl ?: manga.coverUrl,
            tags = metadata.tags.ifEmpty { manga.tags },
            languages = metadata.languages.ifEmpty { manga.languages },
            authors = metadata.authors.ifEmpty { manga.authors },
            artists = metadata.artists.ifEmpty { manga.artists },
            status = metadata.status.ifBlank { manga.status },
            type = metadata.type.ifBlank { manga.type },
            year = metadata.year.ifBlank { manga.year },
            rating = metadata.rating.ifBlank { manga.rating },
        )
    }

    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> {
        var html = fetch(manga.canonicalUrl)
        if (extractAnchors(manga.canonicalUrl, html).none { isChapterUrl(it.first) } && renderedLoader != null) {
            html = renderedLoader.invoke(manga.canonicalUrl)
        }
        val links = extractAnchors(manga.canonicalUrl, html)
            .filter { (url, _) -> isChapterUrl(url) }
            .distinctBy { it.first.substringBefore('#').trimEnd('/') }
        return links.mapIndexed { index, (url, label) ->
            val number = CHAPTER_NUMBER.find("$label $url")?.groupValues?.getOrNull(2).orEmpty()
            MangaChapter(
                sourceId = descriptor.id,
                remoteId = stableId(url),
                mangaId = manga.remoteId,
                mangaTitle = manga.title,
                title = cleanText(label).ifBlank { if (number.isBlank()) "Chapter ${index + 1}" else "Chapter $number" },
                chapterNumber = number,
                language = config.language,
                canonicalUrl = url,
                readingOrder = config.readingOrder,
            )
        }.sortedWith(compareByDescending<MangaChapter> { it.chapterNumber.toDoubleOrNull() }.thenByDescending { it.title })
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> =
        webLinkImporter.inspect(chapter.canonicalUrl).pages.map { page ->
            MangaPage(
                sourceId = descriptor.id,
                chapterId = chapter.remoteId,
                pageIndex = page.index,
                remoteUrl = page.url,
                fileName = page.fileName,
            )
        }

    internal fun parseCatalog(baseUrl: String, html: String): List<MangaSearchResult> {
        if (config.id == "rawkuma") return parseRawkumaCatalog(baseUrl, html)
        val embedded = Jsoup.parse(html).selectFirst("script#initial-data")?.data()
        if (!embedded.isNullOrBlank()) {
            val root = Json.parseToJsonElement(embedded)
            val results = mutableListOf<MangaSearchResult>()
            fun visit(element: JsonElement) {
                when (element) {
                    is JsonArray -> element.forEach(::visit)
                    is JsonObject -> {
                        fun value(key: String) = (element[key] as? JsonPrimitive)?.content.orEmpty()
                        fun labels(key: String) = (element[key] as? JsonArray).orEmpty().mapNotNull {
                            ((it as? JsonObject)?.get("title") as? JsonPrimitive)?.content
                        }
                        val path = value("url")
                        val title = value("title")
                        if (path.startsWith("/title/") && title.isNotBlank()) {
                            val url = resolve(baseUrl, path) ?: return
                            val poster = element["poster"] as? JsonObject
                            results += MangaSearchResult(
                                sourceId = config.id, remoteId = stableId(url), title = title,
                                canonicalUrl = url, description = value("synopsis"),
                                coverUrl = (poster?.get("medium") as? JsonPrimitive)?.content,
                                languages = listOf(config.language, value("originalLanguage")).filter(String::isNotBlank).distinct(),
                                status = when (value("status")) { "releasing" -> "ongoing"; "on_hiatus" -> "hiatus"; "finished" -> "completed"; else -> value("status") },
                                type = value("type"), year = value("year"), rating = value("contentRating"), score = value("ratedAvg"),
                                tags = (labels("genres") + labels("demographics") + labels("formats") + labels("tags")).distinct(),
                                authors = labels("authors"), artists = labels("artists"),
                            )
                        } else element.values.forEach(::visit)
                    }
                    else -> Unit
                }
            }
            visit(root)
            if (results.isNotEmpty()) return results.distinctBy { it.remoteId }
        }
        val anchors = extractAnchors(baseUrl, html)
        return anchors.mapNotNull { (url, label) ->
            if (!isSeriesUrl(url)) return@mapNotNull null
            val occurrence = html.indexOf(url.substringAfter(URI(url).host), ignoreCase = true).coerceAtLeast(0)
            val nearby = html.substring(
                (occurrence - 350).coerceAtLeast(0),
                (occurrence + 2_800).coerceAtMost(html.length),
            )
            val metadata = extractMetadata(baseUrl, nearby)
            val title = cleanText(label).ifBlank { imageAlt(nearby) }.ifBlank {
                url.trimEnd('/').substringAfterLast('/').replace('-', ' ')
            }
            if (title.length < 2 || title.equals("read", true) || title.equals("start reading", true)) return@mapNotNull null
            MangaSearchResult(
                sourceId = descriptor.id,
                remoteId = stableId(url),
                title = title,
                description = metadata.description,
                coverUrl = metadata.coverUrl,
                canonicalUrl = url,
                tags = metadata.tags,
                languages = metadata.languages.ifEmpty { listOf(config.language) },
                authors = metadata.authors,
                artists = metadata.artists,
                status = metadata.status,
                type = metadata.type,
                year = metadata.year,
                rating = metadata.rating,
            )
        }.distinctBy { it.canonicalUrl.trimEnd('/').lowercase() }.take(40)
    }

    private fun parseRawkumaCatalog(baseUrl: String, html: String): List<MangaSearchResult> {
        val document = Jsoup.parse(html, baseUrl)
        // Image alt text on RawKuma can be a section label ("Last Updates").
        // Match the separate title link by URL, never by a surrounding section's text.
        val titleLinks = document.select("a[href]").groupBy { it.absUrl("href").substringBefore('#').trimEnd('/') }
        fun usableTitle(value: String) = value.trim().takeIf {
            it.isNotEmpty() && it.lowercase() !in setOf("last updates", "latest updates", "manga", "series page", "read more")
        }
        return document.select("a[href]").mapNotNull { anchor ->
            val url = anchor.absUrl("href").substringBefore('#')
            if (runCatching { URI(url).host == URI(config.baseUrl).host &&
                Regex("^/manga/[^/]+/?$").matches(URI(url).path.orEmpty()) }.getOrDefault(false).not()) return@mapNotNull null
            val img = anchor.selectFirst("img") ?: return@mapNotNull null
            val cover = imageUrl(baseUrl, img.outerHtml()) ?: return@mapNotNull null
            val title = titleLinks[url.trimEnd('/')].orEmpty().firstNotNullOfOrNull { usableTitle(it.text()) }
                ?: usableTitle(anchor.attr("title")) ?: usableTitle(img.attr("alt"))
                ?: java.net.URLDecoder.decode(URI(url).path.trimEnd('/').substringAfterLast('/'), "UTF-8").replace('-', ' ')
            if (title.isBlank()) return@mapNotNull null
            MangaSearchResult(sourceId = config.id, remoteId = stableId(url), title = title,
                canonicalUrl = url, coverUrl = cover, languages = listOf(config.language))
        }.distinctBy { it.remoteId }
    }

    private suspend fun fetchCatalog(paths: List<String>): List<MangaSearchResult> {
        var lastFailure: Throwable? = null
        var reachedPublicPage = false
        paths.distinct().forEach { path ->
            runCatching {
                val url = resolve(config.baseUrl, path) ?: error("Invalid catalog URL")
                val html = try { fetch(url) } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException || renderedLoader == null) throw failure
                    renderedLoader.invoke(url)
                }
                val static = parseCatalog(url, html)
                if (static.isNotEmpty() || renderedLoader == null) static else {
                    val rendered = renderedLoader.invoke(url)
                    val results = parseCatalog(url, rendered)
                    if (results.isEmpty() && (Jsoup.parse(rendered).text().contains("No results", true) ||
                        Jsoup.parse(rendered).text().contains("No titles found", true))) return emptyList()
                    results
                }
            }.onSuccess {
                reachedPublicPage = true
                if (it.isNotEmpty()) return it
            }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; lastFailure = it }
        }
        if (reachedPublicPage) error("${config.name} returned a page without a readable catalog. Its website format needs an updated adapter.")
        if (lastFailure != null) throw lastFailure as Throwable
        return emptyList()
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.7")
            .header("Accept-Language", "en-US,en;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("${descriptor.name} request failed (HTTP ${response.code}).")
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) error("${descriptor.name} returned an empty catalog.")
            // A login/register CAPTCHA script may be present on an otherwise public catalog.
            // Only classify an actual interstitial, not any page containing the word captcha.
            val document = Jsoup.parse(html)
            if (document.title().contains("just a moment", true) ||
                document.selectFirst("form#challenge-form") != null) {
                error("${descriptor.name} requires a browser verification challenge and cannot be browsed right now.")
            }
            html
        }
    }

    private fun extractAnchors(baseUrl: String, html: String): List<Pair<String, String>> = ANCHOR.findAll(html).mapNotNull { match ->
        val open = match.groupValues[1]
        val body = match.groupValues[2]
        val href = HREF.find(open)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
        val url = resolve(baseUrl, decodeHtml(href)) ?: return@mapNotNull null
        val title = ATTRIBUTE_TITLE.find(open)?.groupValues?.getOrNull(1).orEmpty()
            .ifBlank { imageAlt(body) }
            .ifBlank { cleanText(body) }
        url to title
    }.toList()

    private fun isSeriesUrl(url: String): Boolean {
        val lower = URI(url).path.orEmpty().lowercase()
        if (isChapterUrl(url)) return false
        return config.seriesPathHints.any(lower::contains) && BLOCKED_PATHS.none(lower::contains)
    }

    private fun isChapterUrl(url: String): Boolean {
        val lower = URI(url).path.orEmpty().lowercase()
        return lower.contains("chapter") || lower.contains("/chap-") ||
            (lower.contains("/read/") && CHAPTER_NUMBER.containsMatchIn(lower))
    }

    private fun extractMetadata(baseUrl: String, html: String): PublicHtmlMetadata {
        val jsonGenres = jsonLdValues(html, "genre") + jsonLdValues(html, "keywords")
        val tagLinks = extractAnchors(baseUrl, html).mapNotNull { (url, label) ->
            val path = URI(url).path.orEmpty().lowercase()
            label.takeIf { path.contains("/genre/") || path.contains("/genres/") ||
                path.contains("/tag/") || path.contains("/tags/") || path.contains("/theme/") ||
                path.contains("/demographic/") || path.contains("/format/") ||
                URI(url).query.orEmpty().contains("genre") }
        }
        val authorLinks = extractAnchors(baseUrl, html).mapNotNull { (url, label) ->
            label.takeIf { URI(url).path.orEmpty().lowercase().contains("/author") }
        }
        val artistLinks = extractAnchors(baseUrl, html).mapNotNull { (url, label) ->
            label.takeIf { URI(url).path.orEmpty().lowercase().contains("/artist") }
        }
        val languages = (jsonLdValues(html, "inLanguage") + htmlLanguage(html) +
            labeledValues(html, "language", "languages", "translated language"))
            .map(::cleanText).filter(String::isNotBlank).distinct()
        val tags = (tagLinks + jsonGenres + labeledValues(html, "genre", "genres", "tags", "themes", "demographic", "format", "type"))
            .map(::cleanText).filter(::isMetadataLabel).distinct()
        val authors = (authorLinks + jsonLdValues(html, "author"))
            .map(::cleanText).filter(::isMetadataLabel).distinct()
        val artists = (artistLinks + jsonLdValues(html, "artist"))
            .map(::cleanText).filter(::isMetadataLabel).distinct()
        val description = meta(html, "og:description").ifBlank { meta(html, "description") }
        val cover = meta(html, "og:image").takeIf(String::isNotBlank)
            ?.let { resolve(baseUrl, it) }
            ?: imageUrl(baseUrl, html)
        return PublicHtmlMetadata(
            description = description,
            coverUrl = cover,
            tags = tags,
            languages = languages,
            authors = authors,
            artists = artists,
            status = labeledValues(html, "status").firstOrNull { isMetadataLabel(it) }.orEmpty(),
            type = labeledValues(html, "type", "format").firstOrNull { isMetadataLabel(it) }.orEmpty(),
            year = jsonLdValues(html, "datePublished").firstOrNull()?.take(4).orEmpty(),
            rating = jsonLdValues(html, "ratingValue").firstOrNull().orEmpty(),
        )
    }

    private fun imageUrl(baseUrl: String, html: String): String? {
        return IMAGE_TAG.findAll(html).flatMap { tag ->
            val body = tag.value
            val alt = IMAGE_ALT_ATTRIBUTE.find(body)?.groupValues?.getOrNull(1).orEmpty()
            val candidates = IMAGE_ATTRIBUTE.findAll(body).flatMap { attribute ->
                attribute.groupValues[1].split(',').asSequence().map { it.trim().substringBefore(' ') }
            }
            candidates.mapNotNull { raw ->
                val url = resolve(baseUrl, decodeHtml(raw)) ?: return@mapNotNull null
                val lower = "$url $alt".lowercase()
                if (COVER_IMAGE_REJECT.any(lower::contains)) return@mapNotNull null
                val score = (if (lower.contains("cover") || lower.contains("poster")) 8 else 0) +
                    (if (lower.contains("wp-content/uploads") || lower.contains("cdn")) 5 else 0) +
                    (if (lower.endsWith(".jpg") || lower.contains(".webp") || lower.contains(".avif")) 3 else 0) +
                    (if (alt.isNotBlank()) 2 else 0) +
                    (if (!THUMBNAIL_SUFFIX.containsMatchIn(lower)) 4 else 0)
                score to url
            }
        }.maxByOrNull { it.first }?.second
    }

    private fun imageAlt(html: String): String = IMAGE_ALT.find(html)?.groupValues?.getOrNull(1).orEmpty().let(::cleanText)

    private fun htmlLanguage(html: String): String = HTML_LANGUAGE.find(html)?.groupValues?.getOrNull(1).orEmpty()

    private fun jsonLdValues(html: String, key: String): List<String> {
        val keyPattern = Regex.escape(key)
        val field = Regex(
            "\"$keyPattern\"\\s*:\\s*(\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]|\"(?:\\\\.|[^\"\\\\])*\")",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).flatMap { match ->
            val raw = match.groupValues[1]
            if (raw.startsWith("[")) {
                JSON_STRING.findAll(raw).map { it.groupValues[1] }
            } else sequenceOf(raw.trim('"'))
        }.map { decodeHtml(it).replace("\\/", "/") }.toList()
        return field
    }

    private fun labeledValues(html: String, vararg labels: String): List<String> {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        val visibleText = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ")
        val valuePattern = Regex(
            "(?i)\\b(?:$labelPattern)\\b\\s*[:\\-]?\\s*([^\\r\\n]{0,120})",
        )
        return valuePattern.findAll(visibleText).map { it.groupValues[1] }.map(::cleanText).flatMap { value ->
            value.split(',', '|', '·').asSequence().map(::cleanText)
        }.filter(::isMetadataLabel).distinct().toList()
    }

    private fun isMetadataLabel(value: String): Boolean {
        val clean = cleanText(value)
        return clean.length in 2..80 && clean.lowercase() !in setOf(
            "read", "start reading", "manga", "manhwa", "manhua", "comic", "series", "genre", "genres", "tag", "tags",
        )
    }

    private fun heading(html: String): String = HEADING.find(html)?.groupValues?.getOrNull(1).orEmpty().let(::cleanText)

    private fun meta(html: String, property: String): String {
        val escaped = Regex.escape(property)
        val forward = Regex("<meta[^>]+(?:property|name)=[\"']$escaped[\"'][^>]+content=[\"']([^\"']*)", RegexOption.IGNORE_CASE)
        val reverse = Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+(?:property|name)=[\"']$escaped[\"']", RegexOption.IGNORE_CASE)
        return decodeHtml(forward.find(html)?.groupValues?.getOrNull(1) ?: reverse.find(html)?.groupValues?.getOrNull(1).orEmpty())
    }

    private fun resolve(baseUrl: String, raw: String): String? = runCatching {
        val clean = raw.trim().replace("\\/", "/")
        if (clean.isBlank() || clean.startsWith("javascript:") || clean.startsWith("data:")) return null
        URI(baseUrl).resolve(clean).toString().takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }.getOrNull()

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(28)

    private fun cleanText(value: String): String = decodeHtml(value.replace(Regex("<[^>]+>"), " "))
        .replace(Regex("\\s+"), " ").trim()

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&", true)
        .replace("&quot;", "\"", true)
        .replace("&#39;", "'", true)
        .replace("&lt;", "<", true)
        .replace("&gt;", ">", true)

    private companion object {
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36 Weaverse/1.0"
        val ANCHOR = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val HREF = Regex("href\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        val ATTRIBUTE_TITLE = Regex("title\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        val IMAGE_TAG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
        val IMAGE_ATTRIBUTE = Regex("(?:data-src|data-lazy-src|data-original|srcset|src)\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        val IMAGE_ALT_ATTRIBUTE = Regex("alt\\s*=\\s*[\"']([^\"']*)", RegexOption.IGNORE_CASE)
        val IMAGE_ALT = Regex("<img[^>]+alt\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        val HTML_LANGUAGE = Regex("<html[^>]+lang\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
        val JSON_STRING = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
        val HEADING = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val CHAPTER_NUMBER = Regex("(chapter|chap|ch)[-_. /]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val BLOCKED_PATHS = listOf("/chapter", "/read/", "/genre/", "/author/", "/tag/", "/login", "/bookmark")
        val COVER_IMAGE_REJECT = listOf("logo", "favicon", "avatar", "profile", "banner", "icon", "loading", "placeholder")
        val THUMBNAIL_SUFFIX = Regex("-(?:96|128|160|211x300|300x\\d+)(?:\\.|-)", RegexOption.IGNORE_CASE)
    }
}
