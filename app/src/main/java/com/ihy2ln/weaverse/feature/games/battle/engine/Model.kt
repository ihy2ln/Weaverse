package com.ihy2ln.weaverse.feature.games.battle.engine

/**
 * Kotlin port of the Adams Haven Card Game rules (S:\AI\Game\Adams Haven Card Game\Assets\Scripts\Rules, engine-free C#).
 * Names follow the C# originals (camelCased) so the two games can be read side by side. Pure Kotlin: no Android
 * dependencies, so the whole battle runs in plain unit tests.
 */
enum class BattleElement { Neutral, Fire, Water, Wind, Earth, Lightning, Light, Dark }
enum class BattleRole { Tank, Dps, Ranger, Support }
enum class BattleTarget { Self, Enemy, AllEnemies, Ally, AllAllies, None }
enum class BattleCardKind { Attack, Skill, Summoner, Ultimate, Transfer, Awakening }

/** Deterministic, seedable RNG (SplitMix64). Not bit-identical to .NET's System.Random, which does not matter here. */
class Rng(seed: Long) {
    private var state: Long = seed * -0x61c8864680b583ebL + 0x1234567L

    constructor(seed: Int) : this(seed.toLong())

    private fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** 0 until [bound]. */
    fun next(bound: Int): Int {
        if (bound <= 1) return 0
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** Int in [lo, hi). */
    fun next(lo: Int, hi: Int): Int = lo + next(hi - lo)

    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
}

class BattleCard(
    var id: String = "",
    var name: String = "",
    var owner: String = "",
    var description: String = "",
    var status: String = "",
    var kind: BattleCardKind = BattleCardKind.Attack,
    var target: BattleTarget = BattleTarget.Enemy,
    var element: BattleElement = BattleElement.Neutral,
    var ep: Int = 1,
    var ap: Int = 1,
    var draw: Int = 0,
    var epGain: Int = 0,
    var apGain: Int = 0,
    var duration: Int = 0,
    var level: Int = 1,
    var power: Float = 0f,
    var heal: Float = 0f,
    var magnitude: Float = 0f,
    var bondScale: Float = 0.12f,
    var synergy: String = "",
    var synergyScale: Float = 0f,
    var magic: Boolean = false,
    var transferEp: Boolean = false,
    var transferAp: Boolean = false,
    var partners: List<String> = emptyList(),
    var retain: Boolean = false,
    var exhaust: Boolean = false,
    var initiation: Boolean = false,
    var selfShield: Float = 0f,
    var epiphany: String = "",
) {
    val effectivePower: Float get() = power * (1f + LEVEL_STEP * (level - 1))
    val effectiveHeal: Float get() = heal * (1f + LEVEL_STEP * (level - 1))

    /** An independent copy: cards are compared by identity during a fight, so duplicates must not share state. */
    fun copy(): BattleCard = BattleCard(
        id, name, owner, description, status, kind, target, element, ep, ap, draw, epGain, apGain, duration, level,
        power, heal, magnitude, bondScale, synergy, synergyScale, magic, transferEp, transferAp, partners, retain,
        exhaust, initiation, selfShield, epiphany,
    )

    companion object {
        const val LEVEL_STEP = 0.2f
    }
}

class BattleStatus(var name: String, var magnitude: Float, var turns: Int)

class BattleUnit(
    var id: String = "",
    var name: String = "",
    var species: String = "",
    var art: String = "",
    var element: BattleElement = BattleElement.Neutral,
    var role: BattleRole = BattleRole.Dps,
    var enemy: Boolean = false,
    var innateTaunt: Boolean = false,
    var moved: Boolean = false,
    var awakeningReady: Boolean = false,
    var lane: Int = 0,
    var maxHp: Int = 0,
    var hp: Int = 0,
    var ap: Int = 1,
    var ep: Int = 3,
    var maxAp: Int = 1,
    var maxEp: Int = 3,
    var ultimate: Int = 0,
    var stress: Int = 0,
    var collapseRounds: Int = 0,
    /** Elite affix (Vampiric, Thorned, Hasted, Shielded, Enraged); lair-boss phase (1, then 2 below half health). */
    var affix: String = "",
    var boss: Boolean = false,
    var phase: Int = 0,
    /** Bestiary rank of an enemy, 1 (F) .. 9 (SSR); 0 for heroes. */
    var rank: Int = 0,
    var actionCount: Int = 0,
    var actionMax: Int = 0,
    var tenacity: Int = 0,
    var maxTenacity: Int = 0,
    var actedThisRound: Boolean = false,
    var partnerUsed: Boolean = false,
    var overcame: Boolean = false,
    var attack: Float = 0f,
    var magic: Float = 0f,
    var defense: Float = 0f,
    var resistance: Float = 0f,
    var speed: Float = 0f,
    var critRate: Float = 0.1f,
    var critDamage: Float = 1.75f,
) {
    val statuses: MutableList<BattleStatus> = mutableListOf()
    val alive: Boolean get() = hp > 0
    val taunting: Boolean get() = innateTaunt || hasStatus("Taunt")

    fun hasStatus(name: String): Boolean = statuses.any { it.name == name }

    fun statusValue(name: String): Float = statuses.firstOrNull { it.name == name }?.magnitude ?: 0f

    fun applyStatus(name: String, magnitude: Float, turns: Int) {
        if (name.isEmpty() || turns <= 0) return
        val existing = statuses.firstOrNull { it.name == name }
        if (existing != null) {
            // Shields stack; every other status is refreshed.
            existing.magnitude = if (name == "Shield") existing.magnitude + magnitude else magnitude
            existing.turns = if (name == "Shield") maxOf(existing.turns, turns) else turns
            return
        }
        statuses.add(BattleStatus(name, magnitude, turns))
    }

    val attackMultiplier: Float get() = maxOf(0.1f, 1f + statusValue("AttackUp") - statusValue("AttackDown"))
    val defenseMultiplier: Float
        get() = maxOf(0.1f, 1f + statusValue("DefenseUp") - statusValue("DefenseDown") + (if (hasStatus("IceCounter")) 0.25f else 0f))
    val effectiveSpeed: Float get() = speed * maxOf(0.1f, 1f + statusValue("Haste") - statusValue("Slow"))

    fun refill() { ap = maxAp; ep = maxEp; moved = false }
    fun healBy(amount: Int) { hp = minOf(maxHp, hp + maxOf(0, amount)) }
    fun damageBy(amount: Int) { hp = maxOf(0, hp - maxOf(0, amount)) }
    fun charge(amount: Int) { ultimate = minOf(100, ultimate + amount) }

    /** In breakdown (stress 100) a fighter can still play cheap cards, but not ultimates or anything over 1 EP. */
    fun strained(card: BattleCard): Boolean {
        if (collapseRounds <= 0) return false
        if (card.kind == BattleCardKind.Ultimate) return true
        return card.ep > 1
    }
}

class BattleFact(
    val kind: String,
    val actor: BattleUnit?,
    val target: BattleUnit,
    val card: BattleCard?,
    val amount: Int,
    val hp: Int,
    val maxHp: Int,
    val element: Float,
    val bond: Float,
    val crit: Boolean,
    val fatal: Boolean,
    val status: String,
)

class BattleRunModifiers(
    var relic: String = "",
    var scout: Boolean = false,
    var fortify: Boolean = false,
    var damageBonus: Float = 0f,
    var critBonus: Float = 0f,
    var stressScale: Float = 1f,
)

class BattleEncounterSpec(
    var depth: Int = 1,
    var seed: Int = 0,
    var kind: String = "normal",
    var theme: String = "",
    var region: String = "",
)

class BattleEncounter(
    val enemies: MutableList<BattleUnit> = mutableListOf(),
    var commander: BattleUnit? = null,
    var boss: BattleUnit? = null,
    var kind: String = "normal",
    var theme: String = "",
    var region: String = "",
    var title: String = "",
    var depth: Int = 1,
)
