package com.ihy2ln.weaverse.feature.roleplay.textgame

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

/** One cell on the farm isometric board. */
data class GridCell(val col: Int, val row: Int)

/** A footprint on the farm grid (buildings occupy multiple cells). */
data class GridRect(
    val col: Int,
    val row: Int,
    val width: Int = 1,
    val height: Int = 1,
)

data class FarmPlotCell(val plotId: Int, val col: Int, val row: Int)

enum class StreetSide { Left, Right, Center }

/** Which curb a town lot sits on and how far down the street (0 = far, 1 = near crossroads). */
data class StreetSlot(
    val slot: Int,
    val side: StreetSide,
    val depth: Float,
)

/** Classic 2:1 isometric projection for the farm board. */
object IsoGrid {
    const val FARM_COLS = 8
    const val FARM_ROWS = 8

    fun cellCenter(
        col: Int,
        row: Int,
        originX: Float,
        originY: Float,
        tileW: Float,
        tileH: Float,
    ): Offset = Offset(
        x = originX + (col - row) * tileW / 2f,
        y = originY + (col + row) * tileH / 2f,
    )

    fun gridOrigin(
        canvasW: Float,
        canvasH: Float,
        cols: Int = FARM_COLS,
        rows: Int = FARM_ROWS,
        tileW: Float,
        tileH: Float,
    ): Offset {
        val gridW = (cols + rows) * tileW / 2f
        val originX = (canvasW - gridW) / 2f + rows * tileW / 2f
        val originY = canvasH * 0.34f
        return Offset(originX, originY)
    }

    fun cellAt(
        tapX: Float,
        tapY: Float,
        originX: Float,
        originY: Float,
        tileW: Float,
        tileH: Float,
        cols: Int = FARM_COLS,
        rows: Int = FARM_ROWS,
    ): GridCell? {
        val dx = tapX - originX
        val dy = tapY - originY
        val colF = dy / tileH + dx / tileW
        val rowF = dy / tileH - dx / tileW
        val col = colF.roundToInt()
        val row = rowF.roundToInt()
        for (dc in -1..1) {
            for (dr in -1..1) {
                val c = col + dc
                val r = row + dr
                if (c !in 0 until cols || r !in 0 until rows) continue
                val center = cellCenter(c, r, originX, originY, tileW, tileH)
                if (pointInDiamond(tapX, tapY, center.x, center.y, tileW / 2f, tileH / 2f)) {
                    return GridCell(c, r)
                }
            }
        }
        return null
    }

    fun pointInDiamond(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        halfW: Float,
        halfH: Float,
    ): Boolean {
        val dx = abs(x - centerX) / halfW
        val dy = abs(y - centerY) / halfH
        return dx + dy <= 1f + 0.08f
    }

    fun rectCenter(
        rect: GridRect,
        originX: Float,
        originY: Float,
        tileW: Float,
        tileH: Float,
    ): Offset {
        val col = rect.col + (rect.width - 1) / 2f
        val row = rect.row + (rect.height - 1) / 2f
        return cellCenter(col.roundToInt(), row.roundToInt(), originX, originY, tileW, tileH)
    }

    fun rectBottomAnchor(
        rect: GridRect,
        originX: Float,
        originY: Float,
        tileW: Float,
        tileH: Float,
    ): Offset {
        val col = rect.col + rect.width - 1
        val row = rect.row + rect.height - 1
        val center = cellCenter(col, row, originX, originY, tileW, tileH)
        return Offset(center.x, center.y + tileH / 2f)
    }
}

/** Mafia City-style perspective street for the town board. */
object StreetLayout {
    fun lotPosition(
        slot: StreetSlot,
        canvasW: Float,
        canvasH: Float,
    ): Offset {
        val depth = slot.depth.coerceIn(0f, 1f)
        val y = canvasH * (0.10f + depth * 0.78f)
        val roadHalfNear = canvasW * 0.28f
        val roadHalfFar = canvasW * 0.09f
        val roadHalf = roadHalfFar + (roadHalfNear - roadHalfFar) * depth
        val centerX = canvasW / 2f
        val curbGap = canvasW * (0.06f + 0.04f * depth)
        val x = when (slot.side) {
            StreetSide.Left -> centerX - roadHalf - curbGap
            StreetSide.Right -> centerX + roadHalf + curbGap
            StreetSide.Center -> centerX
        }
        return Offset(x, y)
    }

    fun lotScale(depth: Float): Float = 0.62f + depth.coerceIn(0f, 1f) * 0.48f

    fun roadTrapezoid(canvasW: Float, canvasH: Float): StreetRoad {
        val nearY = canvasH * 0.88f
        val farY = canvasH * 0.14f
        val nearHalf = canvasW * 0.28f
        val farHalf = canvasW * 0.09f
        val cx = canvasW / 2f
        return StreetRoad(
            nearLeft = Offset(cx - nearHalf, nearY),
            nearRight = Offset(cx + nearHalf, nearY),
            farLeft = Offset(cx - farHalf, farY),
            farRight = Offset(cx + farHalf, farY),
        )
    }
}

data class StreetRoad(
    val nearLeft: Offset,
    val nearRight: Offset,
    val farLeft: Offset,
    val farRight: Offset,
)

object FarmLayout {
    val PLOT_CELLS: List<FarmPlotCell> = listOf(
        FarmPlotCell(plotId = 0, col = 3, row = 3),
        FarmPlotCell(plotId = 1, col = 4, row = 3),
        FarmPlotCell(plotId = 2, col = 3, row = 4),
        FarmPlotCell(plotId = 3, col = 4, row = 4),
    )

    val KITCHEN = GridRect(col = 5, row = 0, width = 2, height = 2)
    val BARN = GridRect(col = 0, row = 0, width = 2, height = 2)

    fun plotCell(plotId: Int): FarmPlotCell? = PLOT_CELLS.firstOrNull { it.plotId == plotId }
}

/** One tilled pad on the Town Lot — a real tycoon section, not a free-floating overlay. */
data class YardPlot(
    val id: String,
    val col: Int,
    val row: Int,
)

/** Village Tycoon homestead: isometric earth pads inside a fenced grass lot. */
object TownYard {
    const val IMAGE_W = 1024f
    const val IMAGE_H = 1536f
    const val BACKDROP = "images/adams_haven/locations/town-lot-painterly.png"
    const val HOUSE_ART = "images/adams_haven/world/painterly-cottage.png"

    /** Iso origin (plot 0,0) in image-normalized space — far center of the fenced yard. */
    const val ORIGIN_NX = 0.50f
    const val ORIGIN_NY = 0.32f
    /** Diamond size as a fraction of the backdrop. 2:1 isometric. */
    const val TILE_NW = 0.26f
    const val TILE_NH = 0.13f

    val PLOTS: List<YardPlot> = listOf(
        YardPlot("p_gate", 0, 0),
        YardPlot("p_ne", 1, 0),
        YardPlot("p_nw", 0, 1),
        YardPlot("p_e", 2, 0),
        YardPlot("p_w", 0, 2),
        YardPlot("p_house", 3, 1),
        YardPlot("p_sw", 1, 3),
        YardPlot("p_se", 3, 2),
        YardPlot("p_s", 2, 3),
        YardPlot("p_plaza", 3, 3),
    )

    val DEFAULT_PLOT: Map<String, String> = mapOf(
        "forest_gate" to "p_gate",
        "guild_hall" to "p_nw",
        "workshop" to "p_e",
        "house" to "p_house",
        "deck_hall" to "p_w",
        "market" to "p_sw",
        "frosted_mug" to "p_se",
        "plaza" to "p_plaza",
    )

    fun plot(id: String): YardPlot? = PLOTS.firstOrNull { it.id == id }

    fun defaultPlotId(buildingId: String): String = DEFAULT_PLOT[buildingId] ?: PLOTS.first().id

    fun plotNorm(plot: YardPlot): Offset = Offset(
        x = ORIGIN_NX + (plot.col - plot.row) * TILE_NW / 2f,
        y = ORIGIN_NY + (plot.col + plot.row) * TILE_NH / 2f,
    )

    fun fittedImageRect(canvasW: Float, canvasH: Float): Rect {
        val scale = minOf(canvasW / IMAGE_W, canvasH / IMAGE_H)
        val w = IMAGE_W * scale
        val h = IMAGE_H * scale
        val left = (canvasW - w) / 2f
        val top = (canvasH - h) / 2f
        return Rect(left, top, left + w, top + h)
    }

    fun plotCenter(plot: YardPlot, imageRect: Rect): Offset {
        val n = plotNorm(plot)
        return Offset(imageRect.left + n.x * imageRect.width, imageRect.top + n.y * imageRect.height)
    }

    fun padSize(imageRect: Rect): Offset = Offset(
        TILE_NW * imageRect.width,
        TILE_NH * imageRect.height,
    )

    fun plotAt(x: Float, y: Float, imageRect: Rect): YardPlot? {
        val pad = padSize(imageRect)
        return PLOTS.firstOrNull { candidate ->
            val c = plotCenter(candidate, imageRect)
            IsoGrid.pointInDiamond(x, y, c.x, c.y, pad.x / 2f, pad.y / 2f)
        }
    }

    fun nearestPlot(x: Float, y: Float, imageRect: Rect): YardPlot =
        PLOTS.minBy { candidate ->
            val c = plotCenter(candidate, imageRect)
            val dx = x - c.x
            val dy = y - c.y
            dx * dx + dy * dy
        }

    fun freePlotId(taken: Set<String>, preferred: String = ""): String {
        if (preferred.isNotBlank() && preferred !in taken && plot(preferred) != null) return preferred
        val reserved = DEFAULT_PLOT.values.toSet()
        PLOTS.firstOrNull { it.id !in taken && it.id !in reserved }?.let { return it.id }
        return PLOTS.first { it.id !in taken }.id
    }

    fun position(buildingId: String, canvasW: Float, canvasH: Float): Offset {
        val slot = plot(defaultPlotId(buildingId)) ?: PLOTS.first()
        return plotCenter(slot, fittedImageRect(canvasW, canvasH))
    }
}
