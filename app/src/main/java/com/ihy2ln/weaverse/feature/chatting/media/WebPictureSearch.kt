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
}

const val KEY_GIPHY = "giphy"
const val KEY_TENOR = "tenor"
const val KEY_GOOGLE = "google_cse"
const val KEY_GOOGLE_CX = "google_cse_cx"

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

    /** Results from every enabled source that fits [kind], interleaved so no one site dominates. */
    suspend fun search(query: String, kind: WebSearchKind): SearchOutcome = coroutineScope {
        val q = query.trim()
        val sources = sourcesFor(kind).filter { enabled(it) }
        val jobs = sources.map { source ->
            source to async(Dispatchers.IO) { runCatching { fetch(source, q, kind) } }
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
        WebSearchKind.Gifs -> listOf(WebSource.Giphy, WebSource.Tenor, WebSource.Openverse, WebSource.Commons, WebSource.Google)
        // Reddit is not a source: it refuses API calls without a signed-in app.
        WebSearchKind.Memes -> listOf(WebSource.Imgflip, WebSource.Giphy, WebSource.Openverse, WebSource.Google)
        WebSearchKind.Pictures -> listOf(WebSource.Openverse, WebSource.Commons, WebSource.Google)
        WebSearchKind.All -> WebSource.entries
    }

    private fun fetch(source: WebSource, q: String, kind: WebSearchKind): List<WebPicture> {
        val gifs = kind == WebSearchKind.Gifs
        return when (source) {
            WebSource.Openverse -> openverse(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs)
            WebSource.Commons -> commons(q, gifs)
            WebSource.Imgflip -> imgflip(q)
            WebSource.Giphy -> giphy(q)
            WebSource.Tenor -> tenor(q)
            WebSource.Google -> google(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs)
        }
    }

    // ------------------------------------------------------------ sources

    private fun openverse(q: String, gifs: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://api.openverse.org/v1/images/".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            // Openverse refuses page sizes above 20 without an account.
            .addQueryParameter("page_size", "20")
            .addQueryParameter("mature", if (SocialContentPolicy.explicit) "true" else "false")
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

    private fun tenor(q: String): List<WebPicture> {
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
                    SocialContentPolicy.explicit -> "off"
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

    private fun google(q: String, gifs: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://www.googleapis.com/customsearch/v1".toHttpUrl().newBuilder()
            .addQueryParameter("key", key(KEY_GOOGLE))
            .addQueryParameter("cx", key(KEY_GOOGLE_CX))
            .addQueryParameter("q", q)
            .addQueryParameter("searchType", "image")
            .addQueryParameter("safe", if (SocialContentPolicy.explicit) "off" else "active")
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
            )
        }
    }

    // ------------------------------------------------------------- saving

    /**
     * Downloads a picked result into the Pictures library (category "Web", tagged with its
     * source and the search words) so it can be attached and reused offline.
     */
    suspend fun download(picture: WebPicture, query: String): MediaEntity = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(picture.fullUrl).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Download failed (${response.code})" }
            val body = response.body ?: error("Empty download")
            val length = body.contentLength()
            require(length <= MAX_DOWNLOAD_BYTES) { "That file is too large to add." }
            val bytes = body.bytes()
            require(bytes.size <= MAX_DOWNLOAD_BYTES) { "That file is too large to add." }
            val type = response.header("Content-Type").orEmpty().substringBefore(';').trim()
            val mime = when {
                type.startsWith("image/") -> type
                picture.isGif -> "image/gif"
                picture.fullUrl.contains(".png", true) -> "image/png"
                picture.fullUrl.contains(".webp", true) -> "image/webp"
                else -> "image/jpeg"
            }
            require(mime.startsWith("image/")) { "That link isn't a picture." }
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
                tags = listOf(picture.source, query, if (picture.isGif) "gif" else "", picture.credit, picture.pageUrl)
                    .filter { it.isNotBlank() }.joinToString(", "),
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
        /** Wikimedia refuses images to clients without a descriptive agent like this one. */
        const val USER_AGENT = "android:com.ihy2ln.weaverse:v1 (Weaverse picture search)"
        private const val MAX_DOWNLOAD_BYTES = 15L * 1024 * 1024
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "you", "are", "was", "his", "her", "not",
            "but", "from", "they", "have", "has", "its", "into", "out", "who", "what", "when", "meme",
            "gif", "looking", "being", "just", "like", "get", "got",
        )
    }
}
