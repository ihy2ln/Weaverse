package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaStackOpsTest {
    @Test
    fun stackMediaWithAdjacent_mergesNeighborAfter() {
        val blocks = listOf(
            Paragraph("p1", listOf(Span("hi"))),
            MediaBlock("m1", "img-a", MediaKind.Image),
            MediaBlock("m2", "img-b", MediaKind.Image),
        )
        val next = blocks.stackMediaWithAdjacent(1)!!
        assertEquals(2, next.size)
        val stack = next[1] as MediaStackBlock
        assertEquals(listOf("img-a", "img-b"), stack.mediaIds)
    }

    @Test
    fun stackMediaWithAdjacent_mergesIntoExistingStack() {
        val blocks = listOf(
            MediaStackBlock("s1", listOf("img-a", "img-b"), currentIndex = 0),
            MediaBlock("m3", "img-c", MediaKind.Image),
        )
        val next = blocks.stackMediaWithAdjacent(1)!!
        assertEquals(1, next.size)
        val stack = next[0] as MediaStackBlock
        // Onto neighbor after: pressed media is source, neighbor is target → neighbor ids first.
        assertEquals(listOf("img-a", "img-b", "img-c"), stack.mediaIds)
    }

    @Test
    fun stackMediaWithAdjacent_returnsNullWithoutNeighbor() {
        val blocks = listOf(
            Paragraph("p1", listOf(Span("only text"))),
            MediaBlock("m1", "img-a", MediaKind.Image),
        )
        assertNull(blocks.stackMediaWithAdjacent(1))
    }

    @Test
    fun stackMediaOnto_mergesDraggedOntoTarget() {
        val blocks = listOf(
            MediaBlock("m1", "img-a", MediaKind.Image, gridCol = 1, gridRow = 2),
            Paragraph("p1", listOf(Span("gap"))),
            MediaBlock("m2", "img-b", MediaKind.Image, gridCol = 4, gridRow = 5),
        )
        val next = blocks.stackMediaOnto(fromIndex = 0, ontoIndex = 2)!!
        assertEquals(2, next.size)
        val stack = next[0] as MediaStackBlock
        assertEquals(listOf("img-b", "img-a"), stack.mediaIds)
        assertEquals(4, stack.gridCol)
        assertEquals(5, stack.gridRow)
        assertTrue(next[1] is Paragraph)
    }

    @Test
    fun stackRoundTrip_persistsInDocumentJson() {
        val stacked = listOf(
            MediaBlock("m1", "img-a", MediaKind.Image, gridCol = 2, gridRow = 3),
            MediaBlock("m2", "img-b", MediaKind.Image),
        ).stackMediaWithAdjacent(0)!!
        val doc = Document(stacked)
        val restored = documentFromJson(doc.toJson())
        assertTrue(restored.blocks.single() is MediaStackBlock)
        val stack = restored.blocks.single() as MediaStackBlock
        assertEquals(listOf("img-a", "img-b"), stack.mediaIds)
        assertEquals(2, stack.gridCol)
        assertEquals(3, stack.gridRow)
    }

    @Test
    fun mediaGrid_snapAndNextFree() {
        assertEquals(0, MediaGrid.snapFraction(0f))
        assertEquals(5, MediaGrid.snapFraction(0.99f))
        assertEquals(3, MediaGrid.snapFraction(0.5f))
        val free = MediaGrid.nextFreeCell(setOf(0 to 0, 1 to 0))
        assertEquals(2 to 0, free)
    }
}
