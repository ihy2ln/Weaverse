package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WebsiteMetadataRegressionTest {
    private fun adapter(base: String, id: String = "rawkuma") = PublicHtmlMangaSourceAdapter(
        PublicHtmlSourceConfig(id, id, base, listOf("/manga/"), listOf("/manga/"), listOf("/?q=%s"), listOf("/manga/", "/title/"), language = "ja"),
        OkHttpClient(), MangaWebLinkImporter(OkHttpClient()),
    )

    @Test fun `same title and same remote ID on different sources never collapse`() {
        fun row(id: String, source: String, remote: String, number: String) = MangaChapterEntity(id, source, id, remote, "Same title", "Chapter $number", chapterNumber = number)
        val groups = groupLibraryChapters(listOf(row("a", "rawkuma", "1", "10"), row("b", "rawkuma", "1", "2"), row("c", "comix", "1", "1"), row("d", "rawkuma", "2", "1")))
        assertEquals(3, groups.size)
        assertEquals(listOf("b", "a"), groups.getValue(mangaLibraryKey("rawkuma", "1")).map { it.id })
    }

    @Test fun `detail title is not the sidebar and metadata is scoped to this series`() {
        val result = adapter("https://rawkuma.net/").rawkumaDetails(MangaSearchResult("rawkuma", "id", "Original", canonicalUrl = "https://rawkuma.net/manga/example/"), """
            <html lang="en"><h1>Last Updates</h1><img src="/wrong.jpg"><a href="/genre/wrong/">Unrelated</a>
            <h1 itemprop="name">Actual title</h1><script type="application/ld+json">
            {"@type":["Book","ComicSeries"],"name":"Actual title","description":"Synopsis","image":{"url":"/right.jpg"},
            "genre":["Action","Fantasy"],"author":{"name":"Writer"},"inLanguage":"en_US","creativeWorkStatus":"Ongoing","datePublished":"2026",
            "aggregateRating":{"ratingValue":8.7}}</script></html>
        """)
        assertEquals("Actual title", result.title)
        assertEquals("https://rawkuma.net/right.jpg", result.coverUrl)
        assertEquals(listOf("Action", "Fantasy"), result.tags)
        assertEquals(listOf("Writer"), result.authors)
        assertEquals(listOf("ja"), result.languages)
        assertEquals("8.7", result.score)
        assertEquals("", result.rating)
    }

    @Test fun `website form values not display labels are sent`() {
        val filters = WebsiteCatalogFilters.rawkuma("""<div data-group-selector="genre-selector"><input name="the_genre"><ul><li value="sci-fi">Science Fiction</li></ul></div>
            <div data-group-selector="order-selector"><input name="the_orderby" value="popular"><li value="date">Date</li><li value="popular">Popular</li></div>""")
        (filters.filterIsInstance<WebsiteGroup>().single().state.single() as WebsiteCheck).state = true
        val url = WebsiteCatalogFilters.url("https://rawkuma.net", "rawkuma", "A & B", 0, filters).toHttpUrl()
        assertEquals("sci-fi", url.queryParameter("the_genre"))
        assertEquals("popular", url.queryParameter("the_orderby"))
        assertEquals("A & B", url.queryParameter("search_term"))
    }

    @Test fun `include exclude format and genre IDs combine without overwriting`() {
        val genre = WebsiteGroup("genres_in", "Genres", listOf(WebsiteOption("6", "Action"), WebsiteOption("13", "Girls Love")), "genres_ex")
        (genre.state[0] as WebsiteTri).state = Filter.TriState.STATE_INCLUDE
        (genre.state[1] as WebsiteTri).state = Filter.TriState.STATE_EXCLUDE
        val format = WebsiteGroup("genres_in", "Formats", listOf(WebsiteOption("93172", "Full Color")), "genres_ex")
        (format.state[0] as WebsiteTri).state = Filter.TriState.STATE_INCLUDE
        val url = WebsiteCatalogFilters.url("https://comix.to", "comix", "", 2, FilterList(listOf(genre, format))).toHttpUrl()
        assertEquals("6,93172", url.queryParameter("genres_in"))
        assertEquals("13", url.queryParameter("genres_ex"))
        assertEquals("3", url.queryParameter("page"))
    }

    @Test fun `pagination retains source filter values`() = runTest {
        MockWebServer().use { server ->
            val adapter = adapter(server.url("/").toString())
            val html = """<a href="/manga/story/"><img src="/cover.jpg" alt="Story"></a>"""
            server.enqueue(MockResponse().setBody(html + """<a href="/manga/page/2/">2</a>"""))
            server.enqueue(MockResponse().setBody(html))
            val genre = WebsiteGroup("the_genre", "Genres", listOf(WebsiteOption("action", "Action")))
            (genre.state[0] as WebsiteCheck).state = true
            val filters = FilterList(listOf(genre))
            adapter.searchPage("", 0, filters)
            adapter.searchPage("", 1, filters)
            assertEquals("action", server.takeRequest().requestUrl!!.queryParameter("the_genre"))
            assertEquals("action", server.takeRequest().requestUrl!!.queryParameter("the_genre"))
        }
    }

    @Test fun `chapter identifier suffix is not part of chapter number`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""<div id="tabpanel-chapters"><a href="/manga/example/chapter-24.404887/"><img alt="Series"><span>Chapter 24</span><time datetime="2026-09-15T09:03:00Z">Today</time></a></div>"""))
            val chapter = adapter(server.url("/").toString()).chapters(MangaSearchResult("rawkuma", "series", "Actual", canonicalUrl = server.url("/manga/example/").toString())).single()
            assertEquals("24", chapter.chapterNumber)
            assertEquals("Chapter 24", chapter.title)
            assertTrue(chapter.dateUpload > 0)
        }
    }

    @Test fun `rendered adjacent cards keep titles and covers paired`() {
        val rows = adapter("https://comix.to", "comix").parseCatalog("https://comix.to", """
            <div class="lrow"><a class="lrow__poster" href="/title/a"><img src="/a.jpg"></a><a class="lrow__title-link" href="/title/a"><h3 class="lrow__title">A title</h3></a></div>
            <div class="lrow"><a class="lrow__poster" href="/title/b"><img src="/b.jpg"></a><a class="lrow__title-link" href="/title/b"><h3 class="lrow__title">B title</h3></a></div>
        """)
        assertEquals(listOf("A title", "B title"), rows.map { it.title })
        assertEquals(listOf("https://comix.to/a.jpg", "https://comix.to/b.jpg"), rows.map { it.coverUrl })
    }
}
