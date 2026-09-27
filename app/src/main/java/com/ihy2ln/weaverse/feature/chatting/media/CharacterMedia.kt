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
) {
    suspend fun fetch(tag: MediaTag, adultAllowed: Boolean = SocialContentPolicy.explicit, usedUrls: Set<String> = emptySet()): MediaEntity? {
        fromLibrary(tag, adultAllowed)?.let { return it }
        val results = runCatching { web.search(tag.query, tag.kind, adultAllowed).results }.getOrDefault(emptyList())
            .filter { tag.kind != WebSearchKind.Gifs || it.isGif }
            .ifEmpty { runCatching { web.search(tag.query, WebSearchKind.All, adultAllowed).results }.getOrDefault(emptyList()) }
            .filterNot { it.fullUrl in usedUrls }
        for (pick in results.take(TOP_RESULTS).shuffled()) {
            runCatching { web.download(pick, tag.query) }.getOrNull()?.let { return it }
        }
        val broad = when {
            tag.kind == WebSearchKind.Gifs -> "reaction gif"
            tag.query.contains("adult", true) && adultAllowed -> "adult nude photography"
            tag.query.contains("game", true) -> "video game screenshot"
            tag.query.contains("market", true) || tag.query.contains("invest", true) -> "stock market chart"
            tag.query.contains("animal", true) || tag.query.contains("pet", true) -> "pet animal photo"
            tag.query.contains("travel", true) || tag.query.contains("vacation", true) -> "travel destination"
            else -> tag.query.split(' ').take(3).joinToString(" ")
        }
        if (broad != tag.query) {
            runCatching { web.search(broad, if (tag.kind == WebSearchKind.Gifs) WebSearchKind.Gifs else WebSearchKind.Pictures, adultAllowed).results }
                .getOrDefault(emptyList()).filterNot { it.fullUrl in usedUrls }
                .take(TOP_RESULTS).forEach { pick ->
                    runCatching { web.download(pick, broad) }.getOrNull()?.let { return it }
                }
        }
        return null
    }

    /** An indexed public item for an attributed bot reshare. A page URL is required. */
    suspend fun fetchPublic(tag: MediaTag, adultAllowed: Boolean, usedUrls: Set<String>): Pair<MediaEntity, WebPicture>? {
        val creatorQuery = if (adultAllowed && web.key(KEY_BRAVE).isNotBlank())
            runCatching { web.search("${tag.query} site:onlyfans.com OR site:patreon.com OR site:x.com", tag.kind, true).results }
                .getOrDefault(emptyList()) else emptyList()
        val results = (creatorQuery + runCatching { web.search(tag.query, tag.kind, adultAllowed).results }.getOrDefault(emptyList()))
            .distinctBy { it.fullUrl }
            .filter { it.pageUrl.startsWith("https://") && it.fullUrl !in usedUrls && it.pageUrl !in usedUrls }
        for (pick in results.take(10).shuffled()) {
            runCatching { web.download(pick, tag.query) }.getOrNull()?.let { return it to pick }
        }
        return null
    }

    private suspend fun fromLibrary(tag: MediaTag, adultAllowed: Boolean): MediaEntity? {
        val words = tag.query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        if (words.isEmpty()) return null
        return db.mediaDao().observeAll().first()
            .filter { it.type == "image" }
            .filter { adultAllowed || "source_adult" !in it.tags }
            .filter { tag.kind != WebSearchKind.Gifs || it.mimeType.contains("gif") }
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
