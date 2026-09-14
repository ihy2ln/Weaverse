package com.ihy2ln.weaverse.core.manga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

internal object MangaCatalogJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseTitles(sourceId: String, baseUrl: String, body: String): List<MangaSearchResult> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        return collectObjects(root)
            .mapNotNull { parseTitle(sourceId, baseUrl, it) }
            .distinctBy { it.canonicalUrl.trimEnd('/').lowercase() }
            .take(40)
    }

    fun parseChapters(
        sourceId: String,
        manga: MangaSearchResult,
        body: String,
        language: String,
        readingOrder: String,
        fallbackPath: (number: String, hid: String) -> String?,
    ): List<MangaChapter> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val chapters = collectObjects(root)
            .mapNotNull { parseChapter(sourceId, manga, it, language, readingOrder, fallbackPath) }
            .distinctBy { it.canonicalUrl.trimEnd('/').lowercase() }
        return chapters.sortedWith(
            compareByDescending<MangaChapter> { it.chapterNumber.toDoubleOrNull() }.thenByDescending { it.title },
        )
    }

    fun extractNonce(html: String): String? {
        val patterns = listOf(
            Regex("\"nonce\"\\s*:\\s*\"([A-Za-z0-9]+)\""),
            Regex("""search_nonce["']?\s*[:=]\s*["']([A-Za-z0-9]+)"""),
            Regex("""name=["']nonce["'][^>]*value=["']([^"']+)""", RegexOption.IGNORE_CASE),
            Regex("""value=["']([^"']+)["'][^>]*name=["']nonce["']""", RegexOption.IGNORE_CASE),
            Regex("""id=["'][^"']*nonce[^"']*["'][^>]*value=["']([^"']+)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.takeIf { it.isNotBlank() }
    }

    fun extractMangaId(html: String): String? {
        val patterns = listOf(
            Regex("""data-id=["'](\d+)["']""", RegexOption.IGNORE_CASE),
            Regex("""data-manga[-_]?id=["'](\d+)["']""", RegexOption.IGNORE_CASE),
            Regex("""["']manga_id["']\s*[:=]\s*["']?(\d+)"""),
            Regex("""name=["']manga_id["'][^>]*value=["'](\d+)["']""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
    }

    fun htmlPayload(body: String): String {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return body
        return listOf("html", "list_chapter", "chapter_list", "data", "result")
            .firstNotNullOfOrNull { key -> root.flexibleString(key) }
            ?.takeIf { it.contains("<") }
            ?: body
    }

    private fun parseTitle(sourceId: String, baseUrl: String, obj: JsonObject): MangaSearchResult? {
        val title = obj.flexibleString("title", "name", "comic_name")
            ?: textOf(obj["title"])
            ?: return null
        val hid = obj.flexibleString("hid", "id", "comic_id", "manga_id").orEmpty()
        val slug = obj.flexibleString("slug", "comic_slug", "manga_slug").orEmpty()
        val rawUrl = obj.flexibleString("url", "link", "href", "canonical", "canonical_url")
        val url = rawUrl?.let { resolveHttpUrl(baseUrl, it) ?: it.takeIf { it.startsWith("http") } }
            ?: when {
                slug.isNotBlank() && hid.isNotBlank() && !slug.contains('.') ->
                    resolveHttpUrl(baseUrl, "/manga/$slug.$hid")
                slug.isNotBlank() -> resolveHttpUrl(baseUrl, "/manga/$slug")
                hid.isNotBlank() -> resolveHttpUrl(baseUrl, "/manga/$hid")
                else -> null
            }
            ?: return null
        val cover = (obj.flexibleString("cover", "poster", "image", "thumbnail", "cover_url", "source_url")
            ?: featuredMedia(obj))
            ?.let { resolveHttpUrl(baseUrl, it) ?: it }
        val remoteId = hid.ifBlank { slug }.ifBlank { stableId(url) }
        return MangaSearchResult(
            sourceId = sourceId,
            remoteId = remoteId,
            title = title.replace(Regex("\\s+"), " ").trim(),
            description = obj.flexibleString("description", "summary", "synopsis").orEmpty(),
            coverUrl = cover,
            canonicalUrl = url,
        )
    }

    private fun parseChapter(
        sourceId: String,
        manga: MangaSearchResult,
        obj: JsonObject,
        language: String,
        readingOrder: String,
        fallbackPath: (number: String, hid: String) -> String?,
    ): MangaChapter? {
        if (!looksLikeChapter(obj)) return null
        val number = obj.flexibleString("chap", "chapter", "number", "chapter_number", "name")
            ?.let { CHAPTER_NUMBER.find(it)?.groupValues?.getOrNull(2) ?: TRAILING_NUMBER.find(it)?.groupValues?.getOrNull(1) }
            ?.replace('-', '.')
            .orEmpty()
        val hid = obj.flexibleString("hid", "id", "chapter_id").orEmpty()
        val title = obj.flexibleString("title", "name", "chapter_title").orEmpty()
            .ifBlank { if (number.isBlank()) return null else "Chapter $number" }
        val rawUrl = obj.flexibleString("url", "link", "href", "canonical")
        val url = rawUrl?.let { resolveHttpUrl(manga.canonicalUrl, it) ?: it.takeIf { it.startsWith("http") } }
            ?: fallbackPath(number, hid)?.let { resolveHttpUrl(manga.canonicalUrl, it) }
        val canonical = url ?: return null
        return MangaChapter(
            sourceId = sourceId,
            remoteId = hid.ifBlank { stableId(canonical) },
            mangaId = manga.remoteId,
            mangaTitle = manga.title,
            title = title,
            chapterNumber = number.ifBlank {
                CHAPTER_NUMBER.find("$title $canonical")?.groupValues?.getOrNull(2)
                    ?: TRAILING_NUMBER.find(pathSegments(canonical).lastOrNull().orEmpty())?.groupValues?.getOrNull(1)?.replace('-', '.')
                    ?: ""
            },
            language = language,
            canonicalUrl = canonical,
            readingOrder = readingOrder,
        )
    }

    private fun looksLikeChapter(obj: JsonObject): Boolean {
        if (obj.flexibleString("chap", "chapter", "chapter_number") != null) return true
        if (obj.flexibleString("url", "link", "href")?.let { path ->
                path.contains("chapter", true) || path.contains("/read/", true) || TRAILING_NUMBER.containsMatchIn(path)
            } == true) return true
        val name = obj.flexibleString("name", "title").orEmpty()
        return CHAPTER_NUMBER.containsMatchIn(name) || name.contains("chap", true)
    }

    private fun featuredMedia(obj: JsonObject): String? {
        val embedded = obj["_embedded"] as? JsonObject ?: return null
        val media = embedded["wp:featuredmedia"] as? JsonArray ?: return null
        return (media.firstOrNull() as? JsonObject)?.flexibleString("source_url", "url")
    }

    private fun collectObjects(element: JsonElement, depth: Int = 0): List<JsonObject> {
        if (depth > 6) return emptyList()
        return when (element) {
            is JsonArray -> {
                val objects = element.mapNotNull { it as? JsonObject }
                if (objects.any { looksLikeRecord(it) }) objects else element.flatMap { collectObjects(it, depth + 1) }
            }
            is JsonObject -> {
                for (key in listOf("data", "result", "titles", "comics", "manga", "items", "chapters", "list")) {
                    element[key]?.let { child ->
                        val found = collectObjects(child, depth + 1)
                        if (found.isNotEmpty()) return found
                    }
                }
                if (looksLikeRecord(element)) listOf(element) else element.values.flatMap { collectObjects(it, depth + 1) }
            }
            else -> emptyList()
        }
    }

    private fun looksLikeRecord(obj: JsonObject): Boolean =
        obj.containsKey("title") || obj.containsKey("name") || obj.containsKey("hid") ||
            obj.containsKey("chap") || obj.containsKey("slug") || obj.containsKey("link")

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(28)

    private val CHAPTER_NUMBER = Regex("(chapter|chap|ch)[-_. /]*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val TRAILING_NUMBER = Regex("(\\d+(?:[.-]\\d+)?)")
}

internal fun JsonObject.flexibleString(vararg keys: String): String? {
    keys.forEach { key ->
        this[key]?.let { textOf(it) }?.takeIf { it.isNotBlank() && it != "null" }?.let { return it }
    }
    return null
}

internal fun textOf(element: JsonElement?): String? = when (element) {
    null -> null
    is JsonPrimitive -> element.content.takeIf { it.isNotBlank() && it != "null" }
    is JsonObject -> element["rendered"]?.let(::textOf)
        ?: element["en"]?.let(::textOf)
        ?: element.values.firstNotNullOfOrNull(::textOf)
    is JsonArray -> element.firstOrNull()?.let(::textOf)
    else -> null
}
