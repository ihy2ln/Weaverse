package com.ihy2ln.weaverse.feature.chatting.social

import com.ihy2ln.weaverse.feature.chatting.media.FeedXml
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
    /** Who posted it, as the real account: "u/name", "@handle", "Polygon". */
    val author: String = "",
    /** Its top comments (author to text), shown as replies so threads read like a forum. */
    val comments: List<Pair<String, String>> = emptyList(),
    /** Tags or categories the source gave it; checked by the adult filter too. */
    val tags: String = "",
) {
    /** The name shown as the post's author. */
    val poster: String get() = author.ifBlank { credit.substringBefore(" · ").ifBlank { site } }
}

/**
 * Pulls real, public posts from social sites that allow reading without an account —
 * Reddit's subreddit feeds, 9GAG, Mastodon hashtag timelines, Bluesky's Discover feed,
 * Lemmy's hot posts and Hacker News —
 * so WeaverSocial's made-up people have real things to share, like a real timeline.
 * Nothing is ever posted back to those sites.
 */
@Singleton
class RealWebFeed @Inject constructor(
    private val web: WebPictureSearch,
    private val reddit: RedditAccount,
) {
    private var redgifsToken: String = ""
    private var redgifsTokenUntil: Long = 0L

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
        val lists = jobs.awaitAll().map { list ->
            list.filter { it.url !in seen && eligible(it, adultAllowed) }.shuffled()
        }
        // Round-robin so no one site fills the feed.
        buildList {
            val iterators = lists.map { it.iterator() }
            while (iterators.any { it.hasNext() }) iterators.forEach { if (it.hasNext()) add(it.next()) }
        }.distinctBy { it.url }
    }

    /**
     * Real posts for the kinds of accounts the writer follows ([FeedCategory]) plus their own
     * list of accounts ([CustomFeedSource]): outlet headlines, creators' new videos, community
     * posts. Every source is tried in parallel and interleaved, like [fetch].
     */
    suspend fun fetchFollowed(
        categories: List<FeedCategory>,
        custom: List<CustomFeedSource>,
        adultAllowed: Boolean,
        seen: Set<String>,
    ): List<RealWebItem> = coroutineScope {
        val kinds = categories.filter { adultAllowed || !it.adult }
        val sources = custom.filter { adultAllowed || !it.adult }
        // Anonymous Reddit allows a request or two a minute; signed in, it's a normal API.
        var redditBudget = if (reddit.signedIn) 8 else 3
        val frontPage: List<suspend () -> List<RealWebItem>> =
            if (reddit.signedIn) listOf(suspend { redditApi("/best?limit=40", "followed", forceAdult = false, adultAllowed) }) else emptyList()
        val jobs = frontPage + kinds.flatMap { kind ->
            buildList<suspend () -> List<RealWebItem>> {
                kind.feeds.randomOrNull()?.let { (name, url) -> add { outlet(name, url, kind.topic, kind.adult) } }
                kind.youtube.shuffled().take(2).forEach { (name, id) -> add { youtube(name, id, kind.topic) } }
                if (redditBudget > 0) kind.subreddits.randomOrNull()?.let { sub ->
                    redditBudget--
                    add { subreddit(sub, kind.topic, kind.adult) }
                }
                kind.hashtags.randomOrNull()?.let { tag -> add { mastodon(kind.topic, tag, adultAllowed, forceAdult = kind.adult) } }
                kind.lemmy.forEach { server -> add { lemmy(adultAllowed, server, kind.topic).map { it.copy(adult = it.adult || kind.adult) } } }
                if (kind.blueskyAdult) add { bluesky(adultAllowed).filter { it.adult }.map { it.copy(topic = kind.topic) } }
                if (kind.redgifs) add { redgifs(kind.topic) }
                if (kind.pornhub) add { pornhub(kind.topic) }
                if (kind.eporner) add { eporner(kind.topic) }
                kind.fourChan.randomOrNull()?.let { board -> add { fourChan(board, kind.topic) } }
                kind.blueskySearch.randomOrNull()?.let { words ->
                    add { blueskySearch(words, kind.topic, adultAllowed, adultOnly = kind.adult) }
                }
                kind.lemmyCommunities.shuffled().take(2).forEach { community -> add { lemmyCommunity(community, kind.topic, adultAllowed, kind.adult) } }
            }
        } + sources.shuffled().take(MAX_CUSTOM_PER_REFRESH).mapNotNull { source ->
            val topic = if (source.adult) "adult" else "followed"
            when (source.kind) {
                CustomFeedSource.Kind.Subreddit -> if (redditBudget-- > 0) suspend { subreddit(source.value, topic, source.adult) } else null
                CustomFeedSource.Kind.Hashtag -> suspend { mastodon(topic, source.value, adultAllowed, forceAdult = source.adult) }
                CustomFeedSource.Kind.Bluesky -> suspend { blueskyAuthor(source.value, adultAllowed, topic, source.adult) }
                CustomFeedSource.Kind.Mastodon -> suspend { mastodonAccount(source.value, adultAllowed, topic, source.adult) }
                CustomFeedSource.Kind.YouTube -> suspend { youtube("", source.value, topic) }
                CustomFeedSource.Kind.Feed -> suspend { outlet("", source.value, topic, source.adult) }
                CustomFeedSource.Kind.Lemmy -> suspend { lemmyCommunity(source.value, topic, adultAllowed, source.adult) }
            }
        }
        val lists = jobs.map { job ->
            async(Dispatchers.IO) {
                runCatching { job() }
                    .onFailure { com.ihy2ln.weaverse.ai.WeaverseAiLog.i("followed source failed: ${it.javaClass.simpleName}: ${it.message}") }
                    .getOrDefault(emptyList())
            }
        }
            .awaitAll()
            .map { list ->
                list.filter { it.url !in seen && eligible(it, adultAllowed) }
                    // Newest first for outlets and creators; a little shuffle so refreshes differ.
                    .take(12).shuffled()
            }
        buildList {
            val iterators = lists.shuffled().map { it.iterator() }
            while (iterators.any { it.hasNext() }) iterators.forEach { if (it.hasNext()) add(it.next()) }
        }.distinctBy { it.url }
    }

    /** A news site or blog's RSS/Atom feed: headline, a line of summary, and its lead picture. */
    private fun outlet(name: String, url: String, topic: String, adult: Boolean): List<RealWebItem> {
        // A site's page rather than its feed (a forum, a blog's home page): use the RSS/Atom
        // feed the page advertises, the way feed readers do.
        val xml = web.getText(url).let { page ->
            if (FeedXml.parse(page).isNotEmpty()) page
            else FeedXml.discover(page, url)?.let { feed -> web.getText(feed) } ?: page
        }
        val site = name.ifBlank { FeedXml.feedName(xml).ifBlank { url.substringAfter("://").substringBefore('/').removePrefix("www.") } }
        return FeedXml.parse(xml).map { entry ->
            val media = entry.image?.let { image ->
                WebPicture("rss-" + entry.url.hashCode(), entry.title.take(160), image, image, false, site, entry.url,
                    site, adult = adult, description = entry.title + " " + entry.summary)
            }
            RealWebItem(entry.url, site, entry.author.takeIf { it.isNotBlank() && it.length < 60 }?.let { "by $it" }.orEmpty(),
                (entry.title + entry.summary.takeIf { it.isNotBlank() && !it.startsWith(entry.title) }?.let { "\n" + it.take(220) }.orEmpty()).take(TEXT_CHARS),
                topic, media, adult, author = site)
        }
    }

    /** A YouTube channel's newest uploads, from its public feed. */
    private fun youtube(name: String, channelId: String, topic: String): List<RealWebItem> {
        val xml = web.getText("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        val channel = name.ifBlank { FeedXml.feedName(xml).ifBlank { "YouTube" } }
        return FeedXml.parse(xml).mapNotNull { entry ->
            val video = entry.youtubeVideoId ?: return@mapNotNull null
            val thumb = "https://i.ytimg.com/vi/$video/hqdefault.jpg"
            RealWebItem("https://www.youtube.com/watch?v=$video", "YouTube", "", author = channel, text =
                (entry.title + entry.summary.takeIf { it.isNotBlank() }?.let { "\n" + it.take(200) }.orEmpty()).take(TEXT_CHARS),
                topic = topic, media = WebPicture("yt-$video", entry.title.take(160), thumb, thumb, false, "YouTube", "https://www.youtube.com/watch?v=$video",
                    "$channel on YouTube", description = entry.title))
        }
    }

    private fun blueskyAuthor(handle: String, adultAllowed: Boolean, topic: String, adult: Boolean): List<RealWebItem> =
        blueskyItems(
            web.getJson(
                "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed?limit=30&filter=posts_no_replies&actor=" +
                    URLEncoder.encode(handle, "UTF-8"),
            ).obj()["feed"].arr(),
            adultAllowed, topic,
        ).map { if (adult) it.copy(adult = true) else it }

    private fun mastodonAccount(acct: String, adultAllowed: Boolean, topic: String, adult: Boolean): List<RealWebItem> {
        val server = acct.substringAfter('@')
        val id = web.getJson("https://$server/api/v1/accounts/lookup?acct=" + URLEncoder.encode(acct.substringBefore('@'), "UTF-8"))
            .obj().str("id") ?: return emptyList()
        return mastodonStatuses(web.getJson("https://$server/api/v1/accounts/$id/statuses?limit=30&exclude_replies=true&exclude_reblogs=true").arr(),
            topic, adultAllowed, adult)
    }

    private fun mastodon(topic: String, tag: String, adultAllowed: Boolean, forceAdult: Boolean = false): List<RealWebItem> {
        val server = WebPictureSearch.MASTODON_SERVERS.random()
        return mastodonStatuses(web.getJson("https://$server/api/v1/timelines/tag/$tag?limit=30").arr(), topic, adultAllowed, forceAdult)
    }

    private fun mastodonStatuses(statuses: JsonArray, topic: String, adultAllowed: Boolean, forceAdult: Boolean): List<RealWebItem> =
        statuses.mapNotNull { element ->
            val status = element.obj()
            if (status.str("language")?.startsWith("en") == false) return@mapNotNull null
            val sensitive = status.str("sensitive") == "true" || forceAdult
            if (sensitive && !adultAllowed) return@mapNotNull null
            val url = status.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val text = web.htmlToText(status.str("content").orEmpty())
            if (text.length < 20) return@mapNotNull null
            val media = web.mastodonMedia(status, gifs = false, adultAllowed = adultAllowed).firstOrNull()
                ?: web.mastodonMedia(status, gifs = true, adultAllowed = adultAllowed).firstOrNull()
            val handle = "@" + (status["account"].obj().str("acct") ?: "someone")
            RealWebItem(url, "Mastodon", handle, text.take(TEXT_CHARS), topic, media, sensitive, author = handle)
        }

    private suspend fun reddit(topic: String): List<RealWebItem> =
        subreddit(TOPIC_SUBREDDITS[topic].orEmpty().ifEmpty { TOPIC_SUBREDDITS.getValue("memes") }.random(), topic, adult = false)

    private suspend fun subreddit(sub: String, topic: String, adult: Boolean): List<RealWebItem> {
        if (reddit.signedIn) {
            runCatching { return redditApi("/r/$sub/hot?limit=30", topic, forceAdult = adult, adultAllowed = true) }
        }
        // The over18 cookie lets the feed of an adult subreddit through instead of its age interstitial.
        val headers = if (adult) mapOf("Cookie" to "over18=1") else emptyMap()
        return RedditRss.parse(web.getText("https://www.reddit.com/r/$sub/hot/.rss", headers)).mapNotNull { entry ->
            // Moderator stickies aren't what people share.
            if (entry.author.equals("AutoModerator", true) || entry.title.isBlank()) return@mapNotNull null
            RealWebItem(
                url = entry.url, site = "Reddit",
                credit = listOf("r/${entry.subreddit.ifBlank { sub }}", entry.author.takeIf { it.isNotBlank() }?.let { "u/$it" })
                    .filterNotNull().joinToString(" · "),
                text = (entry.title + entry.selfText.takeIf { it.isNotBlank() }?.let { "\n" + it }.orEmpty()).take(TEXT_CHARS),
                topic = topic,
                media = RedditRss.toPicture(entry, gifsOnly = false)?.copy(adult = adult),
                adult = adult,
                author = entry.author.takeIf { it.isNotBlank() }?.let { "u/$it" } ?: "r/${entry.subreddit.ifBlank { sub }}",
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

    private fun bluesky(adultAllowed: Boolean): List<RealWebItem> = blueskyItems(
        web.getJson(
            "https://public.api.bsky.app/xrpc/app.bsky.feed.getFeed?limit=50&feed=" +
                URLEncoder.encode(BLUESKY_DISCOVER, "UTF-8"),
        ).obj()["feed"].arr(),
        adultAllowed,
    )

    private fun blueskyItems(feed: JsonArray, adultAllowed: Boolean, topic: String? = null): List<RealWebItem> =
        feed.mapNotNull { element ->
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
            RealWebItem(url, "Bluesky", "@$handle", body.take(TEXT_CHARS), topic ?: guessTopic(body), media, adult,
                author = author.str("displayName")?.takeIf { it.isNotBlank() }?.let { "$it (@$handle)" } ?: "@$handle")
        }

    private fun lemmy(adultAllowed: Boolean, server: String = "lemmy.world", topic: String? = null): List<RealWebItem> {
        val posts = web.getJson("https://$server/api/v3/post/list?sort=Hot&type_=All&limit=40").obj()["posts"].arr()
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
                topic ?: guessTopic("$community $title"), media, nsfw)
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

    /**
     * The writer's Reddit through the official API: a subreddit or their own front page,
     * with each post's real author and, for the busiest threads, the top comments.
     */
    private suspend fun redditApi(path: String, topic: String, forceAdult: Boolean, adultAllowed: Boolean): List<RealWebItem> {
        val posts = RedditJson.posts(reddit.getJson(path))
            .filter { adultAllowed || !it.over18 }
        val threads = posts.filter { it.commentCount >= 5 }.sortedByDescending { it.commentCount }.take(2).map { it.id }.toSet()
        return posts.map { post ->
            val adult = forceAdult || post.over18
            val media = post.media?.let { full ->
                WebPicture("rd-" + post.id, post.title.take(160), post.thumbnail ?: full, full, post.isLoop, "Reddit", post.url,
                    "u/${post.author} · r/${post.subreddit}", adult = adult, description = post.title + " " + post.body.take(400))
            }
            val comments = if (post.id in threads) runCatching {
                RedditJson.comments(reddit.getJson("/comments/${post.id}?limit=6&depth=1&sort=top"))
                    .take(3).map { "u/${it.author}" to it.body.take(500) }
            }.getOrDefault(emptyList()) else emptyList()
            RealWebItem(
                url = post.url, site = "Reddit", credit = "r/${post.subreddit}",
                text = (post.title + post.body.takeIf { it.isNotBlank() }?.let { "\n" + it }.orEmpty()).take(TEXT_CHARS),
                topic = if (adult) SocialNpcs.ADULT_TOPIC else topic, media = media, adult = adult,
                author = "u/${post.author}", comments = comments,
            )
        }
    }

    /** RedGIFs' trending clips (an adult aggregator with a public API and verified creators). */
    private fun redgifs(topic: String): List<RealWebItem> {
        if (redgifsToken.isBlank() || System.currentTimeMillis() > redgifsTokenUntil) {
            redgifsToken = web.getJson("https://api.redgifs.com/v2/auth/temporary").obj().str("token").orEmpty()
            redgifsTokenUntil = System.currentTimeMillis() + 20 * 60 * 60_000L
        }
        val order = listOf("trending", "top7", "latest").random()
        val gifs = web.getJson(
            "https://api.redgifs.com/v2/gifs/search?order=$order&count=40",
            mapOf("Authorization" to "Bearer $redgifsToken"),
        ).obj()["gifs"].arr()
        return gifs.mapNotNull { element ->
            val gif = element.obj()
            val id = gif.str("id") ?: return@mapNotNull null
            val urls = gif["urls"].obj()
            val clip = urls.str("sd") ?: urls.str("silent") ?: urls.str("hd") ?: return@mapNotNull null
            val user = gif.str("userName").orEmpty()
            val tags = (gif["tags"].arr() + gif["niches"].arr()).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
            val page = "https://www.redgifs.com/watch/$id"
            val text = gif.str("description")?.takeIf { it.isNotBlank() } ?: tags.split(", ").take(5).joinToString(" · ")
            RealWebItem(page, "RedGIFs", "@$user", text.take(TEXT_CHARS), topic,
                WebPicture("rg-$id", text.take(160), urls.str("thumbnail") ?: urls.str("poster") ?: clip, clip, true, "RedGIFs", page,
                    "@$user on RedGIFs", adult = true, description = "$text $tags"),
                adult = true, author = "@$user", tags = tags)
        }
    }

    /** Pornhub's public webmaster API (made for embedding): this week's most-viewed videos. */
    private fun pornhub(topic: String): List<RealWebItem> {
        val ordering = listOf("mostviewed", "rating", "newest").random()
        val videos = web.getJson("https://www.pornhub.com/webmasters/search?ordering=$ordering&period=weekly&thumbsize=large_hd").obj()["videos"].arr()
        return videos.mapNotNull { element ->
            val video = element.obj()
            val url = video.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val title = video.str("title") ?: return@mapNotNull null
            val thumb = video["thumbs"].arr().firstOrNull()?.obj()?.str("src") ?: video.str("default_thumb") ?: video.str("thumb")
            val tags = video["tags"].arr().mapNotNull { it.obj().str("tag_name") }.joinToString(", ")
            val stars = video["pornstars"].arr().mapNotNull { it.obj().str("pornstar_name") }
            val by = stars.firstOrNull() ?: "Pornhub"
            RealWebItem(url, "Pornhub", stars.joinToString(", "), title.take(TEXT_CHARS), topic,
                thumb?.let { WebPicture("ph-" + url.hashCode(), title.take(160), it, it, false, "Pornhub", url, "$by on Pornhub",
                    adult = true, description = "$title $tags") },
                adult = true, author = by, tags = tags)
        }
    }

    /** One Lemmy community (`name@instance`), like a forum board: its hot threads with top comments. */
    private fun lemmyCommunity(community: String, topic: String, adultAllowed: Boolean, adult: Boolean): List<RealWebItem> {
        val name = community.substringBefore('@')
        val server = community.substringAfter('@', "lemmy.world")
        val posts = web.getJson("https://$server/api/v3/post/list?sort=Hot&limit=25&community_name=" + URLEncoder.encode(name, "UTF-8"))
            .obj()["posts"].arr()
        return posts.mapIndexedNotNull { index, element ->
            val view = element.obj()
            val post = view["post"].obj()
            val nsfw = adult || post.str("nsfw") == "true" || view["community"].obj().str("nsfw") == "true"
            if (nsfw && !adultAllowed) return@mapIndexedNotNull null
            val page = post.str("ap_id")?.takeIf { it.startsWith("https://") } ?: return@mapIndexedNotNull null
            val title = post.str("name") ?: return@mapIndexedNotNull null
            val creator = view["creator"].obj().str("name")?.let { "@$it" } ?: "c/$name"
            val comments = if (index < 2 && (view["counts"].obj().str("comments")?.toIntOrNull() ?: 0) > 0) runCatching {
                web.getJson("https://$server/api/v3/comment/list?sort=Top&max_depth=1&limit=5&post_id=" + post.str("id"))
                    .obj()["comments"].arr().mapNotNull { c ->
                        val body = c.obj()["comment"].obj().str("content") ?: return@mapNotNull null
                        val who = c.obj()["creator"].obj().str("name") ?: return@mapNotNull null
                        "@$who" to body.take(500)
                    }.take(3)
            }.getOrDefault(emptyList()) else emptyList()
            RealWebItem(page, "Lemmy", "c/$name", (title + post.str("body")?.let { "\n" + it }.orEmpty()).take(TEXT_CHARS),
                if (nsfw) SocialNpcs.ADULT_TOPIC else topic, web.lemmyPicture(view, gifs = false, adultAllowed = adultAllowed), nsfw,
                author = creator, comments = comments)
        }
    }

    /** Eporner's public video API: this week's top videos. */
    private fun eporner(topic: String): List<RealWebItem> {
        val order = listOf("top-weekly", "latest", "most-popular").random()
        val videos = web.getJson("https://www.eporner.com/api/v2/video/search/?per_page=30&order=$order&thumbsize=big&format=json")
            .obj()["videos"].arr()
        return videos.mapNotNull { element ->
            val video = element.obj()
            val url = video.str("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val title = video.str("title") ?: return@mapNotNull null
            val thumb = video["default_thumb"].obj().str("src")
            val tags = video.str("keywords").orEmpty()
            RealWebItem(url, "Eporner", "", title.take(TEXT_CHARS), topic,
                thumb?.let { WebPicture("ep-" + url.hashCode(), title.take(160), it, it, false, "Eporner", url, "Eporner",
                    adult = true, description = "$title $tags") },
                adult = true, author = "Eporner", tags = tags)
        }
    }

    /**
     * A work-safe 4chan board through its read-only public API: the busiest threads, each
     * with its opening post and picture, and the top replies of the two busiest.
     */
    private fun fourChan(board: String, topic: String): List<RealWebItem> {
        val threads = web.getJson("https://a.4cdn.org/$board/catalog.json").arr()
            .flatMap { it.obj()["threads"].arr() }
            .map { it.obj() }
            .filter { it.str("sticky") == null && it.str("closed") == null }
            .sortedByDescending { it.str("replies")?.toIntOrNull() ?: 0 }
            .take(20)
        return threads.mapIndexedNotNull { index, thread ->
            val no = thread.str("no") ?: return@mapIndexedNotNull null
            val text = listOfNotNull(thread.str("sub"), thread.str("com")?.let { web.htmlToText(it) })
                .joinToString("\n").trim()
            if (text.length < 15) return@mapIndexedNotNull null
            val url = "https://boards.4chan.org/$board/thread/$no"
            val media = thread.str("tim")?.let { tim ->
                val ext = thread.str("ext").orEmpty()
                val full = if (ext == ".jpg" || ext == ".png" || ext == ".gif") "https://i.4cdn.org/$board/$tim$ext" else "https://i.4cdn.org/$board/${tim}s.jpg"
                WebPicture("4c-$board-$no", text.take(160), "https://i.4cdn.org/$board/${tim}s.jpg", full, ext == ".gif", "4chan", url,
                    "/$board/ on 4chan", description = text.take(400))
            }
            val comments = if (index < 2) runCatching {
                web.getJson("https://a.4cdn.org/$board/thread/$no.json").obj()["posts"].arr().drop(1)
                    .mapNotNull { p -> p.obj().str("com")?.let { web.htmlToText(it) }?.replace(Regex(">>\\d+\\s*"), "")?.trim() }
                    .filter { it.length in 12..400 }
                    .take(3).map { "Anonymous" to it }
            }.getOrDefault(emptyList()) else emptyList()
            RealWebItem(url, "4chan", "/$board/", text.take(TEXT_CHARS), topic, media, author = "Anonymous · /$board/", comments = comments)
        }
    }

    /** Bluesky's public search (no account): top posts for [words]; adult kinds keep only adult-labelled posts. */
    private fun blueskySearch(words: String, topic: String, adultAllowed: Boolean, adultOnly: Boolean): List<RealWebItem> {
        val posts = web.getJson(
            "https://api.bsky.app/xrpc/app.bsky.feed.searchPosts?limit=40&sort=top&q=" + URLEncoder.encode(words, "UTF-8"),
        ).obj()["posts"].arr()
        val asFeed = JsonArray(posts.map { JsonObject(mapOf("post" to it)) })
        return blueskyItems(asFeed, adultAllowed, topic).filter { !adultOnly || it.adult }
    }

    private fun eligible(item: RealWebItem, adultAllowed: Boolean): Boolean =
        !item.adult || (adultAllowed && adultTextIsEligible(listOf(item.text, item.credit, item.author, item.tags).joinToString(" ")))

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
        private const val MAX_CUSTOM_PER_REFRESH = 4

        private val UNDERAGE_WORDS = Regex(
            "\\b(?:loli|shota|child|children|kid|kids|minor|minors|underage|under-age|preteen|pre-teen|teen|teens|teenager|teenagers|" +
                "young[- ]looking|schoolgirls?|schoolboys?|school[- ]?girls?|school[- ]?uniform|high ?school|highschool|jailbait|" +
                "barely legal|little step|lil step|daughter|stepdaughter|step[- ]?daughter|little sis(?:ter)?|lil sis|baby[- ]?girl|" +
                "1[0-7]\\s*(?:yo|y/o|years?[- ]old))\\b",
            RegexOption.IGNORE_CASE,
        )
        /** Leaked paid content and fakes of real people are shared without consent; never shown. */
        private val NONCONSENSUAL_WORDS = Regex(
            "\\b(?:leak|leaks|leaked|deepfakes?|deep[- ]fakes?|celeb ?fakes?|fake nudes?|nudify|undress(?:ed)? ai|hidden cam|spycam|upskirt|revenge)\\b",
            RegexOption.IGNORE_CASE,
        )

        /** Adult posts that mention or hint at anyone under 18 are never shared. */
        fun adultTextIsEligible(text: String): Boolean =
            !UNDERAGE_WORDS.containsMatchIn(text) && !NONCONSENSUAL_WORDS.containsMatchIn(text)
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
