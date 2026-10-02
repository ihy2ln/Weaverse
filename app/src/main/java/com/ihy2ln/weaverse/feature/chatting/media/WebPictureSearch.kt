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
    Mastodon("Mastodon", emptyList()),
    Lemmy("Lemmy", emptyList()),
    Reddit("Reddit", emptyList()),
    NineGag("9GAG", emptyList()),
    Giphy("GIPHY", listOf(KEY_GIPHY)),
    Tenor("Tenor", listOf(KEY_TENOR)),
    Google("Google Images", listOf(KEY_GOOGLE, KEY_GOOGLE_CX)),
    Brave("Brave Image Search", listOf(KEY_BRAVE)),
    Civitai("Civitai", emptyList()),
    Gelbooru("Gelbooru", emptyList()),
    Danbooru("Danbooru", emptyList()),
}

const val KEY_GIPHY = "giphy"
const val KEY_TENOR = "tenor"
const val KEY_GOOGLE = "google_cse"
const val KEY_GOOGLE_CX = "google_cse_cx"
const val KEY_BRAVE = "brave_image_search"
const val KEY_CIVITAI = "civitai"
const val KEY_GELBOORU_USER = "gelbooru_user_id"
const val KEY_GELBOORU_API = "gelbooru_api_key"

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

    suspend fun gelbooruStatus(adultAllowed: Boolean): String = withContext(Dispatchers.IO) {
        if (!adultAllowed) return@withContext "WeaverSocial 18+ is off"
        runCatching { "Gelbooru connected · ${gelbooru("beach nude", false).size} matching preview(s)" }
            .getOrElse { "Gelbooru unavailable: ${it.message.orEmpty().take(90)}" }
    }

    /** Public indexed previews. Source URLs are checked against the requested site. */
    suspend fun publicPreviews(query: String, adultAllowed: Boolean, adultTopic: Boolean = false, gifs: Boolean = false): SearchOutcome = coroutineScope {
        if (key(KEY_BRAVE).isBlank()) return@coroutineScope SearchOutcome(emptyList(), emptyList(), emptyList())
        val offset = previewRotation.getAndIncrement()
        val adultSearch = adultAllowed && adultTopic
        val selected = if (adultSearch) AdultMediaSources.choose(offset, gifs)
            else (0 until 2).map { AdultMediaSources.Site(GENERAL_PREVIEW_SITES[(offset + it).mod(GENERAL_PREVIEW_SITES.size)], "Public media") }
        val jobs = selected.flatMap { site ->
            val q = "${site.query} ${query.split(' ').take(5).joinToString(" ")}"
            val imageJob = "${site.domain} images" to async(Dispatchers.IO) {
                runCatching { brave(q, gifs, adultSearch).filter { AdultMediaSources.belongsTo(site, it.pageUrl) }
                    .map { it.copy(source = site.label, adult = adultSearch || it.adult) } }
            }
            val videoJobs = if (gifs) emptyList() else listOf("${site.domain} videos" to async(Dispatchers.IO) {
                runCatching { braveVideos(q, adultSearch).filter { AdultMediaSources.belongsTo(site, it.pageUrl) }
                    .map { it.copy(source = site.label, adult = adultSearch || it.adult) } }
            })
            listOf(imageJob) + videoJobs
        }
        val results = jobs.map { it.first to it.second.await() }
        val lists = results.map { it.second.getOrDefault(emptyList()).iterator() }
        val merged = buildList {
            while (lists.any { it.hasNext() }) lists.forEach { if (it.hasNext()) add(it.next()) }
        }
        SearchOutcome(
            merged.distinctBy { it.fullUrl },
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
        val sources = sourcesFor(kind).filter { enabled(it) &&
            (it !in setOf(WebSource.Gelbooru, WebSource.Danbooru) ||
                (adultAllowed && ADULT_QUERY_WORDS.containsMatchIn(q))) }
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
        // Mastodon and Lemmy need no key, so GIFs turn up even before GIPHY or Tenor is set up.
        WebSearchKind.Gifs -> listOf(WebSource.Gelbooru, WebSource.Danbooru, WebSource.Brave, WebSource.Giphy, WebSource.Tenor, WebSource.Reddit, WebSource.NineGag, WebSource.Mastodon, WebSource.Lemmy, WebSource.Openverse, WebSource.Google)
        // Reddit refuses anonymous API calls, so it is read through its public RSS feeds.
        WebSearchKind.Memes -> listOf(WebSource.Brave, WebSource.Imgflip, WebSource.Giphy, WebSource.Reddit, WebSource.NineGag, WebSource.Lemmy, WebSource.Openverse, WebSource.Google)
        WebSearchKind.Pictures -> listOf(WebSource.Civitai, WebSource.Gelbooru, WebSource.Danbooru, WebSource.Brave, WebSource.Reddit, WebSource.Mastodon, WebSource.Lemmy, WebSource.Openverse, WebSource.Commons, WebSource.Google)
        WebSearchKind.All -> WebSource.entries
    }

    private fun fetch(source: WebSource, q: String, kind: WebSearchKind, adultAllowed: Boolean): List<WebPicture> {
        val gifs = kind == WebSearchKind.Gifs
        return when (source) {
            WebSource.Openverse -> openverse(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Commons -> commons(q, gifs)
            WebSource.Imgflip -> imgflip(q)
            WebSource.Mastodon -> mastodon(q, gifs, adultAllowed)
            WebSource.Reddit -> reddit(q, kind)
            WebSource.NineGag -> nineGag(q, gifs, adultAllowed)
            WebSource.Lemmy -> lemmy(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Giphy -> giphy(q)
            WebSource.Tenor -> tenor(q, adultAllowed)
            WebSource.Google -> google(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Brave -> brave(if (kind == WebSearchKind.Memes) "$q meme".trim() else q, gifs, adultAllowed)
            WebSource.Civitai -> if (gifs || kind == WebSearchKind.Memes) emptyList() else civitai(q, adultAllowed)
            WebSource.Gelbooru -> if (kind == WebSearchKind.Memes) emptyList() else gelbooru(q, gifs)
            WebSource.Danbooru -> if (kind == WebSearchKind.Memes) emptyList() else danbooru(q, gifs)
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
            if (!AdultMediaSources.adultTagsAreEligible(prompt)) return@mapNotNull null
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

    /** Gelbooru's documented read-only DAPI. Anonymous access can be throttled by the site. */
    private fun gelbooru(q: String, gifs: Boolean): List<WebPicture> {
        val lower = q.lowercase()
        val hints = listOf(
            "blonde" to "blonde_hair", "brunette" to "brown_hair", "redhead" to "red_hair",
            "beach" to "beach", "lingerie" to "lingerie", "cosplay" to "cosplay",
            "tattoo" to "tattoo", "elf" to "elf", "catgirl" to "cat_girl",
        )
        val subject = hints.firstOrNull { Regex("\\b${it.first}\\b").containsMatchIn(lower) }?.second
            ?: lower.split(Regex("[^a-z0-9]+"))
                .firstOrNull { it.length >= 4 && it !in STOP_WORDS && it !in CIVITAI_GENERIC_WORDS &&
                    it !in setOf("nude", "nudity", "porn", "explicit", "sexual", "erotic", "onlyfans", "fansly") }
            ?: return emptyList()
        val url = "https://gelbooru.com/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("page", "dapi").addQueryParameter("s", "post")
            .addQueryParameter("q", "index").addQueryParameter("json", "1")
            .addQueryParameter("limit", "80").addQueryParameter("pid", "0")
            .addQueryParameter("tags", "rating:explicit $subject")
            .apply {
                if (key(KEY_GELBOORU_USER).isNotBlank() && key(KEY_GELBOORU_API).isNotBlank()) {
                    addQueryParameter("user_id", key(KEY_GELBOORU_USER))
                    addQueryParameter("api_key", key(KEY_GELBOORU_API))
                }
            }.build()
        val root = getJson(url.toString()).obj()
        return root["post"].arr().mapNotNull { item ->
            val post = item.obj()
            val id = post.str("id") ?: return@mapNotNull null
            val tags = post.str("tags").orEmpty()
            if (!AdultMediaSources.adultTagsAreEligible(tags)) return@mapNotNull null
            if (post.str("rating")?.lowercase() !in setOf("explicit", "e")) return@mapNotNull null
            val file = post.str("file_url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val gif = file.substringBefore('?').endsWith(".gif", true)
            if (gifs && !gif) return@mapNotNull null
            if (!gif && !Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
                    .containsMatchIn(file.substringBefore('?'))) return@mapNotNull null
            val page = "https://gelbooru.com/index.php?page=post&s=view&id=$id"
            WebPicture(
                id = "gel-$id", title = tags.replace('_', ' ').take(140),
                thumbUrl = post.str("preview_url") ?: post.str("sample_url") ?: file,
                fullUrl = file, isGif = gif, source = "Gelbooru",
                pageUrl = page, credit = post.str("owner")?.let { "@$it on Gelbooru" } ?: "Gelbooru uploader",
                adult = true, description = tags.replace('_', ' ').take(1200),
            )
        }
    }

    /** Danbooru's public posts JSON; no login or paid search key is required. */
    private fun danbooru(q: String, gifs: Boolean): List<WebPicture> {
        val lower = q.lowercase()
        val subject = listOf(
            "blonde" to "blonde_hair", "brunette" to "brown_hair", "redhead" to "red_hair",
            "beach" to "beach", "lingerie" to "lingerie", "cosplay" to "cosplay",
            "tattoo" to "tattoo", "elf" to "elf", "catgirl" to "cat_girl",
        ).firstOrNull { Regex("\\b${it.first}\\b").containsMatchIn(lower) }?.second
            ?: lower.split(Regex("[^a-z0-9]+"))
                .firstOrNull { it.length >= 4 && it !in STOP_WORDS && it !in CIVITAI_GENERIC_WORDS &&
                    it !in setOf("nude", "nudity", "porn", "explicit", "sexual", "erotic", "nsfw", "onlyfans", "fansly") }
            ?: return emptyList()
        val url = "https://danbooru.donmai.us/posts.json".toHttpUrl().newBuilder()
            .addQueryParameter("tags", "rating:explicit $subject")
            .addQueryParameter("limit", "80")
            .addQueryParameter("random", "true")
            .build()
        return getJson(url.toString()).arr().mapNotNull { item ->
            val post = item.obj()
            val id = post.str("id") ?: return@mapNotNull null
            val tags = post.str("tag_string").orEmpty()
            if (!AdultMediaSources.adultTagsAreEligible(tags)) return@mapNotNull null
            if (post.str("rating")?.lowercase() !in setOf("e", "explicit")) return@mapNotNull null
            val file = post.str("file_url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val gif = file.substringBefore('?').endsWith(".gif", true)
            if (gifs && !gif) return@mapNotNull null
            if (!gif && !Regex("\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)
                    .containsMatchIn(file.substringBefore('?'))) return@mapNotNull null
            WebPicture(
                id = "dan-$id", title = tags.replace('_', ' ').take(140),
                thumbUrl = post.str("preview_file_url") ?: post.str("large_file_url") ?: file,
                fullUrl = file, isGif = gif, source = "Danbooru",
                pageUrl = "https://danbooru.donmai.us/posts/$id",
                credit = post.str("uploader_name")?.let { "@$it on Danbooru" } ?: "Danbooru uploader",
                adult = true, description = tags.replace('_', ' ').take(1200),
            )
        }
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

    /**
     * Media posted under a matching hashtag on public Mastodon servers. Their GIFs are
     * "gifv" loops (silent MP4), which the feed plays like a GIF. No account needed.
     */
    private fun mastodon(q: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        val tags = q.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS && it !in CIVITAI_GENERIC_WORDS }
            .distinct().take(2)
        if (tags.isEmpty()) return emptyList()
        return tags.flatMap { tag ->
            MASTODON_SERVERS.take(2).flatMap { server ->
                runCatching { getJson("https://$server/api/v1/timelines/tag/$tag?only_media=true&limit=40").arr() }
                    .getOrDefault(JsonArray(emptyList()))
            }
        }.flatMap { status -> mastodonMedia(status.obj(), gifs, adultAllowed) }.distinctBy { it.fullUrl }
    }

    /** The pictures and GIF loops on one Mastodon status, credited to its author. */
    fun mastodonMedia(status: JsonObject, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        val sensitive = status.str("sensitive") == "true"
        if (sensitive && !adultAllowed) return emptyList()
        val page = status.str("url")?.takeIf { it.startsWith("https://") } ?: return emptyList()
        val account = status["account"].obj()
        val text = htmlToText(status.str("content").orEmpty())
        val tags = status["tags"].arr().mapNotNull { it.obj().str("name") }.joinToString(" ")
        return status["media_attachments"].arr().mapNotNull { item ->
            val m = item.obj()
            val type = m.str("type").orEmpty()
            val loop = type == "gifv"
            if (gifs && !loop) return@mapNotNull null
            if (type != "image" && !loop) return@mapNotNull null
            val full = m.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            WebPicture(
                id = "md-" + (m.str("id") ?: full.hashCode().toString()),
                title = listOf(m.str("description").orEmpty(), tags).filter { it.isNotBlank() }.joinToString(" · ").take(160)
                    .ifBlank { text.take(120) },
                thumbUrl = m.str("preview_url") ?: full,
                fullUrl = full, isGif = loop, source = "Mastodon",
                pageUrl = page,
                credit = "@" + (account.str("acct") ?: "someone") + " on Mastodon",
                adult = sensitive,
                description = listOf(text, tags, m.str("description").orEmpty()).joinToString(" ").take(1200),
            )
        }
    }

    /** Reddit search within subreddits that fit the kind, through its public RSS. */
    private fun reddit(q: String, kind: WebSearchKind): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val subs = when (kind) {
            WebSearchKind.Gifs -> REDDIT_GIF_SUBS
            WebSearchKind.Memes -> REDDIT_MEME_SUBS
            else -> REDDIT_PICTURE_SUBS
        }
        val sub = subs[redditRotation.getAndIncrement().mod(subs.size)]
        val url = "https://www.reddit.com/r/$sub/search.rss".toHttpUrl().newBuilder()
            .addQueryParameter("q", q.split(' ').take(4).joinToString(" "))
            .addQueryParameter("restrict_sr", "on").addQueryParameter("sort", "relevance").build()
        return RedditRss.parse(getText(url.toString())).mapNotNull { RedditRss.toPicture(it, kind == WebSearchKind.Gifs) }
    }

    /** 9GAG's own web feed for a tag: memes, pictures and short silent clips. */
    private fun nineGag(q: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        val tag = q.lowercase().split(Regex("[^a-z0-9]+"))
            .firstOrNull { it.length >= 3 && it !in STOP_WORDS && it !in CIVITAI_GENERIC_WORDS } ?: return emptyList()
        return nineGagPosts("https://9gag.com/v1/tag-posts/tag/$tag/type/hot", gifs, adultAllowed)
    }

    /** Posts from a 9GAG feed URL; "Animated" posts are MP4 loops that play like GIFs. */
    fun nineGagPosts(url: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> =
        getJson(url).obj()["data"].obj()["posts"].arr().mapNotNull { item ->
            val post = item.obj()
            if (post.str("nsfw") == "1" && !adultAllowed) return@mapNotNull null
            val images = post["images"].obj()
            val clip = images["image460sv"].obj()
            val duration = clip.str("duration")?.toIntOrNull() ?: 0
            val loop = post.str("type") in setOf("Animated", "Video") && duration in 1..60
            val full = if (loop) clip.str("url") else images["image700"].obj().str("url")
            if (full == null || !full.startsWith("https://") || (gifs && !loop)) return@mapNotNull null
            if (!loop && post.str("type") != "Photo") return@mapNotNull null
            val tags = post["tags"].arr().mapNotNull { it.obj().str("key") }.joinToString(" ")
            val title = post.str("title").orEmpty()
            WebPicture(
                id = "9g-" + post.str("id").orEmpty(),
                title = title.take(160),
                thumbUrl = images["image460"].obj().str("url") ?: full,
                fullUrl = full, isGif = loop, source = "9GAG",
                pageUrl = post.str("url")?.replace("http://", "https://").orEmpty(),
                credit = post["postSection"].obj().str("name")?.let { "$it on 9GAG" } ?: "9GAG",
                adult = post.str("nsfw") == "1",
                description = "$title $tags".take(1200),
            )
        }

    /** Link posts on Lemmy (a Reddit-like network) whose link is a picture or GIF. */
    private fun lemmy(q: String, gifs: Boolean, adultAllowed: Boolean): List<WebPicture> {
        if (q.isBlank()) return emptyList()
        val url = "https://lemmy.world/api/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q.split(' ').take(4).joinToString(" "))
            .addQueryParameter("type_", "Posts").addQueryParameter("sort", "TopAll")
            .addQueryParameter("limit", "40").build()
        return getJson(url.toString()).obj()["posts"].arr().mapNotNull { lemmyPicture(it.obj(), gifs, adultAllowed) }
    }

    /** The picture or GIF a Lemmy link post points at, or null when it links a web page. */
    fun lemmyPicture(view: JsonObject, gifs: Boolean, adultAllowed: Boolean): WebPicture? {
        val post = view["post"].obj()
        val nsfw = post.str("nsfw") == "true" || view["community"].obj().str("nsfw") == "true"
        if (nsfw && !adultAllowed) return null
        val link = post.str("url")?.takeIf { it.startsWith("https://") } ?: return null
        val file = link.substringBefore('?')
        val full = when {
            // Imgur's .gifv is a web page; the same name with .mp4 is the loop itself.
            file.endsWith(".gifv", true) && "imgur.com" in file -> file.dropLast(5) + ".mp4"
            Regex("\\.(gif|mp4|jpe?g|png|webp)$", RegexOption.IGNORE_CASE).containsMatchIn(file) -> link
            else -> return null
        }
        val loop = Regex("\\.(gif|mp4)$", RegexOption.IGNORE_CASE).containsMatchIn(full.substringBefore('?'))
        if (gifs && !loop) return null
        val title = post.str("name").orEmpty()
        return WebPicture(
            id = "lm-" + (post.str("id") ?: full.hashCode().toString()),
            title = title.take(160),
            thumbUrl = post.str("thumbnail_url") ?: full,
            fullUrl = full, isGif = loop, source = "Lemmy",
            pageUrl = post.str("ap_id")?.takeIf { it.startsWith("https://") } ?: link,
            credit = "c/" + (view["community"].obj().str("name") ?: "lemmy"),
            adult = nsfw,
            description = (title + " " + post.str("body").orEmpty()).take(1200),
        )
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
                // Looping GIF videos ("gifv"), only when the result was offered as a GIF.
                picture.isGif && bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "video/mp4"
                else -> ""
            }
            require(signature.isNotBlank()) { "The result was not a supported image." }
            require(acceptBytes(bytes)) { "This image repeats recent media." }
            val mime = signature
            val ext = when {
                mime.contains("gif") -> "gif"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("mp4") -> "mp4"
                else -> "jpg"
            }
            val id = UUID.randomUUID().toString()
            val media = mediaRepository.importFromBytes(bytes, id = id, fileName = "$id.$ext", mimeType = mime)
            val saved = media.copy(
                displayName = picture.title.ifBlank { query.ifBlank { picture.source } }.take(120),
                category = "Web",
                tags = listOf(picture.source, query, if (picture.isGif) "gif" else "", if (mime.startsWith("video/")) "gifv" else "", if (picture.adult) "source_adult" else "", picture.credit, picture.pageUrl, "origin_url=${picture.fullUrl}")
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

    fun htmlToText(html: String): String = html.replace(Regex("<br\\s*/?>|</p>"), "\n")
        .replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").trim()

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            return response.body?.string().orEmpty()
        }
    }

    fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            return json.parseToJsonElement(response.body?.string().orEmpty())
        }
    }

    fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    fun JsonElement?.arr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
    fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private val previewRotation = java.util.concurrent.atomic.AtomicInteger()
        private val ADULT_PREVIEW_SITES = listOf("onlyfans.com", "fansly.com", "patreon.com", "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com", "redgifs.com", "sex.com", "rule34.xxx", "gelbooru.com", "danbooru.donmai.us", "civitai.red")
        private val redditRotation = java.util.concurrent.atomic.AtomicInteger()
        private val REDDIT_GIF_SUBS = listOf("reactiongifs", "gifs", "HighQualityGifs", "gif")
        private val REDDIT_MEME_SUBS = listOf("memes", "funny", "me_irl", "wholesomememes")
        private val REDDIT_PICTURE_SUBS = listOf("pics", "aww", "itookapicture", "EarthPorn", "FoodPorn")
        val MASTODON_SERVERS = listOf("mastodon.social", "mstdn.social", "mastodon.world")
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
        private val ADULT_QUERY_WORDS = Regex("\\b(nude|nudity|porn|xxx|erotic|explicit|sexual|nsfw|onlyfans|fansly|fetish|lingerie)\\b", RegexOption.IGNORE_CASE)
    }
}
