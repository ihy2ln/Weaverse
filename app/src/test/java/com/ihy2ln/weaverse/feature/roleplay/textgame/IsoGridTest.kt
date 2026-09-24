package com.ihy2ln.weaverse.feature.roleplay.textgame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IsoGridTest {
    @Test
    fun cellCenterProjectsClassicIsoDiamond() {
        val originX = 100f
        val originY = 50f
        val tileW = 80f
        val tileH = 40f
        val c00 = IsoGrid.cellCenter(0, 0, originX, originY, tileW, tileH)
        val c10 = IsoGrid.cellCenter(1, 0, originX, originY, tileW, tileH)
        val c01 = IsoGrid.cellCenter(0, 1, originX, originY, tileW, tileH)
        assertEquals(originX, c00.x, 0.01f)
        assertEquals(originY, c00.y, 0.01f)
        assertEquals(originX + tileW / 2f, c10.x, 0.01f)
        assertEquals(originY + tileH / 2f, c10.y, 0.01f)
        assertEquals(originX - tileW / 2f, c01.x, 0.01f)
        assertEquals(originY + tileH / 2f, c01.y, 0.01f)
    }

    @Test
    fun cellAtRoundTripsThroughProjection() {
        val tileW = 72f
        val tileH = 36f
        val origin = IsoGrid.gridOrigin(canvasW = 400f, canvasH = 600f, tileW = tileW, tileH = tileH)
        val target = GridCell(4, 3)
        val center = IsoGrid.cellCenter(target.col, target.row, origin.x, origin.y, tileW, tileH)
        val hit = IsoGrid.cellAt(center.x, center.y, origin.x, origin.y, tileW, tileH)
        assertNotNull(hit)
        assertEquals(target, hit)
    }

    @Test
    fun cellAtMissesOutsideDiamond() {
        val tileW = 72f
        val tileH = 36f
        val origin = IsoGrid.gridOrigin(canvasW = 400f, canvasH = 600f, tileW = tileW, tileH = tileH)
        assertNull(IsoGrid.cellAt(-40f, -40f, origin.x, origin.y, tileW, tileH))
    }

    @Test
    fun streetLotsGrowTowardCrossroads() {
        val w = 360f
        val h = 640f
        val near = StreetLayout.lotPosition(StreetSlot(0, StreetSide.Right, depth = 0.92f), w, h)
        val far = StreetLayout.lotPosition(StreetSlot(7, StreetSide.Center, depth = 0.08f), w, h)
        assertTrue(near.y > far.y)
        assertTrue(StreetLayout.lotScale(0.92f) > StreetLayout.lotScale(0.08f))
    }

    @Test
    fun townYardPlacesShackOnTheHomestead() {
        assertNotNull(TownYard.DEFAULT_PLOT["house"])
        assertNotNull(TownYard.DEFAULT_PLOT["deck_hall"])
        val house = TownYard.position("house", 400f, 600f)
        val gate = TownYard.position("forest_gate", 400f, 600f)
        assertTrue(house.y > gate.y)
        assertTrue(TownYard.BACKDROP.contains("town-lot-painterly"))
        assertTrue(TownYard.HOUSE_ART.contains("painterly-cottage"))
        assertEquals(10, TownYard.PLOTS.size)
        TownYard.PLOTS.forEach { plot ->
            val n = TownYard.plotNorm(plot)
            assertTrue(n.x in 0.12f..0.88f, "plot ${plot.id} x=${n.x}")
            assertTrue(n.y in 0.28f..0.80f, "plot ${plot.id} y=${n.y}")
        }
    }

    @Test
    fun townYardPlotsAreIsoDiamondsOnTheFittedImage() {
        val rect = TownYard.fittedImageRect(400f, 600f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(400f, rect.width, 0.01f)
        assertEquals(600f, rect.height, 0.01f)
        val housePlot = TownYard.plot(TownYard.defaultPlotId("house"))!!
        val center = TownYard.plotCenter(housePlot, rect)
        assertEquals(housePlot, TownYard.plotAt(center.x, center.y, rect))
        assertEquals(housePlot, TownYard.nearestPlot(center.x + 4f, center.y - 3f, rect))
    }

    @Test
    fun farmPlotClusterIsFourCells() {
        assertEquals(4, FarmLayout.PLOT_CELLS.size)
        assertNotNull(FarmLayout.plotCell(0))
        assertEquals(GridCell(3, 3), GridCell(FarmLayout.PLOT_CELLS[0].col, FarmLayout.PLOT_CELLS[0].row))
    }
}
