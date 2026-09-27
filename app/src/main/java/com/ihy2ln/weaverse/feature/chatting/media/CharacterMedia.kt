package com.ihy2ln.weaverse.feature.chatting.media

import com.ihy2ln.weaverse.ai.prompt.PromptAddOns
import com.ihy2ln.weaverse.ai.prompt.PromptAgeRating
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A meme, GIF or picture a character asked to attach, as `[gif: words]` in their text. */
data class MediaTag(val kind: WebSearchKind, val query: String)

/**
 * The tag syntax characters use to attach a meme, GIF or picture to what they post, and
 * the prompt lines that teach it. Tags are stripped from the visible text.
 */
object MediaTags {
    private val tag = Regex("""\[(gif|meme|pic|picture|photo|image)\s*:\s*([^\]\n]{1,80})\]""", RegexOption.IGNORE_CASE)

    /** The text without its tags, and up to [max] tags in the order they appeared. */
    fun extract(text: String, max: Int = 2): Pair<String, List<MediaTag>> {
        val tags = tag.findAll(text).take(max).map { match ->
            val kind = when (match.groupValues[1].lowercase()) {
                "gif" -> WebSearchKind.Gifs
                "meme" -> WebSearchKind.Memes
                else -> WebSearchKind.Pictures
            }
            MediaTag(kind, match.groupValues[2].trim().trim('"'))
        }.filter { it.query.isNotBlank() }.toList()
        val clean = tag.replace(text, "").replace(Regex("[ \\t]{2,}"), " ").lines()
            .map { it.trimEnd() }.joinToString("\n").trim()
        return clean to tags
    }

    /** Removes tags from text that is still streaming in, including a half-typed one at the end. */
    fun hideWhileStreaming(text: String): String =
        tag.replace(text, "").replace(Regex("""\[(gif|meme|pic|picture|photo|image)\s*:[^\]\n]*$""", RegexOption.IGNORE_CASE), "").trimEnd()

    /** The instruction every social and chat prompt carries. */
    const val PROMPT: String =
        "People can attach a meme, GIF or photo the way they would on a real app: end their text " +
            "with [gif: a few search words], [meme: a few search words] or [pic: a few search words] " +
            "(for example [gif: facepalm] or [meme: this is fine dog]). Use it now and then when it " +
            "fits the person and the moment — reactions, jokes, showing something off — not on " +
            "every line. At most one per message. The tag is replaced by the picture."
}

/**
 * How open the characters' social posting is, from the app's Age rating setting. Like X,
 * anything legal goes at the higher ratings; the lower ones keep the feed tamer.
 */
object SocialContentPolicy {
    val rating: PromptAgeRating get() = PromptAddOns.ageRating

    /** R and above: the X-style open feed. */
    val open: Boolean get() = rating.ordinal >= PromptAgeRating.R.ordinal

    /** NC-17 and X: explicit adult content, and web search without safe-search filters. */
    val explicit: Boolean get() = rating.allowsMatureBlocks

    fun prompt(): String = when {
        explicit ->
            "Content: this is an uncensored, adults-only social network, like X. People post whatever " +
                "they really would — sexual and explicit content between adults, nudity, porn talk and " +
                "thirst posts, crude and offensive humor, slurs and bigoted views if that is who the " +
                "person is, politics, religion, conspiracy theories, drugs, violence, hot takes and " +
                "flame wars. Nothing is softened, moralized or disclaimed; nobody breaks character to " +
                "lecture. The only hard limits are what would be illegal: no sexual content involving " +
                "anyone under 18 or anyone who seems underage, no sexual content about real people, no " +
                "real people's private information, and no genuine threats or calls for violence " +
                "against real people or groups."
        open ->
            "Content: an open social network, like X. People post what they really think — politics, " +
                "religion, crude and offensive humor, controversial or bigoted opinions if that is who " +
                "the person is, drugs, violence, nudity and sexual innuendo — without softening, " +
                "moralizing or disclaimers. Keep sex acts non-explicit. Hard limits: nothing illegal, " +
                "nothing sexual involving anyone under 18, no real people's private information, no " +
                "genuine threats against real people or groups."
        else ->
            "Content: keep posts to the app's ${rating.label} rating — ${rating.instruction}"
    }
}

/**
 * Turns a character's `[gif: …]` / `[meme: …]` / `[pic: …]` tag into a real picture:
 * a close match already in the Pictures library first, otherwise a web search (one of
 * the top few results, so repeats vary), downloaded into the library.
 */
@Singleton
class CharacterMediaFetcher @Inject constructor(
    private val db: WeaverseDatabase,
    private val web: WebPictureSearch,
    private val reuse: SocialMediaReuse,
) {
    suspend fun fetch(tag: MediaTag, adultAllowed: Boolean = SocialContentPolicy.explicit, usedUrls: Set<String> = emptySet(), postText: String? = null, adultTopic: Boolean = false): MediaEntity? {
        val searchAdult = adultAllowed && (postText == null || adultTopic)
        val searchQuery = if (adultAllowed && adultTopic) "${tag.query} nsfw" else tag.query
        fromLibrary(tag, searchAdult, postText != null)?.let {
            if (postText == null || reuse.claimExisting(it)) return it
        }
        val results = runCatching { web.search(searchQuery, tag.kind, searchAdult).results }.getOrDefault(emptyList())
            .filter { tag.kind != WebSearchKind.Gifs || it.isGif }
            .ifEmpty { runCatching { web.search(searchQuery, WebSearchKind.All, searchAdult).results }.getOrDefault(emptyList())
                .filter { tag.kind != WebSearchKind.Gifs || it.isGif } }
            .filterNot { it.fullUrl in usedUrls || it.pageUrl in usedUrls }
            .filter { !it.adult || AdultMediaSources.adultTagsAreEligible(it.title + " " + it.description) }
        val ranked = if (postText == null) results else results
            .map { it to SocialMediaMatch.score(it, tag.query, postText) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        for (pick in ranked.take(if (postText == null) TOP_RESULTS else 24)) {
            val media = downloadCandidate(pick, tag.query, postText != null)
            if (media != null) return media
        }
        return null
    }

    /** An indexed public item for an attributed bot reshare. A page URL is required. */
    suspend fun fetchPublic(tag: MediaTag, adultAllowed: Boolean, usedUrls: Set<String>, postText: String = tag.query, adultTopic: Boolean = false): Pair<MediaEntity, WebPicture>? {
        val searchAdult = adultAllowed && adultTopic
        val searchQuery = if (searchAdult) "${tag.query} nsfw" else tag.query
        val indexed = runCatching { web.publicPreviews(searchQuery, adultAllowed, adultTopic, tag.kind == WebSearchKind.Gifs).results }.getOrDefault(emptyList())
        val results = (indexed + runCatching { web.search(searchQuery, tag.kind, searchAdult).results }.getOrDefault(emptyList()))
            .distinctBy { it.fullUrl }
            .filter { tag.kind != WebSearchKind.Gifs || it.isGif }
            .filter { it.pageUrl.startsWith("https://") && it.fullUrl !in usedUrls && it.pageUrl !in usedUrls }
            .filter { !it.adult || AdultMediaSources.adultTagsAreEligible(it.title + " " + it.description) }
            .map { it to SocialMediaMatch.score(it, tag.query, postText) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        for (pick in results.take(24)) {
            downloadCandidate(pick, tag.query, true)?.let { return it to pick }
        }
        return null
    }

    private suspend fun downloadCandidate(pick: WebPicture, query: String, social: Boolean): MediaEntity? {
        if (social && !reuse.reserve(pick)) return null
        var acceptedBytes: ByteArray? = null
        val saved = runCatching { web.download(pick, query) { bytes ->
            val allowed = !social || reuse.accept(bytes)
            if (allowed && social) acceptedBytes = bytes
            allowed
        } }.getOrNull()
        if (saved == null && social) {
            acceptedBytes?.let { reuse.releaseBytes(it) }
            reuse.release(pick)
        }
        return saved
    }

    suspend fun claimLocalForSocial(media: MediaEntity): Boolean = reuse.claimExisting(media)

    private suspend fun fromLibrary(tag: MediaTag, adultAllowed: Boolean, social: Boolean): MediaEntity? {
        val words = tag.query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        if (words.isEmpty()) return null
        val recentIds = if (social) db.socialDao().observeAllPosts().first().take(150)
            .flatMap { it.mediaId.orEmpty().split(',') }.toSet() else emptySet()
        return db.mediaDao().observeAll().first()
            .filter { it.type == "image" }
            .filter { adultAllowed || "source_adult" !in it.tags }
            .filter { tag.kind != WebSearchKind.Gifs || it.mimeType.contains("gif") }
            .filter { !social || it.sourceSite.isBlank() }
            .filter { it.id !in recentIds }
            .filter { media ->
                val hay = listOf(media.displayName, media.tags).joinToString(" ").lowercase()
                words.all { hay.contains(it) }
            }
            .randomOrNull()
    }

    companion object {
        private const val TOP_RESULTS = 10
    }
}
