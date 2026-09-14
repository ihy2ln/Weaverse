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
                    <a href="/manga/example/"><img data-src="/covers/example.webp" alt="Example Manga"></a>
                    """.trimIndent(),
                ),
            )
            val results = adapter.browse(MangaBrowseMode.Popular)
            assertEquals(1, results.size)
            assertEquals("Example Manga", results.single().title)
            assertTrue(results.single().coverUrl.orEmpty().endsWith("/covers/example.webp"))

            server.enqueue(
                MockResponse().setBody(
                    """
                    <h1>Example Manga</h1>
                    <a href="/manga/example/chapter-38.2/">Chapter 38.2</a>
                    <a href="/manga/example/chapter-38.1/">Chapter 38.1</a>
                    """.trimIndent(),
                ),
            )
            val chapters = adapter.chapters(results.single())
            assertEquals(listOf("38.2", "38.1"), chapters.map { it.chapterNumber })
            assertEquals("ja", chapters.first().language)
            assertEquals("rtl", chapters.first().readingOrder)
        }
    }

    @Test
    fun madaraNumericChapterPathsAreDetectedWithoutTheWordChapter() = runTest {
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
                MockResponse().setBody("""<a href="/manga/example/"><img alt="Example Manga" src="/c.jpg"></a>"""),
            )
            val results = adapter.browse(MangaBrowseMode.Popular)
            server.enqueue(
                MockResponse().setBody(
                    """
                    <a href="/manga/example/38-2/">38.2</a>
                    <a href="/manga/example/38-1/">38.1</a>
                    """.trimIndent(),
                ),
            )
            val chapters = adapter.chapters(results.single())
            assertEquals(listOf("38.2", "38.1"), chapters.map { it.chapterNumber })
            assertTrue(chapters.first().canonicalUrl.contains("/38-2"))
            assertTrue(adapter.isChapterUrl(chapters.first().canonicalUrl))
        }
    }
}
