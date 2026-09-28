package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.feature.chatting.media.RedditRss
import com.ihy2ln.weaverse.feature.chatting.media.WebPicture
import com.ihy2ln.weaverse.feature.chatting.media.WebPictureSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** One real public post found on the open web, to be shared into WeaverSocial with credit. */
data class RealWebItem(
    /** Stable key so the same post is never shared twice. */
    val url: String,
    /** "Reddit", "9GAG", "Mastodon", "Bluesky", "Lemmy", "Hacker News". */
    val site: String,
    /** "@someone on Mastodon", "c/cats", … */
    val credit: String,
    /** The original post's words (or headline), shown as the quoted source card. */
    val text: String,
    val topic: String,
    /** Its picture or GIF, downloaded and attached when present. */
    val media: WebPicture? = null,
    val adult: Boolean = false,
)

/**
 * Pulls real, public posts from social sites that allow reading without an account —
 * Reddit's subreddit feeds, 9GAG, Mastodon hashtag timelines, Bluesky's Discover feed,
 * Lemmy's hot posts and Hacker News —
 * so WeaverSocial's made-up people have real things to share, like a real timeline.
 * Nothing is ever posted back to those sites.
 */
@Singleton
class RealWebFeed @Inject constructor(private val web: WebPictureSearch) {

    suspend fun fetch(topics: List<String>, adultAllowed: Boolean, seen: Set<String>): List<RealWebItem> = coroutineScope {
        val tags = topics.flatMap { TOPIC_HASHTAGS[it].orEmpty().shuffled().take(1).map { tag -> it to tag } }
        val jobs = tags.map { (topic, tag) ->
            async(Dispatchers.IO) { runCatching { mastodon(topic, tag, adultAllowed) }.getOrDefault(emptyList()) }
        } + topics.take(2).map { topic ->
            // Reddit rate-limits anonymous readers, so two subreddits per refresh at most.
            async(Dispatchers.IO) { runCatching { reddit(topic) }.getOrDefault(emptyList()) }
        } + listOf(
            async(Dispatchers.IO) { runCatching { nineGag(adultAllowed) }.getOrDefault(emptyList()) },
            async(Dispatchers.IO) { runCatching { bluesky(adultAllowed) }.getOrDefault(emptyList()) },
            async(Dispatchers.IO) { runCatching { lemmy(adultAllowed) }.getOrDefault(emptyList()) },
            async(Dispatchers.IO) { runCatching { hackerNews() }.getOrDefault(emptyList()) },
        )
        val lists = jobs.awaitAll().map { list -> list.filter { it.url !in seen }.shuffled() }
        // Round-robin so no one site fills the feed.
        buildList {
            val iterators = lists.map { it.iterator() }
            while (iterators.any { it.hasNext() }) iterators.forEach { if (it.hasNext()) add(it.next()) }
        }.distinctBy { it.url }
    }

    private fun mastodon(topic: String, tag: String, adultAllowed: Boolean): List<RealWebItem> {
        val server = WebPictureSearch.MASTODON_SERVERS.random()
        val statuses = web.getJson("https://$server/api/v1/timelines/tag/$tag?limit=30").arr()
        return statuses.mapNotNull { element ->
            val status = element.obj()
            if (status.str("language")?.startsWith("en") == false) return@mapNotNull null
            val sensitive = status.str("sensitive") == "true"
            if (sensitive && !adultAllowed) return@mapNotNull null
            val url = status.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val text = web.htmlToText(status.str("content").orEmpty())
            if (text.length < 20) return@mapNotNull null
            val media = web.mastodonMedia(status, gifs = false, adultAllowed = adultAllowed).firstOrNull()
                ?: web.mastodonMedia(status, gifs = true, adultAllowed = adultAllowed).firstOrNull()
            RealWebItem(url, "Mastodon", "@" + (status["account"].obj().str("acct") ?: "someone"),
                text.take(TEXT_CHARS), topic, media, sensitive)
        }
    }

    private fun reddit(topic: String): List<RealWebItem> {
        val sub = TOPIC_SUBREDDITS[topic].orEmpty().ifEmpty { TOPIC_SUBREDDITS.getValue("memes") }.random()
        return RedditRss.parse(web.getText("https://www.reddit.com/r/$sub/hot/.rss")).mapNotNull { entry ->
            // Moderator stickies aren't what people share.
            if (entry.author.equals("AutoModerator", true) || entry.title.isBlank()) return@mapNotNull null
            RealWebItem(
                url = entry.url, site = "Reddit",
                credit = listOf("r/${entry.subreddit.ifBlank { sub }}", entry.author.takeIf { it.isNotBlank() }?.let { "u/$it" })
                    .filterNotNull().joinToString(" · "),
                text = (entry.title + entry.selfText.takeIf { it.isNotBlank() }?.let { "\n" + it }.orEmpty()).take(TEXT_CHARS),
                topic = topic,
                media = RedditRss.toPicture(entry, gifsOnly = false),
            )
        }
    }

    private fun nineGag(adultAllowed: Boolean): List<RealWebItem> =
        web.nineGagPosts("https://9gag.com/v1/group-posts/group/default/type/hot", gifs = false, adultAllowed = adultAllowed)
            .filter { it.pageUrl.startsWith("https://") }
            .map { picture ->
                RealWebItem(picture.pageUrl, "9GAG", picture.credit.removeSuffix(" on 9GAG"), picture.title,
                    guessTopic(picture.description), picture, picture.adult)
            }

    private fun bluesky(adultAllowed: Boolean): List<RealWebItem> {
        val feed = web.getJson(
            "https://public.api.bsky.app/xrpc/app.bsky.feed.getFeed?limit=50&feed=" +
                URLEncoder.encode(BLUESKY_DISCOVER, "UTF-8"),
        ).obj()["feed"].arr()
        return feed.mapNotNull { element ->
            val post = element.obj()["post"].obj()
            val record = post["record"].obj()
            val langs = record["langs"].arr().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (langs.isNotEmpty() && langs.none { it.startsWith("en") }) return@mapNotNull null
            val labels = post["labels"].arr().mapNotNull { it.obj().str("val") }
            val adult = labels.any { it in setOf("porn", "sexual", "nudity", "graphic-media") }
            if (adult && !adultAllowed) return@mapNotNull null
            val text = record.str("text").orEmpty()
            val author = post["author"].obj()
            val handle = author.str("handle") ?: return@mapNotNull null
            val rkey = post.str("uri")?.substringAfterLast('/') ?: return@mapNotNull null
            val url = "https://bsky.app/profile/$handle/post/$rkey"
            val embed = post["embed"].obj()
            val images = (embed["images"] as? JsonArray) ?: embed["media"].obj()["images"].arr()
            val external = embed["external"].obj()
            val image = images.firstOrNull()?.obj()
            val media = image?.str("fullsize")?.let { full ->
                WebPicture("bs-$rkey", image.str("alt").orEmpty().ifBlank { text }.take(160),
                    image.str("thumb") ?: full, full, false, "Bluesky", url, "@$handle on Bluesky",
                    adult = adult, description = text)
            } ?: external.str("uri")?.takeIf { it.contains("media.tenor.com") && it.contains(".gif") }?.let { gif ->
                // Bluesky's GIF picker posts Tenor GIFs as link cards.
                WebPicture("bs-$rkey", external.str("title").orEmpty().ifBlank { text }.take(160),
                    external.str("thumb") ?: gif, gif, true, "Bluesky", url, "@$handle on Bluesky",
                    adult = adult, description = text + " " + external.str("description").orEmpty())
            }
            val body = text.ifBlank { external.str("title").orEmpty() }
            if (body.length < 12 && media == null) return@mapNotNull null
            RealWebItem(url, "Bluesky", "@$handle", body.take(TEXT_CHARS), guessTopic(body), media, adult)
        }
    }

    private fun lemmy(adultAllowed: Boolean): List<RealWebItem> {
        val posts = web.getJson("https://lemmy.world/api/v3/post/list?sort=Hot&type_=All&limit=40").obj()["posts"].arr()
        return posts.mapNotNull { element ->
            val view = element.obj()
            val post = view["post"].obj()
            val nsfw = post.str("nsfw") == "true" || view["community"].obj().str("nsfw") == "true"
            if (nsfw && !adultAllowed) return@mapNotNull null
            val page = post.str("ap_id")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val title = post.str("name") ?: return@mapNotNull null
            val community = view["community"].obj().str("name") ?: "lemmy"
            val media = web.lemmyPicture(view, gifs = false, adultAllowed = adultAllowed)
            RealWebItem(page, "Lemmy", "c/$community", (title + post.str("body")?.let { "\n" + it }.orEmpty()).take(TEXT_CHARS),
                guessTopic("$community $title"), media, nsfw)
        }
    }

    private fun hackerNews(): List<RealWebItem> {
        val hits = web.getJson("https://hn.algolia.com/api/v1/search?tags=front_page&hitsPerPage=30").obj()["hits"].arr()
        return hits.mapNotNull { element ->
            val hit = element.obj()
            val id = hit.str("objectID") ?: return@mapNotNull null
            val title = hit.str("title") ?: return@mapNotNull null
            RealWebItem("https://news.ycombinator.com/item?id=$id", "Hacker News",
                hit.str("author")?.let { "by $it" }.orEmpty(), title, "tech")
        }
    }

    private fun guessTopic(text: String): String {
        val lower = text.lowercase()
        return TOPIC_WORDS.entries.firstOrNull { (_, words) -> words.any { Regex("\\b$it\\b").containsMatchIn(lower) } }?.key
            ?: "news"
    }

    private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.arr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val TEXT_CHARS = 400
        private const val BLUESKY_DISCOVER = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"

        /** WeaverSocial topics → Mastodon hashtags that reliably carry pictures and GIFs. */
        val TOPIC_HASHTAGS = mapOf(
            "games" to listOf("gaming", "videogames", "retrogaming", "nintendo", "pcgaming"),
            "pets" to listOf("catsofmastodon", "dogsofmastodon", "caturday", "pets"),
            "tech" to listOf("technology", "linux", "programming", "gadgets"),
            "science" to listOf("science", "biology", "physics"),
            "space" to listOf("space", "astronomy", "astrophotography"),
            "photography" to listOf("photography", "streetphotography", "landscapephotography"),
            "outdoors" to listOf("hiking", "nature", "birds", "birdsofmastodon"),
            "food" to listOf("food", "cooking", "baking"),
            "travel" to listOf("travel", "travelphotography"),
            "art" to listOf("art", "mastoart", "pixelart", "illustration"),
            "anime" to listOf("anime", "fanart"),
            "music" to listOf("music", "nowplaying", "vinyl"),
            "movies" to listOf("movies", "film", "tv"),
            "books" to listOf("books", "reading"),
            "sports" to listOf("football", "soccer", "nba", "f1"),
            "cars" to listOf("cars", "classiccars", "motorsport"),
            "fitness" to listOf("fitness", "running", "cycling"),
            "fashion" to listOf("fashion", "knitting", "sewing"),
            "money" to listOf("economics", "finance", "stockmarket"),
            "news" to listOf("news", "worldnews"),
            "memes" to listOf("memes", "funny", "humor", "gif"),
        )

        /** WeaverSocial topics → safe-for-work subreddits that are mostly pictures and GIFs. */
        val TOPIC_SUBREDDITS = mapOf(
            "games" to listOf("gaming", "pcmasterrace", "GamePhysics", "Minecraft"),
            "pets" to listOf("aww", "cats", "dogpictures", "AnimalsBeingDerps", "rarepuppers"),
            "tech" to listOf("technology", "gadgets", "ProgrammerHumor"),
            "science" to listOf("interestingasfuck", "Damnthatsinteresting", "oddlysatisfying"),
            "space" to listOf("spaceporn", "space"),
            "photography" to listOf("itookapicture", "pics", "EarthPorn"),
            "outdoors" to listOf("EarthPorn", "hiking", "NatureIsFuckingLit"),
            "food" to listOf("FoodPorn", "food", "Baking"),
            "travel" to listOf("travel", "CityPorn", "pics"),
            "art" to listOf("Art", "PixelArt", "drawing"),
            "anime" to listOf("anime", "Animemes"),
            "music" to listOf("Music", "WeAreTheMusicMakers"),
            "movies" to listOf("movies", "MovieDetails", "PrequelMemes"),
            "books" to listOf("books", "BookPorn"),
            "sports" to listOf("sports", "nba", "soccer", "formula1"),
            "cars" to listOf("cars", "carporn", "Autos"),
            "fitness" to listOf("GYM", "running", "bodyweightfitness"),
            "fashion" to listOf("streetwear", "OUTFITS", "malefashion"),
            "money" to listOf("wallstreetbets", "StockMarket"),
            "news" to listOf("pics", "UpliftingNews", "worldnews"),
            "memes" to listOf("memes", "funny", "me_irl", "reactiongifs", "gifs", "wholesomememes"),
        )

        private val TOPIC_WORDS = mapOf(
            "pets" to listOf("cat", "cats", "dog", "dogs", "kitten", "puppy", "pet"),
            "games" to listOf("game", "gaming", "nintendo", "steam", "playstation", "xbox"),
            "tech" to listOf("programming", "linux", "software", "ai", "tech", "technology", "programmer"),
            "space" to listOf("space", "jupiter", "mars", "nasa", "moon", "galaxy", "astronomy"),
            "science" to listOf("science", "research", "study", "archaeology", "scientists"),
            "art" to listOf("art", "painting", "artist", "illustration", "drawing"),
            "outdoors" to listOf("bird", "birds", "hiking", "trail", "garden", "nature"),
            "food" to listOf("food", "recipe", "cooking", "chips", "baking"),
            "sports" to listOf("football", "soccer", "match", "league", "nba", "goal"),
            "movies" to listOf("movie", "film", "star wars", "star trek", "hollywood"),
            "music" to listOf("music", "album", "song", "band"),
            "memes" to listOf("meme", "shitpost", "funny", "lol"),
        )
    }
}
