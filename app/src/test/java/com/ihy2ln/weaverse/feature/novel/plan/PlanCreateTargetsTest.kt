package com.ihy2ln.weaverse.feature.novel.plan

import com.ihy2ln.weaverse.data.db.entities.ActEntity
import com.ihy2ln.weaverse.data.db.entities.ChapterEntity
import com.ihy2ln.weaverse.data.db.entities.SceneEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlanCreateTargetsTest {
    private val act = ActEntity("act-1", "book-1", "Act I", 0)
    private val chapter1 = ChapterEntity("ch-1", "act-1", "Chapter 1", 0)
    private val chapter2 = ChapterEntity("ch-2", "act-1", "Chapter 2", 1)
    private val sceneA = sampleScene("s-a", "ch-1", "Scene 1")
    private val sceneB = sampleScene("s-b", "ch-2", "Scene 2")
    private val outline = listOf(
        PlanOutlineNode(
            act = act,
            chapters = listOf(
                ChapterWithScenes(chapter1, listOf(sceneA)),
                ChapterWithScenes(chapter2, listOf(sceneB)),
            ),
        ),
    )

    @Test
    fun newSceneUsesSelectedScenesChapter() {
        assertEquals("ch-1", PlanCreateTargets.chapterIdForNewScene(outline, "s-a"))
    }

    @Test
    fun newSceneFallsBackToLastChapter() {
        assertEquals("ch-2", PlanCreateTargets.chapterIdForNewScene(outline, null))
    }

    @Test
    fun newChapterUsesSelectedScenesAct() {
        assertEquals("act-1", PlanCreateTargets.actIdForNewChapter(outline, "s-b"))
    }

    @Test
    fun sceneBeatUsesSelectedOrLastScene() {
        assertEquals("s-a", PlanCreateTargets.sceneIdForNewBeat(outline, "s-a"))
        assertEquals("s-b", PlanCreateTargets.sceneIdForNewBeat(outline, null))
    }

    @Test
    fun emptyOutlineHasNoTargets() {
        assertNull(PlanCreateTargets.chapterIdForNewScene(emptyList(), null))
        assertNull(PlanCreateTargets.actIdForNewChapter(emptyList(), null))
        assertNull(PlanCreateTargets.sceneIdForNewBeat(emptyList(), null))
    }

    private fun sampleScene(id: String, chapterId: String, title: String) = SceneEntity(
        id = id,
        chapterId = chapterId,
        title = title,
        sortOrder = 0,
        docJson = "{}",
        plainText = "",
        createdAt = 0L,
        updatedAt = 0L,
    )
}
