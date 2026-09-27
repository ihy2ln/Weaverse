package com.ihy2ln.weaverse.feature.chatting.media

import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import com.ihy2ln.weaverse.data.settings.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** One picture or GIF found on the web, before it is downloaded into the library. */
data class WebPicture(
    val id: String,
    val title: String,
    /** Small preview for the grid. */
    val thumbUrl: String,
    /** The file that gets downloaded when picked. */
    val fullUrl: String,
    val isGif: Boolean,
    /** Shown on the tile: "GIPHY", "Openverse", "r/memes"… */
    val source: String,
    /** Where it came from, kept with the saved picture for credit. */
    val pageUrl: String = "",
    val credit: String = "",
    /** The source classified this item as adult; keep that label with saved media. */
    val adult: Boolean = false,
    /** Video results use a public thumbnail and open their attributed watch page. */
    val videoPreview: Boolean = false,
    /** Provider supplied caption, description, or generation prompt. Never the search query. */
    val description: String = "",
)

/** What the search is for. */
enum class WebSearchKind(val label: String) { All("All"), Gifs("GIFs"), Memes("Memes"), Pictures("Pictures") }

/** A search source, and whether it needs a key the writer has to paste in. */
enum class WebSource(val label: String, val keyIds: List<String>) {
    Openverse("Openverse", emptyList()),
    Commons("Wikimedia Commons", emptyList()),
    Imgflip("Imgflip memes", emptyList()),
    Giphy("GIPHY", listOf(KEY_GIPHY)),
    Tenor("Tenor", listOf(KEY_TENOR)),
    Google("Google Images", listOf(KEY_GOOGLE, KEY_GOOGLE_CX)),
    Brave("Brave Image Search", listOf(KEY_BRAVE)),
    Civitai("Civitai", emptyList()),
}

const val KEY_GIPHY = "giphy"
const val KEY_TENOR = "tenor"
const val KEY_GOOGLE = "google_cse"
const val KEY_GOOGLE_CX = "google_cse_cx"
const val KEY_BRAVE = "brave_image_search"
const val KEY_CIVITAI = "civitai"

/**
 * Searches picture and GIF sites for the chat pickers. Openverse, Wikimedia Commons and
 * Imgflip need no account; GIPHY, Tenor and Google Images join in once the
 * writer pastes a key. Every source runs in parallel and a failing one is skipped, so
 * one site being down never empties the results.
 */
@Singleton
class WebPictureSearch @Inject constructor(
    okHttpClient: OkHttpClient,
    private val keys: SecureKeyStore,
    private val mediaRepository: MediaRepository,
    private val db: WeaverseDatabase,
) {
    private val http = okHttpClient.newBuilder()
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private var imgflipCache: List<WebPicture>? = null

    fun key(id: String): String = keys.get(id).orEmpty()

    fun setKey(id: String, value: String) {
        if (value.isBlank()) keys.clear(id) else keys.set(id, value)
    }

    fun enabled(source: WebSource): Boolean = source.keyIds.all { key(it).isNotBlank() }

    suspend fun braveStatus(): String = withContext(Dispatchers.IO) {
        if (!enabled(WebSource.Brave)) return@withContext "Brave key not set"
        runCatching {
            val images = brave("cat", false, false).size
            val videos = runCatching { braveVideos("cat", false).size }
            if (videos.isSuccess) "Brave connected · $images image(s), ${videos.getOrDefault(0)} video(s)"
            else "Brave images connected · video search unavailable: ${videos.exceptionOrNull()?.message.orEmpty().take(65)}"
        }.getOrElse { "Brave unavailable: ${it.message.orEmpty().take(90)}" }
    }

    suspend fun civitaiStatus(adultAllowed: Boolean): String = withContext(Dispatchers.IO) {
        runCatching {
            val query = if (adultAllowed) "nude portrait" else "portrait"
            "Civitai connected · ${civitai(query, adultAllowed).size} matching preview(s)"
        }
            .getOrElse { "Civitai unavailable: ${it.message.orEmpty().take(90)}" }
    }

    /** Public, indexed previews from creator and adult video sites. No account access or scraping. */
    suspend fun publicPreviews(query: String, adultAllowed: Boolean): SearchOutcome = coroutineScope {
        if (key(KEY_BRAVE).isBlank()) return@coroutineScope SearchOutcome(emptyList(), emptyList(), emptyList())
        val sites = if (adultAllowed) ADULT_PREVIEW_SITES else GENERAL_PREVIEW_SITES
        val offset = previewRotation.getAndIncrement()
        val selected = (0 until 2).map { sites[(offset + it).mod(sites.size)] }
        val jobs = selected.flatMap { site ->
            val q = "site:$site ${query.split(' ').take(5).joinToString(" ")}"
            val imageJob = "$site images" to async(Dispatchers.IO) { runCatching { brave(q, false, adultAllowed) } }
            val videoJob = "$site videos" to async(Dispatchers.IO) { runCatching { braveVideos(q, adultAllowed) } }
            if (offset % 2 == 0) listOf(videoJob, imageJob) else listOf(imageJob, videoJob)
        }
        val results = jobs.map { it.first to it.second.await() }
        val lists = results.map { it.second.getOrDefault(emptyList()).iterator() }
        val merged = buildList {
            while (lists.any { it.hasNext() }) lists.forEach { if (it.hasNext()) add(it.next()) }
        }
        SearchOutcome(
            merged.distinctBy { it.pageUrl },
            results.filter { it.second.isFailure }.map { it.first },
            results.map { it.first },
        )
    }

    private fun braveVideos(q: String, adultAllowed: Boolean): List<WebPicture> {
        val url = "https://api.search.brave.com/res/v1/videos/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q).addQueryParameter("count", "20")
            .addQueryParameter("safesearch", if (adultAllowed) "off" else "strict").build()
        val request = Request.Builder().url(url).header("X-Subscription-Token", key(KEY_BRAVE))
            .header("Accept", "application/json").build()
        val root = http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Brave video HTTP ${response.code}" }
            json.parseToJsonElement(response.body?.string().orEmpty()).obj()
        }
        return root["results"].arr().mapNotNull { item ->
            val o = item.obj()
            val page = o.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val thumb = o["thumbnail"].obj().str("src")?.takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            WebPicture("bv-${page.hashCode()}", o.str("title").orEmpty(), thumb, thumb,
                false, "Brave video", page, o["video"].obj().str("creator")
                    ?: o["video"].obj().str("publisher").orEmpty(),
                adult = ADULT_PREVIEW_SITES.any { page.contains(it, true) },
                videoPreview = true, description = o.str("description").orEmpty())
        }
    }

    /** Results from every enabled source that fits [kind], interleaved so no one site dominates. */
    suspend fun search(query: String, kind: WebSearchKind, adultAllowed: Boolean = SocialContentPolicy.explicit): SearchOutcome = coroutineScope {
        val q = query.trim()
        val sources = sourcesFor(kind).filter { enabled(it) }
        val jobs = sources.map { source ->
            source to async(Dispatchers.IO) { runCatching { fetch(source, q, kind, adultAllowed) } }
        }
        val lists = jobs.map { (source, job) -> source to job.await() }
        val failed = lists.filter { it.second.isFailure }.map { it.first.label }
        val found = lists.mapNotNull { it.second.getOrNull() }
        val merged = buildList {
            val iterators = found.map { it.iterator() }
            while (iterators.any { it.hasNext() }) iterators.forEach { if (it.hasNext()) add(it.next()) }
        }.distinctBy { it.fullUrl }
        SearchOutcome(merged, failed, sources.map { it.label })
    }

    data class SearchOutcome(val results: List<WebPicture>, val failedSources: List<String>, val searchedSources: List<String>)

    private fun sourcesFor(kind: WebSearchKind): List<WebSource> = when (kind) {
        WebSearchKind.Gifs -> listOf(WebSource.Brave, WebSource.Giphy, WebSource.Tenor, WebSource.Openverse, WebSource.Commons, WebSource.Google)
        // Reddit is not a source: it refuses API calls without a signed-in app.
        WebSearchKind.Memes -> listOf(WebSource.Brave, WebSource.Imgflip, WebSource.Giphy, WebSource.Openverse, WebSource.Google)
        WebSearchKind.Pictures -> listOf(WebSource.Civitai, WebSource.Brave, WebSource.Openverse, WebSource.Commons, WebSource.Google)
        WebSearchKind.All -> WebSource.entries
    }

    private fun fetch(source: WebSource, q: String, kind: WebSearchKind, adultAllowed: Boolean): List<WebPicture> {
        val gifs = kind == WebSearchKind.Gifs
        return when (source) {
            WebSource.Openverse -> openverse(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Commons -> commons(q, gifs)
            WebSource.Imgflip -> imgflip(q)
            WebSource.Giphy -> giphy(q)
            WebSource.Tenor -> tenor(q, adultAllowed)
            WebSource.Google -> google(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Brave -> brave(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Civitai -> if (gifs || kind == WebSearchKind.Memes) emptyList() else civitai(q, adultAllowed)
        }
    }

    // ------------------------------------------------------------ sources

    /** Public image-index previews only. Never writes to the indexed sites. */
    private fun brave(q: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://api.search.brave.com/res/v1/images/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", if (gifs) "$q gif" else q)
            .addQueryParameter("count", "35")
            .addQueryParameter("safesearch", if (adultAllowed) "off" else "strict")
            .build()
        val request = Request.Builder().url(url).header("X-Subscription-Token", key(KEY_BRAVE))
            .header("Accept", "application/json").build()
        val root = http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Brave HTTP ${response.code}" }
            json.parseToJsonElement(response.body?.string().orEmpty()).obj()
        }
        return root["results"].arr().mapNotNull { item ->
            val o = item.obj()
            val full = o["properties"].obj().str("url") ?: return@mapNotNull null
            if (!full.startsWith("https://")) return@mapNotNull null
            val gif = full.substringBefore('?').endsWith(".gif", true)
            if (gifs && !gif) return@mapNotNull null
            val page = o.str("url").orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
            WebPicture(
                id = "br-" + full.hashCode(), title = o.str("title").orEmpty(),
                thumbUrl = o["thumbnail"].obj().str("src") ?: full,
                fullUrl = full, isGif = gif, source = "Brave",
                pageUrl = page, credit = o.str("source").orEmpty(),
                adult = ADULT_PREVIEW_SITES.any { page.contains(it, true) },
                description = o.str("description").orEmpty(),
            )
        }
    }

    /** Public Civitai gallery only; generation metadata is used for local relevance ranking. */
    private fun civitai(q: String, adultAllowed: Boolean): List<WebPicture> {
        val wantsAdult = adultAllowed && ADULT_QUERY_WORDS.containsMatchIn(q)
        val base = if (wantsAdult) "https://civitai.red" else "https://civitai.com"
        val url = "$base/api/v1/images".toHttpUrl().newBuilder()
            .addQueryParameter("type", "image")
            .addQueryParameter("sort", "Random")
            .addQueryParameter("limit", "80")
            .addQueryParameter("page", Random.nextInt(1, 9).toString())
            .addQueryParameter("withMeta", "true")
            // PG through XXX, excluding Civitai's Blocked level. Off allows PG and PG-13 only.
            .addQueryParameter("browsingLevel", if (wantsAdult) "31" else "3")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .apply { key(KEY_CIVITAI).takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        val root = http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Civitai HTTP ${response.code}" }
            json.parseToJsonElement(response.body?.string().orEmpty()).obj()
        }
        val words = q.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in STOP_WORDS && it !in CIVITAI_GENERIC_WORDS }.distinct()
        val candidates = root["items"].arr().mapNotNull { item ->
            val o = item.obj()
            val id = o.str("id") ?: return@mapNotNull null
            val full = o.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            if (o.str("type") != "image") return@mapNotNull null
            val level = o.str("browsingLevel")?.toIntOrNull() ?: return@mapNotNull null
            if ((level and 32) != 0 || (!wantsAdult && (level and 24) != 0)) return@mapNotNull null
            val prompt = o["meta"].obj().str("prompt").orEmpty().lowercase()
            val score = words.count { prompt.contains(it) }
            val creator = o.str("username").orEmpty()
            val page = "$base/images/$id"
            WebPicture(
                id = "cv-$id", title = prompt.take(100).ifBlank { "Civitai artwork" },
                thumbUrl = full, fullUrl = full, isGif = false, source = "Civitai",
                pageUrl = page, credit = creator.takeIf { it.isNotBlank() }?.let { "@$it on Civitai" } ?: "Civitai creator",
                adult = (level and 24) != 0,
                description = prompt.take(1500),
            ) to score
        }.sortedByDescending { it.second }
        val relevant = candidates.filter { it.second > 0 }
        return relevant.take(20).map { it.first }
    }

    private fun openverse(q: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://api.openverse.org/v1/images/".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            // Openverse refuses page sizes above 20 without an account.
            .addQueryParameter("page_size", "20")
            .addQueryParameter("mature", if (adultAllowed) "true" else "false")
            .apply { if (gifs) addQueryParameter("extension", "gif") }
            .build()
        val root = getJson(url.toString()).obj()
        return root["results"].arr().mapNotNull { item ->
            val o = item.obj()
            val full = o.str("url") ?: return@mapNotNull null
            WebPicture(
                id = "ov-" + (o.str("id") ?: full.hashCode().toString()),
                title = o.str("title").orEmpty(),
                thumbUrl = o.str("thumbnail") ?: full,
                fullUrl = full,
                isGif = o.str("filetype") == "gif" || full.endsWith(".gif", true),
                source = "Openverse",
                pageUrl = o.str("foreign_landing_url").orEmpty(),
                credit = listOfNotNull(o.str("creator"), o.str("license")?.uppercase()?.let { "CC $it" }).joinToString(" · "),
                adult = o.str("mature") == "true",
                description = o.str("description").orEmpty(),
            )
        }
    }

    private fun commons(q: String, gifs: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://commons.wikimedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", if (gifs) "$q filemime:image/gif" else "$q filetype:bitmap")
            .addQueryParameter("gsrnamespace", "6")
            .addQueryParameter("gsrlimit", "24")
            .addQueryParameter("prop", "imageinfo")
            .addQueryParameter("iiprop", "url|mime")
            .addQueryParameter("iiurlwidth", "320")
            .build()
        val pages = getJson(url.toString()).obj()["query"].obj()["pages"].obj()
        return pages.values.mapNotNull { page ->
            val p = page.obj()
            val info = p["imageinfo"].arr().firstOrNull()?.obj() ?: return@mapNotNull null
            val full = info.str("url") ?: return@mapNotNull null
            val mime = info.str("mime").orEmpty()
            if (!mime.startsWith("image/") || mime.contains("svg") || mime.contains("tiff")) return@mapNotNull null
            WebPicture(
                id = "wc-" + (p.str("pageid") ?: full.hashCode().toString()),
                title = p.str("title").orEmpty().removePrefix("File:").substringBeforeLast('.'),
                thumbUrl = info.str("thumburl") ?: full,
                fullUrl = full,
                isGif = mime == "image/gif",
                source = "Wikimedia",
                pageUrl = info.str("descriptionurl").orEmpty(),
                credit = "Wikimedia Commons",
            )
        }
    }

    /** Imgflip's popular meme templates, filtered by the query words locally. */
    private fun imgflip(q: String): List<WebPicture> {
        val all = imgflipCache ?: getJson("https://api.imgflip.com/get_memes").obj()["data"].obj()["memes"].arr()
            .mapNotNull { item ->
                val o = item.obj()
                val full = o.str("url") ?: return@mapNotNull null
                WebPicture(
                    id = "if-" + (o.str("id") ?: full.hashCode().toString()),
                    title = o.str("name").orEmpty(),
                    thumbUrl = full,
                    fullUrl = full,
                    isGif = full.endsWith(".gif", true),
                    source = "Imgflip",
                    pageUrl = "https://imgflip.com/memetemplate/" + o.str("id").orEmpty(),
                )
            }.also { imgflipCache = it }
        val words = memeWords(q)
        if (words.isEmpty()) return if (q.isBlank()) all.take(30) else emptyList()
        // Rank templates by how many meaningful words their name contains.
        return all.map { pic -> pic to words.count { w -> Regex("\\b" + Regex.escape(w)).containsMatchIn(pic.title.lowercase()) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(30)
    }

    /** Words worth matching a meme template name on — no "at", "the", "is"… */
    private fun memeWords(q: String): List<String> =
        q.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP_WORDS }

    private fun giphy(q: String): List<WebPicture> {
        val key = key(KEY_GIPHY)
        val base = if (q.isBlank()) "https://api.giphy.com/v1/gifs/trending" else "https://api.giphy.com/v1/gifs/search"
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("api_key", key)
            .apply { if (q.isNotBlank()) addQueryParameter("q", q) }
            .addQueryParameter("limit", "30")
            // GIPHY's highest rating is "r"; it carries no pornography at any setting.
            .addQueryParameter("rating", if (SocialContentPolicy.open) "r" else "pg-13")
            .build()
        return getJson(url.toString()).obj()["data"].arr().mapNotNull { item ->
            val o = item.obj()
            val images = o["images"].obj()
            val full = images["downsized"].obj().str("url") ?: images["original"].obj().str("url") ?: return@mapNotNull null
            WebPicture(
                id = "gp-" + o.str("id").orEmpty(),
                title = o.str("title").orEmpty(),
                thumbUrl = images["fixed_width_small_still"].obj().str("url") ?: images["fixed_width"].obj().str("url") ?: full,
                fullUrl = full,
                isGif = true,
                source = "GIPHY",
                pageUrl = o.str("url").orEmpty(),
                credit = "Powered by GIPHY",
            )
        }
    }

    private fun tenor(q: String, adultAllowed: Boolean): List<WebPicture> {
        val key = key(KEY_TENOR)
        val base = if (q.isBlank()) "https://tenor.googleapis.com/v2/featured" else "https://tenor.googleapis.com/v2/search"
        val url = base.toHttpUrl().newBuilder()
            .apply { if (q.isNotBlank()) addQueryParameter("q", q) }
            .addQueryParameter("key", key)
            .addQueryParameter("client_key", "weaverse")
            .addQueryParameter("limit", "30")
            .addQueryParameter(
                "contentfilter",
                when {
                    adultAllowed -> "off"
                    SocialContentPolicy.open -> "low"
                    else -> "medium"
                },
            )
            .addQueryParameter("media_filter", "gif,tinygif,nanogifpreview")
            .build()
        return getJson(url.toString()).obj()["results"].arr().mapNotNull { item ->
            val o = item.obj()
            val formats = o["media_formats"].obj()
            val full = formats["gif"].obj().str("url") ?: return@mapNotNull null
            WebPicture(
                id = "tn-" + o.str("id").orEmpty(),
                title = o.str("content_description").orEmpty(),
                thumbUrl = formats["nanogifpreview"].obj().str("url") ?: formats["tinygif"].obj().str("url") ?: full,
                fullUrl = full,
                isGif = true,
                source = "Tenor",
                pageUrl = o.str("itemurl").orEmpty(),
                credit = "Via Tenor",
            )
        }
    }

    private fun google(q: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://www.googleapis.com/customsearch/v1".toHttpUrl().newBuilder()
            .addQueryParameter("key", key(KEY_GOOGLE))
            .addQueryParameter("cx", key(KEY_GOOGLE_CX))
            .addQueryParameter("q", q)
            .addQueryParameter("searchType", "image")
            .addQueryParameter("safe", if (adultAllowed) "off" else "active")
            .addQueryParameter("num", "10")
            .apply { if (gifs) addQueryParameter("fileType", "gif") }
            .build()
        return getJson(url.toString()).obj()["items"].arr().mapNotNull { item ->
            val o = item.obj()
            val full = o.str("link") ?: return@mapNotNull null
            val image = o["image"].obj()
            WebPicture(
                id = "gg-" + full.hashCode(),
                title = o.str("title").orEmpty(),
                thumbUrl = image.str("thumbnailLink") ?: full,
                fullUrl = full,
                isGif = o.str("mime") == "image/gif" || full.substringBefore('?').endsWith(".gif", true),
                source = "Google",
                pageUrl = image.str("contextLink").orEmpty(),
                credit = o.str("displayLink").orEmpty(),
                description = o.str("snippet").orEmpty(),
            )
        }
    }

    // ------------------------------------------------------------- saving

    /**
     * Downloads a picked result into the Pictures library (category "Web", tagged with its
     * source and the search words) so it can be attached and reused offline.
     */
    suspend fun download(picture: WebPicture, query: String, acceptBytes: suspend (ByteArray) -> Boolean = { true }): MediaEntity = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(picture.fullUrl).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Download failed (${response.code})" }
            val body = response.body ?: error("Empty download")
            val length = body.contentLength()
            require(length <= MAX_DOWNLOAD_BYTES) { "That file is too large to add." }
            val bytes = body.bytes()
            require(bytes.size <= MAX_DOWNLOAD_BYTES) { "That file is too large to add." }
            val signature = when {
                bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF8") -> "image/gif"
                bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
                bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                else -> ""
            }
            require(signature.isNotBlank()) { "The result was not a supported image." }
            require(acceptBytes(bytes)) { "This image repeats recent media." }
            val mime = signature
            val ext = when {
                mime.contains("gif") -> "gif"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val id = UUID.randomUUID().toString()
            val media = mediaRepository.importFromBytes(bytes, id = id, fileName = "$id.$ext", mimeType = mime)
            val saved = media.copy(
                displayName = picture.title.ifBlank { query.ifBlank { picture.source } }.take(120),
                category = "Web",
                tags = listOf(picture.source, query, if (picture.isGif) "gif" else "", if (picture.adult) "source_adult" else "", picture.credit, picture.pageUrl, "origin_url=${picture.fullUrl}")
                    .filter { it.isNotBlank() }.joinToString(", "),
                sourceUrl = picture.pageUrl,
                sourceSite = picture.source,
                sourceCredit = picture.credit,
                checksum = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) },
            )
            db.mediaDao().upsert(saved)
            saved
        }
    }

    // ------------------------------------------------------------ helpers

    private fun getJson(url: String): JsonElement {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            return json.parseToJsonElement(response.body?.string().orEmpty())
        }
    }

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.arr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private val previewRotation = java.util.concurrent.atomic.AtomicInteger()
        private val ADULT_PREVIEW_SITES = listOf("onlyfans.com", "pornhub.com", "redgifs.com", "fansly.com", "patreon.com", "civitai.red")
        private val GENERAL_PREVIEW_SITES = listOf("youtube.com", "twitch.tv", "x.com", "civitai.com")
        /** Wikimedia refuses images to clients without a descriptive agent like this one. */
        const val USER_AGENT = "android:com.ihy2ln.weaverse:v1 (Weaverse picture search)"
        private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "you", "are", "was", "his", "her", "not",
            "but", "from", "they", "have", "has", "its", "into", "out", "who", "what", "when", "meme",
            "gif", "looking", "being", "just", "like", "get", "got",
        )
        private val CIVITAI_GENERIC_WORDS = setOf(
            "image", "photo", "picture", "adult", "creator", "clearly", "editorial", "illustration",
            "candid", "search", "words", "style", "content", "public", "media",
        )
        private val ADULT_QUERY_WORDS = Regex("\\b(nude|nudity|porn|xxx|erotic|explicit|sexual|onlyfans|fansly)\\b", RegexOption.IGNORE_CASE)
    }
}
