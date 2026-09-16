package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicHtmlMangaSourceAdapterTest {
    @Test
    fun rawkumaUsesSeriesLinkInsteadOfSectionAltAndSortsLoadedTitles() {
        val client = OkHttpClient()
        val adapter = PublicHtmlMangaSourceAdapter(
            PublicHtmlSourceConfig("rawkuma", "Rawkuma", "https://rawkuma.net/", listOf("/"), listOf("/"), listOf("/?s=%s"), listOf("/manga/")),
            client, MangaWebLinkImporter(client),
        )
        val rows = adapter.parseCatalog("https://rawkuma.net/", """
            <a href="/manga/zebra/"><img src="/covers/z.jpg" alt="Last Updates"></a>
            <h3><a href="/manga/zebra/">Zebra Story</a></h3>
            <a href="/manga/alpha/"><img src="/covers/a.jpg" alt="Last Updates"></a>
            <h3><a href="/manga/alpha/">Alpha Story</a></h3>
        """)
        assertEquals(listOf("Zebra Story", "Alpha Story"), rows.map { it.title })
        assertEquals(listOf("Alpha Story", "Zebra Story"), CatalogSort.apply(rows, "title").map { it.title })
        assertEquals(rows, CatalogSort.apply(rows, "source"))
        assertEquals("Alpha Story", CatalogSort.apply(listOf(rows[0], rows[1].copy(score = "9.1")), "score").first().title)
    }
    @Test
    fun comixDetailsReadAllTaxonomiesAndScore() = runTest {
        MockWebServer().use { server ->
            val client = OkHttpClient()
            val adapter = PublicHtmlMangaSourceAdapter(
                PublicHtmlSourceConfig("comix", "Comix", server.url("/").toString(), listOf("/"), listOf("/"), listOf("/?q=%s"), listOf("/title/")),
                client, MangaWebLinkImporter(client),
            )
            fun html(extra: String) = """<script id="initial-data">{"manga":{"url":"/title/example","title":"Example","synopsis":"Description","type":"manhwa","status":"releasing","ratedAvg":9.1,"contentRating":"safe"$extra}}</script>"""
            val catalog = adapter.parseCatalog(server.url("/").toString(), html("" )).single()
            assertTrue(catalog.tags.isEmpty())
            server.enqueue(MockResponse().setBody(html(""", "genres":[{"title":"Action"}],"formats":[{"title":"Full Color"}],"demographics":[{"title":"Shounen"}],"tags":[{"title":"Transmigration"}],"authors":[{"title":"Creator"}]""")))
            val details = adapter.details(catalog)
            assertEquals(listOf("Action", "Shounen", "Full Color", "Transmigration"), details.tags)
            assertEquals("9.1", details.score)
            assertEquals("safe", details.rating)
            assertEquals("ongoing", details.status)
            assertEquals(listOf("Creator"), details.authors)
        }
    }
    @Test
    fun rawkumaLoadsBeyondFortyAndKeepsEachCoverWithItsTitle() = runTest {
        MockWebServer().use { server ->
            val client = OkHttpClient()
            val adapter = PublicHtmlMangaSourceAdapter(
                PublicHtmlSourceConfig("rawkuma", "Rawkuma", server.url("/").toString(),
                    listOf("/manga/"), listOf("/manga/"), listOf("/?s=%s"), listOf("/manga/")),
                client, MangaWebLinkImporter(client),
            )
            fun cards(start: Int) = (start until start + 60).joinToString("") {
                """<a href="/manga/title-$it/"><img data-src="/covers/$it.jpg" alt="Title $it"></a>"""
            }
            server.enqueue(MockResponse().setBody("<img src='/logo.png'>" + cards(0) +
                """<a href="/manga/page/2/">2</a>"""))
            server.enqueue(MockResponse().setBody(cards(60)))
            val first = adapter.browse(MangaBrowseMode.Popular)
            val second = adapter.browsePage(MangaBrowseMode.Popular, 1)
            assertEquals(120, (first + second).map { it.remoteId }.distinct().size)
            (first + second).forEachIndexed { index, manga ->
                assertEquals("Title $index", manga.title)
                assertEquals(server.url("/covers/$index.jpg").toString(), manga.coverUrl)
            }
            assertTrue(adapter.browsePage(MangaBrowseMode.Popular, 2).isEmpty())
            server.takeRequest()
            assertEquals("/manga/page/2/?the_orderby=popular", server.takeRequest().path)
            assertEquals(2, server.requestCount)
        }
    }
    @Test
    fun catalogPrefersRealCoverOverLogoAndReadsSrcset() {
        val server = MockWebServer()
        server.start()
        server.use {
            val adapter = PublicHtmlMangaSourceAdapter(
                PublicHtmlSourceConfig(
                    id = "rawkuma",
                    name = "Rawkuma",
                    baseUrl = server.url("/").toString(),
                    popularPaths = listOf("/"),
                    latestPaths = listOf("/"),
                    searchPaths = listOf("/?s=%s"),
                    seriesPathHints = listOf("/manga/"),
                ),
                OkHttpClient(),
                MangaWebLinkImporter(OkHttpClient()),
            )
            server.enqueue(MockResponse().setBody("""
                <img src="/wp-content/uploads/site-logo.png" alt="Logo">
                <a href="/manga/example/">Example Manga</a>
                <a href="/manga/example/"><img src="/loading.gif" srcset="/wp-content/uploads/example-cover-128.jpg 128w, /wp-content/uploads/example-cover.jpg 700w" alt="Example Manga"></a>
            """.trimIndent()))

            val result = kotlinx.coroutines.runBlocking { adapter.browse(MangaBrowseMode.Popular).single() }

            assertTrue(result.coverUrl.orEmpty().endsWith("/wp-content/uploads/example-cover.jpg"))
        }
    }
    @Test
    fun catalogAndChapterMetadataAreExtractedFromPublicHtml() = runTest {
        MockWebServer().use { server ->
            val client = OkHttpClient()
            val base = server.url("/").toString()
            val adapter = PublicHtmlMangaSourceAdapter(
                PublicHtmlSourceConfig(
                    id = "fixture",
                    name = "Fixture",
                    baseUrl = base,
                    popularPaths = listOf("/"),
                    latestPaths = listOf("/"),
                    searchPaths = listOf("/?s=%s"),
                    seriesPathHints = listOf("/manga/"),
                    language = "ja",
                    readingOrder = "rtl",
                ),
                client,
                MangaWebLinkImporter(client),
            )
            server.enqueue(
                MockResponse().setBody(
                    """
                    <html lang="en"><head>
                    <meta property="og:description" content="A catalog description">
                    <meta property="og:image" content="/covers/example.webp">
                    <script type="application/ld+json">{"genre":["Action","Fantasy"],"inLanguage":"en"}</script>
                    </head><body>
                    <a href="/manga/example/"><img data-src="/covers/example.webp" alt="Example Manga"></a>
                    <a href="/genre/action">Action</a><a href="/genre/fantasy">Fantasy</a>
                    </body></html>
                    """.trimIndent(),
                ),
            )
            val results = adapter.browse(MangaBrowseMode.Popular)
            assertEquals(1, results.size)
            assertEquals("Example Manga", results.single().title)
            assertTrue(results.single().coverUrl.orEmpty().endsWith("/covers/example.webp"))
            assertEquals("A catalog description", results.single().description)
            assertEquals(listOf("Action", "Fantasy"), results.single().tags)
            assertEquals(listOf("en"), results.single().languages)

            server.enqueue(
                MockResponse().setBody(
                    """
                    <html lang="en"><head><meta property="og:title" content="Example Manga Updated"></head>
                    <body><h1>Example Manga Updated</h1>
                    <meta property="og:description" content="Full details">
                    <a href="/genre/action">Action</a><a href="/author/test-author">Test Author</a>
                    <a href="/manga/example/chapter-38.2/">Chapter 38.2</a>
                    <a href="/manga/example/chapter-38.1/">Chapter 38.1</a>
                    </body></html>
                    """.trimIndent(),
                ),
            )
            val details = adapter.details(results.single())
            assertEquals("Example Manga Updated", details.title)
            assertEquals("Full details", details.description)
            assertEquals(listOf("Action"), details.tags)
            assertEquals(listOf("Test Author"), details.authors)

            server.enqueue(
                MockResponse().setBody(
                    """
                    <h1>Example Manga Updated</h1>
                    <a href="/manga/example/chapter-38.2/">Chapter 38.2</a>
                    <a href="/manga/example/chapter-38.1/">Chapter 38.1</a>
                    """.trimIndent(),
                ),
            )
            val chapters = adapter.chapters(details)
            assertEquals(listOf("38.2", "38.1"), chapters.map { it.chapterNumber })
            assertEquals("ja", chapters.first().language)
            assertEquals("rtl", chapters.first().readingOrder)
        }
    }
}
