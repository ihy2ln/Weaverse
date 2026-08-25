package com.ihy2ln.weaverse.core.ui.util

import androidx.compose.ui.graphics.Color
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColorHexTest {
    @Test
    fun goldRoundTripsThroughHex() {
        val gold = Color(0xC9 / 255f, 0xA2 / 255f, 0x27 / 255f)
        val hex = gold.toHexString()
        assertEquals("#C9A227", hex)
        val parsed = parseHexColor(hex)
        assertEquals(hex, parsed.toHexString())
    }

    @Test
    fun blackIsOnlyBlack() {
        assertEquals("#000000", Color.Black.toHexString())
        assertEquals("#FFFFFF", Color.White.toHexString())
    }

    @Test
    fun parseAcceptsHashlessAndShortHex() {
        assertEquals("#C9A227", parseHexColor("c9a227").toHexString())
        assertEquals("#112233", parseHexColor("#123").toHexString())
    }
}
