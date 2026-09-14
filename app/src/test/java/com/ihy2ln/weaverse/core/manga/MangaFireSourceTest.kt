package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MangaFireSourceTest {
    @Test
    fun jsonCatalogAndChaptersParseWithoutVrf() = runTest {
        MockWebServer().use { server ->
            val base = server.url("/").toString()
            server.dispatcher = mangaDispatcher { request ->
                val path = request.path.orEmpty()
                when {
                    path.startsWith("/api/titles") && path.contains("views_30d") -> MockResponse().setBody(
                        """
                        {"data":[{"hid":"op123","title":"One Piece","slug":"one-piece","cover":"/covers/op.jpg","link":"/manga/one-piece.op123"}]}
                        """.trimIndent(),
                    )
                    path.substringBefore('?') == "/api/titles/op123/chapters" -> MockResponse().setBody(
                        """
                        {"chapters":[
                          {"hid":"c1100","chap":"1100","title":"The Dawn","href":"/read/one-piece.op123/en/chapter-1100"},
                          {"hid":"c1099","chap":"1099","title":"Luffy","href":"/read/one-piece.op123/en/chapter-1099"}
                        ]}
                        """.trimIndent(),
                    )
                    path.contains("/read/") -> MockResponse().setBody(
                        """
                        <img data-src="/pages/001.jpg" />
                        <img data-src="/pages/002.jpg" />
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val adapter = MangaFireSource(OkHttpClient(), MangaWebLinkImporter(OkHttpClient()), base)
            val results = adapter.browse(MangaBrowseMode.Popular)
            assertEquals(1, results.size)
            assertEquals("One Piece", results.single().title)
            assertEquals("op123", results.single().remoteId)
            assertTrue(results.single().coverUrl.orEmpty().endsWith("/covers/op.jpg"))
            assertTrue(results.single().canonicalUrl.contains("/manga/one-piece.op123"))

            val chapters = adapter.chapters(results.single())
            assertEquals(listOf("1100", "1099"), chapters.map { it.chapterNumber })
            assertEquals("en", chapters.first().language)
            assertEquals("ltr", chapters.first().readingOrder)
            assertTrue(chapters.first().canonicalUrl.contains("/read/one-piece.op123/en/chapter-1100"))

            val pages = adapter.pages(chapters.first())
            assertEquals(2, pages.size)
            assertTrue(pages.first().remoteUrl.endsWith("/pages/001.jpg"))
        }
    }

    @Test
    fun htmlCatalogAndReadLinksParseWhenJsonIsUnavailable() = runTest {
        MockWebServer().use { server ->
            val base = server.url("/").toString()
            server.dispatcher = mangaDispatcher { request ->
                val path = request.path.orEmpty()
                when {
                    path.startsWith("/api/") -> MockResponse().setResponseCode(404)
                    path.startsWith("/filter") -> MockResponse().setBody(
                        """
                        <a href="/manga/one-piece.op123"><img src="/covers/op.jpg" alt="One Piece"></a>
                        """.trimIndent(),
                    )
                    path.contains("/manga/one-piece") -> MockResponse().setBody(
                        """
                        <h1>One Piece</h1>
                        <a href="/read/one-piece.op123/en/chapter-2">Chap 2</a>
                        <a href="/read/one-piece.op123/en/chapter-1">Chap 1</a>
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val adapter = MangaFireSource(OkHttpClient(), MangaWebLinkImporter(OkHttpClient()), base)
            val results = adapter.browse(MangaBrowseMode.Popular)
            assertEquals("One Piece", results.single().title)
            val chapters = adapter.chapters(results.single())
            assertEquals(listOf("2", "1"), chapters.map { it.chapterNumber })
        }
    }

    @Test
    fun challengeHtmlFailsWithoutSolving() = runTest {
        MockWebServer().use { server ->
            server.dispatcher = mangaDispatcher {
                MockResponse().setBody("<html><title>Just a moment...</title><div id=\"cf-chl-widget\"></div></html>")
            }
            val adapter = MangaFireSource(OkHttpClient(), MangaWebLinkImporter(OkHttpClient()), server.url("/").toString())
            val error = assertThrows<IllegalStateException> { adapter.browse(MangaBrowseMode.Popular) }
            assertTrue(error.message.orEmpty().contains("challenge", ignoreCase = true))
        }
    }
}
