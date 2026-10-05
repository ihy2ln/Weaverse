package com.ihy2ln.weaverse.feature.games.battle.ui

import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCard
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleUnit
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import java.io.File

/**
 * Picture lookup by key, like the Unity game's Art: "builtin:<id>" (bundled card or portrait), "bestiary:<art>" (monster
 * card) or a path relative to the shared cardgame folder (a novel character's Codex picture).
 */
object CardArt {
    private const val ASSET = "file:///android_asset/images/adams_haven"

    val battleBackground = "$ASSET/cardbg/battle.jpg"

    /** Coil model for a card face's art, falling back to its owner's portrait. */
    fun card(card: BattleCard, dir: File): Any? {
        val art = Catalog.cardDefOf(card.id)?.art.orEmpty()
        if (art.startsWith("builtin:")) return "$ASSET/cards/${art.removePrefix("builtin:")}.jpg"
        if (art.isNotBlank()) File(dir, art).takeIf { it.isFile }?.let { return it }
        return portraitOf(card.owner, dir)
    }

    fun unit(unit: BattleUnit, dir: File): Any? = when {
        unit.art.startsWith("bestiary:") -> "$ASSET/bestiary/${unit.art.removePrefix("bestiary:")}.jpg"
        else -> portraitOf(unit.id, dir)
    }

    fun portraitOf(characterId: String, dir: File): Any? {
        if (characterId == Catalog.SUMMONER_ID) return "$ASSET/cardportraits/jd.jpg"
        val def = Catalog.get(characterId) ?: return null
        return portrait(def.portrait, dir)
    }

    fun portrait(key: String, dir: File): Any? = when {
        key.startsWith("builtin:") -> "$ASSET/cardportraits/${key.removePrefix("builtin:")}.jpg"
        key.isNotBlank() -> File(dir, key).takeIf { it.isFile }
        else -> null
    }
}
