package com.ihy2ln.weaverse.feature.novel.read

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadPagerTest {
    private val chapterOne = ReadChapterInput(
        id = "ch-1",
        title = "Chapter 1",
        scenes = listOf(
            ReadSceneInput("s1", "Scene 1", "{}"),
            ReadSceneInput("s2", "Scene 2", "{}"),
        ),
    )
    private val chapterTwo = ReadChapterInput(
        id = "ch-2",
        title = "Chapter 2",
        scenes = listOf(ReadSceneInput("s3", "Scene 3", "{}")),
    )

    @Test
    fun coverBecomesFirstPageWhenPresent() {
        val pages = ReadPager.buildPages("/tmp/cover.jpg", listOf(chapterOne, chapterTwo))
        assertEquals(ReadPage.Kind.Cover, pages.first().kind)
        assertEquals(4, pages.size)
        assertEquals("s1", pages[1].sceneId)
    }

    @Test
    fun nextPrevStayInRangeAndDefaultToTopOrder() {
        val pages = ReadPager.buildPages(null, listOf(chapterOne, chapterTwo))
        assertEquals(1, ReadPager.nextIndex(pages.size, 0))
        assertEquals(2, ReadPager.nextIndex(pages.size, 1))
        assertEquals(2, ReadPager.nextIndex(pages.size, 2))
        assertEquals(0, ReadPager.prevIndex(pages.size, 0))
        assertEquals(0, ReadPager.prevIndex(pages.size, 1))
    }

    @Test
    fun chapterNavJumpsToFirstSceneOfNeighbour() {
        val pages = ReadPager.buildPages("/cover", listOf(chapterOne, chapterTwo))
        // index 0 cover, 1 s1, 2 s2, 3 s3
        assertEquals(1, ReadPager.nextChapterIndex(pages, 0))
        assertEquals(3, ReadPager.nextChapterIndex(pages, 1))
        assertEquals(1, ReadPager.prevChapterIndex(pages, 3))
        assertEquals(0, ReadPager.prevChapterIndex(pages, 1))
    }

    @Test
    fun indexOfSceneFindsWriteScene() {
        val pages = ReadPager.buildPages("/cover", listOf(chapterOne, chapterTwo))
        assertEquals(3, ReadPager.indexOfScene(pages, "s3"))
        assertEquals(0, ReadPager.indexOfScene(pages, "missing"))
    }
}
