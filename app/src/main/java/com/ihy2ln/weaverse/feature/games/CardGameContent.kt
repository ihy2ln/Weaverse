package com.ihy2ln.weaverse.feature.games

import kotlinx.serialization.Serializable

/**
 * The novel characters in the card game: `cardgame/weaverse-cards.json` in the app's external files folder. The
 * engine reads them through `CardGameCharacter.toDef()`; the extra fields here (codexId, effect…) keep the set
 * editable in Weaverse.
 */
@Serializable
data class CardGamePack(
    val version: Int = 1,
    val characters: List<CardGameCharacter> = emptyList(),
)

@Serializable
data class CardGameCharacter(
    val id: String,
    val name: String,
    val title: String = "",
    val role: String = "Dps",
    val element: String = "Neutral",
    val tier: Int = 4,
    val hp: Int = 140,
    val attack: Float = 18f,
    val magic: Float = 14f,
    val defense: Float = 12f,
    val resistance: Float = 12f,
    val speed: Float = 12f,
    val crit: Float = .1f,
    val critDamage: Float = 1.6f,
    val portrait: String = "",
    val bonds: List<String> = emptyList(),
    val cards: List<CardGameCard> = emptyList(),
    val ultimates: List<CardGameCard> = emptyList(),
    val source: String = "novel",
    val novel: String = "",
    val bio: String = "",
    // Weaverse only.
    val codexId: String = "",
    val magicUser: Boolean = false,
    val inGame: Boolean = true,
    val updatedAt: Long = 0L,
)

@Serializable
data class CardGameCard(
    val id: String,
    val name: String,
    val owner: String = "",
    val kind: String = "",
    val target: String = "Enemy",
    val ep: Int = 1,
    val ap: Int = 1,
    val draw: Int = 0,
    val epGain: Int = 0,
    val apGain: Int = 0,
    val duration: Int = 0,
    val power: Float = 0f,
    val heal: Float = 0f,
    val magnitude: Float = 0f,
    val status: String = "",
    val magic: Boolean = false,
    val transferEp: Boolean = false,
    val transferAp: Boolean = false,
    val retain: Boolean = false,
    val exhaust: Boolean = false,
    val initiation: Boolean = false,
    val text: String = "",
    val art: String = "",
    // Weaverse only: what the card does in plain terms; the numbers are worked out from it (CardSetBuilder).
    val effect: String = "",
)

/** The Adams Haven cast already in the game, so Weaverse never makes a second set for them. */
object BuiltinCast {
    data class Member(val id: String, val name: String, val art: String)

    val members = listOf(
        Member("kaela", "Kaela Stormfang", "kaela-stormfang"),
        Member("ghislaine", "Ghislaine Dedoldia", "ghislaine-dedoldia"),
        Member("elara", "Elara Vellum", "elara-vellum"),
        Member("helda", "Helda Frostmane", "helda-frostmane"),
        Member("daisy", "Daisy Wildheart", "daisy-bonfire-wildheart"),
        Member("clarity", "Clarity Lockhart", "clarity-lockhart"),
        Member("celestium", "Celestium", ""),
    )

    /** A Codex name that is one of the built-in cast ("Kaela", "Daisy Bonfire Wildheart"…). */
    fun match(name: String): Member? {
        val n = name.trim().lowercase()
        if (n.isBlank()) return null
        return members.firstOrNull { m ->
            val full = m.name.lowercase()
            n == full || n == m.id || n.split(' ').firstOrNull() == m.id || full.split(' ').all { it in n.split(' ') }
        }
    }

    fun artUrl(member: Member): String? =
        member.art.takeIf { it.isNotBlank() }?.let { "file:///android_asset/images/adams_haven/characters/$it.webp" }
}
