package com.ihy2ln.weaverse.ai.prompt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptAddOnsTest {
    private fun restore(block: () -> Unit) {
        try {
            block()
        } finally {
            PromptAddOns.mode = PromptingMode.Novel
            PromptAddOns.ecchiOverlay = true
            PromptAddOns.ageRating = PromptAgeRating.X
            PromptAddOns.selectedGenres = setOf(PromptAddOns.DefaultGenre)
        }
    }

    @Test
    fun `off strips ecchi blocks with zero residue`() = restore {
        PromptAddOns.ecchiOverlay = false
        val out = PromptAddOns.resolveBlocks(
            "Base role.\n{ECCHI: amwf line}\n{ECCHI:\nmultiline\nbody\n}\nTail.",
        )
        assertEquals("Base role.\n\n\nTail.", out)
        assertFalse(out.contains("amwf"), out)
        assertFalse(out.contains("multiline"), out)
    }

    @Test
    fun `on unwraps ecchi blocks and keeps the body`() = restore {
        PromptAddOns.ecchiOverlay = true
        val out = PromptAddOns.resolveBlocks("Base. {ECCHI: amwf line} Tail.")
        assertEquals("Base.  amwf line Tail.", out)
    }

    @Test
    fun `mature blocks follow their own toggle`() = restore {
        PromptAddOns.ageRating = PromptAgeRating.Pg13
        val off = PromptAddOns.resolveBlocks("A {MATURE: explicit bit} B")
        assertEquals("A  B", off)
        PromptAddOns.ageRating = PromptAgeRating.X
        val on = PromptAddOns.resolveBlocks("A {MATURE: explicit bit} B")
        assertEquals("A  explicit bit B", on)
    }

    @Test
    fun `overlay and age rating follow the toggles`() = restore {
        PromptAddOns.ecchiOverlay = false
        PromptAddOns.ageRating = PromptAgeRating.Pg13
        assertNull(PromptAddOns.overlayBlock())
        val standard = PromptAddOns.ageRatingBlock()
        assertTrue(standard.contains("PG-13"), standard)
        val stripped = PromptAddOns.applyTo(listOf("base block"))
        assertEquals(4, stripped.size)
        assertTrue(stripped[0].contains("MODE: NOVEL"))
        assertTrue(stripped[1].startsWith("ADD-ON — GENRES:"))
        assertTrue(stripped[2].contains("PG-13"))
        assertEquals("base block", stripped[3])

        PromptAddOns.ecchiOverlay = true
        PromptAddOns.ageRating = PromptAgeRating.X
        val overlay = PromptAddOns.overlayBlock()
        assertTrue(overlay!!.contains("Ecchi adult-themed Mangaka"))
        assertTrue(overlay.contains("GENDER RATIO"))
        assertTrue(overlay.contains("explicit per AGE RATING = X"))
        val applied = PromptAddOns.applyTo(listOf("base block"))
        assertEquals(5, applied.size)
        assertTrue(applied[0].contains("MODE: NOVEL"))
        assertTrue(applied[1].startsWith("ADD-ON — GENRES:"))
        assertTrue(applied[2].contains("AGE RATING: X"))
        assertTrue(applied[3].contains("Ecchi adult-themed Mangaka"))
        assertEquals("base block", applied[4])
    }

    @Test
    fun `mode selection changes the base instructions`() = restore {
        PromptAddOns.mode = PromptingMode.Rpg
        val rpg = PromptAddOns.applyTo(listOf("task"))
        assertTrue(rpg.first().contains("MODE: RPG"), rpg.first())
        assertTrue(rpg.first().contains("game master"), rpg.first())

        PromptAddOns.mode = PromptingMode.Storyboard
        val storyboard = PromptAddOns.applyTo(listOf("task"))
        assertTrue(storyboard.first().contains("MODE: STORYBOARD"), storyboard.first())
        assertTrue(storyboard.first().contains("sequential panels"), storyboard.first())
    }

    @Test
    fun `multiple selected genres produce one combined add-on`() = restore {
        PromptAddOns.selectedGenres = setOf("Romance", "Fantasy", "Comedy")
        assertEquals("ADD-ON — GENRES: Comedy, Fantasy, Romance", PromptAddOns.genreBlock())
        assertEquals("Comedy, Fantasy, Romance", PromptAddOns.genreLabel)
    }

    @Test
    fun `global template stack is idempotent`() = restore {
        val once = PromptAddOns.applyTo(listOf("task"))
        val twice = PromptAddOns.applyTo(once)
        assertEquals(once, twice)
        assertEquals(1, twice.count { it.contains("[WEAVERSE TEMPLATE]") })
    }

    @Test
    fun `ratings from PG through X produce distinct backend instructions`() = restore {
        val blocks = PromptAgeRating.entries.associateWith { rating ->
            PromptAddOns.ageRating = rating
            PromptAddOns.ageRatingBlock()
        }
        assertEquals(5, blocks.values.distinct().size)
        assertTrue(blocks.getValue(PromptAgeRating.Pg).contains("AGE RATING: PG"))
        assertTrue(blocks.getValue(PromptAgeRating.X).contains("fully explicit"))
        assertFalse(PromptAgeRating.R.allowsMatureBlocks)
        assertTrue(PromptAgeRating.Nc17.allowsMatureBlocks)
    }
}
