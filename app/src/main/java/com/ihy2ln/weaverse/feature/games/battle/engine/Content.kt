package com.ihy2ln.weaverse.feature.games.battle.engine

import kotlin.math.max
import kotlin.math.min

private fun <T : Enum<T>> parseEnum(values: Array<T>, name: String?): T? = values.firstOrNull { it.name == name }

/** A card as data. Built-in cards are written in [BuiltinContent]; novel cards arrive from Weaverse. */
class CardDef(
    var id: String = "",
    var name: String = "",
    var owner: String = "",
    /** Attack, Skill, Transfer, Ultimate, Summoner, Awakening. Empty = worked out from the numbers. */
    var kind: String = "",
    var target: String = "Enemy",
    var ep: Int = 1,
    var ap: Int = 1,
    var draw: Int = 0,
    var epGain: Int = 0,
    var apGain: Int = 0,
    var duration: Int = 0,
    var power: Float = 0f,
    var heal: Float = 0f,
    var magnitude: Float = 0f,
    var status: String = "",
    var magic: Boolean = false,
    var transferEp: Boolean = false,
    var transferAp: Boolean = false,
    var retain: Boolean = false,
    var exhaust: Boolean = false,
    var initiation: Boolean = false,
    /** Flavour line shown under the rules text. */
    var text: String = "",
    /** "builtin:<id>" = bundled card art; anything else is a file path relative to the cardgame folder. */
    var art: String = "",
) {
    fun toBattleCard(element: BattleElement, partners: List<String>?): BattleCard {
        val card = BattleCard(
            id = id, name = name, owner = owner, ep = ep, ap = ap, power = power, heal = heal, status = status,
            magnitude = magnitude, duration = duration, magic = magic, draw = draw, epGain = epGain, apGain = apGain,
            transferEp = transferEp, transferAp = transferAp, retain = retain, exhaust = exhaust, initiation = initiation,
            element = element, partners = partners ?: emptyList(), description = text,
        )
        card.target = parseEnum(BattleTarget.entries.toTypedArray(), target) ?: BattleTarget.Enemy
        val k = if (kind.isNotEmpty()) parseEnum(BattleCardKind.entries.toTypedArray(), kind) else null
        card.kind = k ?: when {
            owner == Catalog.SUMMONER_ID -> BattleCardKind.Summoner
            transferEp || transferAp -> BattleCardKind.Transfer
            power > 0 -> BattleCardKind.Attack
            else -> BattleCardKind.Skill
        }
        return card
    }

    /** Keeps imported numbers inside the ranges the built-in cards use, so a typo (or an over-eager AI) cannot break the game. */
    fun sanitize(): CardDef {
        id = clean(id, "card")
        name = if (name.isEmpty()) id else name.trim()
        val ult = kind == "Ultimate"
        val awakening = kind == "Awakening"
        ep = if (ult || awakening) 0 else ep.coerceIn(0, 3)
        ap = if (ult || awakening) 0 else ap.coerceIn(0, 2)
        power = clampF(power, 0f, if (ult) 5f else 3.2f)
        heal = clampF(heal, 0f, if (ult) 60f else 34f)
        magnitude = when (status) {
            "Poison", "Burn", "Regen" -> clampF(magnitude, 0f, 12f)
            "IceCounter" -> clampF(magnitude, 0f, 1.6f)
            "Shield" -> clampF(magnitude, 0f, 40f)
            else -> clampF(magnitude, 0f, .6f)
        }
        duration = duration.coerceIn(0, 3)
        draw = draw.coerceIn(0, 3); epGain = epGain.coerceIn(0, 2); apGain = apGain.coerceIn(0, 1)
        if (status !in Catalog.KNOWN_STATUSES) { status = ""; magnitude = 0f; duration = 0 }
        if (status.isNotEmpty() && duration == 0) duration = 1
        if (parseEnum(BattleTarget.entries.toTypedArray(), target) == null) target = if (power > 0) "Enemy" else "Self"
        // An "ally" card that also deals damage would never find a legal target.
        if ((target == "Ally" || target == "AllAllies" || target == "Self") && power > 0) power = 0f
        return this
    }

    companion object {
        private fun clampF(v: Float, lo: Float, hi: Float): Float = if (v.isNaN()) lo else max(lo, min(hi, v))

        fun clean(id: String, fallback: String): String {
            if (id.isEmpty()) return fallback
            return buildString { for (c in id.lowercase()) append(if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_') }
        }
    }
}

class CharacterDef(
    var id: String = "",
    var name: String = "",
    var title: String = "",
    /** Tank, Dps, Ranger, Support  /  Neutral, Fire, Water, Wind, Earth, Lightning, Light, Dark */
    var role: String = "Dps",
    var element: String = "Neutral",
    /** 0 (F) .. 9; scales health and offence like the Adams Haven roster (3 = baseline). */
    var tier: Int = 3,
    var hp: Int = 140,
    var attack: Float = 18f,
    var magic: Float = 14f,
    var defense: Float = 12f,
    var resistance: Float = 12f,
    var speed: Float = 12f,
    var crit: Float = .1f,
    var critDamage: Float = 1.6f,
    /** "builtin:<id>" or a file path relative to the cardgame folder. */
    var portrait: String = "",
    var bonds: List<String> = emptyList(),
    var cards: MutableList<CardDef> = mutableListOf(),
    var ultimates: MutableList<CardDef> = mutableListOf(),
    var awakening: CardDef? = null,
    /** "adams-haven" for the built-in cast, "novel" for characters brought in from Weaverse. */
    var source: String = "novel",
    var novel: String = "",
    var bio: String = "",
) {
    val roleEnum: BattleRole get() = parseEnum(BattleRole.entries.toTypedArray(), role) ?: BattleRole.Dps
    val elementEnum: BattleElement get() = parseEnum(BattleElement.entries.toTypedArray(), element) ?: BattleElement.Neutral

    fun toUnit(): BattleUnit {
        val scale = TIER_SCALE[tier.coerceIn(0, 9)] / TIER_SCALE[3]
        val maxHp = max(1, (hp * scale).toInt())
        return BattleUnit(
            id = id, name = name, art = portrait, role = roleEnum, element = elementEnum, maxHp = maxHp, hp = maxHp,
            attack = attack * scale, magic = magic * scale, defense = defense * scale, resistance = resistance * scale,
            speed = speed, critRate = crit, critDamage = critDamage, innateTaunt = roleEnum == BattleRole.Tank,
        )
    }

    fun sanitize(): CharacterDef {
        id = CardDef.clean(id, "hero")
        if (name.isEmpty()) name = id
        if (parseEnum(BattleRole.entries.toTypedArray(), role) == null) role = "Dps"
        if (parseEnum(BattleElement.entries.toTypedArray(), element) == null) element = "Neutral"
        tier = tier.coerceIn(0, 7)
        hp = hp.coerceIn(60, 220)
        attack = attack.coerceIn(4f, 30f); magic = magic.coerceIn(4f, 30f)
        defense = defense.coerceIn(4f, 20f); resistance = resistance.coerceIn(4f, 20f)
        speed = speed.coerceIn(6f, 15f); crit = crit.coerceIn(0f, .3f); critDamage = critDamage.coerceIn(1.2f, 2f)
        val seen = HashSet<String>()
        // Deck cards are attacks, skills or transfers; ultimates, awakenings and JD's support cards have their own slots.
        for (c in cards) {
            c.owner = id
            c.kind = if (c.kind == "Attack" || c.kind == "Skill" || c.kind == "Transfer") c.kind else ""
            c.sanitize(); unique(c, seen)
        }
        for (c in ultimates) { c.owner = id; c.kind = "Ultimate"; c.sanitize(); unique(c, seen) }
        val aw = awakening
        if (aw != null && aw.name.isNotEmpty()) { aw.owner = id; aw.kind = "Awakening"; aw.sanitize(); unique(aw, seen) } else awakening = null
        // Exactly one opening card: the first Initiation card, or the first card.
        var opened = false
        for (c in cards) { if (c.initiation && !opened) opened = true else c.initiation = false }
        if (!opened && cards.isNotEmpty()) cards[0].initiation = true
        return this
    }

    private fun unique(c: CardDef, seen: HashSet<String>) {
        if (!c.id.startsWith(id + "_") && source == "novel") c.id = id + "_" + c.id
        val baseId = c.id
        var n = 2
        while (!seen.add(c.id)) c.id = baseId + "_" + n++
    }

    companion object {
        val TIER_SCALE = floatArrayOf(1f, 1.15f, 1.32f, 1.52f, 1.75f, 2.01f, 2.31f, 2.66f, 3.06f, 3.52f)
    }
}

/** Plain-language rules text for a card face, worked out from its numbers so it is always true. */
object CardText {
    fun describe(c: BattleCard): String {
        val parts = mutableListOf<String>()
        if (c.effectivePower > 0) parts.add("Deal " + pct(c.effectivePower) + (if (c.magic) " MAG" else " ATK") + (if (c.target == BattleTarget.AllEnemies) " to all enemies" else "") + ".")
        if (c.effectiveHeal > 0) parts.add("Heal " + num(c.effectiveHeal) + (if (c.target == BattleTarget.AllAllies) " to all allies" else if (c.target == BattleTarget.Self) " to self" else "") + ".")
        if (c.status.isNotEmpty()) parts.add(statusLine(c))
        if (c.draw > 0) parts.add("Draw " + c.draw + (if (c.draw == 1) " card." else " cards."))
        if (c.epGain > 0) parts.add("Target gains " + c.epGain + " EP.")
        if (c.apGain > 0) parts.add("Target gains " + c.apGain + " AP.")
        if (c.transferEp) parts.add("Give all your EP to an ally.")
        if (c.transferAp) parts.add("Give all your AP to an ally.")
        if (c.selfShield > 0) parts.add("Shield yourself for " + num(c.selfShield) + ".")
        if (parts.isEmpty()) parts.add("No effect.")
        return parts.joinToString(" ")
    }

    private fun statusLine(c: BattleCard): String {
        val on = when (c.target) {
            BattleTarget.AllEnemies -> "all enemies"
            BattleTarget.AllAllies -> "all allies"
            BattleTarget.Self -> "yourself"
            BattleTarget.Ally -> "an ally"
            else -> "the target"
        }
        val turns = if (c.duration == 1) "1 round" else c.duration.toString() + " rounds"
        return when (c.status) {
            "AttackUp" -> "Raise $on's attack ${pct(c.magnitude)} for $turns."
            "AttackDown" -> "Lower $on's attack ${pct(c.magnitude)} for $turns."
            "DefenseUp" -> "Raise $on's defence ${pct(c.magnitude)} for $turns."
            "DefenseDown" -> "Lower $on's defence ${pct(c.magnitude)} for $turns."
            "Slow" -> "Slow $on ${pct(c.magnitude)} (1 less EP) for $turns."
            "Haste" -> "Haste $on ${pct(c.magnitude)} for $turns."
            "Poison" -> "Poison $on: ${num(c.magnitude)} a round for $turns."
            "Burn" -> "Burn $on: ${num(c.magnitude)} a round for $turns."
            "Regen" -> "Regenerate ${num(c.magnitude)} a round for $turns ($on)."
            "Stun" -> "Stun $on: they lose their next round."
            "Taunt" -> "Make $on draw enemy attacks for $turns."
            "Shield" -> "Shield $on for ${num(c.magnitude)}."
            "IceCounter" -> "Counter the next hit for ${pct(c.magnitude)} ATK."
            else -> c.status + " for " + turns + "."
        }
    }

    fun pct(v: Float): String = Math.rint((v * 100).toDouble()).toLong().toString() + "%"
    fun num(v: Float): String = Math.rint(v.toDouble()).toLong().toString()

    fun keywords(card: BattleCard): String {
        val words = mutableListOf<String>()
        if (card.initiation) words.add("Initiation")
        if (card.retain) words.add("Retain")
        if (card.exhaust) words.add("Exhaust")
        return words.joinToString(" · ")
    }
}
