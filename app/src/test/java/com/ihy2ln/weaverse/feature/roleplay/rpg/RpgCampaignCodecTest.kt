package com.ihy2ln.weaverse.feature.roleplay.rpg

import com.ihy2ln.weaverse.data.db.entities.TextGameSaveEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RpgCampaignCodecTest {
    @Test
    fun roundTripPreservesNewFields() {
        val original = FirstLightChapter.newCampaign(44L, "Ryn").copy(
            flags = listOf("mira_trust"),
            recentConsequences = listOf("A quiet lane."),
        )
        val restored = RpgCampaignCodec.decode(RpgCampaignCodec.encode(original))
        assertEquals(original.party, restored.party)
        assertEquals(original.companions, restored.companions)
        assertEquals(original.crafting, restored.crafting)
        assertEquals(original.factions, restored.factions)
        assertEquals(original.flags, restored.flags)
        assertEquals(RPG_SCHEMA_VERSION, restored.schemaVersion)
    }

    @Test
    fun olderSavesReceiveDefaultsAndRemainPlayable() {
        val legacy = """{"schemaVersion":0,"rngSeed":9}"""
        val migrated = RpgCampaignCodec.decode(legacy)
        assertEquals(RPG_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(FirstLightChapter.SCENE_ONBOARDING, migrated.scene.id)
        assertTrue(migrated.companions.any { it.id == FirstLightChapter.MIRA_ID })
        assertTrue(migrated.factions.any { it.id == FirstLightChapter.WARDENS_ID })
        assertTrue(migrated.crafting.recipes.any { it.id == FirstLightChapter.SALVE_RECIPE_ID })
        val begun = RpgReducer().completeOnboarding(migrated, "Ada")
        assertTrue(begun.accepted)
        assertEquals(FirstLightChapter.SCENE_FOREST, begun.state.scene.id)
    }

    @Test
    fun rpgSaveTableIsIsolatedFromTextGameSchema() {
        val rpg = com.ihy2ln.weaverse.data.db.entities.RpgCampaignSaveEntity::class.java
        val text = TextGameSaveEntity::class.java
        assertEquals("rpg_campaign_saves", tableName(rpg))
        assertEquals("text_game_saves", tableName(text))
        assertFalse(rpg.declaredFields.map { it.name }.contains("runStateJson"))
        assertTrue(text.declaredFields.map { it.name }.contains("runStateJson"))
        assertTrue(text.declaredFields.map { it.name }.contains("persistentStateJson"))
        assertTrue(rpg.declaredFields.map { it.name }.contains("stateJson"))
    }

    private fun tableName(type: Class<*>): String {
        val entity = type.getAnnotation(androidx.room.Entity::class.java)
        return entity.tableName
    }
}

class RpgIsolationTest {
    @Test
    fun rpgPackageDoesNotImportOrCallTextGame() {
        val roots = listOf(
            File("src/main/java/com/ihy2ln/weaverse/feature/roleplay/rpg"),
            File("app/src/main/java/com/ihy2ln/weaverse/feature/roleplay/rpg"),
        )
        val dir = roots.first { it.exists() }
        val hits = dir.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val mentionsTextGame = line.contains("textgame", ignoreCase = true) ||
                        line.contains("TextGame") ||
                        line.contains("text_game_saves")
                    if (mentionsTextGame) "${file.name}:${index + 1}:$line" else null
                }
            }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }
}
