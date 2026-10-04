package com.ihy2ln.weaverse.core.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Puts back pages a site serves cut into a 4x4 grid of shuffled tiles (MangaRaw; the order rides in the page URL's
 * fragment, see [MangaRawSource.scrambleOrder]). The order can mean "tile k came from k" or "tile k goes to k"; both
 * are tried and the one whose tiles meet with the smallest jumps along their edges wins, so this does not depend on
 * the site's reader code.
 */
object ScrambledPages {
    private const val GRID = 4

    fun isScrambled(url: String): Boolean = MangaRawSource.scrambleOrder(url) != null

    /** The page with its tiles back in place (JPEG), or the original bytes when it is not a shuffled page. */
    fun fix(url: String, bytes: ByteArray): ByteArray {
        val order = MangaRawSource.scrambleOrder(url) ?: return bytes
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        try {
            val inverse = IntArray(order.size).also { inv -> order.forEachIndexed { i, o -> inv[o] = i } }
            val a = assemble(source, order.toIntArray())
            val b = assemble(source, inverse)
            val best = if (seamCost(a) <= seamCost(b)) a.also { b.recycle() } else b.also { a.recycle() }
            val out = ByteArrayOutputStream()
            best.compress(Bitmap.CompressFormat.JPEG, 92, out)
            best.recycle()
            return out.toByteArray()
        } finally {
            source.recycle()
        }
    }

    /** Tile k of the result is tile from[k] of [source]; a remainder that does not divide by 4 stays where it is. */
    fun assemble(source: Bitmap, from: IntArray): Bitmap {
        val tw = source.width / GRID
        val th = source.height / GRID
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, null)
        for (k in 0 until GRID * GRID) {
            val s = from[k]
            val src = Rect((s % GRID) * tw, (s / GRID) * th, (s % GRID + 1) * tw, (s / GRID + 1) * th)
            val dst = Rect((k % GRID) * tw, (k / GRID) * th, (k % GRID + 1) * tw, (k / GRID + 1) * th)
            canvas.drawBitmap(source, src, dst, null)
        }
        return out
    }

    /** How sharply pixels jump across the inner tile edges (lower = the tiles fit together). */
    fun seamCost(bitmap: Bitmap): Long {
        val tw = bitmap.width / GRID
        val th = bitmap.height / GRID
        if (tw < 2 || th < 2) return 0
        var cost = 0L
        val step = 2
        for (i in 1 until GRID) {
            val x = i * tw
            for (y in 0 until th * GRID step step) cost += diff(bitmap.getPixel(x - 1, y), bitmap.getPixel(x, y))
            val yy = i * th
            for (xx in 0 until tw * GRID step step) cost += diff(bitmap.getPixel(xx, yy - 1), bitmap.getPixel(xx, yy))
        }
        return cost
    }

    private fun diff(a: Int, b: Int): Int =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) + abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) + abs((a and 0xFF) - (b and 0xFF))
}
