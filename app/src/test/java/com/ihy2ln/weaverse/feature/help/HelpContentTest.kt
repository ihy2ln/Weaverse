package com.ihy2ln.weaverse.feature.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelpContentTest {
    @Test
    fun everySectionHasContentAndAUniqueId() {
        val ids = HelpContent.sections.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate help section id")
        HelpContent.sections.forEach { section ->
            assertTrue(section.title.isNotBlank(), "${section.id} has no title")
            assertTrue(section.summary.isNotBlank(), "${section.id} has no summary")
            assertTrue(section.entries.isNotEmpty(), "${section.id} has no entries")
            section.entries.forEach {
                assertTrue(it.heading.isNotBlank(), "${section.id} entry has no heading")
                assertTrue(it.body.isNotBlank(), "${it.heading} has no body")
            }
        }
    }

    @Test
    fun everyWorkspaceIsDocumented() {
        val titles = HelpContent.sections.map { it.title }
        listOf("Novel", "RPG", "Chatting", "Storyboard").forEach {
            assertTrue(titles.contains(it), "no help section for $it")
        }
    }

    @Test
    fun blankSearchReturnsEverything() {
        assertEquals(HelpContent.sections, HelpContent.search("  "))
    }

    @Test
    fun searchNarrowsToMatchingEntriesAndIsCaseInsensitive() {
        val hits = HelpContent.search("RUTHLESS")
        assertEquals(1, hits.size)
        assertEquals("RPG", hits.single().title)
        val rpg = HelpContent.sections.first { it.id == "rpg" }
        assertTrue(hits.single().entries.size < rpg.entries.size)
    }

    @Test
    fun searchWithNoMatchReturnsNothing() {
        assertTrue(HelpContent.search("zzzznotathing").isEmpty())
    }
}
