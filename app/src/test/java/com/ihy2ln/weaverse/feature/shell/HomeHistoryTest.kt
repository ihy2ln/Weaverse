package com.ihy2ln.weaverse.feature.shell

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HomeHistoryTest {
    private fun item(id: String, time: Long, mode: String = "Novel", kind: String = "book") =
        HomeItem(mode, kind, id, time, "", id, null, null, null, null)

    @Test fun newestTenAreSelectedPerMode() {
        val shelves = recentShelves((1..15).map { item("n$it", it.toLong()) } + item("game", 1, "Games", "chat"))
        assertEquals((15 downTo 6).map { "n$it" }, shelves.getValue("Novel").map { it.contentId })
        assertEquals(1, shelves.getValue("Games").size)
    }
    @Test fun reopeningDeduplicatesAndMovesToFront() {
        val shelves = recentShelves(listOf(item("a", 1), item("b", 2), item("a", 3)))
        assertEquals(listOf("a", "b"), shelves.getValue("Novel").map { it.contentId })
    }
    @Test fun mixedTypesKeepDistinctIdentityAndShareLimit() {
        val shelves = recentShelves(listOf(item("same", 1, "Notes", "note"), item("same", 2, "Notes", "thread")))
        assertEquals(listOf("thread", "note"), shelves.getValue("Notes").map { it.kind })
    }
    @Test fun noHistoryProducesNoSyntheticContent() {
        assertTrue(recentShelves(emptyList()).isEmpty())
    }
}
