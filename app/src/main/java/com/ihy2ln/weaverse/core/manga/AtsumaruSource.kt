package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public catalog/reader endpoints used by Atsumaru's own website, not its empty SPA shell. */
internal class AtsumaruSource(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://atsu.moe/",
) : MangaSourceAdapter {
    override val descriptor = MangaSourceDescriptor("atsumaru", "Atsumaru", baseUrl,
        "Public manga catalog, chapters and image pages", authorized = false)

    override suspend fun browse(mode: MangaBrowseMode) = browsePage(mode, 0)
    override suspend fun search(query: String) = searchPage(query, 0)
    override suspend fun browsePage(mode: MangaBrowseMode, page: Int): List<MangaSearchResult> {
        require(page >= 0)
        val endpoint = if (mode == MangaBrowseMode.Latest) "recentlyUpdated" else "popular"
        return get("api/home2/$endpoint", mapOf("offset" to "${page * 40}", "limit" to "40",
            "mediums" to "Comic", "contentRatings" to "Safe,Suggestive",
            "timeframe" to "all")).array("items").map { manga(it.jsonObject) }
    }

    override suspend fun searchPage(query: String, page: Int): List<MangaSearchResult> {
        require(page >= 0)
        if (query.isBlank()) return emptyList()
        val data = get("collections/manga/documents/search", mapOf(
            "q" to query.trim(), "query_by" to "title,englishTitle,otherNames,authors,acronyms",
            "query_by_weights" to "4,3,2,2,1", "page" to "${page + 1}", "per_page" to "40",
            "filter_by" to "hidden:!=true && isAdult:=false && medium:=Comic && mbContentRating:=[Safe,Suggestive]",
        ))
        return data.array("hits").map { manga(it.jsonObject.getValue("document").jsonObject) }
    }

    override suspend fun details(manga: MangaSearchResult): MangaSearchResult =
        manga(get("api/manga/page", mapOf("id" to manga.remoteId)).getValue("mangaPage").jsonObject)

    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> =
        get("api/manga/allChapters", mapOf("mangaId" to manga.remoteId)).array("chapters").map {
            val chapter = it.jsonObject
            val id = chapter.string("id")
            MangaChapter(descriptor.id, id, manga.remoteId, manga.title, chapter.string("title"),
                chapterNumber = chapter.string("number"), canonicalUrl = "${baseUrl}read/${manga.remoteId}/$id",
                dateUpload = chapter.string("createdAt").toLongOrNull() ?: 0L)
        }.sortedByDescending { it.chapterNumber.toDoubleOrNull() }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> =
        get("api/read/chapter", mapOf("mangaId" to chapter.mangaId, "chapterId" to chapter.remoteId))
            .getValue("readChapter").jsonObject.array("pages").mapIndexed { index, item ->
                val url = image(item.jsonObject.string("image")) ?: error("Atsumaru returned a page without an image")
                MangaPage(descriptor.id, chapter.remoteId, index, url, "$index.${url.substringBefore('?').substringAfterLast('.', "jpg")}")
            }

    internal fun manga(item: JsonObject): MangaSearchResult {
        val poster = item["poster"]
        val cover = if (poster is JsonObject) poster.string("mediumImage").ifBlank { poster.string("image") }
            else item.string("mediumImage").ifBlank { item.string("posterMedium").ifBlank { item.string("image").ifBlank { item.string("poster") } } }
        fun names(key: String) = item.array(key).mapNotNull {
            when (it) { is JsonObject -> it.string("name"); is JsonPrimitive -> it.contentOrNull; else -> null }
        }.filter(String::isNotBlank)
        val id = item.string("id").also { require(it.isNotBlank()) { "Missing Atsumaru manga ID" } }
        return MangaSearchResult(descriptor.id, id, item.string("title"), description = item.string("synopsis"),
            coverUrl = image(cover), canonicalUrl = "${baseUrl}manga/$id", tags = names("genres") + names("tags"),
            languages = listOf("en"), authors = names("authors"), status = item.string("status"),
            type = item.string("type"), year = item.string("year"), rating = item.string("mbRating"))
    }

    private fun image(path: String): String? = path.takeIf(String::isNotBlank)?.let {
        if (it.startsWith("https://")) it else "https://cdn.atsu.moe/static/${it.removePrefix("/static/").trimStart('/')}"
    }
    private suspend fun get(path: String, params: Map<String, String>): JsonObject = withContext(Dispatchers.IO) {
        val url = baseUrl.toHttpUrl().resolve(path)!!.newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use {
            check(it.isSuccessful) { "Atsumaru request failed (HTTP ${it.code}); retry or open the source website." }
            Json.parseToJsonElement(it.body?.string() ?: error("Empty Atsumaru response")).jsonObject
        }
    }
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    private fun JsonObject.array(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
}
