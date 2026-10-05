package com.ihy2ln.weaverse.feature.games.battle.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Everything the battle rules look up: the characters in play (built-in cast + novel characters), their cards, JD's
 * support cards, epiphany upgrades and the bestiary encounters. A process-wide registry, like the Unity original.
 */
object Catalog {
    const val SUMMONER_ID = "jd"

    val KNOWN_STATUSES = setOf(
        "", "AttackUp", "AttackDown", "DefenseUp", "DefenseDown", "Slow", "Haste", "Poison", "Burn", "Regen", "Stun", "Taunt",
        "Shield", "IceCounter",
    )

    private val byId = HashMap<String, CharacterDef>()
    private val order = mutableListOf<CharacterDef>()

    val characters: List<CharacterDef> get() = order

    /** Built-in cast first, then the novel characters (a novel character with a built-in id is renamed so both stay playable). */
    fun register(builtin: List<CharacterDef>, novel: List<CharacterDef>?) {
        byId.clear(); order.clear()
        for (c in builtin) add(c)
        if (novel == null) return
        for (c in novel) {
            c.source = "novel"
            c.sanitize()
            if (c.cards.isEmpty()) continue
            val baseId = c.id
            var n = 2
            while (byId.containsKey(c.id) || c.id == SUMMONER_ID) c.id = baseId + "_" + n++
            if (c.id != baseId) c.sanitize()
            add(c)
        }
    }

    private fun add(c: CharacterDef) { byId[c.id] = c; order.add(c) }

    fun get(id: String?): CharacterDef? = if (id == null) null else byId[id]

    fun partners(owner: String): List<String> = get(owner)?.bonds ?: emptyList()

    fun unit(id: String): BattleUnit? = get(id)?.toUnit()

    fun kit(id: String): List<BattleCard> {
        val c = get(id) ?: return emptyList()
        return c.cards.map { it.toBattleCard(c.elementEnum, c.bonds) }
    }

    /** Any card by id: a fighter card, an ultimate, or one of JD's. */
    fun card(cardId: String): BattleCard? {
        for (c in order) {
            c.cards.firstOrNull { it.id == cardId }?.let { return it.toBattleCard(c.elementEnum, c.bonds) }
            c.ultimates.firstOrNull { it.id == cardId }?.let { return it.toBattleCard(c.elementEnum, c.bonds) }
        }
        BuiltinContent.summonerKit().firstOrNull { it.id == cardId }?.let { return it.toBattleCard(BattleElement.Light, null) }
        return null
    }

    fun cardDefOf(cardId: String): CardDef? {
        for (c in order) {
            c.cards.firstOrNull { it.id == cardId }?.let { return it }
            c.ultimates.firstOrNull { it.id == cardId }?.let { return it }
        }
        return BuiltinContent.summonerKit().firstOrNull { it.id == cardId }
    }

    fun summonerKit(): List<BattleCard> = BuiltinContent.summonerKit().map { it.toBattleCard(BattleElement.Light, null) }

    fun summonerUltimates(): List<BattleCard> = BuiltinContent.summonerUltimates().map { it.toBattleCard(BattleElement.Light, null) }

    fun basic(unit: BattleUnit) = BattleCard(
        id = "basic_" + unit.id, name = "Basic Attack", owner = unit.id, element = unit.element, ep = 0, ap = 1,
        power = .55f, target = BattleTarget.Enemy, magic = unit.magic > unit.attack,
    )

    /** The free shield beside the basic attack: 1 AP, no EP, about one enemy hit. */
    fun guard(unit: BattleUnit) = BattleCard(
        id = "guard_" + unit.id, name = "Guard", owner = unit.id, element = unit.element, ep = 0, ap = 1,
        target = BattleTarget.Self, kind = BattleCardKind.Skill, status = "Shield",
        magnitude = max(8f, unit.defense * 1.6f + unit.maxHp * .05f), duration = 1,
    )

    fun ultimates(unit: BattleUnit?): List<BattleCard> {
        val c = if (unit != null) get(unit.id) else null
        if (c == null) return emptyList()
        return c.ultimates.map { d -> d.toBattleCard(c.elementEnum, c.bonds).also { it.kind = BattleCardKind.Ultimate } }
    }

    fun awakening(unit: BattleUnit?): BattleCard? {
        val c = (if (unit != null) get(unit.id) else null) ?: return null
        val def = c.awakening
        val card = if (def != null) def.toBattleCard(c.elementEnum, c.bonds)
        // Novel characters without one get a rally that fits their role.
        else if (c.roleEnum == BattleRole.Support)
            BattleCard(id = "aw_" + c.id, name = "Second Breath", owner = c.id, target = BattleTarget.AllAllies, heal = 18f, status = "Regen", magnitude = 6f, duration = 2)
        else BattleCard(id = "aw_" + c.id, name = "Steeled Resolve", owner = c.id, target = BattleTarget.AllAllies, status = "AttackUp", magnitude = .25f, duration = 2)
        card.kind = BattleCardKind.Awakening; card.ep = 0; card.ap = 0; card.element = c.elementEnum
        return card
    }

    // ---- epiphany: playing the battle's glowing card offers 3 of these upgrades ----------------------------------------

    fun epiphanyChoices(card: BattleCard): MutableList<String> {
        val list = mutableListOf<String>()
        val hits = card.power > 0
        if (hits || card.heal > 0) list.add("sharpened")
        if (card.ep > 0) list.add("swift")
        if (card.draw < 2) list.add("echo")
        if (!card.retain) list.add("steadfast")
        if (hits && card.target == BattleTarget.Enemy) list.add("sweeping")
        if (hits && card.status.isEmpty()) list.add("rending")
        if (card.selfShield <= 0) list.add("bulwark")
        if (card.exhaust) list.add("enduring")
        return list
    }

    fun epiphanyName(mod: String): String = when (mod) {
        "sharpened" -> "Sharpened"; "swift" -> "Swift"; "echo" -> "Echo"; "steadfast" -> "Steadfast"
        "sweeping" -> "Sweeping"; "rending" -> "Rending"; "bulwark" -> "Bulwark"; "enduring" -> "Enduring"
        else -> mod
    }

    fun epiphanyText(mod: String): String = when (mod) {
        "sharpened" -> "+20% power and healing."
        "swift" -> "Costs 1 EP less."
        "echo" -> "Draw a card when played."
        "steadfast" -> "Retain: stays in hand between turns."
        "sweeping" -> "Hits every enemy at 70% power."
        "rending" -> "Also lowers the target's defence 20% for 2 rounds."
        "bulwark" -> "Also shields its user."
        "enduring" -> "No longer exhausts."
        else -> ""
    }

    fun applyEpiphany(card: BattleCard, mod: String) {
        when (mod) {
            "sharpened" -> card.level++
            "swift" -> card.ep = max(0, card.ep - 1)
            "echo" -> card.draw++
            "steadfast" -> card.retain = true
            "sweeping" -> if (card.target == BattleTarget.Enemy && card.power > 0) { card.target = BattleTarget.AllEnemies; card.power *= .7f }
            "rending" -> if (card.status.isEmpty()) { card.status = "DefenseDown"; card.magnitude = .2f; card.duration = 2 }
            "bulwark" -> card.selfShield = max(card.selfShield, 14f + card.ep * 6f)
            "enduring" -> card.exhaust = false
            else -> return
        }
        card.epiphany = if (card.epiphany.isEmpty()) mod else card.epiphany + "," + mod
    }

    // ---- enemies --------------------------------------------------------------------------------------------------------

    private val ROLE_NAMES = listOf("Bruiser", "Skirmisher", "Caster", "Guardian")
    private const val GUARDIAN = 3

    // Stat shapes per role: hp, attack, magic, defence, speed. Lair bosses share one shape.
    private val SHAPE = arrayOf(
        floatArrayOf(1.35f, 1.25f, .60f, 1.05f, .80f), floatArrayOf(.72f, 1.10f, .70f, .80f, 1.45f),
        floatArrayOf(.85f, .60f, 1.50f, .85f, 1.05f), floatArrayOf(1.20f, .80f, .85f, 1.50f, .85f),
    )
    private val BOSS_SHAPE = floatArrayOf(1.25f, 1.15f, 1.15f, 1.15f, 1.0f)
    const val ENEMY_HEALTH = 120f
    const val ENEMY_ATTACK = 35f
    const val ENEMY_MAGIC = 31f
    const val HEALTH_PER_DEPTH = .12f
    const val OFFENCE_PER_DEPTH = .065f

    // Early-fight offence: 1.3 (RulesSim: a greedy bot wins ~72% of runs with a full party of six, ~14% with three).
    var offenseShallow = 1.3f
    var offenseDeep = 1f
    var bossOffenseScale = 1.25f
    const val ELITE_POWER = 2.0f
    const val BOSS_POWER = 3.4f
    const val MINION_POWER = .8f
    const val BOSS_OFFENCE = 1.45f
    const val RANK_STEP = .08f
    private val AFFIXES = listOf("Vampiric", "Thorned", "Hasted", "Shielded", "Enraged")

    private fun offenseCurve(depth: Int, boss: Boolean): Float {
        val t = max(0f, min(1f, (depth - 1) / 12f))
        return (offenseShallow + (offenseDeep - offenseShallow) * t) * (if (boss) bossOffenseScale else 1f)
    }

    private fun monster(
        f: BestiaryForm, rank: Int, depth: Int, lane: Int, power: Float, id: String? = null, name: String? = null,
        boss: Boolean = false, rankScaled: Boolean = true,
    ): BattleUnit {
        val element = BattleElement.entries.firstOrNull { it.name == f.element } ?: BattleElement.Neutral
        val role = max(0, ROLE_NAMES.indexOf(f.role))
        val scale = if (rankScaled) max(.5f, 1f + RANK_STEP * (rank - Bestiary.rankForDepth(depth))) else 1f
        val k = if (boss) BOSS_SHAPE else SHAPE[role]
        val d = (max(1, depth) - 1).toFloat()
        val health = (1f + HEALTH_PER_DEPTH * d) * power * (if (f.large && !boss) 1.25f else 1f) * scale
        val offence = (1f + OFFENCE_PER_DEPTH * d) * (if (boss) BOSS_OFFENCE else sqrt(power)) * scale * offenseCurve(depth, boss)
        val hp = max(1, (ENEMY_HEALTH * k[0] * health).toInt())
        return BattleUnit(
            id = (id ?: f.id) + "_" + lane, species = f.id, name = name ?: f.name,
            art = "bestiary:" + f.art.ifEmpty { f.id }, element = element, enemy = true, rank = rank,
            role = if (role == GUARDIAN) BattleRole.Tank else if (role == 2) BattleRole.Support else BattleRole.Dps,
            innateTaunt = role == GUARDIAN, lane = lane, maxHp = hp, hp = hp,
            attack = ENEMY_ATTACK * k[1] * offence, magic = ENEMY_MAGIC * k[2] * offence,
            defense = 9f * k[3] * (1f + .05f * d), resistance = 9f * k[3] * (1f + .05f * d),
            speed = 10f * k[4], critRate = .08f, critDamage = 1.5f, boss = boss, phase = if (boss) 1 else 0,
        )
    }

    /**
     * An encounter: "normal" (a pack), "elite" (one evolved member with an affix, and a commander from depth 3), or
     * "boss" (a lair boss two ranks up flanked by minions). The same seed always builds the same fight.
     */
    fun build(spec: BattleEncounterSpec): BattleEncounter {
        val rng = Rng(if (spec.seed != 0) spec.seed else spec.depth * 7919 + 17)
        val e = BattleEncounter(kind = spec.kind, depth = max(1, spec.depth), theme = spec.theme, region = spec.region)
        check(Bestiary.loaded) { "The bestiary is not loaded." }
        val depth = e.depth
        val natural = Bestiary.rankForDepth(depth)
        if (spec.kind == "boss") {
            var lair: BestiaryBoss? = null
            for (b in Bestiary.bosses) if (b.region == spec.region) lair = b
            if (lair == null && Bestiary.bosses.isNotEmpty()) lair = Bestiary.bosses[rng.next(Bestiary.bosses.size)]
            val family = (if (lair != null) Bestiary.family(lair.family) else null) ?: Bestiary.families[rng.next(Bestiary.families.size)]
            val theme = if (lair != null) lair.theme else spec.theme
            val minions = Bestiary.spawnable(natural, theme, true)
            val bossRank = min(Bestiary.MAX_RANK, natural + 2)
            val big = Bestiary.formFor(family, bossRank)
            e.enemies.add(monster(minions[rng.next(minions.size)], natural, depth, 0, MINION_POWER, "minion"))
            val lord = monster(
                big, max(big.minRank, min(bossRank, big.maxRank)), depth, 1, BOSS_POWER,
                "boss_" + big.id, lair?.title ?: big.name, boss = true, rankScaled = false,
            )
            e.enemies.add(lord)
            e.enemies.add(monster(minions[rng.next(minions.size)], natural, depth, 2, MINION_POWER, "minion"))
            e.boss = lord
            e.title = "LAIR BOSS  -  " + lord.name.uppercase()
            return e
        }
        val count = if (spec.kind == "elite") 3 else if (depth <= 2) 2 + rng.next(2) else 3
        val ranks = IntArray(count)
        for (lane in 0 until count) {
            val rank = Bestiary.rollRank(depth, rng)
            val pool = Bestiary.spawnable(rank, spec.theme)
            ranks[lane] = rank
            e.enemies.add(monster(pool[rng.next(pool.size)], rank, depth, lane, 1f))
        }
        if (spec.kind == "elite") {
            val pick = rng.next(e.enemies.size)
            val old = Bestiary.form(e.enemies[pick].species)!!
            val rank = min(Bestiary.MAX_RANK - 1, max(ranks[pick], natural) + 1)
            val evolved = Bestiary.formFor(old.family!!, rank)
            val elite = monster(evolved, max(evolved.minRank, min(rank, evolved.maxRank)), depth, pick, ELITE_POWER, rankScaled = false)
            elite.affix = AFFIXES[rng.next(AFFIXES.size)]
            elite.name = elite.affix + " " + elite.name
            if (elite.affix == "Hasted") { elite.maxAp = 2; elite.ap = 2 }
            if (elite.affix == "Shielded") elite.applyStatus("Shield", elite.maxHp * .3f, 99)
            e.enemies[pick] = elite
            e.title = "ELITE  -  " + elite.name.uppercase()
            if (depth >= 3) e.commander = enemyCommander(depth)
        } else e.title = "SILVERWOOD PACK"
        return e
    }

    fun enemyCommander(depthIn: Int): BattleUnit {
        val depth = max(1, depthIn)
        val hp = (170 * (1f + .10f * (depth - 1))).toInt()
        val whisperers = Bestiary.family("whisperers")
        val rank = Bestiary.rankForDepth(depth)
        val form = if (whisperers != null) Bestiary.formFor(whisperers, rank) else null
        return BattleUnit(
            id = "enemy_commander", name = form?.name ?: "Vale Commander",
            art = if (form != null) "bestiary:" + form.art.ifEmpty { form.id } else "",
            rank = rank, enemy = true, element = BattleElement.Dark, maxHp = hp, hp = hp,
            defense = 10f * (1f + .05f * depth), resistance = 11f * (1f + .05f * depth), speed = 11f, critRate = .1f, critDamage = 1.6f,
        )
    }

    private fun e(
        id: String, name: String, owner: String, ep: Int, power: Float, target: BattleTarget = BattleTarget.Enemy,
        status: String = "", magnitude: Float = 0f, duration: Int = 0, heal: Float = 0f, magic: Boolean = false,
    ) = BattleCard(
        id = id, name = name, owner = owner, ep = ep, ap = 1, power = power, target = target, status = status,
        magnitude = magnitude, duration = duration, heal = heal, magic = magic,
        kind = if (power > 0) BattleCardKind.Attack else BattleCardKind.Skill,
    )

    /**
     * A form's first two moves; elites add the form's signature (last) move; bosses add Gathering Fury and their charged
     * signature, which lands the round after the charge-up (the intent shows it coming).
     */
    fun enemyCards(enemy: BattleUnit): List<BattleCard> {
        val o = enemy.id
        val cards = mutableListOf<BattleCard>()
        val form = Bestiary.form(enemy.species)
        if (form != null && form.cards.isNotEmpty()) {
            val picked = mutableListOf<BestiaryCard>()
            for (i in 0 until min(2, form.cards.size)) picked.add(form.cards[i])
            if (enemy.affix.isNotEmpty() && form.cards.size > 2) picked.add(form.cards.last())
            for (c in picked) {
                val target = BattleTarget.entries.firstOrNull { it.name == c.target } ?: BattleTarget.Enemy
                cards.add(e(c.id, c.name, o, c.ep, c.power, target, c.status, c.magnitude, c.duration, c.heal, c.magic))
            }
        } else cards.add(e("e_claw", "Corrupted Claw", o, 1, 1.2f))
        if (enemy.boss) {
            cards.add(e("e_gather", "Gathering Fury", o, 1, 0f, BattleTarget.Self, "Charging", 1f, 2))
            cards.add(bossSignature(enemy))
        }
        for (card in cards) card.element = enemy.element
        return cards
    }

    fun bossSignature(boss: BattleUnit): BattleCard {
        val name = when (boss.element) {
            BattleElement.Dark -> "Eclipse Cataclysm"
            BattleElement.Water -> "Tidal Ruin"
            BattleElement.Lightning -> "Stormfall"
            BattleElement.Fire -> "Cinder Storm"
            BattleElement.Light -> "Prism Judgement"
            else -> "Shattering Rage"
        }
        val card = e("e_cataclysm", name, boss.id, 0, 1.6f, BattleTarget.AllEnemies, "DefenseDown", .2f, 2)
        card.element = boss.element
        return card
    }

    fun enemyCommanderCards(): List<BattleCard> = listOf(
        BattleCard(id = "es_dirge", name = "Dirge", owner = "enemy_commander", ep = 0, ap = 1, target = BattleTarget.AllEnemies, status = "AttackDown", magnitude = .20f, duration = 2, kind = BattleCardKind.Skill),
        BattleCard(id = "es_rot", name = "Creeping Rot", owner = "enemy_commander", ep = 0, ap = 1, target = BattleTarget.AllEnemies, status = "Poison", magnitude = 7f, duration = 2, kind = BattleCardKind.Skill),
        BattleCard(id = "es_vigor", name = "Vale Vigor", owner = "enemy_commander", ep = 0, ap = 1, target = BattleTarget.AllAllies, status = "DefenseUp", magnitude = .25f, duration = 2, kind = BattleCardKind.Skill),
    )
}
