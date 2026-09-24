package com.ihy2ln.weaverse.core.text

import com.ihy2ln.weaverse.core.media.NormalizedPanelBox
import java.util.UUID
import kotlin.math.roundToInt

/** New separated blocks plus the untouched source block they came from. */
data class PanelSeparationOutput(
    val originalBlock: Block,
    val newPageId: String,
    val newBlocks: List<MediaBlock>,
)

/**
 * Builds separated-panel blocks without replacing or removing the original.
 * Persistence writes [newBlocks] to a new message/page and leaves
 * [originalBlock] on its existing page.
 */
fun buildPanelSeparationOutput(
    originalBlock: Block,
    croppedMediaIds: List<String>,
    boxes: List<NormalizedPanelBox>,
    newPageId: String,
    gridSize: Int = MediaGrid.SIZE,
    blockId: () -> String = { "mb-${UUID.randomUUID()}" },
): PanelSeparationOutput {
    require(croppedMediaIds.size == boxes.size) { "Every crop needs one panel box" }
    val blocks = croppedMediaIds.zip(boxes).map { (mediaId, box) ->
        val col = (box.left * gridSize).roundToInt().coerceIn(0, gridSize - 1)
        val row = (box.top * gridSize).roundToInt().coerceIn(0, gridSize - 1)
        val colSpan = (box.width * gridSize).roundToInt().coerceIn(1, gridSize - col)
        val rowSpan = (box.height * gridSize).roundToInt().coerceIn(1, gridSize - row)
        MediaBlock(
            id = blockId(),
            mediaId = mediaId,
            kind = MediaKind.Image,
            pageId = newPageId,
        ).withGridPlacement(col, row, colSpan, rowSpan, gridSize) as MediaBlock
    }
    return PanelSeparationOutput(
        originalBlock = originalBlock,
        newPageId = newPageId,
        newBlocks = blocks,
    )
}
