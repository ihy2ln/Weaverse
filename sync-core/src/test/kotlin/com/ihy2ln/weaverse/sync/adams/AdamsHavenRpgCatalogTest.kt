package com.ihy2ln.weaverse.sync.adams

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdamsHavenRpgCatalogTest {
    @Test
    fun ids_areUniqueAndPrefixed() {
        val cardIds = AdamsHavenRpgCatalog.cards.map { it.id }
        val loreIds = AdamsHavenRpgCatalog.lore.map { it.id }
        val sceneIds = AdamsHavenRpgCatalog.scenes.map { it.id }
        assertEquals(cardIds.toSet().size, cardIds.size)
        assertEquals(loreIds.toSet().size, loreIds.size)
        assertEquals(sceneIds.toSet().size, sceneIds.size)
        assertTrue(cardIds.all { it.startsWith(AdamsHavenRpgCatalog.CHAR_ID_PREFIX) })
        assertTrue(loreIds.all { it.startsWith(AdamsHavenRpgCatalog.LORE_ID_PREFIX) })
        assertTrue(sceneIds.all { AdamsHavenRpgCatalog.isRpgSceneId(it) })
    }

    @Test
    fun pack_coversGameSystemsAndScenes() {
        val loreNames = AdamsHavenRpgCatalog.lore.map { it.name }.toSet()
        listOf(
            "Adams Haven",
            "Forest Path",
            "Starter Farm Plot",
            "Farmhouse",
            "Town Districts",
            "Lane Field",
            "Gacha Cards",
            "Tiers F–SSS",
            "Fusion",
            "Lane Tactics",
            "Archipelago Ages",
            "Farming",
            "Materials",
            "Stages",
        ).forEach { assertTrue(it in loreNames, "missing lore $it") }

        val sceneKeys = AdamsHavenRpgCatalog.scenes.map { it.id.removePrefix(AdamsHavenRpgCatalog.SCENE_ID_PREFIX) }
        listOf("void", "forest", "farm", "farmhouse", "guild", "lanes", "town", "kitchen")
            .forEach { assertTrue(it in sceneKeys, "missing scene $it") }

        val classes = AdamsHavenRpgCatalog.cards.mapNotNull { it.classType }.toSet()
        listOf("Warrior", "Guardian", "Ranger", "Mage", "Healer", "Assassin", "Summoner")
            .forEach { assertTrue(it in classes, "missing class card $it") }
    }

    @Test
    fun scenes_pointAtKnownCardsAndUseRoleplayModes() {
        val cardIds = AdamsHavenRpgCatalog.cards.map { it.id }.toSet()
        val modes = setOf("messenger", "dungeonMaster", "roleplay")
        AdamsHavenRpgCatalog.scenes.forEach { scene ->
            assertTrue(scene.characterId in cardIds, "${scene.id} has unknown speaker")
            assertTrue(scene.displayMode in modes, "${scene.id} has bad mode")
            assertTrue(scene.opening.length > 40, "${scene.id} opening too thin")
            assertTrue(scene.authorsNote.isNotBlank())
        }
    }

    @Test
    fun farmLore_matchesStarterMap() {
        val farm = AdamsHavenRpgCatalog.lore.first { it.id.endsWith("farm") }.body.lowercase()
        assertTrue(farm.contains("oak"))
        assertTrue(farm.contains("weed"))
        assertTrue(farm.contains("rock"))
        assertTrue(farm.contains("tilled"))
        assertTrue(farm.contains("axe"))
        assertTrue(farm.contains("scythe"))
        assertTrue(farm.contains("pickaxe"))
    }

    @Test
    fun jsonHelpers_areValidArrays() {
        val card = AdamsHavenRpgCatalog.cards.first { it.classType == "Warrior" }
        assertTrue(AdamsHavenRpgCatalog.tagsJsonFor(card).startsWith("["))
        assertTrue(AdamsHavenRpgCatalog.extensionsJsonFor(card).contains("\"classType\":\"Warrior\""))
        assertTrue(AdamsHavenRpgCatalog.systemPromptFor(card).contains("Warrior"))
        val haven = AdamsHavenRpgCatalog.lore.first()
        assertTrue(AdamsHavenRpgCatalog.keysJsonFor(haven).contains("Adams Haven"))
    }

    @Test
    fun gmBrief_mentionsCoreLoops() {
        val brief = AdamsHavenRpgCatalog.gmBrief.body.lowercase()
        listOf("gacha", "farm", "lane", "fusion", "town").forEach {
            assertTrue(it in brief, "GM brief missing $it")
        }
    }
}
