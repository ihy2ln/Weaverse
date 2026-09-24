package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.dao.MangaDao
import com.ihy2ln.weaverse.data.db.entities.MangaChapterEntity
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MangaDownloadPreservationTest {
    @Test fun repeatWebDownloadNeverReplacesCompletedOrInFlightOriginals() = runBlocking {
        val dao = mockk<MangaDao>()
        val db = mockk<WeaverseDatabase> { every { mangaDao() } returns dao }
        val repository = MangaDownloadRepository(mockk(), db, mockk(), mockk(), mockk(), mockk(), mockk())
        for (status in listOf("completed", "queued", "downloading")) {
            val original = MangaChapterEntity("saved", "weblink", "remote", "series", "Real title", "Chapter 1",
                status = status, pageCount = 25, progress = 100, read = true, bookmarked = true, lastPageRead = 24)
            coEvery { dao.getChapterByRemoteId("weblink", any()) } returns original
            val returned = repository.enqueueWebSnapshot(WebLinkSnapshot("https://example.com/chapter/1", "Changed website title", emptyList()))
            assertEquals(original, returned)
        }
        coVerify(exactly = 0) { dao.upsertChapter(any()) }
        coVerify(exactly = 0) { dao.upsertPages(any()) }
    }
}
