package com.ihy2ln.weaverse.core.text

import com.ihy2ln.weaverse.core.media.NormalizedPanelBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanelSeparationPlanTest {
    @Test
    fun separatedOutputPreservesOriginalMediaAndPage() {
        val original = MediaBlock(
            id = "source-block",
            mediaId = "original-page-image",
            kind = MediaKind.Image,
            pageId = "page-original",
            gridCol = 0,
            gridRow = 0,
            gridColSpan = 12,
            gridRowSpan = 12,
        )
        var nextId = 0

        val output = buildPanelSeparationOutput(
            originalBlock = original,
            croppedMediaIds = listOf("crop-a", "crop-b"),
            boxes = listOf(
                NormalizedPanelBox(0f, 0f, 1f, 0.5f),
                NormalizedPanelBox(0f, 0.5f, 1f, 1f),
            ),
            newPageId = "page-separated",
            blockId = { "new-${nextId++}" },
        )

        assertSame(original, output.originalBlock)
        assertEquals("original-page-image", (output.originalBlock as MediaBlock).mediaId)
        assertEquals("page-original", output.originalBlock.pageId)
        assertEquals(listOf("crop-a", "crop-b"), output.newBlocks.map { it.mediaId })
        assertTrue(output.newBlocks.all { it.pageId == "page-separated" })
    }
}
