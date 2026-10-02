package com.ihy2ln.weaverse.feature.chatting.social

/**
 * The real sites a WeaverSocial timeline mixes together. Each gets its own button under Social,
 * can be hidden from the feed, and has its own view (the feed showing only that site).
 */
object SocialSites {
    const val WEAVERSOCIAL = "WeaverSocial"

    /** Always offered in the site picker, even before any of their posts have come in. */
    val KNOWN = listOf(
        WEAVERSOCIAL, "Reddit", "Bluesky", "Mastodon", "YouTube", "Lemmy", "lemmynsfw.com", "4chan", "Hacker News", "9GAG",
        "RedGIFs", "Pornhub", "Eporner",
    )

    val ADULT = setOf("RedGIFs", "Pornhub", "Eporner", "lemmynsfw.com")

    /** Which site a post came from: the source's name for real posts, WeaverSocial for the cast's own. */
    fun of(post: SocialPostUi): String = when (post.originKind) {
        SocialFeedViewModel.ORIGIN_REAL, "web_share" -> post.sourceSite.substringBefore(" · ").trim()
            // Posts saved before the site always came first: a byline means the author is the outlet.
            .let { if (it.startsWith("by ")) post.authorName else it }
            .ifBlank { "Web" }
        else -> WEAVERSOCIAL
    }

    /** Brand color for a site's button and view header. */
    fun colorHex(site: String): String = when (site) {
        WEAVERSOCIAL -> "#7C5CFF"
        "Reddit" -> "#FF4500"
        "Bluesky" -> "#1185FE"
        "Mastodon" -> "#6364FF"
        "YouTube" -> "#FF0000"
        "Lemmy", "lemmynsfw.com" -> "#00BC8C"
        "4chan" -> "#789922"
        "Hacker News" -> "#FF6600"
        "9GAG" -> "#222222"
        "RedGIFs" -> "#E8205B"
        "Pornhub" -> "#FF9000"
        "Eporner" -> "#3D7FD9"
        else -> "#5B6474"
    }
}
