package com.ihy2ln.weaverse.core.text

/**
 * One panel's place on the storyboard grid, in [MediaGrid.SIZE] cells.
 * [rotationDeg] tilts the frame, which is how a comic page gets slanted gutters.
 */
data class PanelSlot(
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val rotationDeg: Float = 0f,
)

/**
 * A comic page layout. Applying one snaps the page's panels into [slots] in
 * order; panels beyond the slot count keep whatever placement they had, so a
 * template never loses artwork.
 */
data class PanelTemplate(
    val id: String,
    val label: String,
    val slots: List<PanelSlot>,
) {
    val panelCount: Int get() = slots.size
}

/**
 * Layouts on a 12×12 grid ([MediaGrid.SIZE]). Kept as data rather than drawing
 * code so the same list can render the picker thumbnails and drive placement.
 */
object PanelTemplates {
    private const val G = MediaGrid.SIZE

    val all: List<PanelTemplate> = listOf(
        PanelTemplate(
            id = "classic-6",
            label = "Six panel",
            slots = listOf(
                PanelSlot(0, 0, 5, 4), PanelSlot(5, 0, 7, 4),
                PanelSlot(0, 4, 7, 4), PanelSlot(7, 4, 5, 4),
                PanelSlot(0, 8, 5, 4), PanelSlot(5, 8, 7, 4),
            ),
        ),
        PanelTemplate(
            id = "pair-wide-split",
            label = "Pair · wide · split",
            slots = listOf(
                PanelSlot(0, 0, 6, 4), PanelSlot(6, 0, 6, 4),
                PanelSlot(0, 4, 12, 4),
                // The slanted gutter from a classic action page.
                PanelSlot(0, 8, 6, 4, rotationDeg = -4f),
                PanelSlot(6, 8, 6, 4, rotationDeg = -4f),
            ),
        ),
        PanelTemplate(
            id = "establishing",
            label = "Establishing shot",
            slots = listOf(
                PanelSlot(0, 0, 12, 6),
                PanelSlot(0, 6, 5, 6),
                PanelSlot(5, 6, 7, 3),
                PanelSlot(5, 9, 7, 3),
            ),
        ),
        PanelTemplate(
            id = "tall-wide-full",
            label = "Tall · wide · full",
            slots = listOf(
                PanelSlot(0, 0, 4, 5), PanelSlot(4, 0, 8, 5),
                PanelSlot(0, 5, 12, 7),
            ),
        ),
        PanelTemplate(
            id = "vertical-strip",
            label = "Vertical strip",
            slots = listOf(
                PanelSlot(0, 0, 12, 3),
                PanelSlot(0, 3, 12, 3),
                PanelSlot(0, 6, 12, 3),
                PanelSlot(0, 9, 12, 3),
            ),
        ),
        PanelTemplate(
            id = "splash",
            label = "Splash page",
            slots = listOf(PanelSlot(0, 0, 12, 12)),
        ),
    )

    fun byId(id: String): PanelTemplate? = all.firstOrNull { it.id == id }
}
