package com.ihy2ln.weaverse.feature.chatting.social

/**
 * The kinds of real accounts a WeaverSocial timeline follows, modelled on the writer's own
 * feeds: news outlets, creators and communities. Each kind lists public sources that can be
 * read without an account — outlet RSS feeds, YouTube channel feeds, subreddits and Mastodon
 * hashtags. A source that's down or moved just contributes nothing that refresh.
 *
 * [topic] is the interest everyday people ([SocialNpcs]) need to reshare it. Adult kinds
 * only load with WeaverSocial 18+ on and after the writer confirms they're an adult.
 */
enum class FeedCategory(
    val id: String,
    val label: String,
    val description: String,
    val topic: String,
    val adult: Boolean = false,
    val feeds: List<Pair<String, String>> = emptyList(),
    val youtube: List<Pair<String, String>> = emptyList(),
    val subreddits: List<String> = emptyList(),
    val hashtags: List<String> = emptyList(),
    /** Lemmy servers whose hot posts belong to this kind. */
    val lemmy: List<String> = emptyList(),
    /** Take adult-labelled posts from Bluesky's Discover feed. */
    val blueskyAdult: Boolean = false,
) {
    GameOutlets(
        "game_outlets", "Video game outlets", "IGN, GameSpot, Polygon, PC Gamer, Eurogamer, Kotaku", "games",
        feeds = listOf(
            "IGN" to "https://feeds.feedburner.com/ign/games-all",
            "GameSpot" to "https://www.gamespot.com/feeds/mashup/",
            "Polygon" to "https://www.polygon.com/rss/index.xml",
            "PC Gamer" to "https://www.pcgamer.com/rss/",
            "Eurogamer" to "https://www.eurogamer.net/feed",
            "Kotaku" to "https://kotaku.com/rss",
        ),
        subreddits = listOf("Games", "gamingnews", "nintendo", "PS5", "XboxSeriesX"),
        hashtags = listOf("gamingnews", "gaming"),
    ),
    Markets(
        "markets", "Stocks & crypto", "Market and crypto news, r/wallstreetbets, r/CryptoCurrency", "money",
        feeds = listOf(
            "CoinDesk" to "https://www.coindesk.com/arc/outboundfeeds/rss/",
            "Cointelegraph" to "https://cointelegraph.com/rss",
            "Yahoo Finance" to "https://finance.yahoo.com/news/rssindex",
            "MarketWatch" to "https://feeds.content.dowjones.io/public/rss/mw_topstories",
        ),
        subreddits = listOf("wallstreetbets", "stocks", "StockMarket", "investing", "CryptoCurrency", "Bitcoin", "ethtrader"),
        hashtags = listOf("stocks", "crypto", "bitcoin", "investing"),
    ),
    Anime(
        "anime", "Manga & anime", "Anime News Network, MyAnimeList, r/anime, r/manga", "anime",
        feeds = listOf(
            "Anime News Network" to "https://www.animenewsnetwork.com/all/rss.xml?ann-edition=us",
            "MyAnimeList" to "https://myanimelist.net/rss/news.xml",
        ),
        subreddits = listOf("anime", "manga", "Animemes", "OnePiece", "Jujutsufolk", "animeart"),
        hashtags = listOf("anime", "manga", "animeart"),
    ),
    AiNews(
        "ai_news", "AI news", "The Verge, TechCrunch, Ars Technica, MIT Technology Review, WIRED, r/singularity", "ai",
        feeds = listOf(
            "The Verge" to "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml",
            "TechCrunch" to "https://techcrunch.com/category/artificial-intelligence/feed/",
            "Ars Technica" to "https://arstechnica.com/ai/feed/",
            "MIT Technology Review" to "https://www.technologyreview.com/topic/artificial-intelligence/feed",
            "WIRED" to "https://www.wired.com/feed/tag/ai/latest/rss",
        ),
        subreddits = listOf("artificial", "singularity", "OpenAI", "LocalLLaMA", "ClaudeAI"),
        hashtags = listOf("ai", "llm", "machinelearning"),
    ),
    AiCreators(
        "ai_creators", "AI creators", "Two Minute Papers, Fireship, Matt Wolfe, AI Explained, r/StableDiffusion", "ai",
        youtube = listOf(
            "Two Minute Papers" to "UCbfYPyITQ-7l4upoX8nvctg",
            "Fireship" to "UCsBjURrPoezykLs9EqgamOA",
            "Matt Wolfe" to "UChpleBmo18P08aKCIgti38g",
            "AI Explained" to "UCNJ1Ymd5yFuUPtn21xtRbbw",
            "Yannic Kilcher" to "UCZHmQk67mSJgfCCTn7xBfew",
        ),
        subreddits = listOf("StableDiffusion", "aiArt", "midjourney", "ChatGPT"),
        hashtags = listOf("aiart", "generativeai"),
    ),
    GameCreators(
        "game_creators", "Gaming creators", "Markiplier, jacksepticeye, PewDiePie, dunkey, Game Theory, r/LivestreamFail", "creators",
        youtube = listOf(
            "Markiplier" to "UC7_YxT-KID8kRbqZo7MyscQ",
            "jacksepticeye" to "UCYzPXprvl5Y-Sf0g4vX-m6g",
            "PewDiePie" to "UC-lHJZR3Gqxm24_Vd_AJ5Yw",
            "videogamedunkey" to "UCsvn_Po0SmunchJYOWpOxMg",
            "The Game Theorists" to "UCo_IB5145EVNcf8hw1Kku7w",
        ),
        subreddits = listOf("LivestreamFail", "Twitch", "youtubers"),
        hashtags = listOf("twitch", "streamer"),
    ),
    Comedy(
        "comedy", "Comedians", "SNL, Key & Peele, Dropout, r/standupcomedy, r/ContagiousLaughter", "comedy",
        youtube = listOf(
            "Saturday Night Live" to "UCqFzWxSCi39LnW1JKFR3efg",
            "Key & Peele" to "UCdN4aXTrHAtfgbVG9HjBmxQ",
            "Dropout" to "UCPDXXXJj9nax0fr0Wfc048g",
        ),
        subreddits = listOf("standupcomedy", "ContagiousLaughter", "comedyheaven", "funny"),
        hashtags = listOf("comedy", "standup"),
    ),
    Thirst(
        "thirst", "Thirst posts", "Selfies and thirst traps from adult communities", "adult", adult = true,
        subreddits = listOf("PrettyGirls", "LadyBoners", "SFWcurves", "tightdresses", "gentlemanboners"),
    ),
    PornAggregators(
        "porn_aggregators", "Porn aggregators", "r/nsfw, r/NSFW_GIF, r/RealGirls and similar repost pages", "adult", adult = true,
        subreddits = listOf("nsfw", "NSFW_GIF", "RealGirls", "porninfifteenseconds", "nsfw_gifs"),
    ),
    AdultCreators(
        "adult_creators", "Adult creators", "Verified creators posting their own content on r/gonewild and similar", "adult", adult = true,
        subreddits = listOf("gonewild", "gonewildcurvy", "AsiansGoneWild", "GWCouples"),
    ),
    OnlyFans(
        "onlyfans", "OnlyFans promos", "Free public teasers creators post to promote their OnlyFans — never paid content", "adult", adult = true,
        subreddits = listOf("OnlyFans101", "OnlyFansPromotions", "onlyfansgirls101"),
    ),
    OtherAdult(
        "other_adult", "Other adult social", "lemmynsfw, adult-labelled Bluesky and #nsfw on Mastodon", "adult", adult = true,
        hashtags = listOf("nsfw", "nsfwart"),
        lemmy = listOf("lemmynsfw.com"),
        blueskyAdult = true,
    ),
    ;

    companion object {
        fun byId(id: String): FeedCategory? = entries.firstOrNull { it.id == id }

        /** Every safe-for-work kind starts on; adult kinds wait for the writer to opt in. */
        val defaults: Set<FeedCategory> = entries.filterNot { it.adult }.toSet()
    }
}

/**
 * One account or page the writer follows in real life, typed one per line in settings:
 * `r/sub`, `#hashtag`, `@handle.bsky.social`, `@user@mastodon.server`, a YouTube channel
 * link or `UC…` id, or any RSS/Atom feed URL. A leading `18+` marks an adult source, which
 * only loads with WeaverSocial 18+ on.
 */
data class CustomFeedSource(val kind: Kind, val value: String, val adult: Boolean, val raw: String) {
    enum class Kind { Subreddit, Hashtag, Bluesky, Mastodon, YouTube, Feed }

    val label: String get() = when (kind) {
        Kind.Subreddit -> "r/$value"
        Kind.Hashtag -> "#$value"
        Kind.Bluesky, Kind.Mastodon -> "@$value"
        Kind.YouTube -> "YouTube"
        Kind.Feed -> value.substringAfter("://").substringBefore('/').removePrefix("www.")
    }

    companion object {
        private val youtubeChannel = Regex("(?:youtube\\.com/channel/)?(UC[A-Za-z0-9_-]{22})")
        private val mastodonAccount = Regex("^@?([A-Za-z0-9_.]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")

        fun parseAll(text: String): List<CustomFeedSource> = text.lines().mapNotNull(::parse).distinctBy { it.kind to it.value.lowercase() }

        fun parse(line: String): CustomFeedSource? {
            var s = line.trim()
            if (s.isBlank() || s.startsWith("//")) return null
            val adult = s.startsWith("18+")
            if (adult) s = s.removePrefix("18+").trim()
            fun of(kind: Kind, value: String) = value.trim().trim('/').takeIf { it.isNotBlank() }?.let { CustomFeedSource(kind, it, adult, line.trim()) }
            youtubeChannel.find(s)?.takeIf { s.startsWith("UC") || "youtube.com/channel/" in s }?.let { return of(Kind.YouTube, it.groupValues[1]) }
            Regex("(?:reddit\\.com)?/?r/([A-Za-z0-9_]+)").find(s)?.takeIf { s.startsWith("r/") || s.startsWith("/r/") || "reddit.com/r/" in s }
                ?.let { return of(Kind.Subreddit, it.groupValues[1]) }
            Regex("bsky\\.app/profile/([^/?#]+)").find(s)?.let { return of(Kind.Bluesky, it.groupValues[1]) }
            if (s.startsWith("#")) return of(Kind.Hashtag, s.removePrefix("#").filter { it.isLetterOrDigit() || it == '_' })
            mastodonAccount.find(s)?.let { return of(Kind.Mastodon, "${it.groupValues[1]}@${it.groupValues[2]}") }
            if (s.startsWith("@") && '.' in s) return of(Kind.Bluesky, s.removePrefix("@"))
            if (s.startsWith("http://") || s.startsWith("https://")) return of(Kind.Feed, s)
            return null
        }
    }
}
