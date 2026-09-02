package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoryboardAssetPlacementTest {
    private val slots = PanelTemplates.byId("classic-6")!!.slots

    @Test
    fun selectedEmptySlotReceivesFirstImageThenOrderContinues() {
        val plan = planStoryboardAssetPlacements(
            importCount = 3,
            panels = emptyList(),
            slots = slots,
            selectedSlotIndex = 3,
        )

        assertEquals(3, plan.placements.size)
        assertEquals(3, plan.placements[0].templateSlotIndex)
        assertEquals(0, plan.placements[1].templateSlotIndex)
        assertEquals(1, plan.placements[2].templateSlotIndex)
        assertEquals(0, plan.remainingCount)
    }

    @Test
    fun occupiedSelectedSlotIsNeverOverwritten() {
        val occupied = StoryboardGridItem(
            col = slots[2].col,
            row = slots[2].row,
            colSpan = slots[2].colSpan,
            rowSpan = slots[2].rowSpan,
        )

        val plan = planStoryboardAssetPlacements(
            importCount = 1,
            panels = listOf(occupied),
            slots = slots,
            selectedSlotIndex = 2,
        )

        assertTrue(isStoryboardSlotOccupied(slots[2], listOf(occupied)))
        assertFalse(plan.placements.single().templateSlotIndex == 2)
        assertEquals(0, plan.placements.single().templateSlotIndex)
    }

    @Test
    fun fullPageReportsOverflowInsteadOfOverlapping() {
        val full = StoryboardGridItem(0, 0, MediaGrid.SIZE, MediaGrid.SIZE)
        val plan = planStoryboardAssetPlacements(
            importCount = 2,
            panels = listOf(full),
            slots = slots,
        )

        assertTrue(plan.placements.isEmpty())
        assertEquals(2, plan.remainingCount)
    }
}
