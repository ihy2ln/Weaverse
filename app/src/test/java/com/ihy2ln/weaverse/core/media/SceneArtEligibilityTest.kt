package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneArtEligibilityTest {
    private fun media(id: String, category: String = "", tags: String = "", type: String = "image") =
        MediaEntity(id = id, type = type, relativePath = "$id.webp", mimeType = "image/webp", byteSize = 1, category = category, tags = tags, createdAt = 0)

    @Test
    fun onlyTheStorysOwnPicturesAreScenes() {
        val codex = setOf("portrait")
        assertTrue(isSceneArtEligible(media("portrait"), codex))
        assertTrue(isSceneArtEligible(media("tavern", category = "Fantasy / Scene / Tavern"), codex))
        assertTrue(isSceneArtEligible(media("tagged", tags = "scene:forest"), codex))
        assertTrue(isSceneArtEligible(media("adams-haven-map-landscape-farm"), codex))
        // A news screenshot or a picture saved from the web is not.
        assertFalse(isSceneArtEligible(media("download", category = "Downloads", tags = "news,ea"), codex))
        assertFalse(isSceneArtEligible(media("plain"), codex))
        assertFalse(isSceneArtEligible(media("portrait", type = "video"), codex))
    }

    @Test
    fun aSceneWithoutArtShowsWhoOrWhereItNamesFirst() {
        val options = listOf(
            SceneArtOption("/codex/mira.webp", name = "Mira"),
            SceneArtOption("/codex/lantern.webp", name = "Gilded Lantern"),
            SceneArtOption("/scene/tavern.webp", words = setOf("tavern", "inn")),
            SceneArtOption("/scene/forest.webp", words = setOf("forest")),
        )
        assertEquals("/codex/lantern.webp", pickSceneArtFallback("The Gilded Lantern was loud. Mira laughed.", options))
        assertEquals("/scene/tavern.webp", pickSceneArtFallback("Rain hammered the crowded tavern.", options))
        assertNull(pickSceneArtFallback("Nothing matches here.", options))
    }
}
