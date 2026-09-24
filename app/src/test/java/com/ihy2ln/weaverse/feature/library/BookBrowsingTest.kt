package com.ihy2ln.weaverse.feature.library

import com.ihy2ln.weaverse.data.db.entities.BookEntity
import com.ihy2ln.weaverse.data.settings.ReaderSavedState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BookBrowsingTest {
    private fun book(id: String, added: Long = 1, accessed: Long = 0, genre: String = "") =
        BrowseBook(BookEntity(id, null, id, genre = genre, createdAt = added, updatedAt = added), accessedAt = accessed)
    @Test fun featuredPrefersActualAccessThenNewestAddition() {
        val older = book("old", 1, 30)
        val newest = book("new", 50)
        assertEquals(older, featuredBook(listOf(newest, older)))
        assertEquals(newest, featuredBook(listOf(newest, older.copy(accessedAt = 0))))
        assertNull(featuredBook(emptyList()))
    }
    @Test fun detailsActivityDoesNotBecomeReadingOrWriting() {
        val browsed = book("browsed", accessed = 100)
        assertTrue(booksForShelf(listOf(browsed), "reading").isEmpty())
        assertTrue(booksForShelf(listOf(browsed), "writing").isEmpty())
        assertEquals(listOf(browsed), booksForShelf(listOf(browsed), "recent"))
    }
    @Test fun legacyReaderPositionsRemainResumableWithoutInventingTimestamps() {
        val legacy = book("legacy").copy(reader = ReaderSavedState(lastSceneId = "scene"))
        assertEquals(listOf(legacy), booksForShelf(listOf(legacy), "reading"))
        assertEquals(0L, legacy.browsing.readAt)
    }
    @Test fun listAndDistinctActivitiesHaveIndependentOrdering() {
        val a = book("a").copy(browsing = BookBrowsing("a", listedAt = 10, readAt = 50))
        val b = book("b").copy(browsing = BookBrowsing("b", listedAt = 20, writeAt = 80))
        assertEquals(listOf(b, a), booksForShelf(listOf(a, b), "list"))
        assertEquals(listOf(a), booksForShelf(listOf(a, b), "reading"))
        assertEquals(listOf(b), booksForShelf(listOf(a, b), "writing"))
    }
    @Test fun searchAndGenresNeverLoseUncategorizedBooks() {
        val a = book("The Archive", genre = "Fantasy")
        val b = book("Ocean").copy(browsing = BookBrowsing("Ocean", synopsis = "A forgotten archive"))
        assertEquals(2, booksForShelf(listOf(a, b), "all", "ARCHIVE").size)
        assertEquals(listOf(b), booksForShelf(listOf(a, b), "all", genre = "Uncategorized"))
        assertEquals(listOf(a), booksForShelf(listOf(a, b), "genre:Fantasy"))
    }
}
