package com.ihy2ln.weaverse.feature.chatting.media

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Public indexed pages only. A source page is always retained for attribution. */
internal object AdultMediaSources {
    data class Site(val domain: String, val label: String, val pathPrefix: String = "") {
        val query: String get() = "site:$domain${pathPrefix.takeIf { it.isNotBlank() }.orEmpty()}"
    }

    private val creators = listOf(
        Site("onlyfans.com", "Creator preview"), Site("fansly.com", "Creator preview"),
        Site("patreon.com", "Creator preview"),
    )
    private val hubs = listOf(
        Site("pornhub.com", "Adult hub"), Site("xvideos.com", "Adult hub"),
        Site("xhamster.com", "Adult hub"), Site("xnxx.com", "Adult hub"),
        Site("redgifs.com", "Adult GIF hub"),
    )
    private val galleries = listOf(
        Site("civitai.red", "AI gallery"), Site("gelbooru.com", "Adult gallery"),
        Site("danbooru.donmai.us", "Adult gallery"), Site("rule34.xxx", "Adult gallery"),
        Site("sex.com", "Adult aggregator"),
    )
    private val forums = listOf(
        Site("reddit.com", "Adult forum", "/r/nsfw"),
        Site("reddit.com", "Adult forum", "/r/gonewild"),
        Site("reddit.com", "Adult forum", "/r/RealGirls"),
        Site("lemmy.world", "Adult forum", "/c/nsfw"),
    )

    fun choose(rotation: Int, gifs: Boolean): List<Site> {
        val n = rotation.coerceAtLeast(0)
        if (gifs) return listOf(hubs.last(), galleries[(n / 2) % galleries.size], forums[n % forums.size])
        val groups = listOf(creators, hubs, galleries, forums)
        return (0 until 3).map { offset ->
            val group = groups[(n + offset) % groups.size]
            group[(n / groups.size + offset) % group.size]
        }
    }

    fun belongsTo(site: Site, pageUrl: String): Boolean {
        val url = pageUrl.toHttpUrlOrNull() ?: return false
        return url.isHttps && (url.host == site.domain || url.host.endsWith(".${site.domain}")) &&
            (site.pathPrefix.isBlank() || url.encodedPath.startsWith(site.pathPrefix, ignoreCase = true))
    }

    /** Avoid indexing known underage tags in explicit image galleries. */
    fun adultTagsAreEligible(tags: String): Boolean = !Regex(
        "(^|[\\s_])(loli|shota|child|children|minor|underage|toddler|preteen|teen|teenager|youth|baby|infant|schoolgirl|schoolboy|cub)([\\s_]|$)",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(tags)
}
