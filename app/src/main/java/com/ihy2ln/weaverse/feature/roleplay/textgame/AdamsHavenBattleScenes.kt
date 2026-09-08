package com.ihy2ln.weaverse.feature.roleplay.textgame

/** Stable mapping between dungeon room kinds and the Adams Haven battle-art folders. */
object AdamsHavenBattleScenes {
    private val categoryMap = mapOf(
        DungeonKind.Enemy to listOf("standard_combat"),
        DungeonKind.Elite to listOf("elite_combat"),
        DungeonKind.Boss to listOf("boss_arena", "cave_boss"),
        DungeonKind.Treasure to listOf("loot", "cave_loot"),
        DungeonKind.Rest to listOf("safe_camp", "cave_camp"),
        DungeonKind.Merchant to listOf("merchant"),
        DungeonKind.Unknown to listOf("secret", "shrine", "puzzle", "upgrade"),
        DungeonKind.Entrance to listOf("exit"),
        DungeonKind.Stairs to listOf("exit"),
    )

    private val bundled = mapOf(
        "standard_combat" to "images/adams_haven/battle/standard_combat/standard-combat-01-mossgate-arena.webp",
        "elite_combat" to "images/adams_haven/battle/elite_combat/elite-combat-01-thornbound-circle.webp",
        "boss_arena" to "images/adams_haven/battle/boss_arena/boss-arena-01-heartwood-throne.webp",
        "cave_boss" to "images/adams_haven/battle/cave_boss/boss-01-iron-maw-arena.webp",
        "loot" to "images/adams_haven/battle/loot/loot-01-forest-cache.webp",
        "cave_loot" to "images/adams_haven/battle/cave_loot/loot-01-coin-hoard-alcove.webp",
        "safe_camp" to "images/adams_haven/battle/safe_camp/safe-camp-01-mosslit-camp.webp",
        "cave_camp" to "images/adams_haven/battle/cave_camp/camp-01-ember-nook.webp",
        "merchant" to "images/adams_haven/battle/merchant/merchant-01-underroot-trader.webp",
        "secret" to "images/adams_haven/battle/secret/secret-01-hidden-root-door.webp",
        "shrine" to "images/adams_haven/battle/shrine/shrine-01-healing-grove-shrine.webp",
        "puzzle" to "images/adams_haven/battle/puzzle/puzzle-01-standing-stone-mechanism.webp",
        "upgrade" to "images/adams_haven/battle/upgrade/upgrade-01-rootsmith-forge.webp",
        "exit" to "images/adams_haven/battle/exit/exit-01-forest-gate-exit.webp",
    )

    fun categories(kind: DungeonKind): List<String> = categoryMap[kind].orEmpty()

    fun stableIndex(seed: Long, x: Int, y: Int, size: Int): Int {
        if (size <= 1) return 0
        val mixed = (seed xor (x.toLong() shl 32) xor (y.toLong() * 0x9E3779B9L)) and Long.MAX_VALUE
        return (mixed % size).toInt()
    }

    fun bundledFor(kind: DungeonKind, seed: Long, x: Int, y: Int): String? {
        val categories = categories(kind)
        if (categories.isEmpty()) return null
        return bundled[categories[stableIndex(seed, x, y, categories.size)]]
    }

    fun categoryFromAssetPath(assetPath: String): String? =
        assetPath.substringAfter("images/adams_haven/battle/", "")
            .substringBefore('/')
            .takeIf(String::isNotBlank)
}
