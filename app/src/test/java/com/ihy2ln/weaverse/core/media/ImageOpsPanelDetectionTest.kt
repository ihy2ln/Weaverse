package com.ihy2ln.weaverse.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageOpsPanelDetectionTest {
    @Test
    fun cleanPageWithCrossGuttersFindsFourPanels() {
        val width = 80
        val height = 100
        val black = 0xff101010.toInt()
        val white = 0xffffffff.toInt()
        val pixels = IntArray(width * height) { black }
        for (y in 48..52) for (x in 0 until width) pixels[y * width + x] = white
        for (x in 38..42) for (y in 0 until height) pixels[y * width + x] = white

        val result = detectPanelsFromArgb(width, height, pixels)

        assertEquals(OfflinePanelDetectionKind.Multiple, result.kind)
        assertEquals(4, result.boxes.size)
    }

    @Test
    fun pageWithoutNearWhiteGutterIsOnePanel() {
        val result = detectPanelsFromArgb(
            width = 80,
            height = 100,
            pixels = IntArray(80 * 100) { 0xff101010.toInt() },
        )

        assertEquals(OfflinePanelDetectionKind.Single, result.kind)
        assertEquals(1, result.boxes.size)
        assertTrue(result.message.contains("one panel"))
    }

    @Test
    fun unreadablePixelBufferReportsFailure() {
        val result = detectPanelsFromArgb(width = 80, height = 100, pixels = IntArray(4))

        assertEquals(OfflinePanelDetectionKind.Failed, result.kind)
        assertTrue(result.boxes.isEmpty())
        assertTrue(result.message.contains("could not be read"))
    }
}
