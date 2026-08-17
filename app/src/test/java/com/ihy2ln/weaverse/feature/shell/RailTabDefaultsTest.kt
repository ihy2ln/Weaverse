package com.ihy2ln.weaverse.feature.shell

import com.ihy2ln.weaverse.feature.export.ExportTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RailTabDefaultsTest {
    @Test
    fun notesModeUsesNotesRailTab() {
        assertEquals(RailTab.Notes, defaultRailTab(AppMode.Notes))
    }

    @Test
    fun notesRailTabLabelIsNotes() {
        assertEquals("Notes", RailTab.Notes.label)
    }

    @Test
    fun everyModeKeepsCodexAndPromptsAndNotesAndPictures() {
        AppMode.entries.forEach { mode ->
            val tabs = railTabsFor(mode)
            assertTrue(RailTab.Codex in tabs, "$mode missing Codex")
            assertTrue(RailTab.Prompts in tabs, "$mode missing Prompts")
            assertTrue(RailTab.Notes in tabs, "$mode missing Notes")
            assertTrue(RailTab.Pictures in tabs, "$mode missing Pictures")
        }
    }

    @Test
    fun novelDestinationsStayPlanWriteChatReview() {
        assertEquals(
            listOf("Plan", "Write", "Chat", "Review"),
            NovelDestination.entries.map { it.label },
        )
    }

    @Test
    fun roleplayAndNotesAreTopLevelModes() {
        assertEquals(listOf("Novel", "Roleplay", "Notes"), AppMode.entries.map { it.name })
    }

    @Test
    fun writeJumpPlanMenuIsSceneBeatOrChapter() {
        assertEquals(listOf("Scene beat", "Chapter"), WriteJumpKind.planMenu.map { it.label })
        assertEquals("Scene", WriteJumpKind.Scene.label)
        assertTrue(AppMode.entries.none { it.name == "Chat" })
    }

    @Test
    fun workspaceFocusIsStoryAndPictures() {
        assertEquals(listOf("Story", "Pictures"), WorkspaceFocus.entries.map { it.label })
    }

    @Test
    fun exportTabsCoverNovelRoleplayNotes() {
        assertEquals(listOf(ExportTab.Novel, ExportTab.Roleplay, ExportTab.Notes), ExportTab.entries.toList())
    }

    @Test
    fun chromeToolsStayGlobal() {
        val tools = workspaceChromeTools()
        assertTrue(tools.containsAll(listOf(RailTab.Codex, RailTab.Prompts, RailTab.Notes)))
        assertEquals(
            listOf("Codex", "Prompts", "Notes", "Snippets", "Chats", "Pictures"),
            tools.map { it.label },
        )
    }
}
