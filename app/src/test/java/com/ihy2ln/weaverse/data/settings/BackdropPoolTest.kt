package com.ihy2ln.weaverse.data.settings

import com.ihy2ln.weaverse.feature.shell.BackdropSource
import com.ihy2ln.weaverse.feature.shell.backdropSourceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class BackdropPoolTest {
    private val modes = listOf("novel", "rpg", "games", "manga", "notes", "browser")

    @Test
    fun everyModeRotatesThroughSeveralPicturesLedByItsKeyArt() {
        modes.forEach { mode ->
            val pool = BackdropPool.of(mode)
            assertEquals("art:$mode", pool.first())
            assertTrue(pool.size >= 6, "$mode has only ${pool.size} backgrounds")
            assertEquals(pool.size, pool.distinct().size)
        }
        val home = BackdropPool.of("home")
        assertTrue(home.containsAll(BackdropPool.all))
        assertTrue(home.size >= 40, "Home has only ${home.size} backgrounds")
    }

    @Test
    fun everyBundledPictureExists() {
        // Unit tests run from the app module.
        BackdropPool.all.forEach { entry ->
            val path = BackdropPool.assetPath(entry)!!
            assertTrue(File("src/main/assets/$path").isFile, "missing asset $path")
        }
    }

    @Test
    fun assetEntriesResolveToBundledArt() {
        val art = backdropSourceOf("asset:images/adams_haven/locations/mythoria.webp", emptyMap()) as BackdropSource.Art
        assertEquals("file:///android_asset/images/adams_haven/locations/mythoria.webp", art.picture)
        assertEquals("asset:images/adams_haven/locations/mythoria.webp", art.key)
    }

    @Test
    fun rotatesEveryTwoMinutesByDefault() {
        assertEquals(120, UserPreferences().backdropIntervalSeconds)
        assertTrue(ModeBackdrops.DEFAULT_INTERVAL_S in ModeBackdrops.INTERVALS)
    }
}
