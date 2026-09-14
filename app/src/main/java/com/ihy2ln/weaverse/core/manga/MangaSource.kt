package com.ihy2ln.weaverse.core.manga

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Metadata shown in the source catalog.  Only reviewed, app-owned adapters are registered. */
data class MangaSourceDescriptor(
    val id: String,
    val name: String,
    val baseUrl: String,
    val description: String,
    val authorized: Boolean,
    val supportsSearch: Boolean = true,
    val supportsDownloads: Boolean = true,
)

enum class MangaBrowseMode { Popular, Latest }

data class MangaWebsite(
    val id: String,
    val name: String,
    val url: String,
    val builtIn: Boolean = false,
)

/**
 * Optional bookmarks for sites that are not a built-in adapter. Catalog
 * sources such as Rawkuma live in [MangaSourceRegistry] instead of this list.
 */
val bundledMangaWebsites: List<MangaWebsite> = emptyList()

data class MangaSearchResult(
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val description: String = "",
    val coverUrl: String? = null,
    val canonicalUrl: String = "",
    /** Normalized catalog metadata. Public HTML adapters fill these from page/JSON-LD data. */
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val status: String = "",
    val type: String = "",
    val year: String = "",
    val rating: String = "",
)

data class MangaChapter(
    val sourceId: String,
    val remoteId: String,
    val mangaId: String,
    val mangaTitle: String,
    val title: String,
    val volume: String = "",
    val chapterNumber: String = "",
    val language: String = "en",
    val canonicalUrl: String = "",
    val readingOrder: String = "ltr",
)

data class MangaPage(
    val sourceId: String,
    val chapterId: String,
    val pageIndex: Int,
    val remoteUrl: String,
    val fileName: String,
)

interface MangaSourceAdapter {
    val descriptor: MangaSourceDescriptor
    suspend fun search(query: String): List<MangaSearchResult>
    suspend fun browse(mode: MangaBrowseMode): List<MangaSearchResult> = emptyList()

    /**
     * One page of the catalog, zero-based. A source that cannot page past its first screen
     * inherits these defaults and simply reports nothing beyond page 0, which the caller
     * reads as "end of catalog" and stops asking.
     */
    suspend fun browsePage(mode: MangaBrowseMode, page: Int): List<MangaSearchResult> =
        if (page <= 0) browse(mode) else emptyList()

    suspend fun searchPage(query: String, page: Int): List<MangaSearchResult> =
        if (page <= 0) search(query) else emptyList()
    suspend fun details(manga: MangaSearchResult): MangaSearchResult = manga
    suspend fun chapters(manga: MangaSearchResult): List<MangaChapter>
    suspend fun pages(chapter: MangaChapter): List<MangaPage>
}

@Singleton
class MangaSourceRegistry @Inject constructor(
    private val mangaDex: MangaDexSource,
    private val publicHtml: PublicHtmlMangaSources,
) {
    /** MangaDex plus reviewed public-HTML catalog adapters (Comix, Atsumaru, MangaFire, MangaDot, Rawkuma). */
    val sources: List<MangaSourceAdapter> = listOf(mangaDex) + publicHtml.sources

    fun get(sourceId: String): MangaSourceAdapter? = sources.firstOrNull { it.descriptor.id == sourceId }
}

/** First-party connector for MangaDex's documented API and At-Home image service. */
@Singleton
class MangaDexSource @Inject constructor(
    private val http: HttpClient,
) : MangaSourceAdapter {
    override val descriptor = MangaSourceDescriptor(
        id = "mangadex",
        name = "MangaDex",
        baseUrl = "https://api.mangadex.org",
        description = "Authorized API connector with language and chapter metadata.",
        authorized = true,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Catalog rows per request; also the stride for the paging offset. */
    private val PAGE_SIZE = 20

    override suspend fun search(query: String): List<MangaSearchResult> =
        if (query.isBlank()) emptyList() else loadManga(query = query, mode = null)

    override suspend fun browse(mode: MangaBrowseMode): List<MangaSearchResult> =
        loadManga(query = null, mode = mode)

    override suspend fun browsePage(mode: MangaBrowseMode, page: Int): List<MangaSearchResult> =
        loadManga(query = null, mode = mode, page = page)

    override suspend fun searchPage(query: String, page: Int): List<MangaSearchResult> =
        if (query.isBlank()) emptyList() else loadManga(query = query, mode = null, page = page)

    private suspend fun loadManga(
        query: String?,
        mode: MangaBrowseMode?,
        page: Int = 0,
    ): List<MangaSearchResult> {
        val body = http.get("${descriptor.baseUrl}/manga") {
            query?.trim()?.takeIf { it.isNotBlank() }?.let { parameter("title", it) }
            parameter("limit", PAGE_SIZE)
            if (page > 0) parameter("offset", page * PAGE_SIZE)
            parameter("includes[]", "cover_art")
            parameter("contentRating[]", "safe")
            parameter("contentRating[]", "suggestive")
            parameter("contentRating[]", "erotica")
            parameter("contentRating[]", "pornographic")
            when (mode) {
                MangaBrowseMode.Popular -> parameter("order[followedCount]", "desc")
                MangaBrowseMode.Latest -> parameter("order[latestUploadedChapter]", "desc")
                null -> Unit
            }
        }.body<String>()
        return json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val id = obj.string("id") ?: return@mapNotNull null
            val attributes = obj.obj("attributes")
            val title = attributes?.obj("title")?.firstValue() ?: "Untitled manga"
            val description = attributes?.obj("description")?.firstValue().orEmpty()
            val tags = attributes?.jsonArray("tags")?.mapNotNull { tag ->
                tag.jsonObject.obj("attributes")?.obj("name")?.firstValue()
            }.orEmpty()
            val languages = attributes?.jsonArray("availableTranslatedLanguages")
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
            val coverFileName = obj["relationships"]?.jsonArray.orEmpty()
                .firstOrNull { it.jsonObject.string("type") == "cover_art" }
                ?.jsonObject?.obj("attributes")?.string("fileName")
            val cover = coverFileName?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }
            MangaSearchResult(
                sourceId = descriptor.id,
                remoteId = id,
                title = title,
                description = description,
                coverUrl = cover,
                canonicalUrl = "https://mangadex.org/title/$id",
                tags = tags,
                languages = languages,
            )
        }
    }

    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> {
        val chapters = mutableListOf<MangaChapter>()
        var offset = 0
        var received: Int
        var total: Int
        do {
            val body = http.get("${descriptor.baseUrl}/chapter") {
                parameter("manga", manga.remoteId)
                parameter("order[chapter]", "asc")
                parameter("order[volume]", "asc")
                parameter("limit", 100)
                parameter("offset", offset)
                parameter("contentRating[]", "safe")
                parameter("contentRating[]", "suggestive")
                parameter("contentRating[]", "erotica")
                parameter("contentRating[]", "pornographic")
            }.body<String>()
            val root = json.parseToJsonElement(body).jsonObject
            val page = root["data"]?.jsonArray.orEmpty()
            received = page.size
            chapters += page.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj.string("id") ?: return@mapNotNull null
                val a = obj.obj("attributes") ?: return@mapNotNull null
                MangaChapter(
                    sourceId = descriptor.id,
                    remoteId = id,
                    mangaId = manga.remoteId,
                    mangaTitle = manga.title,
                    title = a.string("title").orEmpty().ifBlank { "Chapter ${a.string("chapter").orEmpty()}" },
                    volume = a.string("volume").orEmpty(),
                    chapterNumber = a.string("chapter").orEmpty(),
                    language = a.string("translatedLanguage").orEmpty().ifBlank { "en" },
                    canonicalUrl = "https://mangadex.org/chapter/$id",
                    readingOrder = "ltr",
                )
            }
            offset += received
            total = root["total"]?.jsonPrimitive?.content?.toIntOrNull() ?: offset
        } while (received > 0 && offset < total)
        return chapters
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val body = http.get("${descriptor.baseUrl}/at-home/server/${chapter.remoteId}").body<String>()
        val root = json.parseToJsonElement(body).jsonObject
        val baseUrl = root.string("baseUrl") ?: return emptyList()
        val chapterObj = root.obj("chapter") ?: return emptyList()
        val hash = chapterObj.string("hash") ?: return emptyList()
        val fileNames = chapterObj["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return fileNames.mapIndexed { index, fileName ->
            MangaPage(
                sourceId = descriptor.id,
                chapterId = chapter.remoteId,
                pageIndex = index,
                remoteUrl = "$baseUrl/data/$hash/$fileName",
                fileName = fileName,
            )
        }
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.jsonArray(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.firstValue(): String? = values.firstOrNull()?.jsonPrimitive?.contentOrNull
private fun JsonArray.orEmpty(): JsonArray = this
private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (isString || content.isNotBlank()) content else null
