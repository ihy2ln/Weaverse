package com.ihy2ln.weaverse.feature.games.adventure

import com.ihy2ln.weaverse.feature.games.battle.engine.BattleState
import com.ihy2ln.weaverse.feature.games.battle.engine.BuiltinContent
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.GameLogic
import com.ihy2ln.weaverse.feature.games.battle.engine.GameSave
import com.ihy2ln.weaverse.feature.games.battle.engine.Rng
import com.ihy2ln.weaverse.feature.games.battle.engine.RunNode
import com.ihy2ln.weaverse.feature.games.battle.engine.RunProgress
import com.ihy2ln.weaverse.feature.games.battle.engine.UnitHp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What a choice does. The words are the AI's; the consequences are the engine's. */
object ChoiceKind {
    const val TALK = "talk"       // keep the story going
    const val EXPLORE = "explore"
    const val FIGHT = "fight"     // a pack of monsters: a Card Battle
    const val ELITE = "elite"     // a tougher single foe with a commander
    const val BOSS = "boss"       // the lair boss; winning ends the adventure
    const val TRADE = "trade"     // the barter screen
    const val REST = "rest"       // camp: heal the party
    const val LOOT = "loot"       // find gold or an item
    val all = setOf(TALK, EXPLORE, FIGHT, ELITE, BOSS, TRADE, REST, LOOT)
}

@Serializable
class Choice(val text: String = "", val kind: String = ChoiceKind.TALK)

@Serializable
class Scene(
    var place: String = "",
    var narration: String = "",
    var choices: MutableList<Choice> = mutableListOf(),
    /** The merchant, when a choice trades. */
    var keeper: String = "",
)

@Serializable
class ShopItem(val kind: String = "item", val id: String = "", var price: Int = 0, var haggled: Boolean = false)

@Serializable
class Shop(val keeper: String = "", val stock: MutableList<ShopItem> = mutableListOf(), var mood: String = "")

/** An adventure in progress: one hero party walking through scenes toward a lair boss. */
@Serializable
class AdvSave(
    var active: Boolean = false,
    var over: Boolean = false,
    var won: Boolean = false,
    var premise: String = "",
    var seed: Int = 1,
    var counter: Int = 0,
    var depth: Int = 0,
    var gold: Int = 40,
    var items: MutableMap<String, Int> = mutableMapOf(),
    /** Cards bought from merchants: part of the deck for every battle of this adventure. */
    var bonusCards: MutableList<String> = mutableListOf(),
    var hp: MutableList<UnitHp> = mutableListOf(),
    var summonerHp: Int = -1,
    var scene: Scene = Scene(),
    var shop: Shop? = null,
    /** One line per past scene, newest last, so the AI keeps the thread. */
    var history: MutableList<String> = mutableListOf(),
    /** The latest thing that happened (battle result, purchase…), shown above the scene and fed to the AI. */
    var outcome: String = "",
    var battlesWon: Int = 0,
    var theme: String = "",
    var region: String = "",
)

class AdvItem(val id: String, val name: String, val text: String, val price: Int)

/** The rules of the adventure layer: items, camps, loot, barter and the bridge to Card Battle. Pure Kotlin. */
object AdventureRules {
    /** Scenes before the lair: the last one always offers the boss. */
    const val LENGTH = 10
    const val START_GOLD = 40

    val items: List<AdvItem> = listOf(
        AdvItem("potion", "Healing Draught", "Heals one fighter 40% of their health.", 30),
        AdvItem("tonic", "Ember Tonic", "Heals the whole party 20% of their health.", 55),
        AdvItem("ration", "Trail Ration", "JD recovers 80 health.", 25),
        AdvItem("charm", "Haggler's Charm", "Keep it: bargaining succeeds more often.", 70),
    )

    fun item(id: String): AdvItem? = items.firstOrNull { it.id == id }

    private fun roll(adv: AdvSave): Rng = Rng(adv.seed * 31 + adv.counter++ * 7919L + 13)

    /** A fresh adventure for the saved party. Call after the cast is registered. */
    fun begin(base: GameSave, premise: String, seed: Int): AdvSave {
        val adv = AdvSave(active = true, premise = premise.trim(), seed = seed, gold = START_GOLD)
        for (id in base.party) { val u = Catalog.unit(id) ?: continue; adv.hp.add(UnitHp(id, u.maxHp)) }
        val r = Rng(seed)
        val themes = mutableListOf<String>()
        for (f in com.ihy2ln.weaverse.feature.games.battle.engine.Bestiary.families) for (t in f.themes) if (t !in themes) themes.add(t)
        adv.theme = if (themes.isEmpty()) "edge" else themes[r.next(themes.size)]
        val bosses = com.ihy2ln.weaverse.feature.games.battle.engine.Bestiary.bosses
        adv.region = if (bosses.isEmpty()) "" else bosses[r.next(bosses.size)].region
        adv.items["potion"] = 1
        adv.scene = OfflineDirector.scene(adv)
        return adv
    }

    fun hpOf(adv: AdvSave, id: String): Int {
        val max = Catalog.unit(id)?.maxHp ?: return 0
        return adv.hp.firstOrNull { it.id == id }?.hp?.coerceIn(0, max) ?: max
    }

    private fun setHp(adv: AdvSave, id: String, hp: Int) {
        val h = adv.hp.firstOrNull { it.id == id }
        if (h == null) adv.hp.add(UnitHp(id, hp)) else h.hp = hp
    }

    // ---- choices -------------------------------------------------------------------------------------------------------

    /** What the screen must do after the player picks a choice. */
    sealed class Step {
        /** Ask the director for the next scene (the story moves on). */
        object Narrate : Step()
        class Battle(val kind: String) : Step()
        object Shop : Step()
        object Done : Step()
    }

    /** Applies the engine-side effect of a choice (camp, loot) or tells the screen what to open. */
    fun choose(adv: AdvSave, choice: Choice, base: GameSave? = null): Step {
        if (adv.over) return Step.Done
        adv.outcome = ""
        return when (choice.kind) {
            ChoiceKind.FIGHT, ChoiceKind.ELITE, ChoiceKind.BOSS -> Step.Battle(choice.kind)
            ChoiceKind.TRADE -> { adv.shop = makeShop(adv, adv.scene.keeper.ifBlank { "a wandering merchant" }, base); Step.Shop }
            ChoiceKind.REST -> { camp(adv); advance(adv, choice); Step.Narrate }
            ChoiceKind.LOOT -> { loot(adv); advance(adv, choice); Step.Narrate }
            else -> { advance(adv, choice); Step.Narrate }
        }
    }

    /** Moves the story one scene on and writes the history line. */
    fun advance(adv: AdvSave, choice: Choice?) {
        adv.depth++
        val line = adv.scene.place.ifBlank { "Scene ${adv.depth}" } + ": " + (choice?.text ?: "continued") + (if (adv.outcome.isNotBlank()) " (" + adv.outcome + ")" else "")
        adv.history.add(line.take(200))
        while (adv.history.size > 24) adv.history.removeAt(0)
    }

    fun camp(adv: AdvSave) {
        for (id in adv.hp.map { it.id }) {
            val max = Catalog.unit(id)?.maxHp ?: continue
            setHp(adv, id, min(max, hpOf(adv, id) + (max * .35f).toInt()))
        }
        if (adv.summonerHp >= 0) adv.summonerHp = min(BuiltinContent.summoner().maxHp, adv.summonerHp + 60)
        adv.outcome = "The party rested and recovered."
    }

    fun loot(adv: AdvSave) {
        val r = roll(adv)
        if (r.next(100) < 70) {
            val gold = 15 + adv.depth * 5 + r.next(15)
            adv.gold += gold
            adv.outcome = "Found $gold gold."
        } else {
            val drop = items[r.next(3)]
            adv.items[drop.id] = (adv.items[drop.id] ?: 0) + 1
            adv.outcome = "Found a ${drop.name}."
        }
    }

    // ---- battles -------------------------------------------------------------------------------------------------------

    /** The fight a battle choice leads to, at a depth that grows with the story. */
    fun encounter(adv: AdvSave, kind: String): RunNode {
        val depth = (1 + adv.depth / 2).coerceIn(1, 5)
        val k = when {
            kind == ChoiceKind.BOSS && adv.depth >= LENGTH - 1 -> "boss"
            kind == ChoiceKind.BOSS -> "elite"
            kind == ChoiceKind.ELITE -> "elite"
            else -> "normal"
        }
        val node = RunNode(k, adv.theme, if (k == "boss") adv.region else "", when (k) { "boss" -> "The Lair"; "elite" -> "Elite foe"; else -> "Ambush" }, if (k == "boss") 4 else depth, roll(adv).next(1, Int.MAX_VALUE))
        return node
    }

    /** A Card Battle for the party at its current health, with bought cards in the deck. */
    fun startBattle(adv: AdvSave, base: GameSave, kind: String): BattleState? {
        val tmp = GameSave(
            collection = base.collection.toMutableList(), deck = base.deck.toMutableList(), party = base.party.toMutableList(),
            levels = base.levels.toMutableList(), known = base.known.toMutableList(),
        )
        tmp.run = RunProgress(active = true, seed = adv.seed, summonerHp = adv.summonerHp)
        tmp.run.nodes.add(encounter(adv, kind))
        tmp.run.hp = adv.hp.map { UnitHp(it.id, it.hp) }.toMutableList()
        for (id in base.party) if (tmp.run.hp.none { it.id == id }) Catalog.unit(id)?.let { tmp.run.hp.add(UnitHp(id, it.maxHp)) }
        tmp.run.runCards = adv.bonusCards.filter { it !in tmp.deck }.toMutableList()
        return GameLogic.startBattle(tmp)
    }

    /** Takes health back from a finished fight and pays out. Returns the line to show. */
    fun finishBattle(adv: AdvSave, state: BattleState, kind: String): String {
        for (u in state.allies + state.reserves) setHp(adv, u.id, u.hp)
        adv.summonerHp = state.summoner?.hp ?: adv.summonerHp
        if (state.withdrawn) {
            // Fleeing costs a little gold and the fight counts as a scene passed.
            val lost = min(adv.gold, 10 + adv.depth * 3)
            adv.gold -= lost
            adv.outcome = "The party fled the fight and dropped $lost gold."
            advance(adv, Choice("Fled the fight", kind))
            return adv.outcome
        }
        if (!state.victory) {
            adv.over = true; adv.active = false
            adv.outcome = "The party has fallen."
            return adv.outcome
        }
        adv.battlesWon++
        val r = roll(adv)
        val gold = 20 + adv.depth * 6 + r.next(20) + (if (kind == ChoiceKind.ELITE) 25 else 0) + (if (kind == ChoiceKind.BOSS) 60 else 0)
        adv.gold += gold
        var text = "Victory! The party earned $gold gold."
        if (r.next(100) < 35) {
            val drop = items[r.next(3)]
            adv.items[drop.id] = (adv.items[drop.id] ?: 0) + 1
            text += " They also found a ${drop.name}."
        }
        adv.outcome = text
        if (kind == ChoiceKind.BOSS && adv.depth >= LENGTH - 1) {
            adv.over = true; adv.won = true; adv.active = false
            adv.outcome = "The lair boss is down. $text"
        }
        advance(adv, Choice("Won the battle", kind))
        return adv.outcome
    }

    // ---- items ---------------------------------------------------------------------------------------------------------

    fun count(adv: AdvSave, id: String): Int = adv.items[id] ?: 0

    /** Uses an item. [target] is a fighter id for a draught. Returns false when it can't be used. */
    fun use(adv: AdvSave, id: String, target: String?): Boolean {
        if (count(adv, id) <= 0) return false
        when (id) {
            "potion" -> {
                val t = target ?: return false
                val max = Catalog.unit(t)?.maxHp ?: return false
                val hp = hpOf(adv, t)
                if (hp <= 0 || hp >= max) return false
                setHp(adv, t, min(max, hp + (max * .4f).toInt()))
            }
            "tonic" -> {
                var any = false
                for (hpRow in adv.hp.toList()) {
                    val max = Catalog.unit(hpRow.id)?.maxHp ?: continue
                    if (hpRow.hp in 1 until max) { setHp(adv, hpRow.id, min(max, hpRow.hp + (max * .2f).toInt())); any = true }
                }
                if (!any) return false
            }
            "ration" -> {
                val jd = BuiltinContent.summoner().maxHp
                if (adv.summonerHp < 0) adv.summonerHp = jd
                if (adv.summonerHp >= jd) return false
                adv.summonerHp = min(jd, adv.summonerHp + 80)
            }
            else -> return false
        }
        adv.items[id] = count(adv, id) - 1
        if (adv.items[id] == 0) adv.items.remove(id)
        return true
    }

    // ---- barter ---------------------------------------------------------------------------------------------------------

    fun cardPrice(id: String): Int = 35 + 30 * (Catalog.card(id)?.ep ?: 1)

    /** A merchant's stock: the basic goods plus up to three cards the party doesn't own yet. */
    fun makeShop(adv: AdvSave, keeper: String, base: GameSave? = null): Shop {
        val r = roll(adv)
        val shop = Shop(keeper = keeper, mood = "Open for business.")
        for (ware in items) if (ware.id != "charm" || count(adv, "charm") == 0) shop.stock.add(ShopItem("item", ware.id, (ware.price * (.9f + r.next(21) / 100f)).roundToInt()))
        val owned = (base?.deck.orEmpty() + adv.bonusCards).toSet()
        val pool = mutableListOf<String>()
        for (pid in (base?.party ?: adv.hp.map { it.id })) Catalog.get(pid)?.cards?.forEach { if (it.id !in owned) pool.add(it.id) }
        var n = 0
        while (pool.isNotEmpty() && n < 3) { val c = pool.removeAt(r.next(pool.size)); shop.stock.add(ShopItem("card", c, (cardPrice(c) * (.9f + r.next(21) / 100f)).roundToInt())); n++ }
        return shop
    }

    private fun bargainChance(adv: AdvSave): Float = .5f + (if (count(adv, "charm") > 0) .15f else 0f)

    /** One try per ware: 25% off on a win, 10% more on a miss. Returns the line to show. */
    fun haggle(adv: AdvSave, index: Int): String {
        val shop = adv.shop ?: return ""
        val s = shop.stock.getOrNull(index) ?: return ""
        if (s.haggled) return "${shop.keeper} won't bargain over that again."
        s.haggled = true
        return if (roll(adv).nextDouble() < bargainChance(adv)) {
            s.price = max(1, (s.price * .75f).roundToInt())
            shop.mood = "${shop.keeper} grumbles and drops the price."
            shop.mood
        } else {
            s.price = (s.price * 1.10f).roundToInt()
            shop.mood = "${shop.keeper} scowls: the price just went up."
            shop.mood
        }
    }

    fun buy(adv: AdvSave, index: Int): Boolean {
        val shop = adv.shop ?: return false
        val s = shop.stock.getOrNull(index) ?: return false
        if (adv.gold < s.price) { shop.mood = "Not enough gold."; return false }
        adv.gold -= s.price
        if (s.kind == "item") adv.items[s.id] = count(adv, s.id) + 1 else adv.bonusCards.add(s.id)
        shop.stock.removeAt(index)
        shop.mood = "Sold!"
        return true
    }

    fun sellPrice(id: String): Int = (item(id)?.price ?: 0) / 2

    fun sell(adv: AdvSave, id: String): Boolean {
        if (count(adv, id) <= 0) return false
        val price = sellPrice(id)
        if (price <= 0) return false
        adv.items[id] = count(adv, id) - 1
        if (adv.items[id] == 0) adv.items.remove(id)
        adv.gold += price
        adv.shop?.mood = "A fair price."
        return true
    }

    /** Leaving the shop passes a scene. */
    fun leaveShop(adv: AdvSave) {
        adv.shop = null
        adv.outcome = "Traded and moved on."
        advance(adv, Choice("Traded with ${adv.scene.keeper.ifBlank { "a merchant" }}", ChoiceKind.TRADE))
    }

    // ---- scenes (AI answer -> Scene) -----------------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads the AI's JSON scene; null when it isn't usable (the caller falls back to the offline director). */
    fun parseScene(answer: String, adv: AdvSave): Scene? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
        fun str(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val narration = str(root, "narration").take(1200)
        if (narration.isBlank()) return null
        val choices = ((root["choices"] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }.mapNotNull { c ->
            val text = str(c, "text").take(140)
            if (text.isBlank()) null else Choice(text, str(c, "kind").lowercase().let { if (it in ChoiceKind.all) it else ChoiceKind.TALK })
        }.take(4)
        return normalize(Scene(str(root, "place").take(60), narration, choices.toMutableList(), str(root, "keeper").take(60)), adv)
    }

    /** Keeps the story on rails: enough choices, no boss before the lair, and the boss at the end. */
    fun normalize(scene: Scene, adv: AdvSave): Scene {
        val last = adv.depth >= LENGTH - 1
        scene.choices = scene.choices.map { c ->
            if (c.kind == ChoiceKind.BOSS && !last) Choice(c.text, ChoiceKind.ELITE) else c
        }.toMutableList()
        if (last) {
            scene.choices.removeAll { it.kind == ChoiceKind.BOSS }
            scene.choices.add(0, Choice("Face the lair boss", ChoiceKind.BOSS))
        }
        if (scene.choices.size < 2) {
            if (scene.choices.none { it.kind == ChoiceKind.FIGHT || it.kind == ChoiceKind.ELITE || it.kind == ChoiceKind.BOSS }) scene.choices.add(Choice("Press on and meet what waits ahead", ChoiceKind.FIGHT))
            if (scene.choices.size < 2) scene.choices.add(Choice("Take a moment to look around", ChoiceKind.EXPLORE))
        }
        if (scene.choices.none { it.kind == ChoiceKind.REST } && adv.depth % 3 == 2) scene.choices.add(Choice("Make camp and rest", ChoiceKind.REST))
        if (scene.choices.none { it.kind == ChoiceKind.TRADE } && adv.depth % 3 == 1) {
            scene.keeper = scene.keeper.ifBlank { "a wandering merchant" }
            scene.choices.add(Choice("Trade with ${scene.keeper}", ChoiceKind.TRADE))
        }
        if (scene.keeper.isBlank()) scene.keeper = "a wandering merchant"
        if (scene.place.isBlank()) scene.place = "The Silverwood"
        return scene
    }

    /** The prompt that asks the AI for the next scene. */
    fun scenePrompt(adv: AdvSave, party: List<String>, action: String?): String = buildString {
        appendLine("You are the narrator of a short fantasy text adventure with card battles. Write ONE scene as JSON only.")
        appendLine("Premise: ${adv.premise.ifBlank { "A party led by JD, the Summoner, crosses the haunted Silverwood toward a monster lair." }}")
        appendLine("Party: ${party.joinToString(", ")}. Scene ${adv.depth + 1} of ${LENGTH}. Gold: ${adv.gold}.")
        if (adv.history.isNotEmpty()) appendLine("Story so far: " + adv.history.takeLast(6).joinToString(" | "))
        if (adv.outcome.isNotBlank()) appendLine("Just happened: ${adv.outcome}")
        if (!action.isNullOrBlank()) appendLine("The player chose: $action")
        if (adv.depth >= LENGTH - 1) appendLine("This is the final approach: end the scene at the lair entrance.")
        appendLine("Reply with exactly this JSON and nothing else:")
        appendLine("""{"place":"short place name","narration":"2-4 vivid sentences","keeper":"a merchant's name if one is here, else empty","choices":[{"text":"what the party does","kind":"talk|explore|fight|elite|trade|rest|loot"}]}""")
        appendLine("Give 3 choices. Use kind fight/elite for battles (never boss), trade only when a merchant is present, rest for a camp, loot for treasure. The engine handles all numbers.")
    }
}

/** A built-in storyteller so the adventure plays with no AI model: deterministic scenes from a small table. */
object OfflineDirector {
    private val places = listOf(
        "Forest Edge" to "Silver bark creaks around you. Something that is not the wind moves between the trunks.",
        "The Mossy Ford" to "A slow brown stream cuts the path. Footprints, too large for any deer, lead to the far bank.",
        "A Ruined Chapel" to "Roots have split the nave. Candle stubs still sit on the altar, and one is warm.",
        "The Charcoal Camp" to "A burners' camp lies abandoned mid-meal. The pot is still hot, and nobody is in sight.",
        "Thornfall Gully" to "Brambles arch overhead into a tunnel. Crystal shards glint among the thorns like eyes.",
        "The Hollow Oak" to "A tree wide as a house stands open on one side. Lantern light flickers inside.",
        "Whisper Marsh" to "Mist hangs low and voices carry in it, saying names you almost recognise.",
        "The Old Toll Road" to "A broken toll gate leans across the road. Fresh claw marks run down its iron.",
        "Lantern Hollow" to "A ring of standing stones holds a pale glow. The air tastes of struck metal.",
        "The Lair Gate" to "The trees give way to a cliff and a cave mouth ringed with crystal. Something vast breathes inside.",
    )
    private val keepers = listOf("Old Wren the tinker", "Marl the pedlar", "Sister Odile", "Brindle the cartwright", "Tam Quickhand")
    private val fightLines = listOf("Draw steel and meet them", "Ambush whatever is out there", "Hold the path and fight")
    private val talkLines = listOf("Follow the tracks quietly", "Search the area for clues", "Call out and see who answers")

    fun scene(adv: AdvSave): Scene {
        val r = Rng(adv.seed * 17 + adv.depth * 101)
        val last = adv.depth >= AdventureRules.LENGTH - 1
        val (place, text) = if (last) places.last() else places[adv.depth % (places.size - 1)]
        val keeper = keepers[r.next(keepers.size)]
        val sc = Scene(place, text, mutableListOf(), keeper)
        sc.choices.add(Choice(fightLines[r.next(fightLines.size)], if (adv.depth % 4 == 3) ChoiceKind.ELITE else ChoiceKind.FIGHT))
        sc.choices.add(Choice(talkLines[r.next(talkLines.size)], if (adv.depth % 2 == 0) ChoiceKind.EXPLORE else ChoiceKind.LOOT))
        sc.choices.add(Choice("Skirt the danger and push on", ChoiceKind.TALK))
        return AdventureRules.normalize(sc, adv)
    }
}
