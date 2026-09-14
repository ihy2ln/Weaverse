package com.ihy2ln.weaverse.core.manga

import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Catalog adapter for Rawkuma (WordPress / NatsuId) public HTML, REST, and admin-ajax lists.
 * Does not implement challenge solving.
 */
internal class RawkumaSource(
    client: OkHttpClient,
    private val webLinkImporter: MangaWebLinkImporter,
    private val baseUrl: String = rawkumaHtmlConfig.baseUrl,
) : MangaSourceAdapter {
    private val html = PublicHtmlMangaSourceAdapter(
        config = rawkumaHtmlConfig.copy(baseUrl = baseUrl),
        client = client,
        webLinkImporter = webLinkImporter,
    )
    private val http = MangaCatalogHttp(client, "Rawkuma")

    override val descriptor = html.descriptor.copy(
        description = "Public WordPress catalog (HTML/REST). Host challenges are reported, not bypassed.",
        language = "ja",
        kind = "HTML catalog",
        authorized = false,
    )

    override suspend fun search(query: String): List<MangaSearchResult> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        runCatching { html.search(clean) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        restSearch(clean)?.let { return it }
        return ajaxSearch(clean).orEmpty()
    }

    override suspend fun browse(mode: MangaBrowseMode): List<MangaSearchResult> {
        runCatching { html.browse(mode) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val order = if (mode == MangaBrowseMode.Latest) "modified" else "date"
        val url = resolveHttpUrl(baseUrl, "/wp-json/wp/v2/manga?per_page=20&_embed=1&orderby=$order&order=desc") ?: return emptyList()
        val body = runCatching { http.get(url) }.getOrNull() ?: return emptyList()
        return MangaCatalogJson.parseTitles(descriptor.id, baseUrl, body)
    }

    override suspend fun details(manga: MangaSearchResult): MangaSearchResult {
        restBySlug(manga)?.let { return it }
        return html.details(manga)
    }

    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> {
        val seriesHtml = runCatching { http.get(manga.canonicalUrl) }.getOrNull()
        if (!seriesHtml.isNullOrBlank()) {
            val parsed = html.parseChaptersHtml(manga, manga.canonicalUrl, seriesHtml)
            if (parsed.isNotEmpty()) return parsed
            val mangaId = MangaCatalogJson.extractMangaId(seriesHtml)
            ajaxChapters(manga, mangaId)?.let { return it }
        }
        return html.chapters(manga)
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val htmlBody = http.get(chapter.canonicalUrl)
        val extracted = webLinkImporter.extractImageUrls(chapter.canonicalUrl, htmlBody)
        if (extracted.isEmpty()) {
            error("Rawkuma did not expose public page images for this chapter.")
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

    private suspend fun restSearch(query: String): List<MangaSearchResult>? {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = resolveHttpUrl(baseUrl, "/wp-json/wp/v2/manga?search=$encoded&_embed=1&per_page=20") ?: return null
        val body = runCatching { http.get(url) }.getOrNull() ?: return null
        return MangaCatalogJson.parseTitles(descriptor.id, baseUrl, body).takeIf { it.isNotEmpty() }
    }

    private suspend fun restBySlug(manga: MangaSearchResult): MangaSearchResult? {
        val slug = pathSegments(manga.canonicalUrl).lastOrNull()?.substringBefore('.') ?: return null
        val url = resolveHttpUrl(baseUrl, "/wp-json/wp/v2/manga?slug[]=$slug&_embed=1") ?: return null
        val body = runCatching { http.get(url) }.getOrNull() ?: return null
        return MangaCatalogJson.parseTitles(descriptor.id, baseUrl, body).firstOrNull()?.let { rest ->
            manga.copy(
                title = rest.title.ifBlank { manga.title },
                description = rest.description.ifBlank { manga.description },
                coverUrl = rest.coverUrl ?: manga.coverUrl,
                canonicalUrl = rest.canonicalUrl.ifBlank { manga.canonicalUrl },
            )
        }
    }

    private suspend fun ajaxSearch(query: String): List<MangaSearchResult>? {
        val home = runCatching { http.get(resolveHttpUrl(baseUrl, "/") ?: return null) }.getOrNull() ?: return null
        val nonce = MangaCatalogJson.extractNonce(home)
            ?: runCatching {
                http.get(resolveHttpUrl(baseUrl, "/wp-admin/admin-ajax.php?action=get_nonce") ?: return null)
            }.getOrNull()?.let { MangaCatalogJson.extractNonce(it) ?: it.trim().trim('"') }
            ?: return null
        val ajax = resolveHttpUrl(baseUrl, "/wp-admin/admin-ajax.php") ?: return null
        val body = runCatching {
            http.post(
                ajax,
                mapOf(
                    "action" to "advanced_search",
                    "nonce" to nonce,
                    "s" to query,
                    "post_type" to "wp-manga",
                ),
            )
        }.getOrNull() ?: return null
        val htmlBody = MangaCatalogJson.htmlPayload(body)
        return html.parseCatalog(baseUrl, htmlBody).takeIf { it.isNotEmpty() }
    }

    private suspend fun ajaxChapters(manga: MangaSearchResult, mangaId: String?): List<MangaChapter>? {
        val id = mangaId ?: return null
        val url = resolveHttpUrl(baseUrl, "/wp-admin/admin-ajax.php?action=chapter_list&manga_id=$id") ?: return null
        val body = runCatching { http.get(url) }.getOrNull() ?: return null
        val htmlBody = MangaCatalogJson.htmlPayload(body)
        return html.parseChaptersHtml(manga, manga.canonicalUrl, htmlBody).takeIf { it.isNotEmpty() }
    }
}
