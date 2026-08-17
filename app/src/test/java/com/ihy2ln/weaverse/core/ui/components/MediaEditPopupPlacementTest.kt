package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaEditPopupPlacementTest {
    @Test
    fun menuAnchorsAtThePressPoint() {
        assertEquals(IntOffset(24, 80), mediaMenuAnchor(Offset(24f, 80f)))
        assertEquals(IntOffset(0, 0), mediaMenuAnchor(Offset(-12f, -4f)))
        assertEquals(IntOffset(100, 12), mediaMenuAnchor(Offset(100.4f, 12.4f)))
    }
}
