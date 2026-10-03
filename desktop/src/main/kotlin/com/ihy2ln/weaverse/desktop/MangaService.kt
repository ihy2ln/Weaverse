package com.ihy2ln.weaverse.desktop

import com.ihy2ln.weaverse.core.manga.MangaBrowseMode
import com.ihy2ln.weaverse.core.manga.MangaChapter
import com.ihy2ln.weaverse.core.manga.MangaDexSource
import com.ihy2ln.weaverse.core.manga.MangaPage
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.core.manga.MangaSourceAdapter
import com.ihy2ln.weaverse.core.manga.MangaSourceDescriptor
import com.ihy2ln.weaverse.core.manga.MangaWebLinkImporter
import com.ihy2ln.weaverse.core.manga.PublicHtmlMangaSources
import com.ihy2ln.weaverse.core.manga.RenderedCatalog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class LibraryCategory(val id: String, val name: String)

@Serializable
data class LibraryEntry(
    val manga: MangaSearchResult,
    val categoryIds: List<String> = emptyList(),
    val addedAt: Long = System.currentTimeMillis(),
    /** Chapters last seen for this title, newest first; Updates compares against these. */
    val chapters: List<MangaChapter> = emptyList(),
    val checkedAt: Long = 0L,
)

@Serializable
data class HistoryEntry(
    val manga: MangaSearchResult,
    val chapter: MangaChapter,
    val page: Int = 0,
    val pageCount: Int = 0,
    val readAt: Long = System.currentTimeMillis(),
)

@Serializable
data class MangaLibrary(
    val categories: List<LibraryCategory> = listOf(LibraryCategory("favorites", "Favorites")),
    val entries: List<LibraryEntry> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    /** Chapter ids opened at least once, so lists can mark them read. */
    val readChapterIds: Set<String> = emptySet(),
)

@Serializable
data class UpdateItem(val manga: MangaSearchResult, val chapter: MangaChapter)

/**
 * Manga Studio for the web version: the APK's own sources (shared `manga-core`), configured like
 * the app, plus a library, history and update check kept in data/manga on this PC.
 */
class MangaService(private val dataDir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val http = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    }

    /** No WebView on the PC: script-built listings fall back to the page's plain HTML. */
    private val plainRendered = object : RenderedCatalog {
        override suspend fun load(url: String): String = withContext(Dispatchers.IO) {
            okHttp.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { it.body?.string().orEmpty() }
        }
    }

    val sources: List<MangaSourceAdapter> = listOf(MangaDexSource(http)) +
        PublicHtmlMangaSources(okHttp, MangaWebLinkImporter(okHttp), plainRendered).sources

    fun descriptors(): List<MangaSourceDescriptor> = sources.map { it.descriptor }

    private fun source(id: String): MangaSourceAdapter = sources.firstOrNull { it.descriptor.id == id }
        ?: error("Unknown source $id")

    suspend fun catalog(sourceId: String, mode: MangaBrowseMode, page: Int): List<MangaSearchResult> =
        withContext(Dispatchers.IO) { source(sourceId).browsePage(mode, page) }

    suspend fun search(sourceId: String, query: String, page: Int): List<MangaSearchResult> =
        withContext(Dispatchers.IO) { source(sourceId).searchPage(query, page) }

    suspend fun details(manga: MangaSearchResult): MangaSearchResult =
        withContext(Dispatchers.IO) { source(manga.sourceId).details(manga) }

    suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> =
        withContext(Dispatchers.IO) { source(manga.sourceId).chapters(manga) }.also { fresh ->
            // A title in the library keeps its latest chapter list for Updates.
            update { lib ->
                lib.copy(entries = lib.entries.map {
                    if (it.manga.sourceId == manga.sourceId && it.manga.remoteId == manga.remoteId) it.copy(chapters = fresh, checkedAt = System.currentTimeMillis()) else it
                })
            }
        }

    suspend fun pages(chapter: MangaChapter): List<MangaPage> =
        withContext(Dispatchers.IO) { source(chapter.sourceId).pages(chapter) }

    /** Cover and page pictures, fetched with the source as referer and cached on disk. */
    suspend fun image(url: String, referer: String): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val cache = File(dataDir, "manga/cache").apply { mkdirs() }
        val key = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(cache, key)
        if (file.isFile && file.length() > 0) return@withContext file.readBytes() to sniff(file.readBytes())
        val request = Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.5")
            .apply { if (referer.startsWith("http")) header("Referer", referer) }
            .build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: ByteArray(0)
            file.writeBytes(bytes)
            bytes to (response.header("Content-Type")?.takeIf { it.startsWith("image/") } ?: sniff(bytes))
        }
    }

    // ---------------------------------------------------------------- library, history, updates

    private val libraryFile get() = File(dataDir, "manga/library.json")
    private val lock = Any()

    /** The web library merged with the phone's (favorites and downloaded titles from its last push). */
    fun library(): MangaLibrary {
        val own = ownLibrary()
        val phone = runCatching { phoneLibrary() }.getOrNull() ?: return own
        val known = own.entries.map { it.manga.sourceId + ":" + it.manga.remoteId }.toSet()
        return own.copy(
            categories = own.categories + phone.first.filter { cat -> own.categories.none { it.id == cat.id || it.name == cat.name } },
            entries = own.entries + phone.second.filter { (it.manga.sourceId + ":" + it.manga.remoteId) !in known },
        )
    }

    private fun ownLibrary(): MangaLibrary = synchronized(lock) {
        libraryFile.takeIf(File::isFile)?.let { runCatching { json.decodeFromString(MangaLibrary.serializer(), it.readText()) }.getOrNull() }
            ?: MangaLibrary()
    }

    /** Reads the phone's manga library out of the database its last sync push left on this PC. */
    private fun phoneLibrary(): Pair<List<LibraryCategory>, List<LibraryEntry>>? {
        val db = DesktopPaths.dbFile(dataDir).takeIf(File::isFile) ?: return null
        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
            fun tables() = conn.metaData.getTables(null, null, "manga_%", null).use { rs -> buildSet { while (rs.next()) add(rs.getString("TABLE_NAME")) } }
            val have = tables()
            if ("manga_series" !in have) return null
            val categories = if ("manga_favorite_categories" in have) conn.createStatement().use { st ->
                st.executeQuery("SELECT id, name FROM manga_favorite_categories ORDER BY sortOrder").use { rs ->
                    buildList { while (rs.next()) add(LibraryCategory("phone-" + rs.getString("id"), rs.getString("name"))) }
                }
            } else emptyList()
            val favorites = mutableMapOf<String, MutableList<String>>()
            if ("manga_favorites" in have) conn.createStatement().use { st ->
                st.executeQuery("SELECT seriesId, categoryId FROM manga_favorites").use { rs ->
                    while (rs.next()) favorites.getOrPut(rs.getString("seriesId")) { mutableListOf() } += "phone-" + rs.getString("categoryId")
                }
            }
            val downloaded = mutableSetOf<String>()
            if ("manga_chapters" in have) conn.createStatement().use { st ->
                st.executeQuery("SELECT DISTINCT sourceId, mangaId FROM manga_chapters WHERE status = 'completed'").use { rs ->
                    while (rs.next()) downloaded += rs.getString("sourceId") + ":" + rs.getString("mangaId")
                }
            }
            val entries = conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM manga_series").use { rs ->
                    val cols = (1..rs.metaData.columnCount).map { rs.metaData.getColumnName(it) }.toSet()
                    fun col(name: String) = if (name in cols) rs.getString(name).orEmpty() else ""
                    fun list(raw: String): List<String> = raw.trim().removePrefix("[").removeSuffix("]").split('|', ',', '\n')
                        .map { it.trim().trim('"') }.filter { it.isNotBlank() }
                    buildList {
                        while (rs.next()) {
                            val id = col("id")
                            val sourceKey = col("sourceId") + ":" + col("remoteId")
                            if (id !in favorites && sourceKey !in downloaded) continue
                            add(LibraryEntry(
                                manga = MangaSearchResult(
                                    sourceId = col("sourceId"), remoteId = col("remoteId"), title = col("title"),
                                    description = col("description"), coverUrl = col("coverUrl").ifBlank { null },
                                    canonicalUrl = col("canonicalUrl"), tags = list(col("tags")), languages = list(col("languages")),
                                    authors = list(col("authors")), artists = list(col("artists")), status = col("publicationStatus"),
                                    type = col("publicationType"), year = col("releaseYear"), rating = col("contentRating"), score = col("catalogScore"),
                                ),
                                categoryIds = favorites[id].orEmpty(),
                                addedAt = rs.getLong("updatedAt"),
                            ))
                        }
                    }
                }
            }
            return categories to entries
        }
    }

    private fun update(change: (MangaLibrary) -> MangaLibrary): MangaLibrary {
        synchronized(lock) {
            val next = change(ownLibrary())
            libraryFile.parentFile.mkdirs()
            libraryFile.writeText(json.encodeToString(MangaLibrary.serializer(), next))
        }
        return library()
    }

    fun setInLibrary(manga: MangaSearchResult, categoryIds: List<String>, inLibrary: Boolean): MangaLibrary = update { lib ->
        val others = lib.entries.filterNot { it.manga.sourceId == manga.sourceId && it.manga.remoteId == manga.remoteId }
        val existing = lib.entries.firstOrNull { it.manga.sourceId == manga.sourceId && it.manga.remoteId == manga.remoteId }
        lib.copy(entries = if (inLibrary) listOf((existing ?: LibraryEntry(manga)).copy(manga = manga, categoryIds = categoryIds)) + others else others)
    }

    fun addCategory(name: String): MangaLibrary = update { lib ->
        val id = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "category" } + "-" + (lib.categories.size + 1)
        lib.copy(categories = lib.categories + LibraryCategory(id, name.trim().take(40)))
    }

    fun recordRead(entry: HistoryEntry): MangaLibrary = update { lib ->
        lib.copy(
            history = (listOf(entry) + lib.history.filterNot { it.manga.sourceId == entry.manga.sourceId && it.manga.remoteId == entry.manga.remoteId }).take(300),
            readChapterIds = lib.readChapterIds + "${entry.chapter.sourceId}:${entry.chapter.remoteId}",
        )
    }

    fun removeHistory(sourceId: String, remoteId: String): MangaLibrary = update { lib ->
        lib.copy(history = lib.history.filterNot { it.manga.sourceId == sourceId && it.manga.remoteId == remoteId })
    }

    /** Checks every library title for chapters, then lists the newest across the library. */
    suspend fun refreshUpdates(): List<UpdateItem> {
        library().entries.forEach { entry -> runCatching { chapters(entry.manga) } }
        return updates()
    }

    fun updates(): List<UpdateItem> = library().entries
        .flatMap { entry -> entry.chapters.sortedByDescending { it.dateUpload }.take(20).map { UpdateItem(entry.manga, it) } }
        .sortedByDescending { it.chapter.dateUpload }
        .take(200)

    private fun sniff(bytes: ByteArray): String = when {
        bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
        bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 11 && String(bytes, 8, 4) == "WEBP" -> "image/webp"
        bytes.size > 5 && String(bytes, 0, 3) == "GIF" -> "image/gif"
        else -> "application/octet-stream"
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
    }
}
