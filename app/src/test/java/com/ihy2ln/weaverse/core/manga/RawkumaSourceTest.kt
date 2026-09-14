package com.ihy2ln.weaverse.core.manga

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RawkumaSourceTest {
    @Test
    fun htmlCatalogAndNumericChapterPathsAreRtlJapanese() = runTest {
        MockWebServer().use { server ->
            val base = server.url("/").toString()
            server.dispatcher = mangaDispatcher { request ->
                val path = request.path.orEmpty()
                when {
                    path.startsWith("/manga/") && path.contains("order=popular") -> MockResponse().setBody(
                        """
                        <div class="listupd">
                          <a href="/manga/one-piece/"><img data-src="/covers/op.webp" alt="ワンピース"></a>
                        </div>
                        """.trimIndent(),
                    )
                    path.substringBefore('?').trimEnd('/') == "/manga/one-piece" -> MockResponse().setBody(
                        """
                        <div class="eplister" data-id="42">
                          <ul>
                            <li data-num="38.2"><a href="/manga/one-piece/38-2/"><span class="chapternum">Chapter 38.2</span></a></li>
                            <li data-num="38.1"><a href="/manga/one-piece/38-1/"><span class="chapternum">Chapter 38.1</span></a></li>
                          </ul>
                        </div>
                        """.trimIndent(),
                    )
                    path.contains("/38-2") -> MockResponse().setBody(
                        """
                        <main><div class="relative"><section>
                          <img src="/scr/38.2/1.jpg" />
                          <img src="/scr/38.2/2.jpg" />
                        </section></div></main>
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val adapter = RawkumaSource(OkHttpClient(), MangaWebLinkImporter(OkHttpClient()), base)
            val results = adapter.browse(MangaBrowseMode.Popular)
            assertEquals("ワンピース", results.single().title)
            val chapters = adapter.chapters(results.single())
            assertEquals(listOf("38.2", "38.1"), chapters.map { it.chapterNumber })
            assertEquals("ja", chapters.first().language)
            assertEquals("rtl", chapters.first().readingOrder)
            assertTrue(chapters.first().canonicalUrl.contains("/38-2"), chapters.first().canonicalUrl)
            assertTrue(!chapters.first().canonicalUrl.contains("chapter"), chapters.first().canonicalUrl)

            val pages = adapter.pages(chapters.first())
            assertEquals(2, pages.size)
            assertTrue(pages.last().remoteUrl.endsWith("/scr/38.2/2.jpg"))
        }
    }

    @Test
    fun restAndAjaxChapterListParseWhenHtmlHasNoChapterLinks() = runTest {
        MockWebServer().use { server ->
            val base = server.url("/").toString()
            server.dispatcher = mangaDispatcher { request ->
                val path = request.path.orEmpty()
                val url = request.requestUrl
                when {
                    path.startsWith("/manga/?") || path == "/manga/" || path == "/" -> MockResponse().setBody("<html></html>")
                    path.startsWith("/wp-json/wp/v2/manga") && url?.queryParameter("search") == null &&
                        url?.queryParameter("slug[]") == null -> MockResponse().setBody(
                        """
                        [{"id":42,"slug":"one-piece","link":"/manga/one-piece/","title":{"rendered":"One Piece"},
                          "_embedded":{"wp:featuredmedia":[{"source_url":"/covers/op.jpg"}]}}]
                        """.trimIndent(),
                    )
                    path.substringBefore('?').trimEnd('/') == "/manga/one-piece" -> MockResponse().setBody(
                        """<article id="manga-chapters-holder" data-id="42"><h1>One Piece</h1></article>""",
                    )
                    path.contains("admin-ajax.php") && url?.queryParameter("action") == "chapter_list" -> MockResponse().setBody(
                        """
                        {"html":"<ul><li><a href=\"/manga/one-piece/1/\">Chapter 1</a></li><li><a href=\"/manga/one-piece/2/\">Chapter 2</a></li></ul>"}
                        """.trimIndent(),
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val adapter = RawkumaSource(OkHttpClient(), MangaWebLinkImporter(OkHttpClient()), base)
            val results = adapter.browse(MangaBrowseMode.Popular)
            assertEquals("One Piece", results.single().title)
            assertTrue(results.single().coverUrl.orEmpty().endsWith("/covers/op.jpg"))
            val chapters = adapter.chapters(results.single())
            assertEquals(listOf("2", "1"), chapters.map { it.chapterNumber })
            assertEquals("ja", chapters.first().language)
            assertEquals("rtl", chapters.first().readingOrder)
        }
    }
}
