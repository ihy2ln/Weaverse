package com.ihy2ln.weaverse.core.manga

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MangaWebLinkImporterTest {
    private val importer = MangaWebLinkImporter(OkHttpClient())

    @Test
    fun extractsLazySrcsetAndEscapedReaderImagesInOrder() {
        val html = """
            <html><body>
              <img class="logo" src="/logo.png" />
              <img class="series-cover" src="/uploads/example-cover.jpg" />
              <img class="reader-page" data-src="/chapter/001.webp" />
              <source data-srcset="https://cdn.example.test/chapter/002.jpg 1x, https://cdn.example.test/chapter/002@2x.jpg 2x" />
              <script>{"pages":["https:\/\/cdn.example.test\/chapter\/003.png"]}</script>
            </body></html>
        """.trimIndent()

        val pages = importer.extractImageUrls("https://reader.example.test/chapter/1", html)

        assertEquals(3, pages.size)
        assertEquals("https://reader.example.test/chapter/001.webp", pages[0].url)
        assertEquals("https://cdn.example.test/chapter/002.jpg", pages[1].url)
        assertEquals("https://cdn.example.test/chapter/003.png", pages[2].url)
        assertFalse(pages.any { it.url.contains("logo") })
    }

    @Test
    fun rawkumaStyleChapterPreviewKeepsTwelvePagesAndRejectsSiteArt() {
        val chapterImages = (1..12).joinToString("\n") { page ->
            "<img src=\"https://kuma.kyut.dev/wp-content/scr/example/38.2/$page.jpg\" alt=\"\" />"
        }
        val html = """
            <img src="https://rawkuma.net/wp-content/uploads/Rawkuma-Logo.png" />
            <img src="https://ads.example.test/banner.jpg" />
            $chapterImages
        """.trimIndent()

        val pages = importer.extractImageUrls("https://rawkuma.net/manga/example/chapter-38.2/", html)

        assertEquals(12, pages.size)
        assertEquals("https://kuma.kyut.dev/wp-content/scr/example/38.2/1.jpg", pages.first().url)
        assertEquals("https://kuma.kyut.dev/wp-content/scr/example/38.2/12.jpg", pages.last().url)
    }
}
