package com.ihy2ln.weaverse.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryTabTest {
    @Test
    fun defaultTab_isBooks() {
        assertEquals(LibraryTab.Novels, LibraryUiState().tab)
        assertEquals("Books", LibraryTab.Novels.novelSubLabel())
    }

    @Test
    fun novelsDropdown_listsBooksThenSeries() {
        val labels = LibraryTab.entries.map { it.novelSubLabel() }
        assertEquals(listOf("Books", "Series"), labels)
    }
}
