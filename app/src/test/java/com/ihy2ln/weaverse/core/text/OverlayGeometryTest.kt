package com.ihy2ln.weaverse.core.text

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OverlayGeometryTest {
    @Test fun sideHandleChangesOnlyWidthAndAnchorsOppositeEdge() {
        val frame = OverlayFrame(100f, 150f, 60f, 120f)
        val result = OverlayGeometry.resize(frame, 40f, 80f, 1, 0, 0f, 500f, 500f)
        assertEquals(100f, result.width)
        assertEquals(frame.height, result.height)
        assertEquals(frame.x - frame.width / 2, result.x - result.width / 2)
    }
    @Test fun topHandleAnchorsBottom() {
        val frame = OverlayFrame(100f, 150f, 60f, 120f)
        val result = OverlayGeometry.resize(frame, 40f, -30f, 0, -1, 0f, 500f, 500f)
        assertEquals(frame.width, result.width)
        assertEquals(150f, result.height)
        assertEquals(frame.y + frame.height / 2, result.y + result.height / 2)
    }
    @Test fun rotationTransformsLocalResizeAndMoveStaysInBounds() {
        val frame = OverlayFrame(100f, 150f, 60f, 120f)
        val result = OverlayGeometry.resize(frame, 40f, 0f, 1, 0, 90f, 500f, 500f)
        assertEquals(100f, result.x, .01f)
        assertEquals(170f, result.y, .01f)
        val moved = OverlayGeometry.move(frame, -1000f, -1000f, 90f, 500f, 500f)
        assertEquals(60f, moved.x, .01f)
        assertEquals(30f, moved.y, .01f)
    }
    @Test fun resizeStopsAtPageEdgeWithoutMovingAnchor() {
        val frame = OverlayFrame(100f, 150f, 60f, 120f)
        val result = OverlayGeometry.resize(frame, 1000f, 0f, 1, 0, 0f, 200f, 300f)
        assertEquals(200f, result.x + result.width / 2, .02f)
        assertEquals(70f, result.x - result.width / 2, .02f)
    }
}
