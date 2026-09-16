package com.ihy2ln.weaverse.core.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.coroutines.coroutineContext

/** Integrated source. No extension APK or third-party package identity is impersonated. */
class GallerySource(private val account: GalleryAccountManager, http: OkHttpClient, private val restricted: Boolean) : MangaSourceAdapter {
    private val base = if (restricted) "https://exhentai.org" else "https://e-hentai.org"
    override val descriptor = MangaSourceDescriptor(
        id = if (restricted) "exhentai" else "ehentai", name = if (restricted) "ExHentai" else "E-Hentai",
        baseUrl = base, description = "18+ · Built-in gallery source · Account settings in Extensions", authorized = true,
        language = "all", supportsNativeFilters = true,
    )
    private val client = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val pagingLock = Mutex()
    private var pagingKey = ""
    private val cursors = mutableMapOf<Int, String?>()
    private fun requireEnabled() {
        check(account.state.value.enabled) { "Enable the 18+ sources in Extensions → E-Hentai / ExHentai first." }
        check(!restricted || (account.state.value.restricted && account.state.value.sessionSaved)) {
            "ExHentai requires website login and Enable ExHentai in account settings."
        }
    }
    private suspend fun document(url: String): Document = withContext(Dispatchers.IO) {
        requireEnabled()
        var target = url
        repeat(5) {
            coroutineContext.ensureActive()
            check(GallerySessionPolicy.allowed(target) && target.toHttpUrl().host == base.toHttpUrl().host) { "The website redirected outside this source. Open account settings to sign in." }
            val request = Request.Builder().url(target).header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html").apply { account.cookieHeader(target)?.let { header("Cookie", it) } }.build()
            client.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    target = response.header("Location")?.let { target.toHttpUrl().resolve(it)?.toString() }
                        ?: error("The website returned an invalid redirect.")
                } else {
                    check(response.isSuccessful) { "${descriptor.name} returned HTTP ${response.code}. Check account access or retry later." }
                    check(response.header("Content-Type").orEmpty().contains("text/html", true)) { "${descriptor.name} denied access. Sign in or check your account's access on the website." }
                    val body = response.body?.string().orEmpty()
                    val doc = Jsoup.parse(body, target)
                    check(doc.selectFirst(".itg, #gdt, #img, #gd1, #searchbox, #searchnav, #f_search") != null || body.contains("No hits found", true)) {
                        "${descriptor.name} did not return a gallery page. Login, access restrictions or a website challenge may need attention in account settings."
                    }
                    return@withContext doc
                }
            }
        }
        error("Too many website redirects. Open account settings.")
    }
    override fun nativeFilters() = FilterList(listOf(
        WebsiteSelect("listing", "Listing", listOf(WebsiteOption("", "Latest"), WebsiteOption("popular", "Popular"))),
        Filter.Header("Use the website's namespace:value syntax in search. Prefix a term with - to exclude it."),
        WebsiteSelect("f_srdd", "Minimum rating", listOf(WebsiteOption("", "Any")) + (2..5).map { WebsiteOption(it.toString(), "$it stars") }),
        WebsiteText("f_spf", "Minimum pages"), WebsiteText("f_spt", "Maximum pages"),
    ))
    override suspend fun search(query: String) = searchPage(query, 0, nativeFilters())
    override suspend fun searchPage(query: String, page: Int) = searchPage(query, page, nativeFilters())
    override suspend fun browse(mode: MangaBrowseMode) = browsePage(mode, 0)
    override suspend fun browsePage(mode: MangaBrowseMode, page: Int) = searchPage("", page, nativeFilters().apply {
        filterIsInstance<WebsiteSelect>().first().state = if (mode == MangaBrowseMode.Popular) 1 else 0
    })
    override suspend fun searchPage(query: String, page: Int, filters: FilterList): List<MangaSearchResult> = pagingLock.withLock {
        require(page >= 0)
        val start = GalleryHtml.searchUrl(base, query, filters)
        if (page == 0 || pagingKey != start) { pagingKey = start; cursors.clear(); cursors[0] = start }
        check(cursors.containsKey(page)) { "Reload the source before requesting this page." }
        val url = cursors[page] ?: return@withLock emptyList()
        val doc = document(url)
        cursors[page + 1] = GalleryHtml.nextCatalog(doc)?.takeUnless { it == url }
        GalleryHtml.catalog(doc, descriptor.id)
    }
    override suspend fun details(manga: MangaSearchResult): MangaSearchResult = GalleryHtml.details(document(manga.canonicalUrl), manga)
    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> {
        val detail = details(manga)
        return listOf(MangaChapter(descriptor.id, detail.remoteId, detail.remoteId, detail.title, "Gallery", canonicalUrl = detail.canonicalUrl,
            language = detail.languages.firstOrNull().orEmpty(), readingOrder = "rtl"))
    }
    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val links = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var url: String? = chapter.canonicalUrl
        while (url != null) {
            coroutineContext.ensureActive()
            check(visited.add(url)) { "The website repeated a thumbnail page. No partial chapter was saved." }
            check(visited.size <= 500) { "Gallery is too large for one download. No partial manifest was saved." }
            val doc = document(url)
            links += doc.select("#gdt a[href], .gdtm a[href]").map { it.absUrl("href") }.filter { it.toHttpUrlOrNull()?.encodedPath?.startsWith("/s/") == true }
            url = GalleryHtml.nextThumbnails(doc)
        }
        check(links.isNotEmpty()) { "No image pages found. Check website access or retry." }
        return links.mapIndexed { index, link ->
            if (index > 0) delay(250)
            val doc = document(link)
            val image = doc.selectFirst("img#img")?.absUrl("src").orEmpty()
            check(image.toHttpUrlOrNull()?.scheme == "https") { "Page ${index + 1} is unavailable. Check image limits or retry later." }
            check(image.toHttpUrl().encodedPath.substringAfterLast('/') !in setOf("509.gif", "509s.gif")) { "Website image limit reached. Wait before retrying; no partial manifest was saved." }
            MangaPage(descriptor.id, chapter.remoteId, index, image, "page-${index + 1}.jpg")
        }
    }
}

internal object GalleryHtml {
    fun searchUrl(base: String, query: String, filters: FilterList): String {
        val listing = filters.filterIsInstance<WebsiteSelect>().firstOrNull { it.parameter == "listing" }?.state ?: 0
        val url = "$base/${if (listing == 1) "popular" else ""}".toHttpUrl().newBuilder()
        if (query.isNotBlank()) url.addQueryParameter("f_search", query.trim())
        filters.filterIsInstance<WebsiteSelect>().firstOrNull { it.parameter == "f_srdd" }?.let {
            val value = it.options.getOrNull(it.state)?.value.orEmpty()
            if (value.isNotBlank()) { url.addQueryParameter("f_sr", "on"); url.addQueryParameter("f_srdd", value) }
        }
        val ranges = filters.filterIsInstance<WebsiteText>().associate { it.parameter to it.state.trim() }
        ranges.filterValues { it.isNotBlank() }.forEach { (key, value) ->
            require(value.toIntOrNull()?.let { it >= 0 } == true) { "Page limits must be non-negative whole numbers." }
            url.addQueryParameter(key, value)
        }
        if (ranges.values.any { it.isNotBlank() }) url.addQueryParameter("f_sp", "on")
        val min = ranges["f_spf"]?.toIntOrNull(); val max = ranges["f_spt"]?.toIntOrNull()
        require(min == null || max == null || min <= max) { "Minimum pages cannot exceed maximum pages." }
        return url.build().toString()
    }
    fun catalog(doc: Document, source: String): List<MangaSearchResult> = doc.select(".itg tr, .itg > .gl1t").mapNotNull { row ->
        val title = row.selectFirst(".glink") ?: return@mapNotNull null
        val anchor = title.closest("a[href]") ?: row.selectFirst("a[href*=/g/]") ?: return@mapNotNull null
        val url = anchor.absUrl("href")
        if (!GallerySessionPolicy.allowed(url)) return@mapNotNull null
        val path = url.toHttpUrl().encodedPath
        if (!Regex("/g/\\d+/[a-zA-Z0-9]+/?").matches(path)) return@mapNotNull null
        val image = row.selectFirst(".glthumb img, .gl1e img, .gl3t img, img")
        val cover = image?.absUrl("data-src")?.ifBlank { image.absUrl("src") }
        MangaSearchResult(source, path, title.text(), coverUrl = cover?.takeIf { it.startsWith("https://") }, canonicalUrl = url,
            tags = row.select("[title^=language:], [title^=artist:], .gt, .gtl").map { it.attr("title").ifBlank { it.text() } }.distinct(),
            rating = "Adult")
    }.distinctBy { it.remoteId }
    fun nextCatalog(doc: Document): String? = doc.selectFirst("a#unext[href], a#dnext[href]")?.absUrl("href")?.takeIf { GallerySessionPolicy.allowed(it) }
    fun nextThumbnails(doc: Document): String? = doc.select(".ptt a[href]").lastOrNull { it.text().trim() in setOf(">", "›", "Next") }
        ?.absUrl("href")?.takeIf { GallerySessionPolicy.allowed(it) }
    fun details(doc: Document, original: MangaSearchResult): MangaSearchResult {
        val title = doc.selectFirst("#gn")?.text()?.takeIf { it.isNotBlank() } ?: error("Gallery title was missing. Nothing was saved.")
        val tags = doc.select("#taglist tr").flatMap { row ->
            val namespace = row.selectFirst("td")?.text()?.trim()?.removeSuffix(":").orEmpty()
            row.select("a[id^=ta_]").map { "$namespace:${it.text()}" }
        }.distinct()
        val style = doc.selectFirst("#gd1 div")?.attr("style").orEmpty()
        val cover = Regex("url\\(['\"]?([^)'\"]+)").find(style)?.groupValues?.get(1)
        return original.copy(title = title, description = doc.selectFirst("#gj")?.text().orEmpty(),
            coverUrl = cover?.takeIf { it.startsWith("https://") } ?: original.coverUrl, tags = tags,
            languages = tags.filter { it.startsWith("language:") }.map { it.substringAfter(':') },
            artists = tags.filter { it.startsWith("artist:") }.map { it.substringAfter(':') },
            type = doc.selectFirst("#gdc")?.text().orEmpty(), rating = "Adult",
            score = Regex("[0-5](?:\\.\\d+)?").find(doc.selectFirst("#rating_label")?.text().orEmpty())?.value.orEmpty())
    }
}
