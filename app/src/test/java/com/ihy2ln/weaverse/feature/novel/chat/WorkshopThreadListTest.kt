package com.ihy2ln.weaverse.feature.novel.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkshopThreadListTest {
    private val threads = listOf(
        WorkshopThreadUi("1", "Location", pinned = false, updatedAt = 1L, messageCount = 16),
        WorkshopThreadUi("2", "Chapter 9", pinned = true, updatedAt = 2L, messageCount = 8),
        WorkshopThreadUi("3", "Story help", pinned = false, updatedAt = 3L, messageCount = 32),
    )

    @Test
    fun searchMatchesTitleCaseInsensitive() {
        val found = WorkshopThreadList.filter(threads, "chapter")
        assertEquals(listOf("Chapter 9"), found.map { it.name })
    }

    @Test
    fun blankQueryReturnsAll() {
        assertEquals(3, WorkshopThreadList.filter(threads, "  ").size)
    }

    @Test
    fun pinnedAndUnpinnedSplit() {
        assertEquals(listOf("Chapter 9"), WorkshopThreadList.pinned(threads).map { it.name })
        assertEquals(listOf("Location", "Story help"), WorkshopThreadList.unpinned(threads).map { it.name })
    }
}
