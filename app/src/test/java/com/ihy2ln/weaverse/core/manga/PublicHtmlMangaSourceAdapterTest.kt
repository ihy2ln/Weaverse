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
