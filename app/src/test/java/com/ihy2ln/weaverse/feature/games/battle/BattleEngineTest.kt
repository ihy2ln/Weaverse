package com.ihy2ln.weaverse.feature.games.battle

import com.ihy2ln.weaverse.feature.games.CardGameCard
import com.ihy2ln.weaverse.feature.games.CardGameCharacter
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCard
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCardKind
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleElement
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleEncounterSpec
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleRole
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleState
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleTarget
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleUnit
import com.ihy2ln.weaverse.feature.games.battle.engine.Bestiary
import com.ihy2ln.weaverse.feature.games.battle.engine.BuiltinContent
import com.ihy2ln.weaverse.feature.games.battle.engine.CardText
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.GameLogic
import com.ihy2ln.weaverse.feature.games.battle.engine.GameSave
import com.ihy2ln.weaverse.feature.games.battle.engine.Rng
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Ports of the checks in the Unity game's Tools/RulesSim, plus unit tests for the rules that matter most. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BattleEngineTest {
    private val novel = CardGameCharacter(
        id = "mara_vell", name = "Mara Vell", role = "Support", element = "Light", tier = 4, hp = 120, magic = 20f, bonds = listOf("kaela"),
        cards = listOf(
            CardGameCard("lantern_flick", "Lantern Flick", ep = 1, ap = 1, power = 9f, initiation = true),
            CardGameCard("canal_mist", "Canal Mist", ep = 2, ap = 1, power = 1f, status = "AttackDown", magnitude = .25f, duration = 2),
            CardGameCard("courier_dash", "Courier Dash", ep = 1, ap = 0, target = "None", draw = 2),
            CardGameCard("signal_flare", "Signal Flare", ep = 2, ap = 1, power = 1.2f, target = "AllEnemies"),
            CardGameCard("warm_light", "Warm Light", ep = 1, ap = 1, power = 0f, heal = 99f, target = "Ally"),
            CardGameCard("last_delivery", "Last Delivery", ep = 3, ap = 1, power = 2f),
        ),
        ultimates = listOf(
            CardGameCard("thousand_lanterns", "A Thousand Lanterns", power = 3f, target = "AllEnemies"),
            CardGameCard("lighthouse_vigil", "Lighthouse Vigil", target = "AllAllies", status = "DefenseUp", magnitude = .4f, duration = 2, heal = 30f),
        ),
    )

    @BeforeAll
    fun load() {
        Bestiary.loadJson(File("src/main/assets/cardgame/bestiary.json").readText())
        Catalog.register(BuiltinContent.characters(), listOf(novel.toDef()))
    }

    @Test
    fun contentLoadsAndNovelCardsAreSanitised() {
        assertTrue(Bestiary.loaded)
        val mara = Catalog.get("mara_vell")
        assertNotNull(mara)
        mara!!
        assertEquals(6, mara.cards.size)
        assertEquals(2, mara.ultimates.size)
        assertTrue(mara.cards[0].power <= 3.2f, "power is clamped")
        assertTrue(mara.cards.all { it.id.startsWith("mara_vell_") }, "ids are namespaced")
        assertEquals(1, mara.cards.count { it.initiation })
        // An ally card never deals damage; heal is clamped to the built-in range.
        assertEquals(0f, mara.cards[4].power)
        assertTrue(mara.cards[4].heal <= 34f)
        for (c in Catalog.characters) for (d in c.cards) assertTrue(CardText.describe(d.toBattleCard(c.elementEnum, c.bonds)).isNotEmpty(), d.id)
    }

    @Test
    fun builtinCastIsRegisteredFirst() {
        assertEquals(listOf("kaela", "ghislaine", "elara", "helda", "daisy", "clarity", "celestium", "mara_vell"), Catalog.characters.map { it.id })
    }

    @Test
    fun elementWheelAndDamagePreview() {
        val fire = BattleCard(id = "f", name = "Fire", owner = "a", element = BattleElement.Fire, power = 1f)
        val state = duel(attacker = unit("a", BattleElement.Fire), defender = unit("d", BattleElement.Wind, enemy = true))
        val wind = state.enemies[0]
        val a = state.allies[0]
        assertEquals(1.5f, state.previewDamage(fire, a, wind).element, 0.001f)
        wind.element = BattleElement.Water
        assertEquals(1f / 1.5f, state.previewDamage(fire, a, wind).element, 0.001f)
        wind.element = BattleElement.Neutral
        assertEquals(1f, state.previewDamage(fire, a, wind).element, 0.001f)
        assertTrue(state.previewDamage(fire, a, wind).amount >= 1)
    }

    @Test
    fun breakAfterEnoughHitsMakesTheEnemyTakeMore() {
        val state = duel(attacker = unit("a", BattleElement.Neutral), defender = unit("d", BattleElement.Neutral, enemy = true))
        val enemy = state.enemies[0]
        val a = state.allies[0]
        a.ap = 9; a.ep = 9
        val hit = BattleCard(id = "h", name = "Hit", owner = "a", ep = 0, ap = 0, power = .1f)
        val before = state.previewDamage(hit, a, enemy).amount
        enemy.hp = enemy.maxHp
        repeat(10) { state.tryPlay(hit.copy(), enemy) }
        assertTrue(enemy.hasStatus("Broken"), "tenacity ran out")
        assertTrue(state.previewDamage(hit, a, enemy).amount >= before)
        assertEquals(3, BattleState.tenacityDamage(BattleCard(ep = 3), 1f).minus(0), "3 EP card wears 3")
        assertEquals(3, BattleState.tenacityDamage(BattleCard(ep = 1, kind = BattleCardKind.Ultimate), 1f))
    }

    @Test
    fun aTauntingEnemyShieldsItsSide() {
        val a = unit("a", BattleElement.Neutral)
        val front = unit("front", BattleElement.Neutral, enemy = true).also { it.maxHp = 300; it.hp = 300 }
        val back = unit("back", BattleElement.Neutral, enemy = true)
        val state = BattleState(1, listOf(a), emptyList(), listOf(back, front), listOf(), BuiltinContent.summoner())
        // Nobody taunts, so the sturdiest enemy is made the wall and the other is covered.
        assertTrue(state.exposed(front))
        assertFalse(state.exposed(back))
    }

    @Test
    fun saveSurvivesAJsonRoundTrip() {
        val save = GameSave()
        GameLogic.welcome(save)
        GameLogic.startRun(save, 42)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val copy = json.decodeFromString(GameSave.serializer(), json.encodeToString(GameSave.serializer(), save))
        assertEquals(save.party, copy.party)
        assertEquals(save.deck, copy.deck)
        assertEquals(save.run.nodes.map { it.title to it.seed }, copy.run.nodes.map { it.title to it.seed })
        assertTrue(copy.run.active)
        // A new novel character joins an existing save once.
        assertTrue("mara_vell" in save.known)
        assertTrue(save.collection.any { it.startsWith("mara_vell_") })
    }

    @Test
    fun sameSeedBuildsTheSameEncounter() {
        val spec = BattleEncounterSpec(depth = 3, seed = 99, kind = "elite", theme = "edge")
        val a = Catalog.build(spec)
        val b = Catalog.build(spec)
        assertEquals(a.enemies.map { it.name to it.maxHp }, b.enemies.map { it.name to it.maxHp })
        assertTrue(Catalog.build(BattleEncounterSpec(depth = 4, seed = 7, kind = "boss", region = "silverbrook_edge")).boss != null)
    }

    @Test
    fun autoPlayedRunsAlwaysTerminateAndTheDraftGrowsTheCollection() {
        val parties = listOf(
            listOf("kaela", "daisy", "clarity", "elara", "helda", "ghislaine"),
            listOf("ghislaine", "elara", "helda"),
            listOf("mara_vell", "kaela", "celestium", "daisy"),
        )
        val rates = parties.map { party ->
            var won = 0
            val runs = 60
            val rng = Rng(7)
            repeat(runs) {
                val save = GameSave()
                GameLogic.welcome(save)
                save.party = party.toMutableList()
                GameLogic.startRun(save, rng.next(1, Int.MAX_VALUE))
                var guard = 0
                while (save.run.active && guard++ < 20) {
                    val node = GameLogic.current(save)!!
                    if (node.kind == "rest") { GameLogic.rest(save); continue }
                    val s = GameLogic.startBattle(save)!!
                    autoPlay(s)
                    assertTrue(s.finished, "battle finished within 60 rounds (${node.kind} depth ${node.depth})")
                    GameLogic.finishBattle(save, s, rng.next(1, Int.MAX_VALUE))
                    if (save.run.reward.isNotEmpty()) GameLogic.takeReward(save, save.run.reward[0])
                }
                if (save.run.won) won++
                val before = save.collection.size
                val lv = save.levels.sumOf { it.level }
                for (i in save.draft.indices) GameLogic.draft(save, i, save.draft[i].options[0])
                val lvAfter = save.levels.sumOf { it.level }
                assertTrue(save.draft.isEmpty() || save.collection.size + lvAfter > before + lv, "draft grows the collection")
                GameLogic.closeDraft(save)
            }
            won / runs.toFloat()
        }
        // A full party of six wins far more often than three fighters, and the game is neither impossible nor trivial.
        assertTrue(rates[0] > rates[1], "six fighters beat three: $rates")
        assertTrue(rates[0] in 0.15f..0.98f, "six-fighter win rate in a playable band: ${rates[0]}")
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private fun unit(id: String, element: BattleElement, enemy: Boolean = false) = BattleUnit(
        id = id, name = id, element = element, enemy = enemy, role = BattleRole.Dps, maxHp = 200, hp = 200, attack = 20f, magic = 20f,
        defense = 5f, resistance = 5f, speed = 10f,
    )

    private fun duel(attacker: BattleUnit, defender: BattleUnit) =
        BattleState(1, listOf(attacker), emptyList(), listOf(defender), listOf(), BuiltinContent.summoner())

    // Greedy: ultimates when ready, then the best affordable card per EP+AP spent, decrees at full SP, basic attacks, end turn.
    private fun autoPlay(s: BattleState) {
        var safety = 0
        while (!s.finished && s.round <= 60 && safety++ < 5000) {
            if (s.epiphanyCard != null) s.chooseEpiphany(0)
            var acted = false
            for (a in s.allies) {
                if (!a.alive || a.ultimate < 100 || s.sp < s.ultCost(a)) continue
                val ults = Catalog.ultimates(a)
                if (ults.isEmpty()) continue
                val u = ults[0]
                val t = if (u.target == BattleTarget.Enemy) firstExposed(s) else if (u.target == BattleTarget.Self) a else null
                if (s.tryUltimate(a, 0, t)) { acted = true; break }
            }
            if (acted) continue
            if (s.sp >= BattleState.SP_MAX && s.trySummonerUltimate()) continue
            var best: BattleCard? = null
            var bestTarget: BattleUnit? = null
            var bestScore = 0f
            for (c in s.hand) {
                val actor = s.ownerOf(c)
                if (!s.canPay(c, actor)) continue
                for (t in targets(s, c, actor)) {
                    if (!s.isTarget(c, actor, t)) continue
                    val score = score(s, c, actor, t)
                    if (score > bestScore) { bestScore = score; best = c; bestTarget = t }
                }
            }
            if (best != null && s.tryPlay(best, bestTarget)) continue
            for (a in s.allies) if (a.alive && a.ap > 0 && s.tryBasic(a, firstExposed(s))) { acted = true; break }
            if (acted) continue
            s.endTurn()
        }
        if (!s.finished) s.forfeit()
    }

    private fun targets(s: BattleState, c: BattleCard, actor: BattleUnit?): List<BattleUnit?> = when {
        c.target == BattleTarget.AllEnemies || c.target == BattleTarget.AllAllies || c.target == BattleTarget.None -> listOf(null)
        c.target == BattleTarget.Self -> listOf(actor)
        else -> (if (c.target == BattleTarget.Enemy) s.enemies else s.allies) + listOfNotNull(if (c.target == BattleTarget.Enemy) s.enemySummoner else null)
    }

    private fun firstExposed(s: BattleState): BattleUnit? =
        s.enemies.firstOrNull { s.exposed(it) } ?: s.enemySummoner?.takeIf { it.alive }

    private fun score(s: BattleState, c: BattleCard, actor: BattleUnit?, t: BattleUnit?): Float {
        var score = 1f
        if (c.effectivePower > 0 && actor != null)
            for (e in s.enemies) if (e.alive && (c.target == BattleTarget.AllEnemies || e === t)) score += s.previewDamage(c, actor, e).amount
        if (c.effectivePower > 0 && actor != null && t != null && t === s.enemySummoner) score += s.previewDamage(c, actor, t).amount
        if (c.effectiveHeal > 0) {
            var missing = 0f
            for (a in s.allies) if (a.alive && (c.target == BattleTarget.AllAllies || a === t)) missing += a.maxHp - a.hp
            score += min(missing, c.effectiveHeal * (if (c.target == BattleTarget.AllAllies) 3 else 1)) * 1.2f
        }
        if (c.status.isNotEmpty()) score += 12
        if (c.draw > 0) score += 10 * c.draw
        if (c.transferEp) score += if (actor != null && actor.ep > 0 && t != null && t !== actor) 6 else -100
        return score / max(1, c.ep + c.ap)
    }
}
