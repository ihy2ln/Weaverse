package com.ihy2ln.weaverse.feature.roleplay.combat

import kotlin.math.max
import kotlinx.serialization.Serializable

@Serializable
enum class RpgCombatRuleset(val id: String, val label: String) {
    CardBattle("rpg-cards", "Focused Tactical Cards"),
    DndD20("rpg-d20", "D&D d20"),
    TextReactions("rpg-text", "Text Reactions"),
    ;

    companion object {
        fun fromId(id: String?): RpgCombatRuleset {
            val value = id.orEmpty().trim()
            return entries.firstOrNull { mode ->
                value.equals(mode.id, ignoreCase = true) ||
                    value.equals(mode.label, ignoreCase = true)
            } ?: when {
                value.contains("card", ignoreCase = true) -> CardBattle
                value.contains("text", ignoreCase = true) || value.contains("reaction", ignoreCase = true) -> TextReactions
                else -> DndD20
            }
        }
    }
}

enum class RpgStatusEffect { Stunned, Guarded, Bleeding, Exposed, Empowered }

data class RpgEnemyIntent(
    val enemyId: String,
    val label: String,
    val targetId: String? = null,
    val power: Int = 0,
)

data class RpgCombatant(
    val id: String,
    val name: String,
    val maxHp: Int,
    val hp: Int = maxHp,
    val armorClass: Int = 10,
    val attackModifier: Int = 0,
    val statuses: Set<RpgStatusEffect> = emptySet(),
    val isEnemy: Boolean = false,
)

data class RpgCombatCard(
    val id: String,
    val title: String,
    val description: String,
    val apCost: Int,
    val epCost: Int,
    val power: Int,
    val status: RpgStatusEffect? = null,
    val healing: Boolean = false,
)

data class RpgEncounterSetup(
    val id: String,
    val title: String,
    val stakes: String,
    val sceneArtAssetId: String = "",
    val campaignRuleset: RpgCombatRuleset = RpgCombatRuleset.DndD20,
    val enemies: List<RpgCombatant>,
    val party: List<RpgCombatant>,
)

data class RpgCombatState(
    val encounter: RpgEncounterSetup,
    val ruleset: RpgCombatRuleset = encounter.campaignRuleset,
    val turn: Int = 1,
    val activeCombatantId: String = encounter.party.firstOrNull()?.id.orEmpty(),
    val ap: Int = 3,
    val ep: Int = 3,
    val hand: List<RpgCombatCard> = defaultRpgCombatHand(),
    val intents: List<RpgEnemyIntent> = emptyList(),
    val combatants: List<RpgCombatant> = encounter.party + encounter.enemies,
    val log: List<String> = emptyList(),
    val finished: Boolean = false,
    val outcome: RpgCombatOutcome? = null,
)

data class RpgCombatAction(
    val actorId: String,
    val cardId: String? = null,
    val targetId: String? = null,
    val text: String = "",
    val requestedCheck: Boolean = false,
)

data class RpgCombatPreview(
    val legal: Boolean,
    val reason: String = "",
    val checkLabel: String = "",
    val estimatedEffect: String = "",
)

@Serializable
data class RpgCombatOutcome(
    val encounterId: String,
    val result: Result,
    val ruleset: RpgCombatRuleset,
    val defeatedEnemyIds: List<String> = emptyList(),
    val survivingPartyIds: List<String> = emptyList(),
    val rewards: List<String> = emptyList(),
    val relationshipDelta: Int = 0,
    val recap: String,
) {
    enum class Result { Victory, Defeat, Retreat, Surrender }
}

fun defaultRpgCombatHand(): List<RpgCombatCard> = listOf(
    RpgCombatCard("strike", "Strike", "Deal weapon damage to one enemy.", 1, 0, 5),
    RpgCombatCard("guard", "Guard", "Protect the selected ally and gain Guarded.", 1, 0, 0, RpgStatusEffect.Guarded),
    RpgCombatCard("arcane_burst", "Arcane Burst", "Blast one enemy with focused energy.", 1, 2, 8, RpgStatusEffect.Exposed),
    RpgCombatCard("rally", "Rally", "Restore an ally's resolve and empower them.", 1, 1, 4, RpgStatusEffect.Empowered, healing = true),
)

fun createRpgEncounter(setup: RpgEncounterSetup, overrideRuleset: RpgCombatRuleset? = null): RpgCombatState =
    RpgCombatState(
        encounter = setup,
        ruleset = overrideRuleset ?: setup.campaignRuleset,
        intents = setup.enemies.map { RpgEnemyIntent(it.id, "Attack", setup.party.firstOrNull()?.id, 4) },
    )

fun previewRpgCombatAction(state: RpgCombatState, action: RpgCombatAction): RpgCombatPreview {
    if (state.finished) return RpgCombatPreview(false, "This encounter is already resolved.")
    val actor = state.combatants.firstOrNull { it.id == action.actorId }
        ?: return RpgCombatPreview(false, "That character is not in the encounter.")
    if (actor.isEnemy) return RpgCombatPreview(false, "Enemy turns are controlled by the encounter.")
    return when (state.ruleset) {
        RpgCombatRuleset.CardBattle -> {
            val card = state.hand.firstOrNull { it.id == action.cardId }
                ?: return RpgCombatPreview(false, "Choose a card first.")
            val target = action.targetId?.let { id -> state.combatants.firstOrNull { it.id == id } }
            when {
                card.apCost > state.ap -> RpgCombatPreview(false, "Not enough AP.")
                card.epCost > state.ep -> RpgCombatPreview(false, "Not enough EP.")
                target == null -> RpgCombatPreview(false, "Choose a target.")
                card.healing && target.isEnemy -> RpgCombatPreview(false, "That card targets an ally.")
                !card.healing && !target.isEnemy -> RpgCombatPreview(false, "That card targets an enemy.")
                else -> RpgCombatPreview(true, estimatedEffect = if (card.healing) "Restore ${card.power} HP" else "Deal ${card.power} damage")
            }
        }
        RpgCombatRuleset.DndD20 -> RpgCombatPreview(
            legal = action.targetId != null,
            reason = if (action.targetId == null) "Choose a target or describe the check." else "",
            checkLabel = "D&D d20 check",
            estimatedEffect = "The app rolls once, then the AI narrates the result.",
        )
        RpgCombatRuleset.TextReactions -> RpgCombatPreview(
            legal = action.text.isNotBlank(),
            reason = if (action.text.isBlank()) "Describe the action first." else "",
            checkLabel = if (action.requestedCheck) "Player-confirmed check" else "",
            estimatedEffect = "The AI describes the reaction; app state remains authoritative.",
        )
    }
}

fun resolveRpgCombatAction(state: RpgCombatState, action: RpgCombatAction, seed: Long = 1L): RpgCombatState {
    val preview = previewRpgCombatAction(state, action)
    if (!preview.legal) return state.copy(log = state.log + preview.reason)
    val index = state.combatants.indexOfFirst { it.id == action.targetId }
    if (index < 0) return state.copy(log = state.log + "No valid target.")
    val target = state.combatants[index]
    val actorIndex = state.combatants.indexOfFirst { it.id == action.actorId }
    val actor = state.combatants[actorIndex]
    val random = ((seed xor state.turn.toLong() xor action.actorId.hashCode().toLong()) and Long.MAX_VALUE)
    val nextCombatants = state.combatants.toMutableList()
    var nextAp = state.ap
    var nextEp = state.ep
    val message: String
    when (state.ruleset) {
        RpgCombatRuleset.CardBattle -> {
            val card = state.hand.first { it.id == action.cardId }
            nextAp -= card.apCost
            nextEp -= card.epCost
            val amount = if (card.healing) card.power else max(1, card.power + if (random % 10L == 0L) 3 else 0)
            val hp = if (card.healing) minOf(target.maxHp, target.hp + amount) else max(0, target.hp - amount)
            val statuses = if (card.status != null) target.statuses + card.status else target.statuses
            nextCombatants[index] = target.copy(hp = hp, statuses = statuses)
            message = "${actor.name} played ${card.title} on ${target.name}."
        }
        RpgCombatRuleset.DndD20 -> {
            val d20 = (random % 20L).toInt() + 1
            val total = d20 + actor.attackModifier
            val hit = total >= target.armorClass
            val damage = if (hit) 5 + actor.attackModifier.coerceAtLeast(0) else 0
            nextCombatants[index] = target.copy(hp = max(0, target.hp - damage))
            message = "${actor.name} rolled $d20 + ${actor.attackModifier}: ${if (hit) "hit for $damage" else "miss"}."
        }
        RpgCombatRuleset.TextReactions -> {
            val succeeds = !action.requestedCheck || random % 4L != 0L
            val damage = if (succeeds && target.isEnemy) 4 else 0
            nextCombatants[index] = target.copy(hp = max(0, target.hp - damage))
            message = "${actor.name}: ${if (succeeds) "the reaction opens an advantage" else "the reaction turns against the party"}."
        }
    }
    val enemiesLeft = nextCombatants.any { it.isEnemy && it.hp > 0 }
    val partyLeft = nextCombatants.any { !it.isEnemy && it.hp > 0 }
    val finished = !enemiesLeft || !partyLeft
    val outcome = if (finished) {
        val result = if (enemiesLeft) RpgCombatOutcome.Result.Defeat else RpgCombatOutcome.Result.Victory
        RpgCombatOutcome(
            encounterId = state.encounter.id,
            result = result,
            ruleset = state.ruleset,
            defeatedEnemyIds = nextCombatants.filter { it.isEnemy && it.hp <= 0 }.map { it.id },
            survivingPartyIds = nextCombatants.filter { !it.isEnemy && it.hp > 0 }.map { it.id },
            rewards = if (result == RpgCombatOutcome.Result.Victory) listOf("Encounter reward") else emptyList(),
            relationshipDelta = if (result == RpgCombatOutcome.Result.Victory) 1 else -1,
            recap = if (result == RpgCombatOutcome.Result.Victory) "The party prevailed in ${state.encounter.title}." else "The party was overcome in ${state.encounter.title}.",
        )
    } else null
    return state.copy(
        turn = state.turn + 1,
        ap = if (nextAp <= 0) 3 else nextAp,
        ep = if (nextAp <= 0) 3 else nextEp,
        activeCombatantId = state.encounter.party.firstOrNull { it.hp > 0 }?.id.orEmpty(),
        combatants = nextCombatants,
        log = state.log + message,
        finished = finished,
        outcome = outcome,
    )
}

fun rpgCombatRulesetFromSetup(setup: String): RpgCombatRuleset {
    val raw = Regex("(?im)^(?:Game mode|Combat style):\\s*([^\\r\\n]+)")
        .find(setup)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
    RpgCombatRuleset.fromId(raw).let { parsed ->
        if (raw.equals(parsed.id, ignoreCase = true)) return parsed
    }
    return RpgCombatRuleset.entries.firstOrNull { mode ->
        raw.equals(mode.label, ignoreCase = true) ||
            raw.startsWith(mode.label + ".", ignoreCase = true)
    } ?: RpgCombatRuleset.DndD20
}
