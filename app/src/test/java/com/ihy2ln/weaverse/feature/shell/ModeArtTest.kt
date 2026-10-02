package com.ihy2ln.weaverse.feature.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ModeArtTest {
    @Test fun everyModeHasItsOwnBundledArt() {
        val keys = AppMode.entries.map { ModeArt.of(it).key }
        assertEquals(keys.size, keys.toSet().size, "each mode needs distinct art")
        (keys + ModeArt.home.key).forEach { key ->
            val file = File("src/main/assets/images/weaverse/modes/$key.webp")
            assertTrue(file.isFile && file.length() > 10_000, "missing ${file.path}")
        }
    }

    @Test fun splashChoiceFallsBackToWeaverseArt() {
        assertEquals("home", ModeArt.splash("").key)
        assertEquals("home", ModeArt.splash("gone").key)
        assertEquals("rpg", ModeArt.splash("rpg").key)
        assertEquals(AppMode.entries.size + 1, ModeArt.splashChoices.size)
    }
}
