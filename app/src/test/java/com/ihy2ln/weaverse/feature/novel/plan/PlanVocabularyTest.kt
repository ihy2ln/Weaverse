package com.ihy2ln.weaverse.feature.novel.plan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanVocabularyTest {
    @Test
    fun rpgRenamesEveryManuscriptLevel() {
        // A campaign has the same shape as a book, so RPG reuses the manuscript
        // entities and only the wording differs.
        val rpg = PlanVocabulary.Rpg
        assertEquals("Adventure", rpg.book)
        assertEquals("Day", rpg.chapter)
        assertEquals("Mission", rpg.scene)
        assertEquals("Event", rpg.sceneBeat)
    }

    @Test
    fun novelKeepsItsOriginalWording() {
        val novel = PlanVocabulary.Novel
        assertEquals("Book", novel.book)
        assertEquals("Chapter", novel.chapter)
        assertEquals("Scene", novel.scene)
        assertEquals("Scene beat", novel.sceneBeat)
    }

    @Test
    fun noLevelSharesALabelWithinAVocabulary() {
        listOf(PlanVocabulary.Novel, PlanVocabulary.Rpg).forEach { v ->
            val labels = listOf(v.book, v.chapter, v.scene, v.sceneBeat)
            assertEquals(labels.size, labels.toSet().size, "duplicate level label in $v")
            assertTrue(labels.none { it.isBlank() }, "blank level label in $v")
        }
    }
}
