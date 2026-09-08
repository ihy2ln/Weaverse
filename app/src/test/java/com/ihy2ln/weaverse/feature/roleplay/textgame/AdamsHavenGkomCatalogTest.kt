package com.ihy2ln.weaverse.feature.roleplay.textgame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AdamsHavenGkomCatalogTest {
    @Test
    fun tierPoolsHaveExpectedSixSixFourDistribution() {
        val grouped = adamsHavenGkomMonsters().groupingBy { it.tier }.eachCount()
        assertEquals(6, grouped[GkomTier.Trash])
        assertEquals(6, grouped[GkomTier.Elite])
        assertEquals(4, grouped[GkomTier.Boss])
    }

    @Test
    fun authoredEnemiesKeepTheirFixedPortraits() {
        val expected = mapOf(
            "stinger" to "glasswing_mite",
            "maw_spawn" to "shardling_sprout",
            "endless_stinger" to "flintjaw_skitterer",
            "road_wisp" to "cobalt_burrower",
            "warden" to "viridian_prism_warden",
            "glassroot_sentinel" to "thorncrystal_stalker",
            "endless_warden" to "moonstone_ravager",
            "gatebreaker" to "crowned_geode_knight",
        )
        expected.forEach { (enemyId, monsterId) ->
            assertEquals(monsterId, gkomForEnemy(enemyId)?.id)
        }
    }

    @Test
    fun seededFallbackNeverEscapesRequestedTier() {
        GkomTier.entries.forEach { tier ->
            repeat(50) { seed ->
                val selected = pickGkomVariant("unknown_$seed", seed.toLong(), tier = tier)
                assertNotNull(selected)
                assertEquals(tier, selected?.tier)
            }
        }
    }
}
