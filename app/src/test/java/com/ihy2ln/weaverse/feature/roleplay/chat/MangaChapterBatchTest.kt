package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MangaChapterBatchTest {
    @Test
    fun failedPageDoesNotStopLaterChapterPages() = runBlocking {
        val visited = mutableListOf<Int>()
        val failed = mutableListOf<Int>()

        runMangaChapterBatch((1..5).toList(), onFailure = { index, _ -> failed += index + 1 }) { _, page ->
            visited += page
            if (page == 2) error("temporary image failure")
        }

        assertEquals(listOf(1, 2, 3, 4, 5), visited)
        assertEquals(listOf(2), failed)
    }

    @Test
    fun cancellationStillStopsChapter() {
        val visited = mutableListOf<Int>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runMangaChapterBatch((1..5).toList(), onFailure = { _, _ -> }) { _, page ->
                    visited += page
                    if (page == 2) throw CancellationException("stopped")
                }
            }
        }
        assertEquals(listOf(1, 2), visited)
    }
}
