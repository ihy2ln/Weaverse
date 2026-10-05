package com.ihy2ln.weaverse.feature.games.battle.engine

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * The player's progress (cardgame/native_save.json) and the run loop around battles: party + deck from the collection ->
 * a short run (fights, a rest, a lair boss) -> the end-of-run draft, which is the only way cards join the collection for
 * good. Cards picked up during a run are lost when it ends. Port of the Unity game's GameSave/GameLogic.
 */
@Serializable class CardLevel(var id: String = "", var level: Int = 1)
@Serializable class UnitHp(var id: String = "", var hp: Int = 0)
@Serializable class CardMods(var id: String = "", var mods: String = "")

@Serializable
class RunNode(
    /** normal, elite, rest, boss */
    var kind: String = "normal",
    var theme: String = "",
    var region: String = "",
    var title: String = "",
    var depth: Int = 1,
    var seed: Int = 0,
)

@Serializable
class RunProgress(
    var active: Boolean = false,
    var seed: Int = 0,
    var index: Int = 0,
    var victories: Int = 0,
    var summonerHp: Int = -1,
    var nodes: MutableList<RunNode> = mutableListOf(),
    var hp: MutableList<UnitHp> = mutableListOf(),
    /** Cards won after fights this run (gone when the run ends) and epiphanies taken this run. */
    var runCards: MutableList<String> = mutableListOf(),
    var epiphanies: MutableList<CardMods> = mutableListOf(),
    /** The cards offered after the last fight, waiting for a pick (empty when nothing is waiting). */
    var reward: MutableList<String> = mutableListOf(),
    var won: Boolean = false,
    var lost: Boolean = false,
)

@Serializable
class DraftRow(
    /** A fighter id (or "jd"), and up to three card ids. A card already in the collection means "level it up". */
    var owner: String = "",
    var options: MutableList<String> = mutableListOf(),
    var picked: String = "",
)

@Serializable
class GameSave(
    var version: Int = 1,
    var collection: MutableList<String> = mutableListOf(),
    var deck: MutableList<String> = mutableListOf(),
    /** Fighters in order: the first three start on the field, the next three wait in reserve. */
    var party: MutableList<String> = mutableListOf(),
    var levels: MutableList<CardLevel> = mutableListOf(),
    /** Characters whose starter cards were already added (so a new novel character joins once). */
    var known: MutableList<String> = mutableListOf(),
    var run: RunProgress = RunProgress(),
    var draft: MutableList<DraftRow> = mutableListOf(),
    var runsPlayed: Int = 0,
    var runsWon: Int = 0,
)

object GameLogic {
    const val FIELD_SIZE = 3
    const val PARTY_SIZE = 6
    const val MAX_CARD_LEVEL = 5
    const val LEVEL_POWER = .10f
    private val DEFAULT_PARTY = listOf("kaela", "daisy", "clarity", "elara", "helda", "ghislaine")

    // ---- collection ----------------------------------------------------------------------------------------------------

    /**
     * New characters (first launch, or a character just brought in from a novel) get their starter cards: their opening
     * card plus the three cheapest others. A fresh save also gets JD's four starters and the default party.
     */
    fun welcome(save: GameSave) {
        if (Catalog.SUMMONER_ID !in save.known) {
            save.known.add(Catalog.SUMMONER_ID)
            for (id in BuiltinContent.SUMMONER_STARTERS) grant(save, id, true)
        }
        // The deck holds the player's picks for every character; only the party's cards go into a fight.
        for (c in Catalog.characters) {
            if (c.id in save.known) continue
            save.known.add(c.id)
            for (id in starters(c)) grant(save, id, true)
        }
        save.party.removeAll { Catalog.get(it) == null }
        if (save.party.isEmpty())
            for (id in DEFAULT_PARTY) if (Catalog.get(id) != null && save.party.size < PARTY_SIZE) save.party.add(id)
        save.deck.removeAll { it !in save.collection }
    }

    fun starters(c: CharacterDef): List<String> {
        val list = mutableListOf<String>()
        val opener = c.cards.firstOrNull { it.initiation } ?: c.cards.firstOrNull()
        if (opener != null) list.add(opener.id)
        val rest = c.cards.filter { it !== opener }.sortedWith(compareBy({ it.ep }, { c.cards.indexOf(it) }))
        for (r in rest) { if (list.size >= 4) break; list.add(r.id) }
        return list
    }

    private fun grant(save: GameSave, id: String, toDeck: Boolean) {
        if (id !in save.collection) save.collection.add(id)
        if (toDeck && id !in save.deck) save.deck.add(id)
    }

    fun level(save: GameSave, id: String): Int = save.levels.firstOrNull { it.id == id }?.level ?: 1

    fun owned(save: GameSave, id: String): Boolean = id in save.collection

    fun toggleDeck(save: GameSave, id: String) {
        if (id !in save.collection) return
        if (!save.deck.remove(id)) save.deck.add(id)
    }

    fun toggleParty(save: GameSave, id: String) {
        if (save.run.active) return
        if (id in save.party) { if (save.party.size > 1) save.party.remove(id) }
        else if (save.party.size < PARTY_SIZE && Catalog.get(id) != null) save.party.add(id)
    }

    /** A card as it plays for this player: its permanent level (draft duplicates, +10% each) on top. */
    fun played(save: GameSave, id: String): BattleCard? {
        val card = Catalog.card(id) ?: return null
        val level = level(save, id)
        if (level > 1) { card.power *= 1f + LEVEL_POWER * (level - 1); card.heal *= 1f + LEVEL_POWER * (level - 1) }
        return card
    }

    // ---- runs ----------------------------------------------------------------------------------------------------------

    fun startRun(save: GameSave, seed: Int) {
        val rng = Rng(seed)
        val themes = mutableListOf<String>()
        for (f in Bestiary.families) for (t in f.themes) if (t !in themes) themes.add(t)
        if (themes.isEmpty()) themes.add("edge")
        val theme = themes[rng.next(themes.size)]
        val region = if (Bestiary.bosses.isNotEmpty()) Bestiary.bosses[rng.next(Bestiary.bosses.size)].region else ""
        save.run = RunProgress(active = true, seed = seed)
        save.run.nodes.add(RunNode("normal", theme, "", "Forest Edge", 1, rng.next(1, Int.MAX_VALUE)))
        save.run.nodes.add(RunNode("normal", theme, "", "Deeper Woods", 2, rng.next(1, Int.MAX_VALUE)))
        save.run.nodes.add(RunNode("elite", theme, "", "Elite Hunt", 3, rng.next(1, Int.MAX_VALUE)))
        save.run.nodes.add(RunNode("rest", "", "", "Campfire", 3, 0))
        save.run.nodes.add(RunNode("boss", theme, region, "The Lair", 4, rng.next(1, Int.MAX_VALUE)))
        for (id in save.party) { val u = Catalog.unit(id); if (u != null) save.run.hp.add(UnitHp(id, u.maxHp)) }
        save.runsPlayed++
        save.draft.clear()
    }

    fun current(save: GameSave): RunNode? = if (save.run.active && save.run.index < save.run.nodes.size) save.run.nodes[save.run.index] else null

    private fun hp(save: GameSave, id: String, maxHp: Int): Int {
        val h = save.run.hp.firstOrNull { it.id == id }
        return if (h != null) max(0, min(maxHp, h.hp)) else maxHp
    }

    fun startBattle(save: GameSave): BattleState? {
        val node = current(save)
        if (node == null || node.kind == "rest") return null
        val field = mutableListOf<BattleUnit>()
        val reserves = mutableListOf<BattleUnit>()
        for (id in save.party) {
            val u = Catalog.unit(id) ?: continue
            u.hp = hp(save, u.id, u.maxHp)
            (if (field.size < FIELD_SIZE) field else reserves).add(u)
        }
        // The fallen sit out: a downed fighter on the field swaps with the first living reserve.
        for (i in field.indices)
            if (!field[i].alive) {
                val r = reserves.indexOfFirst { it.alive }
                if (r >= 0) { val t = field[i]; field[i] = reserves[r]; reserves[r] = t }
            }
        val deck = mutableListOf<BattleCard>()
        val ids = save.deck + save.run.runCards
        for (id in ids) {
            val card = played(save, id) ?: continue
            if (card.kind != BattleCardKind.Summoner && card.owner !in save.party) continue
            val mods = save.run.epiphanies.firstOrNull { it.id == id }
            if (mods != null) for (m in mods.mods.split(',')) Catalog.applyEpiphany(card, m)
            deck.add(card)
        }
        val e = Catalog.build(BattleEncounterSpec(node.depth, node.seed, node.kind, node.theme, node.region))
        val jd = BuiltinContent.summoner()
        if (save.run.summonerHp >= 0) jd.hp = max(1, min(jd.maxHp, save.run.summonerHp))
        val state = BattleState(node.seed xor 0x2f6b, field, reserves, e.enemies, deck, jd, e.commander)
        // Each fight one fighter card glows: playing it offers an epiphany that lasts the rest of the run.
        val glow = deck.filter { it.kind != BattleCardKind.Summoner && it.epiphany.isEmpty() }
        if (glow.isNotEmpty()) state.glowCard = glow[Rng(node.seed).next(glow.size)].id
        return state
    }

    /** After a fight: health carries over, epiphanies are kept for the run, and a win offers three cards. */
    fun finishBattle(save: GameSave, state: BattleState, rewardSeed: Int) {
        val run = save.run
        for (u in state.allies) setHp(save, u)
        for (u in state.reserves) setHp(save, u)
        run.summonerHp = state.summoner?.hp ?: -1
        for ((id, mods) in state.epiphanies) {
            val m = run.epiphanies.firstOrNull { it.id == id }
            if (m == null) run.epiphanies.add(CardMods(id, mods)) else m.mods = m.mods + "," + mods
        }
        if (!state.victory) { run.lost = true; endRun(save, false); return }
        run.victories++
        val node = current(save)
        run.index++
        if (node != null && node.kind == "boss") { run.won = true; endRun(save, true); return }
        run.reward = rewardOptions(save, Rng(rewardSeed), 3).toMutableList()
    }

    private fun setHp(save: GameSave, u: BattleUnit) {
        val h = save.run.hp.firstOrNull { it.id == u.id }
        if (h == null) save.run.hp.add(UnitHp(u.id, u.hp)) else h.hp = u.hp
    }

    /** Three party cards that are not already in this run's deck: owned ones the player left out, and ones not drafted yet. */
    fun rewardOptions(save: GameSave, rng: Rng, count: Int): List<String> {
        val pool = mutableListOf<String>()
        for (id in save.party) {
            val c = Catalog.get(id) ?: continue
            for (d in c.cards) if (d.id !in save.deck && d.id !in save.run.runCards) pool.add(d.id)
        }
        for (d in BuiltinContent.summonerKit()) if (d.id !in save.deck && d.id !in save.run.runCards) pool.add(d.id)
        val picks = mutableListOf<String>()
        while (pool.isNotEmpty() && picks.size < count) { val i = rng.next(pool.size); picks.add(pool[i]); pool.removeAt(i) }
        return picks
    }

    fun takeReward(save: GameSave, id: String) {
        if (id !in save.run.reward) return
        save.run.runCards.add(id)
        save.run.reward.clear()
    }

    fun skipReward(save: GameSave) { save.run.reward.clear() }

    /** The campfire: everyone heals 40% of their health; the fallen get up with 40%. */
    fun rest(save: GameSave) {
        val node = current(save)
        if (node == null || node.kind != "rest") return
        for (h in save.run.hp) {
            val u = Catalog.unit(h.id)
            if (u != null) h.hp = min(u.maxHp, h.hp + (u.maxHp * .4f).toInt())
        }
        if (save.run.summonerHp >= 0) save.run.summonerHp = min(BuiltinContent.summoner().maxHp, save.run.summonerHp + 80)
        save.run.index++
    }

    fun abandon(save: GameSave) { if (save.run.active) { save.run.lost = true; endRun(save, false) } }

    /**
     * The draft: one row per fighter in the party (only the field three after a defeat) plus one for JD after a win. Each
     * row offers up to three of that character's cards: unowned ones first, then level-ups of owned ones.
     */
    private fun endRun(save: GameSave, won: Boolean) {
        save.run.active = false
        if (won) save.runsWon++
        val rng = Rng(save.run.seed xor 0x51ed)
        save.draft.clear()
        val owners = mutableListOf<String>()
        for (i in save.party.indices) { if (!(won || i < FIELD_SIZE)) break; owners.add(save.party[i]) }
        if (won) owners.add(Catalog.SUMMONER_ID)
        for (owner in owners) {
            val cards = mutableListOf<String>()
            if (owner == Catalog.SUMMONER_ID) for (d in BuiltinContent.summonerKit()) cards.add(d.id)
            else { val c = Catalog.get(owner) ?: continue; for (d in c.cards) cards.add(d.id) }
            val fresh = cards.filter { it !in save.collection }
            val upgrades = cards.filter { it in save.collection && level(save, it) < MAX_CARD_LEVEL }
            val row = DraftRow(owner)
            pick(row.options, fresh, rng, 3)
            pick(row.options, upgrades, rng, 3)
            if (row.options.isNotEmpty()) save.draft.add(row)
        }
    }

    private fun pick(into: MutableList<String>, from: List<String>, rng: Rng, max: Int) {
        val pool = from.toMutableList()
        while (pool.isNotEmpty() && into.size < max) { val i = rng.next(pool.size); into.add(pool[i]); pool.removeAt(i) }
    }

    fun draft(save: GameSave, row: Int, id: String) {
        if (row < 0 || row >= save.draft.size) return
        val r = save.draft[row]
        if (id !in r.options || r.picked.isNotEmpty()) return
        r.picked = id
        if (id !in save.collection) { save.collection.add(id); save.deck.add(id) }
        else {
            val l = save.levels.firstOrNull { it.id == id }
            if (l == null) save.levels.add(CardLevel(id, 2)) else l.level = min(MAX_CARD_LEVEL, l.level + 1)
        }
    }

    fun draftDone(save: GameSave): Boolean = save.draft.all { it.picked.isNotEmpty() }

    fun closeDraft(save: GameSave) { save.draft.clear(); save.run = RunProgress() }
}
