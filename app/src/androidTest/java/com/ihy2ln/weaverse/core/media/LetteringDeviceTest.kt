package com.ihy2ln.weaverse.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LetteringDeviceTest {
    @Test fun shortTextNeverInflatesAndPreviewExportAgree() {
        val bitmap = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        val layer = TypesetLayer(RectF(.1f, .1f, .9f, .7f), "A library.",
            fontSizePx = 26f, referenceWidth = 1000f, uppercase = false)
        var measured: LetteringRenderer.Metrics? = null
        LetteringRenderer.draw(Canvas(bitmap), 1000f, 1400f, layer) { measured = it }
        assertTrue(measured!!.fontSize <= 26f)
        assertFalse(measured!!.overflow)
        bitmap.recycle()
    }
    @Test fun correctedPassagesFitWithoutCollisionAndRotationIsChecked() {
        val regions = listOf(
            com.ihy2ln.weaverse.feature.roleplay.chat.PanelTextRegion(.12f, .2f, .34f, .15f,
                "", "Ah yes, here is all the clan's knowledge… or at least part of it.", fontSizePx = 24f),
            com.ihy2ln.weaverse.feature.roleplay.chat.PanelTextRegion(.55f, .3f, .28f, .2f,
                "", "Wisdom? I see… it's a library.", fontSizePx = 32f),
        )
        assertTrue(com.ihy2ln.weaverse.feature.roleplay.chat.MangaLetteringValidator.problems(regions, 1000, 1400).isEmpty())
        assertFalse(com.ihy2ln.weaverse.feature.roleplay.chat.MangaLetteringValidator.problems(
            listOf(regions[0].copy(w = .04f, h = .03f, rotationDeg = 45f, autoFit = false)), 1000, 1400).isEmpty())
    }
    @Test fun bothDirectionsFitNarrowTallBoxesWithoutTouchingOutsidePixels() {
        for (writing in TypesetWriting.entries) {
            val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val layer = TypesetLayer(RectF(.2f, .1f, .4f, .85f),
                "All translated text must fit inside this narrow bubble without changing the bubble or losing words.",
                writing = writing, uppercase = false, autoFit = true)
            assertFalse(LetteringRenderer.draw(Canvas(bitmap), 300f, 400f, layer))
            var ink = 0
            for (y in 0 until 400) for (x in 0 until 300) {
                if (x < 60 || x >= 120 || y < 40 || y >= 340) assertEquals(Color.WHITE, bitmap.getPixel(x, y))
                else if (bitmap.getPixel(x, y) != Color.WHITE) ink++
            }
            assertTrue(ink > 0)
            bitmap.recycle()
        }
    }
    @Test fun manualOverflowIsReportedAndMaskRepairPreservesArtwork() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        assertTrue(LetteringRenderer.draw(Canvas(bitmap), 100f, 100f,
            TypesetLayer(RectF(.4f, .4f, .6f, .6f), "Far too much lettering", autoFit = false, fontSizePx = 40f)))
        val pixels = IntArray(10000) { Color.rgb(it % 256, (it / 10) % 256, (it / 100) % 256) }
        bitmap.setPixels(pixels, 0, 100, 0, 0, 100, 100)
        val mask = BooleanArray(10000)
        ImageOps.markCircleMask(mask, 100, 100, 50f, 50f, 8f)
        ImageOps.inpaintMasked(bitmap, mask)
        val after = IntArray(10000)
        bitmap.getPixels(after, 0, 100, 0, 0, 100, 100)
        mask.forEachIndexed { i, selected -> if (!selected) assertEquals(pixels[i], after[i]) }
        bitmap.recycle()
    }
}
