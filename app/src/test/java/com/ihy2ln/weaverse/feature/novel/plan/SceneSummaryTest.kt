package com.ihy2ln.weaverse.feature.novel.plan

import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneSummaryTest {
    @Test
    fun source_prefersStoredSummary() {
        val scene = sampleScene(summary = "JD waits on the loading screen.", plainText = "Longer prose that should be ignored.")
        assertEquals("JD waits on the loading screen.", SceneSummary.source(scene))
    }

    @Test
    fun source_fallsBackToPlainText() {
        val scene = sampleScene(summary = "  ", plainText = "The loading screen had been going for thirty seconds.")
        assertEquals(
            "The loading screen had been going for thirty seconds.",
            SceneSummary.source(scene),
        )
    }

    @Test
    fun compact_keepsShortText() {
        assertEquals("A short beat.", SceneSummary.compact("A short beat.", maxChars = 96))
    }

    @Test
    fun compact_truncatesOnWordBoundary() {
        val text = "The loading screen had been going for thirty seconds longer than it should have."
        val compact = SceneSummary.compact(text, maxChars = 40)
        assertTrue(compact.endsWith("…"))
        assertTrue(compact.length <= 42)
        assertTrue(!compact.contains("should"))
    }

    @Test
    fun outlineAllowsMoreThanGrid() {
        assertTrue(SceneSummary.OUTLINE_MAX_CHARS > SceneSummary.GRID_MAX_CHARS)
        assertEquals(96, SceneSummary.GRID_MAX_CHARS)
        assertEquals(360, SceneSummary.OUTLINE_MAX_CHARS)
    }

    @Test
    fun compact_outlineKeepsMoreThanGrid() {
        val text = "JD waits on the loading screen while the gacha menu ticks through another pity counter, " +
            "then the summon flare hits and the new card name fills the whole HUD before he can blink."
        val grid = SceneSummary.compact(text, SceneSummary.GRID_MAX_CHARS)
        val outline = SceneSummary.compact(text, SceneSummary.OUTLINE_MAX_CHARS)
        assertTrue(grid.endsWith("…"))
        assertTrue(outline.length > grid.length)
        assertTrue(outline.contains("summon flare"))
        assertTrue(!grid.contains("summon flare"))
    }

    private fun sampleScene(summary: String, plainText: String) = SceneEntity(
        id = "s1",
        chapterId = "c1",
        title = "Scene 1",
        sortOrder = 0,
        docJson = "{}",
        plainText = plainText,
        summary = summary,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
