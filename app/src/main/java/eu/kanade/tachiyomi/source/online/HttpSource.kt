package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.security.MessageDigest

abstract class HttpSource : CatalogueSource {
    protected val network: NetworkHelper by injectLazy()
    abstract val baseUrl: String
    open fun getHomeUrl(): String = baseUrl
    open val versionId: Int = 1
    override val id: Long by lazy { generateId(name, lang, versionId) }
    val headers: Headers by lazy { headersBuilder().build() }
    open val client: OkHttpClient get() = network.client
    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().add("User-Agent", network.defaultUserAgentProvider())
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val bytes = MessageDigest.getInstance("MD5").digest("${name.lowercase()}/$lang/$versionId".toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }
    override fun toString() = "$name (${lang.uppercase()})"

    @Deprecated("Use suspend API") override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess().map(::popularMangaParse)
    protected open fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Deprecated("Use suspend API") override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess().map(::searchMangaParse)
    protected open fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    protected open fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Deprecated("Use suspend API") override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess().map(::latestUpdatesParse)
    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    protected open fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Deprecated("Use combined API") override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map { response -> mangaDetailsParse(response).apply { initialized = true } }
    open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    protected open fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    @Deprecated("Use combined API") override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess().map(::chapterListParse)
    protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)
    protected open fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    @Deprecated("Use suspend API") override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess().map(::pageListParse)
    protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)
    protected open fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @Deprecated("Use getImageUrl") open fun fetchImageUrl(page: Page): Observable<String> =
        client.newCall(imageUrlRequest(page)).asObservableSuccess().map(::imageUrlParse)
    @Suppress("DEPRECATION") open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingle()
    protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)
    protected open fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    suspend fun getImage(page: Page, existingSize: Long = 0L): Response =
        client.newCachelessCallWithProgress(imageRequest(page), page, existingSize).awaitSuccess()
    protected open fun imageRequest(page: Page): Request = GET(requireNotNull(page.imageUrl), headers)

    fun SChapter.setUrlWithoutDomain(url: String) { this.url = getUrlWithoutDomain(url) }
    fun SManga.setUrlWithoutDomain(url: String) { this.url = getUrlWithoutDomain(url) }
    private fun getUrlWithoutDomain(original: String): String = runCatching {
        val uri = URI(original.replace(" ", "%20")); buildString { append(uri.path); uri.query?.let { append("?$it") }; uri.fragment?.let { append("#$it") } }
    }.getOrDefault(original)
    @Suppress("DEPRECATION") open fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()
    @Suppress("DEPRECATION") open fun getChapterUrl(chapter: SChapter): String = pageListRequest(chapter).url.toString()
    @Deprecated("Modify chapter while constructing it") open fun prepareNewChapter(chapter: SChapter, manga: SManga) = Unit
}

@Deprecated("Prefer a purpose-built source")
abstract class ParsedHttpSource : HttpSource() {
    override fun popularMangaParse(response: Response): MangasPage = response.asJsoup().let { doc ->
        MangasPage(doc.select(popularMangaSelector()).map(::popularMangaFromElement), popularMangaNextPageSelector()?.let { doc.select(it).first() } != null)
    }
    protected abstract fun popularMangaSelector(): String
    protected abstract fun popularMangaFromElement(element: Element): SManga
    protected abstract fun popularMangaNextPageSelector(): String?
    override fun searchMangaParse(response: Response): MangasPage = response.asJsoup().let { doc ->
        MangasPage(doc.select(searchMangaSelector()).map(::searchMangaFromElement), searchMangaNextPageSelector()?.let { doc.select(it).first() } != null)
    }
    protected abstract fun searchMangaSelector(): String
    protected abstract fun searchMangaFromElement(element: Element): SManga
    protected abstract fun searchMangaNextPageSelector(): String?
    override fun latestUpdatesParse(response: Response): MangasPage = response.asJsoup().let { doc ->
        MangasPage(doc.select(latestUpdatesSelector()).map(::latestUpdatesFromElement), latestUpdatesNextPageSelector()?.let { doc.select(it).first() } != null)
    }
    protected abstract fun latestUpdatesSelector(): String
    protected abstract fun latestUpdatesFromElement(element: Element): SManga
    protected abstract fun latestUpdatesNextPageSelector(): String?
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())
    protected abstract fun mangaDetailsParse(document: Document): SManga
    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(chapterListSelector()).map(::chapterFromElement)
    protected abstract fun chapterListSelector(): String
    protected abstract fun chapterFromElement(element: Element): SChapter
    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())
    protected abstract fun pageListParse(document: Document): List<Page>
    override fun imageUrlParse(response: Response): String = imageUrlParse(response.asJsoup())
    protected abstract fun imageUrlParse(document: Document): String
}

interface ResolvableSource : eu.kanade.tachiyomi.source.Source {
    fun getUriType(uri: String): UriType
    suspend fun getManga(uri: String): SManga?
    suspend fun getChapter(uri: String): SChapter?
}
sealed interface UriType { data object Manga:UriType; data object Chapter:UriType; data object Unknown:UriType }
