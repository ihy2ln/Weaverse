package com.ihy2ln.weaverse.feature.roleplay.textgame

/**
 * Godot AdamsHavenCardGame `art/monster` portraits, registered as GKOM
 * (God Killer Of Men) crystalline variants for Text Game battles and the
 * shared Pictures library.
 */
data class AdamsHavenGkomMonster(
    val id: String,
    val mediaId: String,
    val displayName: String,
    val artAssetPath: String,
    val tier: GkomTier,
    val width: Int = 1045,
    val height: Int = 1505,
)

enum class GkomTier { Trash, Elite, Boss }

fun adamsHavenGkomMonsters(): List<AdamsHavenGkomMonster> = listOf(
    gkom("amberhide_grazer", "Amberhide Grazer", GkomTier.Trash),
    gkom("blood_opal_siren", "Blood Opal Siren", GkomTier.Elite),
    gkom("cobalt_burrower", "Cobalt Burrower", GkomTier.Trash),
    gkom("crowned_geode_knight", "Crowned Geode Knight", GkomTier.Boss),
    gkom("eclipse_core_golem", "Eclipse Core Golem", GkomTier.Boss),
    gkom("enemy_summoner", "Enemy Summoner", GkomTier.Elite),
    gkom("flintjaw_skitterer", "Flintjaw Skitterer", GkomTier.Trash),
    gkom("glasswing_mite", "Glasswing Mite", GkomTier.Trash),
    gkom("moonstone_ravager", "Moonstone Ravager", GkomTier.Elite),
    gkom("obsidian_talon", "Obsidian Talon", GkomTier.Elite),
    gkom("quartzback_hound", "Quartzback Hound", GkomTier.Trash),
    gkom("shardling_sprout", "Shardling Sprout", GkomTier.Trash),
    gkom("stormglass_wyvern", "Stormglass Wyvern", GkomTier.Boss),
    gkom("thorncrystal_stalker", "Thorncrystal Stalker", GkomTier.Elite),
    gkom("verdant_cathedral_hydra", "Verdant Cathedral Hydra", GkomTier.Boss),
    gkom("viridian_prism_warden", "Viridian Prism Warden", GkomTier.Elite),
)

private fun gkom(stem: String, title: String, tier: GkomTier) = AdamsHavenGkomMonster(
    id = stem,
    mediaId = "adams-haven-gkom-$stem",
    displayName = title,
    artAssetPath = "images/adams_haven/monsters/gkom/$stem.webp",
    tier = tier,
)

private val AUTHORED_ENEMY_GKOM = mapOf(
    "stinger" to "glasswing_mite",
    "maw_spawn" to "shardling_sprout",
    "endless_stinger" to "flintjaw_skitterer",
    "road_wisp" to "cobalt_burrower",
    "warden" to "viridian_prism_warden",
    "glassroot_sentinel" to "thorncrystal_stalker",
    "endless_warden" to "moonstone_ravager",
    "gatebreaker" to "crowned_geode_knight",
)

fun gkomForEnemy(
    enemyId: String,
    pool: List<AdamsHavenGkomMonster> = adamsHavenGkomMonsters(),
): AdamsHavenGkomMonster? = AUTHORED_ENEMY_GKOM[enemyId]?.let { mapped ->
    pool.firstOrNull { it.id == mapped }
}

fun authoredEnemyIdsForGkom(monsterId: String): List<String> = AUTHORED_ENEMY_GKOM
    .filterValues { it == monsterId }
    .keys
    .sorted()

fun gkomTierFor(kind: DungeonKind?): GkomTier = when (kind) {
    DungeonKind.Elite -> GkomTier.Elite
    DungeonKind.Boss -> GkomTier.Boss
    else -> GkomTier.Trash
}

internal fun adamsHavenGkomMediaCategory(): String = "Adams Haven / Monsters / GKOM"

internal fun adamsHavenGkomMediaTags(monster: AdamsHavenGkomMonster): String = listOf(
    "adams-haven",
    "text-game",
    "gkom",
    "monster",
    "enemy",
    "battle",
    "gkom-variant",
    monster.id,
).joinToString(",")

/**
 * Stable GKOM portrait pick for an enemy in a battle — seeded so the same foe
 * keeps the same crystalline variant across turns.
 */
fun pickGkomVariant(
    enemyId: String,
    seed: Long,
    tier: GkomTier = GkomTier.Trash,
    pool: List<AdamsHavenGkomMonster> = adamsHavenGkomMonsters(),
): AdamsHavenGkomMonster? {
    gkomForEnemy(enemyId, pool)?.let { return it }
    val tierPool = pool.filter { it.tier == tier }
    if (tierPool.isEmpty()) return null
    val mixed = (seed xor enemyId.hashCode().toLong()) and Long.MAX_VALUE
    return tierPool[(mixed % tierPool.size).toInt()]
}

/** Compatibility overload for existing callers that supplied the pool as argument three. */
fun pickGkomVariant(
    enemyId: String,
    seed: Long,
    pool: List<AdamsHavenGkomMonster>,
): AdamsHavenGkomMonster? = pickGkomVariant(enemyId, seed, GkomTier.Trash, pool)
