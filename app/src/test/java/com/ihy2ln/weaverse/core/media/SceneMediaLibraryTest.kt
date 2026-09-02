package com.ihy2ln.weaverse.core.media

import com.ihy2ln.weaverse.data.db.entities.MediaEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SceneMediaLibraryTest {
    @Test
    fun movingCategoryPreservesGeneralTagsAndRefreshesSceneTags() {
        val moved = tagsAfterCategoryMove(
            "adams-haven,text-game,scene:farm,crop,scene:old-room",
            "Adams Haven / Scene / Dungeons",
        ).split(',')
        assertTrue("adams-haven" in moved)
        assertTrue("crop" in moved)
        assertTrue("scene:dungeon" in moved)
        assertFalse("scene:farm" in moved)
        assertFalse("scene:old-room" in moved)
    }

    @Test
    fun combinedSceneCategoryCreatesMultipleAiLookupTags() {
        assertEquals(
            listOf("scene:battle", "scene:dungeon"),
            sceneTagsForCategory("Adams Haven / Scene / Battle & Dungeons"),
        )
        assertTrue(sceneTagsForCategory("Adams Haven / Cards / Locations").isEmpty())
        assertEquals("Adams Haven / Scene / Town", sanitizeMediaCategory(" Adams Haven/ Scene / Town "))
    }

    @Test
    fun sceneLookupRanksCategoryAndTagsAndHonorsMediaKind() {
        val dungeonBoss = media(
            id = "boss-room",
            type = "image",
            category = "Adams Haven / Scene / Dungeons",
            tags = "scene:dungeon,boss,cave-dungeon",
        )
        val dungeonLoot = media(
            id = "loot-room",
            type = "image",
            category = "Adams Haven / Scene / Dungeons",
            tags = "scene:dungeon,loot",
        )
        val farmVideo = media(
            id = "farm-loop",
            type = "video",
            category = "Adams Haven / Scene / Farm",
            tags = "scene:farm,crops,daylight",
        )
        val media = listOf(dungeonLoot, farmVideo, dungeonBoss)

        val bossResults = rankSceneMedia(media, SceneMediaRequest("dungeon battle", "image", listOf("boss")))
        assertEquals("boss-room", bossResults.first().id)
        assertTrue(bossResults.all { it.kind == "image" })

        val videoResults = rankSceneMedia(media, SceneMediaRequest("farm", "video"))
        assertEquals(listOf("farm-loop"), videoResults.map { it.id })
    }

    private fun media(
        id: String,
        type: String,
        category: String,
        tags: String,
    ): MediaEntity = MediaEntity(
        id = id,
        type = type,
        relativePath = "media/$id",
        mimeType = if (type == "video") "video/mp4" else "image/png",
        byteSize = 1L,
        displayName = id,
        category = category,
        tags = tags,
        createdAt = 1L,
    )
}
