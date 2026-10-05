package com.ihy2ln.weaverse.feature.games.battle.engine

import kotlin.math.max
import kotlin.math.min

/** Expected damage of a card and the element multiplier it lands with (target previews, enemy intent badges). */
class DamagePreview(val amount: Int, val element: Float)

/**
 * One battle: AP/EP fighters, a shared hand, JD the Summoner, enemy action counters, tenacity/BREAK, shields, reserve
 * partners and epiphany. Port of the Unity card game's BattleState.
 */
class BattleState(
    seed: Int,
    allies: List<BattleUnit>,
    reserves: List<BattleUnit>,
    enemies: List<BattleUnit>,
    deck: List<BattleCard>,
    summoner: BattleUnit?,
    enemySummoner: BattleUnit? = null,
    bondRanks: MutableMap<String, Int>? = null,
    runModifiers: BattleRunModifiers? = null,
) {
    val allies = mutableListOf<BattleUnit>()
    val reserves = mutableListOf<BattleUnit>()
    val enemies = mutableListOf<BattleUnit>()
    val drawPile = mutableListOf<BattleCard>()
    val hand = mutableListOf<BattleCard>()
    val discard = mutableListOf<BattleCard>()
    val exhausted = mutableListOf<BattleCard>()

    /** Cards played this turn (the CHAIN counter) and how many enemy actions interrupted the party this battle. */
    var cardsThisTurn = 0
    var interrupts = 0

    /**
     * Epiphany: the card that glows this battle (playing it offers 1 of 3 upgrades), the choice waiting to be made, and
     * every epiphany taken (card id -> "swift,echo") so the run can keep them.
     */
    var glowCard = ""
    var epiphanyCard: BattleCard? = null
    val epiphanyOptions = mutableListOf<String>()
    val epiphanies = LinkedHashMap<String, String>()
    val log = mutableListOf<String>()
    val facts = mutableListOf<BattleFact>()
    val intents = LinkedHashMap<String, BattleCard>()
    val intentTargets = LinkedHashMap<String, BattleUnit>()
    val bondRanks: MutableMap<String, Int> = bondRanks ?: HashMap()
    val runModifiers: BattleRunModifiers = runModifiers ?: BattleRunModifiers()
    var summoner: BattleUnit? = summoner
    var enemySummoner: BattleUnit? = enemySummoner
    var round = 0
    var sp = 0
    var cp = 0
    var enemySp = 0
    var enemyCp = 0

    /** Counts every fact ever recorded; [facts] itself is capped, so presentation reads the tail by serial. */
    var factSerial = 0
    var finished = false
    var victory = false
    var withdrawn = false
    var lastMessage = ""
    private val rng = Rng(seed)
    private var relicSpent = false
    private var runPlayMultiplier = 1f

    init {
        for (unit in allies) if (this.allies.size < FIELD_MAX) this.allies.add(unit)
        for (unit in reserves) if (this.reserves.size < RESERVE_MAX) this.reserves.add(unit)
        for (unit in enemies) if (this.enemies.size < ENEMY_MAX) this.enemies.add(unit)
        for (i in this.allies.indices) this.allies[i].lane = i
        for (i in this.enemies.indices) { this.enemies[i].lane = i; initEnemy(this.enemies[i]) }
        for (card in deck) drawPile.add(card.copy())
        shuffle(drawPile)
        // Initiation cards of the fighters on the field go on top (the pile is drawn from its end).
        val opening = drawPile.filter { it.initiation && ownerOnField(it) }
        for (card in opening) { drawPile.remove(card); drawPile.add(card) }
        startRound()
    }

    private fun say(line: String) {
        lastMessage = line
        log.add(line)
        if (log.size > 80) log.removeAt(0)
    }

    private fun shuffle(list: MutableList<BattleCard>) {
        for (i in list.size - 1 downTo 1) {
            val j = rng.next(i + 1)
            val temp = list[i]; list[i] = list[j]; list[j] = temp
        }
    }

    private fun ownerOnField(card: BattleCard): Boolean {
        if (card.kind == BattleCardKind.Summoner || card.owner.isEmpty()) return true
        return allies.any { it.id == card.owner && it.alive }
    }

    /** The reserve paired with a field fighter (same slot), or null. */
    fun partnerOf(fighter: BattleUnit): BattleUnit? {
        val i = allies.indexOf(fighter)
        return if (i >= 0 && i < reserves.size) reserves[i] else null
    }

    /** The field fighter a reserve partners, or null. */
    fun partneredBy(reserve: BattleUnit): BattleUnit? {
        val i = reserves.indexOf(reserve)
        return if (i >= 0 && i < allies.size) allies[i] else null
    }

    fun ultCost(unit: BattleUnit?): Int = if (unit != null && unit.overcame) ULTIMATE_SP_COST - 1 else ULTIMATE_SP_COST

    private fun startRound() {
        round++
        cardsThisTurn = 0
        // A fighter who fell is replaced by the first living reserve before the new round.
        for (i in allies.indices) {
            if (allies[i].alive) continue
            val reserve = reserves.firstOrNull { it.alive }
            if (reserve != null) trySwap(allies[i], reserve)
        }
        // Stun and Slow bite on the round after they land, so they are read before durations tick down.
        val stunned = HashSet<BattleUnit>()
        val slowed = HashSet<BattleUnit>()
        for (unit in bothSides()) {
            if (!unit.alive) continue
            if (unit.hasStatus("Stun")) stunned.add(unit)
            if (unit.hasStatus("Slow")) slowed.add(unit)
            for (i in unit.statuses.size - 1 downTo 0) {
                val status = unit.statuses[i]
                if (status.name == "Poison" || status.name == "Burn") applyTick(unit, "poison", max(1, status.magnitude.toInt()))
                if (status.name == "Regen") applyTick(unit, "regen", max(1, status.magnitude.toInt()))
                status.turns--
                if (status.turns <= 0) { unit.statuses.removeAt(i); say(unit.name + ": " + status.name + " expired") }
            }
        }
        for (unit in bothSides()) {
            if (!unit.alive) continue
            unit.refill()
            if (unit in stunned) { unit.ap = 0; say(unit.name + " is stunned and loses the round.") }
            if (unit in slowed) unit.ep = max(0, unit.ep - 1)
            if (!unit.enemy && unit.collapseRounds > 0) {
                unit.collapseRounds--
                if (unit.collapseRounds == 0) {
                    unit.stress = min(unit.stress, 40)
                    unit.awakeningReady = true
                    unit.overcame = true
                    say(unit.name + " overcomes the breakdown: an awakening skill, and ultimates cost 1 SP less.")
                }
            }
        }
        // Fury: from round 11 the enemies hit 12% harder every round, so a fight nobody can win still ends.
        if (round > FURY_ROUND) {
            for (enemy in enemies) if (enemy.alive) { enemy.attack *= FURY_STEP; enemy.magic *= FURY_STEP }
            if (round == FURY_ROUND + 1) say("The enemies grow furious: they hit harder every round from now on.")
        }
        if (round == 1 && allies.isNotEmpty() && allies[0].alive) {
            if (runModifiers.scout) allies[0].ep++
            if (runModifiers.fortify) allies[0].applyStatus("DefenseUp", .20f, 1)
        }
        sp = min(SP_MAX, sp + 1)
        enemySp = min(SP_MAX, enemySp + 1)
        cp = 2; enemyCp = 2
        for (enemy in enemies) {
            if (!enemy.alive) continue
            enemy.actedThisRound = false
            enemy.actionMax = actionCountFor(enemy)
            enemy.actionCount = enemy.actionMax
            if (!enemy.hasStatus("Broken")) enemy.tenacity = enemy.maxTenacity
        }
        ensureWall(enemies)
        rollIntents()
        refillHand()
        checkEnd()
        if (!finished) say("Round $round: play your cards, then end the turn.")
    }

    private fun bothSides(): List<BattleUnit> = allies + enemies

    private fun canDraw(card: BattleCard): Boolean {
        if (card.kind == BattleCardKind.Summoner) return countHand(true) < SUMMONER_HAND_MAX
        if (countHand(false) >= ALLY_HAND_MAX) return false
        if (card.owner.isEmpty()) return true
        return allies.any { it.id == card.owner && it.alive }
    }

    fun countHand(summoner: Boolean): Int = hand.count { (it.kind == BattleCardKind.Summoner) == summoner }

    /**
     * Draws fighter cards until HAND_SIZE of them are in hand. JD's support cards met on the way join the hand too (up to
     * three); cards whose owner is benched or down are set aside.
     */
    private fun refillHand() {
        var misses = 0
        while (countHand(false) < HAND_SIZE) {
            if (drawPile.isEmpty()) {
                if (discard.isEmpty()) break
                drawPile.addAll(discard); discard.clear(); shuffle(drawPile); say("The deck is reshuffled.")
            }
            val card = drawPile.removeAt(drawPile.size - 1)
            if (canDraw(card)) { hand.add(card); if (card.kind != BattleCardKind.Summoner) misses = 0; continue }
            discard.add(card)
            // Nothing left that can be drawn: stop instead of reshuffling forever.
            if (++misses > drawPile.size + discard.size + 1) break
        }
    }

    fun drawCards(count: Int): Int {
        var gained = 0
        for (n in 0 until count) {
            var got = false
            for (tries in 0 until 20) {
                if (drawPile.isEmpty()) {
                    if (discard.isEmpty()) break
                    drawPile.addAll(discard); discard.clear(); shuffle(drawPile); say("The deck is reshuffled.")
                }
                val card = drawPile.removeAt(drawPile.size - 1)
                if (canDraw(card)) { hand.add(card); gained++; got = true; break }
                discard.add(card)
            }
            if (!got) break
        }
        return gained
    }

    fun ownerOf(card: BattleCard?): BattleUnit? {
        if (card == null || card.kind == BattleCardKind.Summoner) return null
        return allies.firstOrNull { it.id == card.owner }
    }

    fun canPay(card: BattleCard?, actor: BattleUnit?): Boolean {
        if (finished || card == null) return false
        if (card.kind == BattleCardKind.Summoner) { val s = summoner; return s != null && s.alive && cp >= max(1, card.ap) }
        if (actor == null || !actor.alive || actor.enemy || actor.strained(card)) return false
        if (card.owner.isNotEmpty() && actor.id != card.owner) return false
        if (card.kind == BattleCardKind.Awakening) return actor.awakeningReady && actor.collapseRounds == 0 && allies.contains(actor)
        if (card.kind == BattleCardKind.Ultimate) return actor.ultimate >= 100 && sp >= ultCost(actor)
        return actor.ap >= card.ap && actor.ep >= card.ep
    }

    fun isTarget(card: BattleCard?, actor: BattleUnit?, target: BattleUnit?): Boolean {
        if (card == null) return false
        if (target == null) return card.target == BattleTarget.AllEnemies || card.target == BattleTarget.AllAllies || card.target == BattleTarget.None
        if (!target.alive || reserves.contains(target)) return false
        val actorEnemy = actor != null && actor.enemy
        val same = target.enemy == actorEnemy
        if (card.target == BattleTarget.Self) return target === actor
        if (card.target == BattleTarget.Ally) return same && card.power <= 0
        if (card.target == BattleTarget.Enemy) return !same && exposed(target)
        return false
    }

    fun exposed(target: BattleUnit?): Boolean {
        if (target == null || !target.alive) return false
        if (target === summoner || target === enemySummoner) return true
        val side = if (target.enemy) enemies else allies
        if (!side.contains(target)) return false
        if (target.taunting) return true
        return side.none { it.alive && it.taunting }
    }

    fun tryPlay(card: BattleCard, target: BattleUnit?): Boolean {
        val actor = ownerOf(card)
        if (!canPay(card, actor) || !isTarget(card, actor, target)) return false
        when (card.kind) {
            BattleCardKind.Summoner -> cp -= max(1, card.ap)
            BattleCardKind.Ultimate -> { sp -= ultCost(actor); actor!!.ultimate = 0 }
            BattleCardKind.Awakening -> actor!!.awakeningReady = false
            else -> { actor!!.ap -= card.ap; actor.ep -= card.ep; actor.charge(18) }
        }
        say((actor?.name ?: summoner!!.name) + " uses " + card.name + ".")
        resolve(card, actor, target)
        if (hand.remove(card)) (if (card.exhaust) exhausted else discard).add(card)
        if (glowCard.isNotEmpty() && card.id == glowCard) offerEpiphany(card)
        checkEnd()
        // Only the fighters' own cards move the enemies' action counts and the chain: ego moves (ultimates, awakenings)
        // and JD's command cards and decrees do not.
        if (card.kind != BattleCardKind.Ultimate && card.kind != BattleCardKind.Awakening && card.kind != BattleCardKind.Summoner) {
            cardsThisTurn++
            tickActionCounts()
        }
        return true
    }

    /**
     * Each card played lowers every enemy's action count; one that reaches 0 acts at once, mid-turn, then its count starts
     * over with a fresh intent. An enemy that acted mid-turn does not act again at the end of the turn.
     */
    private fun tickActionCounts() {
        if (finished) return
        val ready = mutableListOf<BattleUnit>()
        for (enemy in enemies) {
            if (!enemy.alive || enemy.actionMax <= 0) continue
            enemy.actionCount = max(0, enemy.actionCount - 1)
            if (enemy.actionCount == 0) ready.add(enemy)
        }
        ready.sortByDescending { it.effectiveSpeed }
        for (enemy in ready) {
            if (finished) return
            if (!enemy.alive) continue
            enemyAct(enemy, true)
            if (finished || !enemy.alive) continue
            enemy.actionCount = enemy.actionMax
            enemy.ap = enemy.maxAp; enemy.ep = enemy.maxEp
            rollIntent(enemy)
        }
    }

    // ---- reserve partners ---------------------------------------------------------------------------------------------

    /** A reserve's assist: their first ultimate at 60%, once per battle, for PARTNER_SP_COST SP. */
    fun partnerMove(reserve: BattleUnit): BattleCard? {
        val ults = Catalog.ultimates(reserve)
        if (ults.isEmpty()) return null
        val card = ults[0].copy()
        card.power *= .6f; card.heal *= .6f
        card.name = card.name + " (Assist)"
        return card
    }

    fun canPartnerAssist(reserve: BattleUnit?): Boolean =
        !finished && reserve != null && reserves.contains(reserve) && reserve.alive && !reserve.partnerUsed &&
            sp >= PARTNER_SP_COST && partneredBy(reserve) != null && partnerMove(reserve) != null

    fun tryPartnerAssist(reserve: BattleUnit, target: BattleUnit?): Boolean {
        if (!canPartnerAssist(reserve)) return false
        val card = partnerMove(reserve)!!
        if (card.target == BattleTarget.Enemy && (target == null || !target.enemy || !exposed(target))) return false
        if (card.target == BattleTarget.Ally && (target == null || target.enemy || !allies.contains(target) || !target.alive)) return false
        sp -= PARTNER_SP_COST
        reserve.partnerUsed = true
        say(reserve.name + " steps in beside " + partneredBy(reserve)!!.name + ": " + card.name + "!")
        resolve(card, reserve, if (card.target == BattleTarget.Self) reserve else target)
        checkEnd()
        return true
    }

    // ---- epiphany -----------------------------------------------------------------------------------------------------

    private fun offerEpiphany(card: BattleCard) {
        glowCard = ""
        epiphanyOptions.clear()
        val pool = Catalog.epiphanyChoices(card)
        while (pool.isNotEmpty() && epiphanyOptions.size < 3) {
            val i = rng.next(pool.size)
            epiphanyOptions.add(pool[i]); pool.removeAt(i)
        }
        if (epiphanyOptions.isNotEmpty()) { epiphanyCard = card; say("Epiphany! " + card.name + " can grow.") }
    }

    /** Applies option [index] to every copy of the pending card for the rest of the battle (and records it for the run). */
    fun chooseEpiphany(index: Int): Boolean {
        val picked = epiphanyCard
        if (picked == null || index < 0 || index >= epiphanyOptions.size) return false
        val mod = epiphanyOptions[index]
        val id = picked.id
        var appliedToPicked = false
        for (list in listOf(drawPile, hand, discard, exhausted))
            for (c in list)
                if (c.id == id) { Catalog.applyEpiphany(c, mod); if (c === picked) appliedToPicked = true }
        if (!appliedToPicked) Catalog.applyEpiphany(picked, mod)
        val before = epiphanies[id]
        epiphanies[id] = if (before != null && before.isNotEmpty()) "$before,$mod" else mod
        val owner = ownerOf(picked)
        fact("epiphany", owner, owner ?: summoner!!, picked, 0, false, 1f, 1f)
        say(picked.name + " gains " + Catalog.epiphanyName(mod) + ".")
        epiphanyCard = null
        epiphanyOptions.clear()
        return true
    }

    fun tryBasic(actor: BattleUnit?, target: BattleUnit?): Boolean = actor != null && tryPlay(Catalog.basic(actor), target)

    fun tryGuard(actor: BattleUnit?): Boolean = actor != null && tryPlay(Catalog.guard(actor), actor)

    fun tryUltimate(actor: BattleUnit, choice: Int, target: BattleUnit?): Boolean {
        val options = Catalog.ultimates(actor)
        if (choice < 0 || choice >= options.size) return false
        return tryPlay(options[choice], target)
    }

    fun tryAwakening(actor: BattleUnit): Boolean {
        val card = Catalog.awakening(actor)
        return card != null && tryPlay(card, null)
    }

    fun trySummonerUltimate(): Boolean {
        val options = Catalog.summonerUltimates()
        val s = summoner
        if (finished || sp < SP_MAX || s == null || !s.alive) return false
        sp = 0
        say(s.name + " issues a rallying and sundering decree.")
        for (card in options) resolve(card, null, null)
        checkEnd()
        return true
    }

    fun trySwap(field: BattleUnit?, reserve: BattleUnit?): Boolean {
        if (finished || field == null || reserve == null || !allies.contains(field) || !reserves.contains(reserve) || !reserve.alive) return false
        val free = !field.alive
        val lane = field.lane
        val index = allies.indexOf(field)
        val bench = reserves.indexOf(reserve)
        allies[index] = reserve; reserves[bench] = field; reserve.lane = lane
        for (i in hand.size - 1 downTo 0)
            if (hand[i].owner == field.id) { drawPile.add(hand[i]); hand.removeAt(i) }
        shuffle(drawPile)
        reserve.refill()
        if (!free) reserve.ap = 0
        drawCards(2)
        say(reserve.name + " replaces " + field.name + (if (free) " for free." else " and spends the round."))
        checkEnd()
        return true
    }

    private fun resolve(card: BattleCard, actor: BattleUnit?, chosen: BattleUnit?) {
        runPlayMultiplier = runDamageMultiplier(card, actor)
        if (actor != null && chosen != null && card.transferAp) { chosen.ap += actor.ap; actor.ap = 0 }
        if (actor != null && chosen != null && card.transferEp) { chosen.ep += actor.ep; actor.ep = 0 }
        if (chosen != null) { chosen.ap += card.apGain; chosen.ep += card.epGain }
        if (card.selfShield > 0 && actor != null && actor.alive && !actor.enemy) {
            val amount = max(1, Math.rint((card.selfShield * mendMultiplier(actor)).toDouble()).toInt())
            actor.applyStatus("Shield", amount.toFloat(), 1)
            fact("shield", actor, actor, card, amount, false, 1f, 1f)
        }
        val fullBond = fullBond(card)
        if (card.draw > 0) drawCards(card.draw + (if (fullBond) 1 else 0))
        val bond = bond(card)
        if (runPlayMultiplier > 1f) {
            if (targets(card, actor, chosen).any { it.alive }) relicSpent = true
        }
        for (target in targets(card, actor, chosen)) {
            if (!target.alive) continue
            if (card.effectivePower > 0 && actor != null) deal(card, actor, target, bond, true)
            if (card.effectiveHeal > 0) {
                val amount = max(1, Math.rint((card.effectiveHeal * bond * mendMultiplier(actor)).toDouble()).toInt())
                val before = target.hp
                target.healBy(amount)
                target.stress = max(0, target.stress - max(8, amount / 3))
                fact("heal", actor, target, card, target.hp - before, false, 1f, bond)
            }
            if (card.status.isNotEmpty()) {
                var magnitude = if (card.status == "IceCounter") card.magnitude else card.magnitude * bond * mendMultiplier(actor)
                if (card.status == "Shield") magnitude = card.magnitude * mendMultiplier(actor)
                target.applyStatus(card.status, magnitude, card.duration + (if (fullBond && card.status != "IceCounter") 1 else 0))
                fact("status", actor, target, card, 0, false, 1f, bond)
                say(target.name + " gains " + card.status + ".")
            }
        }
    }

    private fun targets(card: BattleCard, actor: BattleUnit?, chosen: BattleUnit?): List<BattleUnit> {
        if (card.target == BattleTarget.Self || card.target == BattleTarget.Ally || card.target == BattleTarget.Enemy)
            return if (chosen != null) listOf(chosen) else emptyList()
        val side = if (actor != null && actor.enemy) enemies else allies
        val foe = if (actor != null && actor.enemy) allies else enemies
        if (card.target == BattleTarget.AllAllies) return side.filter { it.alive }
        if (card.target == BattleTarget.AllEnemies) return foe.filter { it.alive }
        return emptyList()
    }

    private fun bond(card: BattleCard): Float {
        var bonus = 0f
        for (partner in card.partners)
            for (ally in allies)
                if (ally.alive && ally.id == partner) {
                    val rank = bondRanks[bondKey(card.owner, partner)] ?: 0
                    bonus += card.bondScale * (1f + .30f * rank)
                }
        return 1f + bonus
    }

    private fun fullBond(card: BattleCard): Boolean {
        if (card.partners.isEmpty()) return false
        for (partner in card.partners) if (allies.none { it.alive && it.id == partner }) return false
        return true
    }

    private fun runDamageMultiplier(card: BattleCard, actor: BattleUnit?): Float {
        if (card.effectivePower <= 0 || (actor != null && actor.enemy)) return 1f
        val bonus = 1f + runModifiers.damageBonus
        if (runModifiers.relic != "stormglass" && runModifiers.relic != "ironbark") return bonus
        val favored = if (runModifiers.relic == "stormglass") card.magic else !card.magic
        return bonus * (if (favored) (if (relicSpent) 1f else 1.25f) else .9f)
    }

    private fun covered(unit: BattleUnit): Boolean {
        val side = if (unit.enemy) enemies else allies
        return side.any { it !== unit && it.alive && it.taunting }
    }

    private fun strikeMultiplier(unit: BattleUnit): Float {
        val partner = partnerBonus(unit)
        if (unit.role == BattleRole.Dps && !covered(unit)) return 1.15f * partner
        if (unit.role == BattleRole.Ranger && covered(unit)) return 1.20f * partner
        return partner
    }

    /** A field fighter with a living reserve partner in their slot hits, heals and shields 8% harder. */
    fun partnerBonus(unit: BattleUnit?): Float {
        if (unit == null || unit.enemy || !allies.contains(unit)) return 1f
        val partner = partnerOf(unit)
        return if (partner != null && partner.alive) PARTNER_PASSIVE else 1f
    }

    private fun guardMultiplier(unit: BattleUnit): Float = if (unit.role == BattleRole.Tank && unit.taunting) 1.25f else 1f

    private fun mendMultiplier(unit: BattleUnit?): Float {
        if (unit == null) return 1f
        return (if (unit.role == BattleRole.Support && covered(unit)) 1.25f else 1f) * partnerBonus(unit)
    }

    /** Damage before crit as (raw, element multiplier). Shared by [deal] and [previewDamage] so the numbers on screen match what lands. */
    private fun baseRaw(card: BattleCard, actor: BattleUnit, target: BattleUnit, bond: Float, runMultiplier: Float): Pair<Float, Float> {
        val element = elementMultiplier(card.element, target.element)
        val attack = if (card.magic) actor.magic else actor.attack
        val defense = if (card.magic) target.resistance else target.defense
        val synergy = if (card.synergy == "combo_marked" && target.hasStatus("DefenseDown")) 1f + card.synergyScale else 1f
        val rage = if (actor.affix == "Enraged" && actor.hp * 2 <= actor.maxHp) 1.5f else 1f
        val raw = max(
            1f,
            card.effectivePower * synergy * bond * runMultiplier * attack * rage * actor.attackMultiplier * strikeMultiplier(actor) -
                defense * target.defenseMultiplier * guardMultiplier(target),
        )
        // A broken enemy takes more from everything until it recovers.
        return raw * element * (if (target.hasStatus("Broken")) 1f + target.statusValue("Broken") else 1f) to element
    }

    /** Expected non-critical damage of a card, for target previews and enemy intent badges. */
    fun previewDamage(card: BattleCard?, actor: BattleUnit?, target: BattleUnit?): DamagePreview {
        if (card == null || actor == null || target == null || card.effectivePower <= 0) return DamagePreview(0, 1f)
        val (raw, element) = baseRaw(card, actor, target, bond(card), runDamageMultiplier(card, actor))
        return DamagePreview(max(1, Math.round(raw.toDouble()).toInt()), element)
    }

    private fun deal(card: BattleCard, actor: BattleUnit, target: BattleUnit, bond: Float, counter: Boolean) {
        val crit = rng.nextDouble() < actor.critRate + (if (actor.enemy) 0f else runModifiers.critBonus)
        val (base, element) = baseRaw(card, actor, target, bond, runPlayMultiplier)
        val raw = base * (if (crit) max(1f, actor.critDamage) else 1f)
        var amount = max(1, Math.round(raw.toDouble()).toInt())
        if (target.enemy && !actor.enemy) wearTenacity(card, actor, target, element)
        val shield = target.statusValue("Shield").toInt()
        if (shield > 0) {
            val absorbed = min(shield, amount)
            amount -= absorbed
            if (shield - absorbed <= 0) target.statuses.removeAll { it.name == "Shield" }
            else target.statuses.first { it.name == "Shield" }.magnitude = (shield - absorbed).toFloat()
            say(target.name + "'s shield absorbs " + absorbed + ".")
            // Fully blocked: no health lost and no stress gained.
            if (amount <= 0) { fact("blocked", actor, target, card, absorbed, crit, element, bond); return }
        }
        target.damageBy(amount)
        target.charge(12)
        if (actor.affix == "Vampiric" && actor.alive) {
            val drained = max(1, amount * 3 / 10)
            actor.healBy(drained)
            fact("heal", actor, actor, card, drained, false, 1f, 1f)
        }
        if (target.affix == "Thorned" && !card.magic && actor.alive && target !== actor) {
            val thorns = max(1, amount / 5)
            actor.damageBy(thorns)
            fact("poison", null, actor, null, thorns, false, 1f, 1f)
            say(actor.name + " is cut by thorns for " + thorns + ".")
        }
        if (target.boss && target.phase == 1 && target.alive && target.hp * 2 <= target.maxHp) enterSecondPhase(target)
        if (!target.enemy) {
            sp = min(SP_MAX, sp + 1)
            val gained = Math.rint((35f * amount / max(1, target.maxHp)).toDouble()).toInt()
            target.stress = min(100, target.stress + Math.rint((runModifiers.stressScale * max(8, gained)).toDouble()).toInt())
            if (target.stress >= 100 && target.collapseRounds == 0) {
                target.collapseRounds = 2
                target.awakeningReady = false
                say(target.name + " enters breakdown.")
            }
        }
        fact("damage", actor, target, card, amount, crit, element, bond)
        say(target.name + " takes " + amount + (if (element > 1.01f) " WEAK" else if (element < 0.99f) " resisted" else "") + (if (crit) " CRIT" else "") + ".")
        if (counter && target.alive && actor.alive && target.hasStatus("IceCounter")) {
            val power = target.statusValue("IceCounter")
            target.statuses.removeAll { it.name == "IceCounter" }
            val retaliation = BattleCard(id = "mv_whiteout_counter", name = "Whiteout Counter", owner = target.id, element = target.element, power = power)
            val previousMultiplier = runPlayMultiplier
            runPlayMultiplier = 1f
            deal(retaliation, target, actor, 1f, false)
            runPlayMultiplier = previousMultiplier
        }
    }

    private fun wearTenacity(card: BattleCard, actor: BattleUnit?, target: BattleUnit, element: Float) {
        if (target.maxTenacity <= 0 || target.hasStatus("Broken") || !target.alive) return
        target.tenacity = max(0, target.tenacity - tenacityDamage(card, element))
        if (target.tenacity > 0) return
        target.applyStatus("Broken", BREAK_DAMAGE, 1)
        target.actionCount += 1
        if (actor != null && allies.contains(actor) && actor.alive) { actor.ap += 1; actor.stress = max(0, actor.stress - 10) }
        sp = min(SP_MAX, sp + 1)
        fact("break", actor, target, card, 0, false, element, 1f)
        say(target.name + " BREAKS!")
    }

    private fun fact(kind: String, actor: BattleUnit?, target: BattleUnit, card: BattleCard?, amount: Int, crit: Boolean, element: Float, bond: Float) {
        facts.add(
            BattleFact(
                kind, actor, target, card, amount, target.hp, target.maxHp, element, bond, crit, !target.alive,
                if (kind == "status" && card != null) card.status else "",
            ),
        )
        factSerial++
        if (facts.size > 40) facts.removeAt(0)
    }

    private fun applyTick(unit: BattleUnit, kind: String, amount: Int) {
        if (kind == "poison") unit.damageBy(amount) else unit.healBy(amount)
        fact(kind, null, unit, null, amount, false, 1f, 1f)
        say(unit.name + (if (kind == "poison") " takes " else " regenerates ") + amount + " HP.")
    }

    /**
     * Below half health a lair boss shakes off its debuffs, hits harder for the rest of the fight and raises a shield;
     * from then on it charges its signature move more often.
     */
    private fun enterSecondPhase(boss: BattleUnit) {
        boss.phase = 2
        boss.statuses.removeAll {
            it.name == "AttackDown" || it.name == "DefenseDown" || it.name == "Slow" || it.name == "Poison" || it.name == "Burn" || it.name == "Stun"
        }
        boss.applyStatus("AttackUp", .25f, 99)
        boss.applyStatus("Shield", boss.maxHp * .12f, 99)
        fact("status", boss, boss, BattleCard(id = "boss_phase", name = "Frenzy", status = "Frenzy"), 0, false, 1f, 1f)
        say(boss.name + " flies into a frenzy!")
    }

    private fun rollIntents() {
        intents.clear(); intentTargets.clear()
        for (enemy in enemies) rollIntent(enemy)
    }

    private fun rollIntent(enemy: BattleUnit) {
        intents.remove(enemy.id); intentTargets.remove(enemy.id)
        if (!enemy.alive) return
        val pool = Catalog.enemyCards(enemy)
        if (pool.isEmpty()) return
        var card: BattleCard? = null
        if (enemy.boss && enemy.hasStatus("Charging")) {
            // The charge-up announced last round: the signature move lands now.
            card = pool.firstOrNull { it.id == "e_cataclysm" }
            enemy.statuses.removeAll { it.name == "Charging" }
        }
        if (card == null) card = chooseEnemyCard(enemy, pool)
        if (card == null) return
        intents[enemy.id] = card
        val victim = when (card.target) {
            BattleTarget.Enemy -> enemyVictim(card)
            BattleTarget.Self -> enemy
            BattleTarget.Ally -> mostHurt(enemies)
            else -> null
        }
        if (victim != null) intentTargets[enemy.id] = victim
    }

    /** Weighted by the situation: heal a hurt friend, buff when nobody is buffed, guard when unguarded, else attack. */
    private fun chooseEnemyCard(enemy: BattleUnit, pool: List<BattleCard>): BattleCard? {
        var total = 0f
        val weights = FloatArray(pool.size)
        for (i in pool.indices) {
            val card = pool[i]
            val w = when {
                card.id == "e_cataclysm" || card.ep > enemy.ep || card.ap > enemy.ap -> 0f
                card.id == "e_gather" -> if (enemy.hasStatus("Charging")) 0f else if (enemy.phase >= 2) 2.2f else 1.1f
                card.target == BattleTarget.Ally -> { val hurt = mostHurt(enemies); if (hurt != null && hurt.hp * 100 < hurt.maxHp * 55) 4f else 0f }
                card.target == BattleTarget.AllAllies && card.effectivePower <= 0 ->
                    if (enemies.any { it.alive && it.hasStatus(card.status) }) .3f else 2f
                card.target == BattleTarget.Self -> if (enemy.hasStatus(card.status)) .2f else 1.2f
                card.target == BattleTarget.AllEnemies && card.effectivePower <= 0 -> 1.5f
                else -> if (card.target == BattleTarget.AllEnemies) 2.5f else 3f
            }
            weights[i] = w; total += w
        }
        if (total <= 0f) return null
        var roll = rng.nextDouble() * total
        for (i in pool.indices) { roll -= weights[i]; if (roll <= 0) return pool[i] }
        return pool.last()
    }

    private fun mostHurt(side: List<BattleUnit>): BattleUnit? {
        var best: BattleUnit? = null
        for (unit in side)
            if (unit.alive && unit.hp < unit.maxHp && (best == null || unit.hp.toLong() * best.maxHp < best.hp.toLong() * unit.maxHp)) best = unit
        return best
    }

    /** Enemies go for the weakest exposed fighter more often than not; JD is a rarer, opportunistic target. */
    private fun enemyVictim(card: BattleCard): BattleUnit? {
        val available = allies.filter { exposed(it) }
        val s = summoner
        if (s != null && s.alive && card.target == BattleTarget.Enemy && (available.isEmpty() || rng.nextDouble() < 0.12)) return s
        if (available.isEmpty()) return null
        if (rng.nextDouble() < 0.55) {
            var weakest = available[0]
            for (unit in available) if (unit.hp.toLong() * weakest.maxHp < weakest.hp.toLong() * unit.maxHp) weakest = unit
            return weakest
        }
        return available[rng.next(available.size)]
    }

    /** One enemy carries out its intent (at the end of the turn, or mid-turn when its action count runs out). */
    private fun enemyAct(enemy: BattleUnit, interrupt: Boolean) {
        enemy.actedThisRound = true
        enemy.actionCount = 0
        val card = intents[enemy.id] ?: return
        var target = intentTargets[enemy.id]
        if (interrupt) {
            interrupts++
            fact("interrupt", enemy, enemy, card, 0, false, 1f, 1f)
            say(enemy.name + " moves before the party can finish!")
        }
        // Hasted elites have two actions and repeat their move while they can pay for it.
        var act = 0
        while (act < 2 && enemy.alive && enemy.ap >= card.ap && enemy.ep >= card.ep) {
            if (act > 0 && enemy.affix != "Hasted") break
            if (card.target == BattleTarget.Enemy && (target == null || !target.alive || !exposed(target))) target = enemyVictim(card)
            if (card.target == BattleTarget.Ally && (target == null || !target.alive)) target = mostHurt(enemies)
            if ((card.target == BattleTarget.Enemy || card.target == BattleTarget.Self || card.target == BattleTarget.Ally) && target == null) break
            enemy.ap -= card.ap; enemy.ep -= card.ep
            say(enemy.name + " uses " + card.name + ".")
            resolve(card, enemy, target)
            checkEnd()
            if (finished) return
            act++
        }
        intents.remove(enemy.id)
        intentTargets.remove(enemy.id)
    }

    fun endTurn() {
        if (finished) return
        // Every card not played this turn is thrown out (to the discard pile) unless it Retains, JD's included.
        for (i in hand.size - 1 downTo 0)
            if (!hand[i].retain) { discard.add(hand[i]); hand.removeAt(i) }
        if (epiphanyCard != null) chooseEpiphany(0)
        val ordered = enemies.sortedByDescending { it.effectiveSpeed }
        for (enemy in ordered) {
            if (!enemy.alive || enemy.actedThisRound) continue
            enemyAct(enemy, false)
            if (finished) return
        }
        val commander = enemySummoner
        if (commander != null && commander.alive) {
            if (enemySp >= SP_MAX) {
                enemySp = 0
                for (enemy in enemies) if (enemy.alive) enemy.applyStatus("AttackUp", 0.4f, 3)
                for (ally in allies) if (ally.alive) ally.applyStatus("DefenseDown", 0.3f, 3)
                say("The enemy commander empowers the line.")
            } else if (enemyCp > 0) {
                val pool = Catalog.enemyCommanderCards()
                val support = pool[rng.next(pool.size)]
                enemyCp -= max(1, support.ap)
                say("Enemy commander uses " + support.name + ".")
                resolve(support, commander, null)
            }
        }
        startRound()
    }

    private fun checkEnd() {
        if (finished) return
        val s = summoner
        if (s != null && !s.alive) { finished = true; say("Defeat: your Summoner has fallen."); return }
        val ec = enemySummoner
        if (ec != null && !ec.alive) { finished = true; victory = true; say("Victory: enemy commander defeated."); return }
        val partyAlive = allies.any { it.alive } || reserves.any { it.alive }
        if (!partyAlive) { finished = true; say("Defeat: the party has fallen."); return }
        // With every monster down the commander retreats: that is a win too.
        if (enemies.none { it.alive }) { finished = true; victory = true; say(if (enemySummoner != null) "Victory! The commander retreats." else "Victory!") }
    }

    fun forfeit() { if (finished) return; finished = true; withdrawn = true; say("Withdrawn from battle.") }

    companion object {
        const val FIELD_MAX = 3
        const val RESERVE_MAX = 3
        const val ENEMY_MAX = 6
        const val ALLY_HAND_MAX = 10
        const val SUMMONER_HAND_MAX = 3
        const val SP_MAX = 10
        const val ULTIMATE_SP_COST = 3

        /** Fighter cards drawn to at the start of every round, and the SP a reserve partner's assist costs. */
        const val HAND_SIZE = 5
        const val PARTNER_SP_COST = 2
        const val FURY_ROUND = 10
        const val FURY_STEP = 1.12f
        const val PARTNER_PASSIVE = 1.08f

        /** Tenacity for packs, guards, elites, bosses; the extra damage a broken enemy takes. */
        val TENACITY = intArrayOf(6, 7, 9, 12)
        var BREAK_DAMAGE = .25f

        /** Action count by build: quick skirmishers act after 3 party cards, bruisers and guards after 5, bosses after 5 (4 once enraged). */
        private fun actionCountFor(u: BattleUnit): Int {
            if (u.boss) return if (u.phase >= 2) 4 else 5
            if (u.affix == "Hasted") return 3
            return if (u.speed >= 13f) 3 else if (u.speed <= 9.5f) 5 else 4
        }

        private fun initEnemy(u: BattleUnit) {
            u.actionMax = actionCountFor(u)
            u.actionCount = u.actionMax
            u.maxTenacity = if (u.boss) TENACITY[3] else if (u.affix.isNotEmpty()) TENACITY[2] else if (u.role == BattleRole.Tank) TENACITY[1] else TENACITY[0]
            u.tenacity = u.maxTenacity
        }

        private fun ensureWall(side: List<BattleUnit>) {
            if (side.any { it.alive && it.taunting }) return
            var wall: BattleUnit? = null
            for (u in side) if (u.alive && (wall == null || u.maxHp > wall.maxHp)) wall = u
            wall?.innateTaunt = true
        }

        fun bondKey(first: String, second: String): String = if (first.compareTo(second) < 0) "$first:$second" else "$second:$first"

        private fun elementMultiplier(attack: BattleElement, defend: BattleElement): Float {
            if (attack == BattleElement.Neutral || defend == BattleElement.Neutral) return 1f
            val beats = arrayOf(
                BattleElement.Neutral, BattleElement.Wind, BattleElement.Fire, BattleElement.Earth, BattleElement.Lightning,
                BattleElement.Water, BattleElement.Dark, BattleElement.Light,
            )
            if (beats[attack.ordinal] == defend) return 1.5f
            if (beats[defend.ordinal] == attack) return 1f / 1.5f
            return 1f
        }

        /** Party hits wear an enemy's tenacity: 1, +1 for a 2 EP card, +1 more at 3 EP, +1 on a WEAK hit, +2 for an ultimate. */
        fun tenacityDamage(card: BattleCard?, element: Float): Int {
            if (card == null) return 1
            var wear = 1 + (if (card.ep >= 2) 1 else 0) + (if (card.ep >= 3) 1 else 0) + (if (element > 1.01f) 1 else 0)
            if (card.kind == BattleCardKind.Ultimate) wear += 2
            return wear
        }
    }
}
