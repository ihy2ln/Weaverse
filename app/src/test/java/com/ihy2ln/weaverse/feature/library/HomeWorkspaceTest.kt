package com.ihy2ln.weaverse.feature.library

import com.ihy2ln.weaverse.core.ui.components.label
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeWorkspaceTest {
    @Test
    fun homeCardsMatchRequestedModes() {
        assertEquals(
            listOf("Novel", "RPG", "Chatting", "Storyboard", "Notes"),
            HomeWorkspace.entries.map { it.title },
        )
        assertTrue(HomeWorkspace.Novel.blurb.contains("Plan, write", ignoreCase = true))
    }

    @Test
    fun bookshelfAdminMenuIncludesRemoveOptions() {
        val actions = listOf(
            ItemAdminAction.Export,
            ItemAdminAction.Copy,
            ItemAdminAction.AddCover,
            ItemAdminAction.Delete,
            ItemAdminAction.SelectToRemove,
        )
        assertEquals(
            listOf("Export", "Copy", "Add cover art", "Delete", "Select to remove"),
            actions.map { it.label() },
        )
    }

    @Test
    fun defaultLibraryPaneIsHomeNotBookshelf() {
        assertEquals(LibraryPane.Home, LibraryPane.entries.first())
    }
}
