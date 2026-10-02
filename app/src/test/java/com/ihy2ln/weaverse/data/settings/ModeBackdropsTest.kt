package com.ihy2ln.weaverse.data.settings

import com.ihy2ln.weaverse.feature.shell.BackdropFile
import com.ihy2ln.weaverse.feature.shell.BackdropSource
import com.ihy2ln.weaverse.feature.shell.backdropSourceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModeBackdropsTest {
    @Test
    fun roundTripsAndDropsEmptyModes() {
        val map = mapOf("novel" to listOf("video:rain", "art:rpg", "video:rain"), "rpg" to emptyList())
        assertEquals(mapOf("novel" to listOf("video:rain", "art:rpg")), ModeBackdrops.decode(ModeBackdrops.encode(map)))
        assertEquals(emptyMap<String, List<String>>(), ModeBackdrops.decode("not json"))
        assertEquals(emptyMap<String, List<String>>(), ModeBackdrops.decode(null))
    }

    @Test
    fun entriesResolveToSources() {
        val files = mapOf("m1" to BackdropFile("/x/pic.webp", video = false), "m2" to BackdropFile("/x/clip.mp4", video = true))
        assertEquals(BackdropSource.Wallpaper, backdropSourceOf("wallpaper", files))
        assertEquals("asset:///videos/ambient/ocean.mp4", (backdropSourceOf("video:ocean", files) as BackdropSource.Video).uri)
        assertEquals("rpg", (backdropSourceOf("art:rpg", files) as BackdropSource.Art).brand.key)
        assertTrue(backdropSourceOf("media:m1", files) is BackdropSource.Art)
        assertEquals("/x/clip.mp4", (backdropSourceOf("media:m2", files) as BackdropSource.Video).uri)
        assertNull(backdropSourceOf("media:gone", files))
    }
}
