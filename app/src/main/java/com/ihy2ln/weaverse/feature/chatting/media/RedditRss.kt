package com.ihy2ln.weaverse.feature.chatting.media

/**
 * Reddit's public Atom feeds (`/r/<sub>/hot/.rss`, `/r/<sub>/search.rss`) — the one way to
 * read Reddit without a signed-in app, since its JSON API refuses anonymous calls.
 */
object RedditRss {
    data class Entry(
        val title: String,
        /** The comments page, kept for credit. */
        val url: String,
        val author: String,
        val subreddit: String,
        /** Direct picture or GIF (i.redd.it, i.imgur.com), when the post links one. */
        val directMedia: String?,
        /** Reddit's preview image; also stands in for videos, which need an account to play. */
        val preview: String?,
        val selfText: String,
    )

    private val entry = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val link = Regex("<link href=\"([^\"]+)\"")
    private val author = Regex("<author>\\s*<name>([^<]+)</name>")
    private val category = Regex("<category term=\"([^\"]+)\"")
    private val content = Regex("<content type=\"html\">(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
    private val img = Regex("<img src=\"([^\"]+)\"")
    private val href = Regex("href=\"([^\"]+)\"")
    private val direct = Regex("^https://(i\\.redd\\.it|i\\.imgur\\.com)/[^?#]+\\.(gifv?|jpe?g|png|webp|mp4)$", RegexOption.IGNORE_CASE)

    fun parse(xml: String): List<Entry> = entry.findAll(xml).mapNotNull { match ->
        val body = match.groupValues[1]
        val url = link.find(body)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.startsWith("https://") }
            ?: return@mapNotNull null
        val html = content.find(body)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        val links = href.findAll(html).map { unescape(it.groupValues[1]) }.toList()
        val media = links.firstOrNull { direct.matches(it) }?.let { found ->
            // Imgur's .gifv is a web page; the .mp4 beside it is the loop itself.
            if (found.endsWith(".gifv", true)) found.dropLast(5) + ".mp4" else found
        }
        Entry(
            title = unescape(title.find(body)?.groupValues?.get(1).orEmpty()).trim(),
            url = url,
            author = author.find(body)?.groupValues?.get(1)?.removePrefix("/u/").orEmpty(),
            subreddit = category.find(body)?.groupValues?.get(1).orEmpty(),
            directMedia = media,
            preview = img.find(html)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.startsWith("https://") },
            selfText = html.substringAfter("<div class=\"md\">", "").substringBefore("</div>")
                .replace(Regex("<[^>]+>"), " ").let(::unescape).replace(Regex("\\s+"), " ").trim(),
        )
    }.toList()

    /** A Reddit entry as a picture or GIF result, credited to its poster and subreddit. */
    fun toPicture(entry: Entry, gifsOnly: Boolean): WebPicture? {
        val full = entry.directMedia ?: entry.preview ?: return null
        val loop = Regex("\\.(gif|mp4)$", RegexOption.IGNORE_CASE).containsMatchIn(full.substringBefore('?'))
        if (gifsOnly && !loop) return null
        return WebPicture(
            id = "rd-" + entry.url.hashCode(),
            title = entry.title.take(160),
            thumbUrl = entry.preview ?: full,
            fullUrl = full, isGif = loop, source = "Reddit",
            pageUrl = entry.url,
            credit = listOf(entry.author.takeIf { it.isNotBlank() }?.let { "u/$it" }, entry.subreddit.takeIf { it.isNotBlank() }?.let { "r/$it" })
                .filterNotNull().joinToString(" · ").ifBlank { "Reddit" },
            description = (entry.title + " " + entry.selfText).take(1200),
        )
    }

    private fun unescape(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&#32;", " ").replace("&amp;", "&")
}
