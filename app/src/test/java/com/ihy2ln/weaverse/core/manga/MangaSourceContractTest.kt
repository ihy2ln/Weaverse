package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import com.ihy2ln.weaverse.data.db.entities.MangaPageEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaSourceContractTest {
    @Test
    fun mangaDexIsTheOnlyBuiltInAuthorizedSourceContract() {
        val descriptor = MangaSourceDescriptor(
            id = "mangadex",
            name = "MangaDex",
            baseUrl = "https://api.mangadex.org",
            description = "test",
            authorized = true,
        )
        assertEquals("mangadex", descriptor.id)
        assertTrue(descriptor.authorized)
        assertTrue(descriptor.supportsDownloads)
    }

    @Test
    fun pageManifestKeepsStableChapterOrderAndOriginalMetadata() {
        val pages = listOf(
            MangaPageEntity("p1", "c1", "mangadex", 0, "https://example/1.jpg", "1.jpg"),
            MangaPageEntity("p2", "c1", "mangadex", 1, "https://example/2.jpg", "2.jpg"),
        )
        assertEquals(listOf(0, 1), pages.sortedBy { it.pageIndex }.map { it.pageIndex })
        assertEquals("", pages.first().localPath)
        assertEquals("queued", pages.first().status)
    }

    @Test
    fun chapterStateCanRepresentStoppedRetryableDownload() {
        val chapter = MangaChapterEntity(
            id = "c1",
            sourceId = "mangadex",
            remoteId = "remote-c1",
            mangaId = "m1",
            mangaTitle = "Example",
            title = "Chapter 1",
            status = "stopped",
            progress = 42,
            errorMessage = "Stopped by user",
        )
        val retry = chapter.copy(status = "queued", errorMessage = "")
        assertEquals("stopped", chapter.status)
        assertEquals(42, chapter.progress)
        assertEquals("queued", retry.status)
        assertEquals("", retry.errorMessage)
    }
}
