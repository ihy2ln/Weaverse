package com.ihy2ln.weaverse.core.text

/** Grid footprint used while planning generated-panel imports. */
data class StoryboardGridItem(
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
)

/** One safe, non-overlapping destination for an imported panel image. */
data class StoryboardAssetPlacement(
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val rotationDeg: Float = 0f,
    val templateSlotIndex: Int? = null,
)

data class StoryboardAssetPlacementPlan(
    val placements: List<StoryboardAssetPlacement>,
    val remainingCount: Int,
)

/** True when any existing panel overlaps this template slot. */
fun isStoryboardSlotOccupied(
    slot: PanelSlot,
    panels: List<StoryboardGridItem>,
    gridSize: Int = MediaGrid.SIZE,
): Boolean {
    val slotCells = MediaGrid.cellsCovered(
        slot.col,
        slot.row,
        slot.colSpan,
        slot.rowSpan,
        gridSize,
    )
    return panels.any { panel ->
        MediaGrid.cellsCovered(
            panel.col,
            panel.row,
            panel.colSpan,
            panel.rowSpan,
            gridSize,
        ).any { it in slotCells }
    }
}

/**
 * Plans as many imports as fit on one page. The selected empty slot receives
 * the first image, then layout slots and finally bare grid cells are filled in
 * stable order. No returned placement overlaps an existing or planned panel.
 */
fun planStoryboardAssetPlacements(
    importCount: Int,
    panels: List<StoryboardGridItem>,
    slots: List<PanelSlot>,
    selectedSlotIndex: Int? = null,
    gridSize: Int = MediaGrid.SIZE,
): StoryboardAssetPlacementPlan {
    if (importCount <= 0) return StoryboardAssetPlacementPlan(emptyList(), 0)

    val occupied = panels.flatMapTo(mutableSetOf()) { panel ->
        MediaGrid.cellsCovered(
            panel.col,
            panel.row,
            panel.colSpan,
            panel.rowSpan,
            gridSize,
        )
    }
    val placements = mutableListOf<StoryboardAssetPlacement>()

    fun placeSlot(index: Int): Boolean {
        val slot = slots.getOrNull(index) ?: return false
        val cells = MediaGrid.cellsCovered(
            slot.col,
            slot.row,
            slot.colSpan,
            slot.rowSpan,
            gridSize,
        )
        if (cells.isEmpty() || cells.any { it in occupied }) return false
        placements += StoryboardAssetPlacement(
            col = slot.col,
            row = slot.row,
            colSpan = slot.colSpan,
            rowSpan = slot.rowSpan,
            rotationDeg = slot.rotationDeg,
            templateSlotIndex = index,
        )
        occupied += cells
        return true
    }

    if (selectedSlotIndex != null && placements.size < importCount) {
        placeSlot(selectedSlotIndex)
    }
    slots.indices.forEach { index ->
        if (placements.size < importCount && index != selectedSlotIndex) placeSlot(index)
    }
    while (placements.size < importCount) {
        val cell = firstFreeStoryboardCell(occupied, gridSize) ?: break
        placements += StoryboardAssetPlacement(
            col = cell.first,
            row = cell.second,
            colSpan = 1,
            rowSpan = 1,
        )
        occupied += cell
    }
    return StoryboardAssetPlacementPlan(
        placements = placements,
        remainingCount = importCount - placements.size,
    )
}

private fun firstFreeStoryboardCell(
    occupied: Set<Pair<Int, Int>>,
    gridSize: Int,
): Pair<Int, Int>? {
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val cell = col to row
            if (cell !in occupied) return cell
        }
    }
    return null
}
