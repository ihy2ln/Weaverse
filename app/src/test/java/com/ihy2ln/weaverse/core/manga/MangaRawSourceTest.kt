package com.ihy2ln.weaverse.core.manga

import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Fixtures are trimmed real mangaraw.ac pages and a real page-list answer (2026-10-04). */
class MangaRawSourceTest {
    private val source = MangaRawSource(OkHttpClient())
    private fun fixture(name: String) = javaClass.getResource("/mangaraw/$name")!!.readText()

    @Test
    fun catalogListsSeriesWithCleanTitlesAndCovers() {
        val list = source.parseCatalog(fixture("catalog.html"))
        assertEquals(6, list.size)
        val first = list.first()
        assertEquals("ワンピース", first.title)
        assertEquals("/manga/ワンピース-raw-free/", first.remoteId)
        assertTrue(first.coverUrl!!.startsWith("https://mangaraw.ac/uploads/images/"))
        assertTrue(list.all { it.sourceId == MangaRawSource.ID && it.languages == listOf("ja") })
    }

    @Test
    fun seriesPageGivesDetailsAndChapters() {
        val manga = MangaSearchResult(MangaRawSource.ID, "/manga/キングダム-raw-free/", "キングダム",
            canonicalUrl = "https://mangaraw.ac/manga/%e3%82%ad%e3%83%b3%e3%82%b0%e3%83%80%e3%83%a0-raw-free/")
        val details = source.parseDetails(manga, fixture("series.html"))
        assertEquals("キングダム", details.title)
        assertTrue(details.description.contains("信"))
        assertTrue("アクション" in details.tags)
        val chapters = source.parseChapters(manga, fixture("series.html"))
        assertEquals(5, chapters.size)
        assertEquals("889", chapters.first().chapterNumber)
        assertEquals("rtl", chapters.first().readingOrder)
        assertTrue(chapters.first().dateUpload > 0)
        assertTrue(chapters.first().remoteId.startsWith("/manga/キングダム-raw-free/"))
        // Used as a Referer header, so it must be plain ASCII.
        assertTrue(chapters.all { c -> c.canonicalUrl.all { it.code < 128 } })
    }

    @Test
    fun pageListDecodesHostAndCarriesTileOrder() {
        val chapter = MangaChapter(MangaRawSource.ID, "/manga/キングダム-raw-free/第889話/", "m", "キングダム", "第889話")
        val pages = source.parsePages(chapter, fixture("pages.json"))
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.remoteUrl.startsWith("https://img-cdn.stackpathcdn.app/public/key/?id=") })
        val order = MangaRawSource.scrambleOrder(pages[0].remoteUrl)
        assertEquals(listOf(14, 4, 7, 15, 9, 5, 8, 10, 2, 13, 6, 0, 12, 3, 11, 1), order)
        assertNull(MangaRawSource.scrambleOrder("https://example.com/a.jpg"))
        assertNull(MangaRawSource.scrambleOrder("https://example.com/a.jpg#ahscramble=1,2,3"))
        assertEquals("https://img-cdn.stackpathcdn.app", MangaRawSource.decode("4458585c5f16030345414b014f4842025f584d4f475c4d58444f4842024d5c5c"))
    }
}
