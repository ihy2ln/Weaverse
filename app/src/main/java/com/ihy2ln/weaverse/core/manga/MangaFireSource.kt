package com.ihy2ln.weaverse.core.manga

import okhttp3.OkHttpClient

/**
 * Catalog adapter for MangaFire public HTML and optional JSON endpoints.
 * Does not implement VRF signing or challenge solving.
 */
internal class MangaFireSource(
    client: OkHttpClient,
    private val webLinkImporter: MangaWebLinkImporter,
    private val baseUrl: String = mangaFireHtmlConfig.baseUrl,
) : MangaSourceAdapter {
    private val html = PublicHtmlMangaSourceAdapter(
        config = mangaFireHtmlConfig.copy(baseUrl = baseUrl),
        client = client,
        webLinkImporter = webLinkImporter,
    )
    private val http = MangaCatalogHttp(client, "MangaFire")

    override val descriptor = html.descriptor.copy(
        description = "Public HTML/JSON catalog. Host challenges are reported, not bypassed.",
        language = "en",
        kind = "HTML catalog",
        authorized = false,
    )

    override suspend fun search(query: String): List<MangaSearchResult> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val encoded = java.net.URLEncoder.encode(clean, Charsets.UTF_8.name())
        jsonTitles("/api/titles?keyword=$encoded")?.let { return it }
        return html.search(clean).map(::withHid)
    }

    override suspend fun browse(mode: MangaBrowseMode): List<MangaSearchResult> {
        val path = when (mode) {
            MangaBrowseMode.Popular -> "/api/titles?order[views_30d]=desc"
            MangaBrowseMode.Latest -> "/api/titles?order[chapter_updated_at]=desc"
        }
        jsonTitles(path)?.let { return it }
        return html.browse(mode).map(::withHid)
    }

    override suspend fun details(manga: MangaSearchResult): MangaSearchResult = html.details(manga)

    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> {
        val hid = mangaFireHid(manga)
        if (hid.isNotBlank()) {
            jsonChapters(manga, "/api/titles/$hid/chapters")?.let { return it }
        }
        return html.chapters(manga)
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val htmlBody = http.get(chapter.canonicalUrl)
        val extracted = webLinkImporter.extractImageUrls(chapter.canonicalUrl, htmlBody)
        if (extracted.isEmpty()) {
            error("MangaFire did not expose public page images for this chapter.")
        }
        return extracted.map { page ->
            MangaPage(
                sourceId = descriptor.id,
                chapterId = chapter.remoteId,
                pageIndex = page.index,
                remoteUrl = page.url,
                fileName = page.fileName,
            )
        }
    }

    private suspend fun jsonTitles(path: String): List<MangaSearchResult>? {
        val url = resolveHttpUrl(baseUrl, path) ?: return null
        val body = runCatching { http.get(url) }.getOrNull() ?: return null
        val parsed = MangaCatalogJson.parseTitles(descriptor.id, baseUrl, body).map(::withHid)
        return parsed.takeIf { it.isNotEmpty() }
    }

    private suspend fun jsonChapters(manga: MangaSearchResult, path: String): List<MangaChapter>? {
        val url = resolveHttpUrl(baseUrl, path) ?: return null
        val body = runCatching { http.get(url) }.getOrNull() ?: return null
        val parsed = MangaCatalogJson.parseChapters(
            sourceId = descriptor.id,
            manga = manga,
            body = body,
            language = "en",
            readingOrder = "ltr",
            fallbackPath = { number, chapterHid ->
                val slug = pathSegments(manga.canonicalUrl).lastOrNull().orEmpty()
                    .ifBlank { chapterHid.ifBlank { manga.remoteId } }
                if (number.isBlank()) null else "/read/$slug/en/chapter-$number"
            },
        )
        return parsed.takeIf { it.isNotEmpty() }
    }

    private fun withHid(result: MangaSearchResult): MangaSearchResult {
        val hid = mangaFireHid(result)
        return if (hid.isBlank() || result.remoteId == hid) result else result.copy(remoteId = hid)
    }

    private fun mangaFireHid(manga: MangaSearchResult): String =
        mangaFireHid(manga.canonicalUrl).ifBlank { manga.remoteId }

    companion object {
        fun mangaFireHid(url: String): String {
            val slug = pathSegments(url).lastOrNull().orEmpty()
            return slug.substringAfterLast('.').takeIf { slug.contains('.') }.orEmpty()
        }
    }
}
