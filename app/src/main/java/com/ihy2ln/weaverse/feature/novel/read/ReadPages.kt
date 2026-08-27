package com.ihy2ln.weaverse.feature.novel.read

/**
 * One reading surface: either the book cover or a manuscript scene.
 * Next/previous pages move through this list; chapter controls jump to the
 * first page of the neighbouring chapter (or the cover).
 */
data class ReadPage(
    val index: Int,
    val kind: Kind,
    val title: String,
    val chapterId: String?,
    val chapterTitle: String,
    val sceneId: String?,
    val docJson: String = "",
    val coverPath: String? = null,
) {
    enum class Kind { Cover, Scene }
}

data class ReadChapterInput(
    val id: String,
    val title: String,
    val scenes: List<ReadSceneInput>,
)

data class ReadSceneInput(
    val id: String,
    val title: String,
    val docJson: String,
)

object ReadPager {
    fun buildPages(
        coverPath: String?,
        chapters: List<ReadChapterInput>,
    ): List<ReadPage> {
        val pages = mutableListOf<ReadPage>()
        if (!coverPath.isNullOrBlank()) {
            pages += ReadPage(
                index = 0,
                kind = ReadPage.Kind.Cover,
                title = "Cover",
                chapterId = null,
                chapterTitle = "",
                sceneId = null,
                coverPath = coverPath,
            )
        }
        chapters.forEach { chapter ->
            chapter.scenes.forEach { scene ->
                pages += ReadPage(
                    index = pages.size,
                    kind = ReadPage.Kind.Scene,
                    title = scene.title.ifBlank { "Scene" },
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    sceneId = scene.id,
                    docJson = scene.docJson,
                )
            }
        }
        return pages
    }

    fun nextIndex(pageCount: Int, current: Int): Int =
        if (pageCount <= 0) 0 else (current + 1).coerceAtMost(pageCount - 1)

    fun prevIndex(pageCount: Int, current: Int): Int =
        if (pageCount <= 0) 0 else (current - 1).coerceAtLeast(0)

    fun indexOfScene(pages: List<ReadPage>, sceneId: String?): Int {
        if (sceneId.isNullOrBlank()) return 0
        return pages.indexOfFirst { it.sceneId == sceneId }.takeIf { it >= 0 } ?: 0
    }

    /** First page of the next chapter, or last page if already at the end. */
    fun nextChapterIndex(pages: List<ReadPage>, current: Int): Int {
        if (pages.isEmpty()) return 0
        val clamped = current.coerceIn(0, pages.lastIndex)
        val currentChapter = pages[clamped].chapterId
        val next = pages.indexOfFirst { page ->
            page.index > clamped && page.kind == ReadPage.Kind.Scene && page.chapterId != currentChapter
        }
        return if (next >= 0) next else pages.lastIndex
    }

    /** First page of the previous chapter, or the cover if leaving the first chapter. */
    fun prevChapterIndex(pages: List<ReadPage>, current: Int): Int {
        if (pages.isEmpty()) return 0
        val clamped = current.coerceIn(0, pages.lastIndex)
        val currentChapter = pages[clamped].chapterId
        for (i in (clamped - 1) downTo 0) {
            val page = pages[i]
            if (page.kind == ReadPage.Kind.Cover) return 0
            if (page.chapterId != currentChapter) {
                val start = pages.indexOfFirst {
                    it.chapterId == page.chapterId && it.kind == ReadPage.Kind.Scene
                }
                return start.coerceAtLeast(0)
            }
        }
        return 0
    }
}
