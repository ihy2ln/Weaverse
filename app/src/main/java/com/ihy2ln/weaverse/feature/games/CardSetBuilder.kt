package com.ihy2ln.weaverse.feature.games

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns a character from a novel into a card set for the Adams Haven Card Game. The AI decides what the character
 * IS (role, element, a title, six moves and two ultimates in their own words, each one of a fixed set of effects);
 * the numbers come from here, on the same curve as the built-in cast, so every novel character is fair to play.
 * Editing a card in Weaverse changes its name, words, effect or EP, and the numbers are worked out again.
 */
object CardSetBuilder {
    val roles = listOf("Tank", "Dps", "Ranger", "Support")
    val elements = listOf("Fire", "Water", "Wind", "Earth", "Lightning", "Light", "Dark", "Neutral")

    /** The moves a deck card can be, with what they mean, for the prompt and the editor. */
    val effects = linkedMapOf(
        "strike" to "a hit on one enemy",
        "sweep" to "a hit on every enemy",
        "hex" to "a hit that also weakens the target (pick a status)",
        "guard" to "raises their own defence",
        "ward" to "raises the whole party's defence",
        "rally" to "raises the whole party's attack",
        "mend" to "heals one ally",
        "aid" to "gives an ally 1 EP and draws a card",
        "insight" to "draws two cards",
        "barrier" to "shields the whole party",
    )
    val ultimateEffects = linkedMapOf(
        "nova" to "a huge hit on every enemy that also breaks their guard",
        "execute" to "an enormous hit on one enemy",
        "sanctuary" to "heals the party and raises its defence",
        "feast" to "heals the party and raises its attack",
        "renewal" to "a big heal plus regeneration for the party",
    )
    val hexStatuses = listOf("DefenseDown", "AttackDown", "Slow", "Poison", "Burn", "Stun")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun prompt(name: String, description: String, novel: String): String = buildString {
        appendLine("Design an Adams Haven card game kit for this character from the novel \"$novel\".")
        appendLine()
        appendLine("CHARACTER: $name")
        appendLine(description.take(2400).ifBlank { "(no description in the codex yet)" })
        appendLine()
        appendLine("Answer with JSON only, no commentary, in exactly this shape:")
        appendLine("""{"role":"Tank|Dps|Ranger|Support","element":"Fire|Water|Wind|Earth|Lightning|Light|Dark|Neutral","tier":3,""")
        appendLine(""" "magic":false,"title":"a 2-4 word epithet","bio":"one sentence about them",""")
        appendLine(""" "cards":[{"name":"","effect":"","ep":1,"status":"","text":""}],""")
        appendLine(""" "ultimates":[{"name":"","effect":"","text":""}]}""")
        appendLine()
        appendLine("Rules:")
        appendLine("- Exactly 6 cards and 2 ultimates. Names are short (1-3 words) and sound like this character's own moves, gear or habits.")
        appendLine("- Card effect is one of: " + effects.entries.joinToString("; ") { "${it.key} (${it.value})" } + ".")
        appendLine("- Ultimate effect is one of: " + ultimateEffects.entries.joinToString("; ") { "${it.key} (${it.value})" } + ".")
        appendLine("- ep is the card's weight: 1 light, 2 solid, 3 their heaviest move. Use a mix: at least two ep 1 cards, at most two ep 3.")
        appendLine("- At least two cards deal damage (strike, sweep or hex). Supports lean on mend/ward/aid; tanks on guard/strike.")
        appendLine("- status is only for hex: one of ${hexStatuses.joinToString(", ")}. Leave it empty otherwise.")
        appendLine("- magic is true if they fight with spells, light, magic or technology rather than weapons or fists.")
        appendLine("- tier 3 is an ordinary fighter, 5 a veteran, 6 a legend. Most characters are 3 or 4.")
        appendLine("- text is a flavour line of at most 12 words, in the novel's voice. No game numbers in names or text.")
    }

    /** The AI's answer (JSON, possibly wrapped in prose or a code fence) into a full, balanced set. */
    fun fromAiAnswer(answer: String, id: String, name: String, novel: String, codexId: String, portrait: String): CardGameCharacter {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        require(start >= 0 && end > start) { "The AI did not answer with a card set." }
        val root = json.parseToJsonElement(answer.substring(start, end + 1)).jsonObject
        fun str(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val role = roles.firstOrNull { it.equals(str(root, "role"), true) } ?: "Dps"
        val element = elements.firstOrNull { it.equals(str(root, "element"), true) } ?: "Neutral"
        val tier = (root["tier"]?.jsonPrimitive?.intOrNull ?: 4).coerceIn(2, 6)
        val magic = root["magic"]?.jsonPrimitive?.booleanOrNull ?: (role == "Ranger" || role == "Support")
        val moves = (root["cards"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { c ->
            Move(str(c, "name"), str(c, "effect").lowercase(), c["ep"]?.jsonPrimitive?.intOrNull ?: 1, str(c, "status"), str(c, "text"))
        }
        val ults = (root["ultimates"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { c ->
            Move(str(c, "name"), str(c, "effect").lowercase(), 0, "", str(c, "text"))
        }
        return build(id, name, str(root, "title").take(40), role, element, tier, magic, str(root, "bio").take(200), novel, codexId,
            portrait, moves, ults)
    }

    data class Move(val name: String, val effect: String, val ep: Int, val status: String, val text: String)

    /** A full set from moves (the AI's or the editor's): fills gaps, fixes the curve and works out every number. */
    fun build(
        id: String, name: String, title: String, role: String, element: String, tier: Int, magic: Boolean, bio: String,
        novel: String, codexId: String, portrait: String, moves: List<Move>, ultimates: List<Move>,
    ): CardGameCharacter {
        val kit = moves.filter { it.name.isNotBlank() }.take(6).toMutableList()
        val fallback = defaultMoves(role)
        var f = 0
        while (kit.size < 6) kit += fallback[f++ % fallback.size]
        // At least two damage cards, or the character cannot win a fight alone.
        val damaging = setOf("strike", "sweep", "hex")
        var dmg = kit.count { it.effect in damaging }
        for (i in kit.indices.reversed()) {
            if (dmg >= 2) break
            if (kit[i].effect !in damaging) { kit[i] = kit[i].copy(effect = if (dmg == 0) "strike" else "hex", status = "DefenseDown"); dmg++ }
        }
        val ults = ultimates.filter { it.name.isNotBlank() }.take(2).toMutableList()
        val ultFallback = if (role == "Support") listOf(Move("Last Light", "renewal", 0, "", ""), Move("Shared Resolve", "feast", 0, "", ""))
            else listOf(Move("Final Stand", "nova", 0, "", ""), Move("Decisive Blow", "execute", 0, "", ""))
        while (ults.size < 2) ults += ultFallback[ults.size]

        val seen = HashSet<String>()
        fun unique(c: CardGameCard): CardGameCard {
            var cid = c.id; var n = 2
            while (!seen.add(cid)) cid = c.id + "_" + n++
            return c.copy(id = cid)
        }
        val cards = kit.mapIndexed { i, m -> unique(card(id, i, m, magic, opener = i == 0)) }
        val ultCards = ults.mapIndexed { i, m -> unique(ultimate(id, i, m, magic)) }
        val stats = statsFor(role)
        return CardGameCharacter(
            id = id, name = name, title = title, role = role, element = element, tier = tier.coerceIn(2, 6),
            hp = stats[0].toInt(), attack = stats[1], magic = stats[2], defense = stats[3], resistance = stats[4], speed = stats[5],
            crit = stats[6], critDamage = stats[7], portrait = portrait, cards = cards, ultimates = ultCards,
            novel = novel, bio = bio, codexId = codexId, magicUser = magic, updatedAt = System.currentTimeMillis(),
        )
    }

    /** One deck card's numbers, from its effect and EP, on the built-in cast's curve. */
    fun card(owner: String, index: Int, m: Move, magic: Boolean, opener: Boolean): CardGameCard {
        val effect = m.effect.takeIf { it in effects } ?: "strike"
        val ep = m.ep.coerceIn(1, 3)
        val base = CardGameCard(id = slug(owner, m.name, index), name = m.name.take(28), owner = owner, ep = ep, ap = 1,
            text = m.text.take(90), effect = effect, initiation = opener)
        return when (effect) {
            "strike" -> base.copy(power = floatArrayOf(1.35f, 1.9f, 2.6f)[ep - 1], magic = magic, exhaust = ep == 3)
            "sweep" -> base.copy(target = "AllEnemies", power = floatArrayOf(.8f, 1.3f, 1.65f)[ep - 1], magic = magic)
            "hex" -> {
                val status = hexStatuses.firstOrNull { it.equals(m.status, true) } ?: "DefenseDown"
                val power = floatArrayOf(.9f, 1.15f, 1.8f)[ep - 1]
                when (status) {
                    "Poison", "Burn" -> base.copy(power = power, magic = magic, status = status, magnitude = 4f + 2f * ep, duration = 2)
                    "Stun" -> base.copy(ep = 3, power = 1.2f, magic = magic, status = "Stun", magnitude = 1f, duration = 1, exhaust = true)
                    "Slow" -> base.copy(power = power, magic = magic, status = status, magnitude = .25f, duration = 2)
                    else -> base.copy(power = power, magic = magic, status = status, magnitude = .15f + .05f * ep, duration = 2)
                }
            }
            "guard" -> if (ep == 1) base.copy(ap = 0, target = "Self", status = "DefenseUp", magnitude = .35f, duration = 2, retain = true)
                else base.copy(ap = 0, ep = 2, target = "Self", status = "DefenseUp", magnitude = .4f, duration = 2, heal = 22f)
            "ward" -> base.copy(ep = ep.coerceAtLeast(2), target = "AllAllies", status = "DefenseUp", magnitude = if (ep >= 3) .45f else .25f, duration = 2, exhaust = ep >= 3)
            "rally" -> base.copy(ep = ep.coerceAtLeast(2), target = "AllAllies", status = "AttackUp", magnitude = if (ep >= 3) .35f else .25f, duration = 2)
            "mend" -> if (ep == 1) base.copy(target = "Ally", heal = 24f) else base.copy(ap = 0, target = "Ally", heal = if (ep >= 3) 34f else 22f, epGain = if (ep >= 3) 0 else 1)
            "aid" -> base.copy(ep = 1, ap = 0, target = "Ally", draw = 1, epGain = 1, retain = true)
            "insight" -> base.copy(ep = 1, ap = 0, target = "None", draw = 2)
            "barrier" -> base.copy(ep = 2, target = "AllAllies", status = "Shield", magnitude = 18f, duration = 1)
            else -> base
        }.let { if (opener) it.copy(exhaust = false) else it }
    }

    fun ultimate(owner: String, index: Int, m: Move, magic: Boolean): CardGameCard {
        val effect = m.effect.takeIf { it in ultimateEffects } ?: if (index == 0) "nova" else "execute"
        val base = CardGameCard(id = slug(owner, m.name, 10 + index), name = m.name.take(28), owner = owner, kind = "Ultimate",
            ep = 0, ap = 0, text = m.text.take(90), effect = effect)
        return when (effect) {
            "nova" -> base.copy(target = "AllEnemies", power = 3f, magic = magic, status = "DefenseDown", magnitude = .3f, duration = 2)
            "execute" -> base.copy(target = "Enemy", power = 4.5f, magic = magic, status = "DefenseDown", magnitude = .35f, duration = 2)
            "sanctuary" -> base.copy(target = "AllAllies", status = "DefenseUp", magnitude = .45f, duration = 2, heal = 32f)
            "feast" -> base.copy(target = "AllAllies", status = "AttackUp", magnitude = .5f, duration = 3, heal = 25f)
            "renewal" -> base.copy(target = "AllAllies", status = "Regen", magnitude = 10f, duration = 3, heal = 50f)
            else -> base
        }
    }

    /** hp, attack, magic, defence, resistance, speed, crit, crit damage by role (the built-in cast's averages). */
    fun statsFor(role: String): FloatArray = when (role) {
        "Tank" -> floatArrayOf(170f, 22f, 7f, 16f, 9f, 12f, .10f, 1.65f)
        "Ranger" -> floatArrayOf(112f, 9f, 23f, 8f, 14f, 12f, .09f, 1.5f)
        "Support" -> floatArrayOf(136f, 14f, 18f, 12f, 14f, 10f, .10f, 1.55f)
        else -> floatArrayOf(156f, 24f, 12f, 12f, 10f, 13f, .17f, 1.7f)
    }

    private fun defaultMoves(role: String): List<Move> = when (role) {
        "Tank" -> listOf(Move("Shoulder Check", "strike", 1, "", ""), Move("Hold the Line", "guard", 1, "", ""),
            Move("Wide Swing", "sweep", 2, "", ""), Move("Crushing Blow", "strike", 3, "", ""), Move("Stand Firm", "ward", 2, "", ""),
            Move("Rattle", "hex", 2, "AttackDown", ""))
        "Support" -> listOf(Move("Quick Jab", "strike", 1, "", ""), Move("Patch Up", "mend", 1, "", ""),
            Move("Second Wind", "aid", 1, "", ""), Move("Steady Now", "ward", 2, "", ""), Move("Cheer On", "rally", 2, "", ""),
            Move("Sting", "hex", 2, "DefenseDown", ""))
        "Ranger" -> listOf(Move("Snap Shot", "strike", 1, "", ""), Move("Scout Ahead", "insight", 1, "", ""),
            Move("Volley", "sweep", 2, "", ""), Move("Pin Down", "hex", 2, "Slow", ""), Move("Aimed Shot", "strike", 3, "", ""),
            Move("Cover Fire", "sweep", 1, "", ""))
        else -> listOf(Move("Quick Cut", "strike", 1, "", ""), Move("Flurry", "sweep", 2, "", ""),
            Move("Opening", "hex", 1, "DefenseDown", ""), Move("Finisher", "strike", 3, "", ""), Move("Momentum", "rally", 2, "", ""),
            Move("Feint", "strike", 2, "", ""))
    }

    fun slug(owner: String, name: String, index: Int): String {
        val base = name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_')
            .replace(Regex("_+"), "_").take(24).ifBlank { "move$index" }
        return "${owner}_${base}"
    }

    fun characterId(name: String, codexId: String): String {
        val base = name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_')
            .replace(Regex("_+"), "_").take(20).ifBlank { "hero" }
        return "nv_" + base + "_" + codexId.filter { it.isLetterOrDigit() }.takeLast(4).lowercase()
    }

    /** Re-derives a card after an edit (name, words, effect, EP or status), keeping its id and position. */
    fun edit(owner: CardGameCharacter, old: CardGameCard, name: String, text: String, effect: String, ep: Int, status: String): CardGameCharacter {
        val ultIndex = owner.ultimates.indexOfFirst { it.id == old.id }
        if (ultIndex >= 0) {
            val updated = ultimate(owner.id, ultIndex, Move(name, effect, 0, "", text), owner.magicUser).copy(id = old.id, art = old.art)
            return owner.copy(ultimates = owner.ultimates.map { if (it.id == old.id) updated else it }, updatedAt = System.currentTimeMillis())
        }
        val index = owner.cards.indexOfFirst { it.id == old.id }
        if (index < 0) return owner
        val updated = card(owner.id, index, Move(name, effect, ep, status, text), owner.magicUser, opener = index == 0)
            .copy(id = old.id, art = old.art)
        return owner.copy(cards = owner.cards.map { if (it.id == old.id) updated else it }, updatedAt = System.currentTimeMillis())
    }

    /** Rebuilds the numbers after the role, element or magic changed. */
    fun restyle(c: CardGameCharacter, role: String, element: String, magic: Boolean): CardGameCharacter {
        val moves = c.cards.map { Move(it.name, it.effect, it.ep, it.status, it.text) }
        val ults = c.ultimates.map { Move(it.name, it.effect, 0, "", it.text) }
        val fresh = build(c.id, c.name, c.title, role, element, c.tier, magic, c.bio, c.novel, c.codexId, c.portrait, moves, ults)
        // Card ids stay put so the game's collection and deck keep pointing at the same cards.
        return fresh.copy(
            inGame = c.inGame,
            cards = fresh.cards.mapIndexed { i, card -> card.copy(id = c.cards.getOrNull(i)?.id ?: card.id) },
            ultimates = fresh.ultimates.mapIndexed { i, card -> card.copy(id = c.ultimates.getOrNull(i)?.id ?: card.id) },
        )
    }
}
