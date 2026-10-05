package com.ihy2ln.weaverse.feature.games.adventure

import com.ihy2ln.weaverse.feature.games.battle.TestBot
import com.ihy2ln.weaverse.feature.games.battle.engine.Bestiary
import com.ihy2ln.weaverse.feature.games.battle.engine.BuiltinContent
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.GameLogic
import com.ihy2ln.weaverse.feature.games.battle.engine.GameSave
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdventureRulesTest {
    private lateinit var base: GameSave

    @BeforeAll
    fun load() {
        Bestiary.loadJson(File("src/main/assets/cardgame/bestiary.json").readText())
        Catalog.register(BuiltinContent.characters(), null)
        base = GameSave().also { GameLogic.welcome(it) }
    }

    private fun adventure(seed: Int = 5) = AdventureRules.begin(base, "", seed)

    @Test
    fun beginGivesAFullPartyAStarterPotionAndAScene() {
        val adv = adventure()
        assertTrue(adv.active)
        assertEquals(base.party.size, adv.hp.size)
        assertEquals(1, AdventureRules.count(adv, "potion"))
        assertEquals(AdventureRules.START_GOLD, adv.gold)
        assertTrue(adv.scene.narration.isNotBlank())
        assertTrue(adv.scene.choices.size >= 2)
    }

    @Test
    fun offlineStorytellerIsDeterministicAndEndsAtTheLair() {
        val a = adventure(9); val b = adventure(9)
        assertEquals(a.scene.narration, b.scene.narration)
        assertEquals(a.scene.choices.map { it.text }, b.scene.choices.map { it.text })
        a.depth = AdventureRules.LENGTH - 1
        val last = OfflineDirector.scene(a)
        assertEquals(ChoiceKind.BOSS, last.choices.first().kind)
        assertEquals(1, last.choices.count { it.kind == ChoiceKind.BOSS })
    }

    @Test
    fun aiScenesAreParsedAndKeptOnRails() {
        val adv = adventure()
        val ok = AdventureRules.parseScene(
            """Sure! {"place":"Mossy Ford","narration":"Mist rolls over the water.","keeper":"Old Wren","choices":[{"text":"Wade across","kind":"explore"},{"text":"Slay the thing in the reeds","kind":"boss"},{"text":"Do a cartwheel","kind":"juggling"}]}""",
            adv,
        )
        assertNotNull(ok)
        ok!!
        assertEquals("Mossy Ford", ok.place)
        assertTrue(ok.choices.none { it.kind == ChoiceKind.BOSS }, "no boss before the lair")
        assertTrue(ok.choices.all { it.kind in ChoiceKind.all }, "unknown kinds become talk")
        assertNull(AdventureRules.parseScene("no json here", adv))
        assertNull(AdventureRules.parseScene("""{"narration":""}""", adv))
        // A scene with a single choice is topped up.
        assertTrue(AdventureRules.parseScene("""{"narration":"Quiet.","choices":[{"text":"Wait"}]}""", adv)!!.choices.size >= 2)
    }

    @Test
    fun campLootAndItemsChangeHealthAndPack() {
        val adv = adventure()
        val id = base.party.first()
        val max = Catalog.unit(id)!!.maxHp
        adv.hp.first { it.id == id }.hp = 1
        assertTrue(AdventureRules.use(adv, "potion", id))
        assertTrue(AdventureRules.hpOf(adv, id) > 1)
        assertFalse(AdventureRules.use(adv, "potion", id), "the potion is gone")
        adv.hp.first { it.id == id }.hp = 1
        AdventureRules.camp(adv)
        assertEquals(1 + (max * .35f).toInt(), AdventureRules.hpOf(adv, id))
        val gold = adv.gold
        AdventureRules.loot(adv)
        assertTrue(adv.gold > gold || adv.items.values.sum() > 0)
        // A full-health fighter can't drink a draught.
        adv.items["potion"] = 1
        val other = base.party[1]
        assertFalse(AdventureRules.use(adv, "potion", other))
    }

    @Test
    fun barterBuySellAndHaggle() {
        val adv = adventure()
        adv.shop = AdventureRules.makeShop(adv, "Marl", base)
        val shop = adv.shop!!
        assertTrue(shop.stock.any { it.kind == "item" })
        assertTrue(shop.stock.any { it.kind == "card" }, "cards the party doesn't have yet")
        assertTrue(shop.stock.filter { it.kind == "card" }.none { it.id in base.deck })
        // Can't afford it.
        adv.gold = 0
        assertFalse(AdventureRules.buy(adv, 0))
        // Buy a ration.
        val idx = shop.stock.indexOfFirst { it.id == "ration" }
        adv.gold = 500
        val price = shop.stock[idx].price
        assertTrue(AdventureRules.buy(adv, idx))
        assertEquals(500 - price, adv.gold)
        assertEquals(1, AdventureRules.count(adv, "ration"))
        // Buy a card: it joins the adventure deck.
        val ci = shop.stock.indexOfFirst { it.kind == "card" }
        val cardId = shop.stock[ci].id
        assertTrue(AdventureRules.buy(adv, ci))
        assertTrue(cardId in adv.bonusCards)
        // Haggle: one try per ware.
        val first = AdventureRules.haggle(adv, 0)
        assertTrue(first.isNotBlank())
        assertTrue(shop.stock[0].haggled)
        assertTrue(AdventureRules.haggle(adv, 0).contains("again"))
        // Sell the ration for half.
        val g = adv.gold
        assertTrue(AdventureRules.sell(adv, "ration"))
        assertEquals(g + AdventureRules.sellPrice("ration"), adv.gold)
        assertFalse(AdventureRules.sell(adv, "ration"))
        // The charm raises the chance, never lowers it.
        adv.items["charm"] = 1
        var wins = 0
        repeat(200) { n -> val a = adventure(n + 1); a.items["charm"] = 1; a.shop = AdventureRules.makeShop(a, "x", base); val before = a.shop!!.stock[0].price; AdventureRules.haggle(a, 0); if (a.shop!!.stock[0].price < before) wins++ }
        assertTrue(wins in 90..170, "about 65% wins with the charm: $wins")
    }

    @Test
    fun aFightPaysOutAndCarriesHealthBack() {
        val adv = adventure(3)
        adv.depth = 2
        val state = AdventureRules.startBattle(adv, base, ChoiceKind.FIGHT)
        assertNotNull(state)
        state!!
        assertTrue(state.enemies.isNotEmpty())
        // Bought cards are in the deck of the fight.
        adv.bonusCards.add(Catalog.get(base.party.first())!!.cards.first { it.id !in base.deck }.id)
        val withBonus = AdventureRules.startBattle(adv, base, ChoiceKind.FIGHT)!!
        val total = withBonus.drawPile.size + withBonus.hand.size
        assertTrue(total > state.drawPile.size + state.hand.size, "the bought card is shuffled in")
        TestBot.autoPlay(state)
        val gold = adv.gold
        val line = AdventureRules.finishBattle(adv, state, ChoiceKind.FIGHT)
        assertTrue(line.isNotBlank())
        if (state.victory) { assertTrue(adv.gold > gold); assertEquals(1, adv.battlesWon); assertEquals(3, adv.depth) }
        else { assertTrue(adv.over); assertFalse(adv.active) }
        // Health follows the fighters out of the battle.
        for (u in state.allies + state.reserves) assertEquals(u.hp, AdventureRules.hpOf(adv, u.id))
    }

    @Test
    fun withdrawingCostsGoldAndDefeatEndsTheAdventure() {
        val adv = adventure(4)
        adv.gold = 100
        val s = AdventureRules.startBattle(adv, base, ChoiceKind.FIGHT)!!
        s.forfeit()
        AdventureRules.finishBattle(adv, s, ChoiceKind.FIGHT)
        assertTrue(adv.gold < 100)
        assertFalse(adv.over)
        val dead = AdventureRules.startBattle(adv, base, ChoiceKind.FIGHT)!!
        dead.allies.forEach { it.hp = 0 }; dead.reserves.forEach { it.hp = 0 }
        dead.summoner!!.hp = 0
        dead.endTurn()
        assertTrue(dead.finished && !dead.victory)
        AdventureRules.finishBattle(adv, dead, ChoiceKind.FIGHT)
        assertTrue(adv.over)
    }

    @Test
    fun saveSurvivesAJsonRoundTrip() {
        val adv = adventure(8)
        adv.items["tonic"] = 2; adv.bonusCards.add("kaela_x"); adv.shop = AdventureRules.makeShop(adv, "Marl", base)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val copy = json.decodeFromString(AdvSave.serializer(), json.encodeToString(AdvSave.serializer(), adv))
        assertEquals(adv.scene.narration, copy.scene.narration)
        assertEquals(adv.items, copy.items)
        assertEquals(adv.shop!!.stock.map { it.id to it.price }, copy.shop!!.stock.map { it.id to it.price })
    }

    @Test
    fun anOfflineAdventurePlaysFromStartToEnd() {
        repeat(5) { seed ->
            val adv = adventure(seed + 11)
            var guard = 0
            while (!adv.over && guard++ < 60) {
                // Take the first fight/boss choice if there is one, otherwise the first choice.
                val choice = adv.scene.choices.firstOrNull { it.kind == ChoiceKind.BOSS }
                    ?: adv.scene.choices.firstOrNull { it.kind == ChoiceKind.FIGHT || it.kind == ChoiceKind.ELITE }
                    ?: adv.scene.choices.first()
                when (val step = AdventureRules.choose(adv, choice, base)) {
                    is AdventureRules.Step.Battle -> {
                        val s = AdventureRules.startBattle(adv, base, step.kind)!!
                        TestBot.autoPlay(s)
                        AdventureRules.finishBattle(adv, s, step.kind)
                        // Patch the party up so a long walk can finish.
                        if (!adv.over) AdventureRules.camp(adv)
                    }
                    AdventureRules.Step.Shop -> AdventureRules.leaveShop(adv)
                    AdventureRules.Step.Narrate, AdventureRules.Step.Done -> {}
                }
                if (!adv.over) adv.scene = OfflineDirector.scene(adv)
            }
            assertTrue(adv.over, "the adventure ends (won=${adv.won}) within 60 steps")
        }
    }
}
