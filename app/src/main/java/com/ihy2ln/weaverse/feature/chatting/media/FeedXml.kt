package com.ihy2ln.weaverse.feature.chatting.media

/**
 * Reads RSS 2.0 and Atom feeds — news outlets, blogs and YouTube channel feeds — into
 * headline, link, author and lead picture. Lenient by design: feeds in the wild mix CDATA,
 * escaped HTML and media namespaces, and a field that can't be found is just left empty.
 */
object FeedXml {
    data class Entry(
        val title: String,
        val url: String,
        val author: String,
        val summary: String,
        /** Lead picture: media:thumbnail/content, an image enclosure, or the first <img>. */
        val image: String?,
        /** Set for YouTube feed entries. */
        val youtubeVideoId: String? = null,
    )

    private val item = Regex("<(item|entry)(?:\\s[^>]*)?>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val feedTitle = Regex("<title(?:\\s[^>]*)?>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)

    fun parse(xml: String): List<Entry> = item.findAll(xml).mapNotNull { match ->
        val body = match.groupValues[2]
        val url = link(body)?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
        val title = clean(tag(body, "title").orEmpty())
        val rawSummary = tag(body, "media:description") ?: tag(body, "description") ?: tag(body, "summary")
            ?: tag(body, "content:encoded") ?: tag(body, "content")
        val summaryHtml = unescape(cdata(rawSummary.orEmpty()))
        val videoId = tag(body, "yt:videoId")?.trim()
        val image = attr(body, "media:thumbnail", "url")
            ?: Regex("<media:content[^>]*medium=\"image\"[^>]*>").find(body)?.value?.let { attrOf(it, "url") }
            ?: Regex("<media:content[^>]*url=\"[^\"]+\\.(?:jpe?g|png|webp|gif)[^\"]*\"[^>]*>", RegexOption.IGNORE_CASE).find(body)?.value?.let { attrOf(it, "url") }
            ?: Regex("<enclosure[^>]*type=\"image/[^\"]*\"[^>]*>").find(body)?.value?.let { attrOf(it, "url") }
            ?: Regex("<img[^>]+src=\"([^\"]+)\"").find(summaryHtml)?.groupValues?.get(1)
        Entry(
            title = title,
            url = unescape(url),
            author = clean(tag(body, "dc:creator") ?: Regex("<author>\\s*(?:<name>)?(.*?)(?:</name>.*?)?</author>", RegexOption.DOT_MATCHES_ALL)
                .find(body)?.groupValues?.get(1).orEmpty()),
            summary = clean(summaryHtml).take(600),
            image = image?.let(::unescape)?.takeIf { it.startsWith("https://") },
            youtubeVideoId = videoId,
        )
    }.filter { it.title.isNotBlank() }.toList()

    /**
     * The RSS/Atom feed a web page advertises (`<link rel="alternate" type="application/rss+xml">`),
     * resolved against [pageUrl]; how forums (XenForo, Discourse, phpBB) and blogs expose theirs.
     */
    fun discover(html: String, pageUrl: String): String? {
        val tag = Regex("<link[^>]+type=\"application/(?:rss|atom)\\+xml\"[^>]*>", RegexOption.IGNORE_CASE).find(html)?.value ?: return null
        val href = Regex("href=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)?.let(::unescape) ?: return null
        return runCatching { java.net.URI(pageUrl).resolve(href).toString() }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    /** The feed's own name ("Polygon", "Markiplier"), used for credit on custom feeds. */
    fun feedName(xml: String): String = clean(feedTitle.find(xml.substringBefore("<item").substringBefore("<entry"))?.groupValues?.get(1).orEmpty())

    private fun link(body: String): String? =
        Regex("<link[^>]*rel=\"alternate\"[^>]*>").find(body)?.value?.let { attrOf(it, "href") }
            ?: Regex("<link[^>]*href=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: tag(body, "link")?.trim()?.let(::cdata)?.trim()
            ?: tag(body, "guid")?.trim()?.takeIf { it.startsWith("http") }

    private fun tag(body: String, name: String): String? =
        Regex("<$name(?:\\s[^>]*)?>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)

    private fun attr(body: String, name: String, attribute: String): String? =
        Regex("<$name[^>]*>").find(body)?.value?.let { attrOf(it, attribute) }

    private fun attrOf(tag: String, attribute: String): String? =
        Regex("\\b$attribute=\"([^\"]+)\"").find(tag)?.groupValues?.get(1)

    private fun cdata(s: String): String = s.replace(Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL), "$1")

    private fun clean(s: String): String = unescape(unescape(cdata(s)).replace(Regex("<[^>]+>"), " "))
        .replace(Regex("\\s+"), " ").trim()

    private fun unescape(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&#039;", "'").replace("&apos;", "'").replace("&#8217;", "’")
        .replace("&#8216;", "‘").replace("&#8220;", "“").replace("&#8221;", "”").replace("&#8211;", "–")
        .replace("&#8212;", "—").replace("&nbsp;", " ").replace("&#32;", " ").replace("&amp;", "&")
}
