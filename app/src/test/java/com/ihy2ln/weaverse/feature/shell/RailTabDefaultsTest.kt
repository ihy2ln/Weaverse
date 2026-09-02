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
    fun novelDestinationsIncludePremiumReader() {
        assertEquals(
            listOf("Bookshelf", "Plan", "Write", "Read", "Chat", "Review"),
            NovelDestination.entries.map { it.label },
        )
    }

    @Test
    fun theFourWorkspacesPlusNotesAreTopLevelModes() {
        // Constants are persisted in shell state and the launchMode preference, so
        // Roleplay keeps its name and only carries the "RPG" label.
        assertEquals(
            listOf("Novel", "Roleplay", "Chatting", "Storyboard", "Notes"),
            AppMode.entries.map { it.name },
        )
        assertEquals(
            listOf("Novel", "RPG", "Chatting", "Storyboard", "Notes"),
            AppMode.entries.map { it.label },
        )
    }

    @Test
    fun everyModeHasRailTabsAndAValidDefault() {
        AppMode.entries.forEach { mode ->
            val tabs = railTabsFor(mode)
            assertTrue(tabs.isNotEmpty(), "$mode has no rail tabs")
            assertTrue(
                defaultRailTab(mode) in tabs,
                "$mode default rail tab is not among its own tabs",
            )
        }
    }

    @Test
    fun eachWorkspaceHasItsOwnSubModes() {
        // Checked structurally rather than against a frozen list, so adding a
        // sub-mode does not fail this test for no reason.
        listOf(
            RoleplayDestination.entries.map { it.label },
            ChattingDestination.entries.map { it.label },
            StoryboardDestination.entries.map { it.label },
            NotesDestination.entries.map { it.label },
            NovelDestination.entries.map { it.label },
        ).forEach { labels ->
            assertTrue(labels.isNotEmpty())
            assertEquals(labels.size, labels.toSet().size, "duplicate sub-mode label in $labels")
            assertTrue(labels.none { it.isBlank() }, "blank sub-mode label in $labels")
        }
        // The pieces the RPG workspace must always offer.
        val rpg = RoleplayDestination.entries.map { it.label }
        listOf("Adventure", "Inventory", "Roster", "Lore", "Town").forEach {
            assertTrue(rpg.contains(it), "RPG is missing $it")
        }
    }

    @Test
    fun destinationLookupsSurviveStaleSavedState() {
        // Personas was removed as a destination; shell state saved by an older build
        // still names it, and must fall back rather than throw.
        assertEquals(RoleplayDestination.Chats, roleplayDestinationOf("Personas"))
        assertEquals(RoleplayDestination.Chats, roleplayDestinationOf(null))
        assertEquals(RoleplayDestination.Presets, roleplayDestinationOf("Presets"))
        assertEquals(RoleplayDestination.Codex, roleplayDestinationOf("Codex"))
        assertEquals(ChattingDestination.Chats, chattingDestinationOf("nonsense"))
        assertEquals(StoryboardDestination.Window, storyboardDestinationOf("Pages"))
        assertEquals(NovelDestination.Bookshelf, novelDestinationOf("gone"))
        assertEquals(NovelDestination.Write, novelDestinationOf("Write"))
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

    @Test
    fun savedNavigationOrderKeepsNewAndUnknownItemsSafe() {
        val items = listOf("Novel", "Roleplay", "Chatting", "Storyboard", "Notes")
        assertEquals(
            listOf("Chatting", "Novel", "Roleplay", "Storyboard", "Notes"),
            applySavedOrder(items, "Chatting,Novel,removed") { it },
        )
        assertEquals(items, applySavedOrder(items, "") { it })
    }
}
