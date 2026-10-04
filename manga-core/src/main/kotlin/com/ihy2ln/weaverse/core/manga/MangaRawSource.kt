package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * MangaRaw (mangaraw.ac): Japanese raws. Catalog, search and series pages are plain HTML; a chapter page only
 * carries `window.MangaId` / `window.CNumber`, and its reader asks `POST /api/v1/get/c {"m":id,"n":chapter}` for
 * the pages. That answer hex-encodes its strings XOR 0x2C: `d` is the image host, `e` the page paths, and when `b` is
 * true each picture is cut into a 4x4 grid shuffled in the order `c`. Shuffled pages keep that order in the URL
 * fragment ([SCRAMBLE_FRAGMENT], never sent to the server) so the app can put the tiles back after downloading.
 */
class MangaRawSource(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://mangaraw.ac/",
) : MangaSourceAdapter {
    override val descriptor = MangaSourceDescriptor(
        id = ID,
        name = "MangaRaw",
        baseUrl = baseUrl,
        description = "Japanese raw manga: catalog, chapters and page downloads.",
        authorized = false,
        language = "ja",
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String) = searchPage(query, 0)
    override suspend fun browse(mode: MangaBrowseMode) = browsePage(mode, 0)

    override suspend fun browsePage(mode: MangaBrowseMode, page: Int): List<MangaSearchResult> {
        // Popular is the ranking (one page); Latest is the front page's update list, paged as /page/N.
        val path = if (mode == MangaBrowseMode.Popular) { if (page > 0) return emptyList(); "ranking/" }
            else if (page == 0) "" else "page/${page + 1}"
        return parseCatalog(get(resolve(path)))
    }

    override suspend fun searchPage(query: String, page: Int): List<MangaSearchResult> {
        if (query.isBlank() || page > 0) return emptyList()
        val all = parseCatalog(get(resolve("?s=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name()))))
        // The site answers with its whole list when nothing matches, so keep only real matches.
        val needle = query.trim().lowercase()
        return all.filter { it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle) }
    }

    override suspend fun details(manga: MangaSearchResult): MangaSearchResult =
        parseDetails(manga, get(seriesUrl(manga)))

    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> =
        parseChapters(manga, get(seriesUrl(manga)))

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val chapterUrl = ascii(chapter.canonicalUrl.ifBlank { resolve(chapter.remoteId) })
        val html = get(chapterUrl)
        val mangaId = Regex("""window\.MangaId\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)
            ?: error("MangaRaw did not say which manga this chapter belongs to.")
        val number = Regex("""window\.CNumber\s*=\s*([\d.]+)""").find(html)?.groupValues?.get(1)
            ?: error("MangaRaw did not give this chapter's number.")
        val body = """{"m":$mangaId,"n":$number}""".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(resolve("api/v1/get/c")).post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", baseUrl.trimEnd('/'))
            .header("Referer", chapterUrl)
            .build()
        val answer = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MangaRaw's page list returned HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        parsePages(chapter, answer)
    }

    // ---- parsing (public for tests) ------------------------------------------------------------------------------

    fun parseCatalog(html: String): List<MangaSearchResult> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("a.pa-image[href*=/manga/]").mapNotNull { a ->
            val href = ascii(a.absUrl("href").ifBlank { return@mapNotNull null })
            val path = pathOf(href) ?: return@mapNotNull null
            val img = a.selectFirst("img")
            val cover = img?.attr("data-src")?.takeIf { it.isNotBlank() }?.let { resolve(it) }
                ?: img?.absUrl("src")?.takeIf { it.startsWith("http") }
            MangaSearchResult(
                sourceId = ID, remoteId = path, title = cleanTitle(a.attr("title").ifBlank { img?.attr("alt").orEmpty() }),
                coverUrl = cover, canonicalUrl = href, languages = listOf("ja"),
            )
        }.distinctBy { it.remoteId }
    }

    fun parseDetails(manga: MangaSearchResult, html: String): MangaSearchResult {
        val doc = Jsoup.parse(html, baseUrl)
        fun meta(property: String) = doc.selectFirst("meta[property=$property]")?.attr("content")?.trim().orEmpty()
        val title = doc.selectFirst("h1")?.text()?.let(::cleanTitle)?.ifBlank { null } ?: manga.title
        val genres = rowLinks(doc, "ジャンル")
        val tags = (genres + rowLinks(doc, "タグ")).distinct()
        return manga.copy(
            title = title,
            description = meta("og:description").ifBlank { manga.description },
            coverUrl = meta("og:image").ifBlank { null } ?: manga.coverUrl,
            tags = tags.ifEmpty { manga.tags },
            languages = listOf("ja"),
            type = "Manga",
        )
    }

    fun parseChapters(manga: MangaSearchResult, html: String): List<MangaChapter> {
        val doc = Jsoup.parse(html, baseUrl)
        val series = pathOf(manga.canonicalUrl.ifBlank { resolve(manga.remoteId) }) ?: manga.remoteId
        val title = doc.selectFirst("h1")?.text()?.let(::cleanTitle)?.ifBlank { null } ?: manga.title
        return doc.select("a[href]").mapNotNull { a ->
            val href = ascii(a.absUrl("href"))
            val path = pathOf(href) ?: return@mapNotNull null
            if (!path.startsWith(series) || path == series) return@mapNotNull null
            val label = a.text().trim().ifBlank { URLDecoder.decode(path.removePrefix(series).trim('/'), StandardCharsets.UTF_8.name()) }
            val number = Regex("""([\d.]+)""").find(label)?.groupValues?.get(1).orEmpty()
            val date = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""").find(a.parent()?.text().orEmpty())?.let { m ->
                runCatching {
                    java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrNull()
            } ?: 0L
            MangaChapter(
                sourceId = ID, remoteId = path, mangaId = manga.remoteId, mangaTitle = title, title = label,
                chapterNumber = number, language = "ja", canonicalUrl = href, readingOrder = "rtl", dateUpload = date,
            )
        }.distinctBy { it.remoteId }
    }

    fun parsePages(chapter: MangaChapter, answer: String): List<MangaPage> {
        val root = json.parseToJsonElement(answer).jsonObject
        val host = decode(root["d"]?.jsonPrimitive?.contentOrNull.orEmpty()).trimEnd('/')
        require(host.startsWith("http")) { "MangaRaw's page list had no image host." }
        val scrambled = root["b"]?.jsonPrimitive?.let { it.booleanOrNull ?: (it.contentOrNull == "true") } == true
        val order = if (scrambled) decode(root["c"]?.jsonPrimitive?.contentOrNull.orEmpty()) else ""
        val paths = root["e"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        return paths.mapIndexed { i, p ->
            val url = if (p.startsWith("http")) p else host + "/" + p.trimStart('/')
            MangaPage(
                sourceId = ID, chapterId = chapter.remoteId, pageIndex = i,
                remoteUrl = if (order.isNotBlank()) "$url#$SCRAMBLE_FRAGMENT$order" else url,
                fileName = "%03d.jpg".format(i + 1),
            )
        }
    }

    private fun rowLinks(doc: Document, label: String): List<String> =
        doc.select("table.post-table tr").firstOrNull { it.selectFirst("td")?.text()?.contains(label) == true }
            ?.select("a")?.map { it.text().trim() }?.filter { it.isNotBlank() }.orEmpty()

    private fun seriesUrl(manga: MangaSearchResult) = ascii(manga.canonicalUrl.ifBlank { resolve(manga.remoteId) })

    /** The site links chapters with raw Japanese in the path; HTTP headers (Referer) and OkHttp want plain ASCII. */
    private fun ascii(url: String): String = runCatching {
        val uri = URI(url.replace(" ", "%20"))
        URI(uri.scheme, uri.authority, uri.path, uri.query, uri.fragment).toASCIIString()
    }.getOrDefault(url)

    private fun cleanTitle(raw: String) = raw.replace(Regex("""\s*Raw\s*Free\s*$""", RegexOption.IGNORE_CASE), "").trim()

    /** The site path of a mangaraw.ac link, percent-decoded so the same page always has the same id. */
    private fun pathOf(url: String): String? = runCatching {
        val uri = URI(url.replace(" ", "%20"))
        if (uri.host != null && !uri.host.endsWith("mangaraw.ac")) return null
        URLDecoder.decode(uri.rawPath ?: return null, StandardCharsets.UTF_8.name()).let { if (it.endsWith("/")) it else "$it/" }
    }.getOrNull()?.takeIf { it.startsWith("/manga/") }

    private fun resolve(path: String): String = when {
        path.startsWith("http") -> path
        path.startsWith("/manga/") -> baseUrl.trimEnd('/') + path.split('/').joinToString("/") { seg ->
            if (seg.any { it.code > 127 || it == ' ' }) URLEncoder.encode(seg, StandardCharsets.UTF_8.name()).replace("+", "%20") else seg
        }
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(resolve(url)).header("User-Agent", USER_AGENT).header("Referer", baseUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MangaRaw returned HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    companion object {
        const val ID = "mangaraw"
        /** URL fragment carrying a shuffled page's 4x4 tile order, e.g. `#ahscramble=14,4,7,…`. */
        const val SCRAMBLE_FRAGMENT = "ahscramble="
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"

        /** The site's string encoding: hex bytes XOR 0x2C. */
        fun decode(hex: String): String {
            if (hex.length % 2 != 0 || hex.any { !it.isLetterOrDigit() }) return hex
            return hex.chunked(2).mapNotNull { it.toIntOrNull(16) }.map { (it xor 0x2C).toChar() }.joinToString("")
        }

        /** The tile order in a shuffled page's URL, or null for an ordinary page. */
        fun scrambleOrder(url: String): List<Int>? {
            val fragment = url.substringAfter('#', "").takeIf { it.startsWith(SCRAMBLE_FRAGMENT) } ?: return null
            val order = fragment.removePrefix(SCRAMBLE_FRAGMENT).split(',').mapNotNull { it.trim().toIntOrNull() }
            return order.takeIf { it.size == 16 && it.sorted() == (0 until 16).toList() }
        }
    }
}
