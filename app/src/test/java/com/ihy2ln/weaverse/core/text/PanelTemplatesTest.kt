package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelTemplatesTest {
    @Test
    fun everySlotFitsOnTheGrid() {
        PanelTemplates.all.forEach { template ->
            template.slots.forEachIndexed { i, slot ->
                assertTrue(
                    slot.col >= 0 && slot.row >= 0,
                    "${template.id} slot $i starts off-grid",
                )
                assertTrue(
                    slot.col + slot.colSpan <= MediaGrid.SIZE,
                    "${template.id} slot $i overflows the right edge",
                )
                assertTrue(
                    slot.row + slot.rowSpan <= MediaGrid.SIZE,
                    "${template.id} slot $i overflows the bottom edge",
                )
                assertTrue(slot.colSpan > 0 && slot.rowSpan > 0, "${template.id} slot $i is empty")
            }
        }
    }

    @Test
    fun panelsWithinATemplateNeverOverlap() {
        PanelTemplates.all.forEach { template ->
            val seen = mutableSetOf<Pair<Int, Int>>()
            template.slots.forEachIndexed { i, slot ->
                val cells = MediaGrid.cellsCovered(
                    slot.col,
                    slot.row,
                    slot.colSpan,
                    slot.rowSpan,
                    MediaGrid.SIZE,
                )
                val clash = cells.intersect(seen)
                assertTrue(clash.isEmpty(), "${template.id} slot $i overlaps at $clash")
                seen += cells
            }
        }
    }

    @Test
    fun idsAreUniqueAndResolvable() {
        val ids = PanelTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate template id")
        ids.forEach { assertNotNull(PanelTemplates.byId(it)) }
        assertNull(PanelTemplates.byId("no-such-template"))
    }

    @Test
    fun templatesDoNotFitTheCoarserDmGrid() {
        // Templates are authored against MediaGrid.SIZE. On the DM board they would
        // overflow, which is why the canvas and auto-placement both refuse them
        // there — this pins the assumption behind that guard.
        val overflowing = PanelTemplates.all.filter { template ->
            template.slots.any { slot ->
                slot.col + slot.colSpan > MediaGrid.DM_SIZE ||
                    slot.row + slot.rowSpan > MediaGrid.DM_SIZE
            }
        }
        assertTrue(
            overflowing.isNotEmpty(),
            "templates now fit DM_SIZE; the grid-size guard may no longer be needed",
        )
    }

    @Test
    fun defaultPageTemplateExists() {
        // RpPageMeta defaults every page to this id, so it must resolve.
        assertNotNull(PanelTemplates.byId("classic-6"))
    }

    @Test
    fun splashCoversThePageAndPanelCountMatchesSlots() {
        val splash = PanelTemplates.byId("splash")!!
        assertEquals(1, splash.panelCount)
        val only = splash.slots.single()
        assertEquals(MediaGrid.SIZE, only.colSpan)
        assertEquals(MediaGrid.SIZE, only.rowSpan)
        PanelTemplates.all.forEach { assertEquals(it.slots.size, it.panelCount) }
    }
}
