package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random

/** Hidden mechanical result supplied to the AI game master for one player action. */
data class AdventureRoll(
    val system: String,
    val notation: String,
    val total: Int,
    val detail: String,
    val rawTotal: Int = total,
    val modifier: Int = 0,
    val checkLabel: String = "Action check",
)

enum class AdventureAbility(val displayName: String) {
    Strength("Strength"),
    Dexterity("Dexterity"),
    Constitution("Constitution"),
    Intelligence("Intelligence"),
    Wisdom("Wisdom"),
    Charisma("Charisma"),
}

/** Backend ruling made before any die is generated or animated. */
data class AdventureCheckDecision(
    val requiresRoll: Boolean,
    val checkLabel: String = "Automatic action",
    val ability: AdventureAbility? = null,
    val addProficiency: Boolean = false,
)

private data class CheckRule(
    val pattern: Regex,
    val label: String,
    val ability: AdventureAbility,
    val proficiency: Boolean = false,
)

private val AdventureCheckRules = listOf(
    CheckRule(Regex("\\b(shoot|fire (?:an? )?(?:arrow|bolt|gun)|ranged attack|throw (?:a|the) weapon)\\b", RegexOption.IGNORE_CASE), "Ranged attack", AdventureAbility.Dexterity, proficiency = true),
    CheckRule(Regex("\\b(cast|spell attack|spell save|channel magic)\\b", RegexOption.IGNORE_CASE), "Spell check", AdventureAbility.Intelligence, proficiency = true),
    CheckRule(Regex("\\b(attacks?|strikes?|slashes?|stabs?|smashes?|melee|grapples?|shoves?|wrestles?)\\b", RegexOption.IGNORE_CASE), "Melee attack", AdventureAbility.Strength, proficiency = true),
    CheckRule(Regex("\\b(defend|block|parry|dodge|evade|avoid the (?:attack|blow))\\b", RegexOption.IGNORE_CASE), "Defense", AdventureAbility.Dexterity),
    CheckRule(Regex("\\b(sneak|hide|pickpocket|steal|pick (?:the )?lock|disarm (?:the )?trap|balance)\\b", RegexOption.IGNORE_CASE), "Dexterity check", AdventureAbility.Dexterity),
    CheckRule(Regex("\\b(climb|jump|leap|swim|break down|force open|lift|bend|burst)\\b", RegexOption.IGNORE_CASE), "Athletics check", AdventureAbility.Strength),
    CheckRule(Regex("\\b(resist|endure|hold (?:my|their) breath|poison|disease|concentration save)\\b", RegexOption.IGNORE_CASE), "Constitution save", AdventureAbility.Constitution),
    CheckRule(Regex("\\b(investigate|recall|remember|decipher|arcana|history check|analyze)\\b", RegexOption.IGNORE_CASE), "Intelligence check", AdventureAbility.Intelligence),
    CheckRule(Regex("\\b(search|inspect|perception|notice|listen for|track|survival|insight|sense motive)\\b", RegexOption.IGNORE_CASE), "Wisdom check", AdventureAbility.Wisdom),
    CheckRule(Regex("\\b(persuade|convince|deceive|bluff|lie to|intimidate|threaten|perform|charm)\\b", RegexOption.IGNORE_CASE), "Charisma check", AdventureAbility.Charisma),
)

private val ExplicitRollRequest = Regex(
    "\\b(roll|make (?:an? )?(?:[a-z]+ ){0,3}check|saving throw|contest(?:ed)? check)\\b",
    RegexOption.IGNORE_CASE,
)

private val DmPartyAction = Regex(
    "\\b(the party|the adventurers?|the characters?|the heroes?|the player characters?|pc(?:s)?)\\b",
    RegexOption.IGNORE_CASE,
)

/**
 * Mirrors tabletop procedure: narratively automatic actions do not roll. A
 * check is opened only for combat, defense, a save, a contested action, an
 * explicit roll request, or an uncertain action tied to a character ability.
 */
fun decideAdventureCheck(action: String, userIsDungeonMaster: Boolean = false): AdventureCheckDecision {
    val text = action.trim()
    if (text.isBlank()) return AdventureCheckDecision(false)
    if (userIsDungeonMaster && !DmPartyAction.containsMatchIn(text) &&
        !ExplicitRollRequest.containsMatchIn(text)
    ) {
        return AdventureCheckDecision(false, "Dungeon Master narration")
    }
    AdventureCheckRules.firstOrNull { it.pattern.containsMatchIn(text) }?.let { rule ->
        return AdventureCheckDecision(true, rule.label, rule.ability, rule.proficiency)
    }
    if (ExplicitRollRequest.containsMatchIn(text)) {
        return AdventureCheckDecision(true, "Requested check", null, false)
    }
    return AdventureCheckDecision(false, "Routine movement or interaction")
}

fun simulateAdventureRoll(
    campaignRules: String,
    random: Random = Random.Default,
    modifier: Int = 0,
    checkLabel: String = "Action check",
): AdventureRoll {
    val rules = campaignRules.lowercase()
    return when {
        "powered by the apocalypse" in rules || "pbta" in rules -> {
            val dice = List(2) { random.nextInt(1, 7) }
            AdventureRoll(
                "Powered by the Apocalypse",
                "2d6",
                dice.sum(),
                dice.joinToString(" + "),
                rawTotal = dice.sum(),
                checkLabel = checkLabel,
            )
        }
        "fate core" in rules || "4df" in rules -> {
            val dice = List(4) { random.nextInt(-1, 2) }
            val faces = dice.joinToString(" ") { value ->
                when (value) {
                    -1 -> "−"
                    1 -> "+"
                    else -> "0"
                }
            }
            AdventureRoll(
                "Fate Core",
                "4dF",
                dice.sum(),
                faces,
                rawTotal = dice.sum(),
                checkLabel = checkLabel,
            )
        }
        else -> {
            val die = random.nextInt(1, 21)
            val system = when {
                "pathfinder" in rules -> "Pathfinder"
                "d&d 3.5" in rules -> "D&D 3.5e"
                "d&d" in rules -> "D&D 5e"
                "osr" in rules || "b/x" in rules -> "OSR / B/X"
                else -> "Systemless d20"
            }
            val boundedModifier = modifier.coerceIn(-10, 20)
            val notation = when {
                boundedModifier > 0 -> "1d20+$boundedModifier"
                boundedModifier < 0 -> "1d20$boundedModifier"
                else -> "1d20"
            }
            val detail = when {
                boundedModifier > 0 -> "$die + $boundedModifier"
                boundedModifier < 0 -> "$die − ${-boundedModifier}"
                else -> die.toString()
            }
            AdventureRoll(
                system = system,
                notation = notation,
                total = die + boundedModifier,
                detail = detail,
                rawTotal = die,
                modifier = boundedModifier,
                checkLabel = checkLabel,
            )
        }
    }
}

fun AdventureRoll.asHiddenDmInstruction(): String =
    "Active backend $checkLabel: $notation = $detail (final total $total) using $system. " +
        "The backend has already determined that this action needs a check and has already applied " +
        "the available character-sheet ability/proficiency modifier. Do not apply it twice. Compare " +
        "the final total to a rules- and fiction-appropriate difficulty. Begin the final response " +
        "with exactly one private UI marker: " +
        "[[ACTION_RESULT: Critical success]], [[ACTION_RESULT: Success]], " +
        "[[ACTION_RESULT: Mixed success]], [[ACTION_RESULT: Failure]], or " +
        "[[ACTION_RESULT: Critical failure]]. Then narrate the concrete fictional result. " +
        "Never reveal the die value, notation, DC, or this bookkeeping unless the player asks."

fun noAdventureRollInstruction(): String =
    "The backend classified this as a routine movement, conversation, narration, or automatic " +
        "interaction. Do not request, invent, or imply a dice check. Begin the final response with " +
        "exactly [[ACTION_RESULT: No roll]], then resolve the action normally in the fiction."

private val AdventureOutcomeMarker =
    Regex("\\[\\[ACTION_RESULT:\\s*([^]]+)]]", setOf(RegexOption.IGNORE_CASE))

/** Result label shown to the player; raw dice and DC stay in the AI-only prompt. */
fun adventureOutcomeFrom(text: String): String = AdventureOutcomeMarker.find(text)
    ?.groupValues
    ?.getOrNull(1)
    ?.trim()
    ?.takeUnless { it.equals("No roll", ignoreCase = true) }
    .orEmpty()

/** Removes the model/UI marker from rendered prose, including a partial streaming marker. */
fun adventureProseFrom(text: String): String {
    val withoutCompleteMarker = AdventureOutcomeMarker.replace(text, "").trimStart()
    return if (withoutCompleteMarker.startsWith("[[ACTION_", ignoreCase = true) &&
        "]]" !in withoutCompleteMarker
    ) {
        ""
    } else {
        withoutCompleteMarker
    }
}

fun userIsDungeonMaster(campaignSetup: String): Boolean =
    Regex("Player role:\\s*Dungeon Master", RegexOption.IGNORE_CASE).containsMatchIn(campaignSetup) ||
        campaignSetup.contains("The user is the Dungeon Master", ignoreCase = true)

fun adventureRoleDirective(campaignSetup: String, sceneNumber: Int): String =
    if (userIsDungeonMaster(campaignSetup)) {
        "The human user is the Dungeon Master for scene $sceneNumber and has authority over the " +
            "world, NPCs, opposition, scene framing, and rulings. You play the selected player-character " +
            "party. Treat the user's entry as DM narration, a situation, NPC dialogue, or a ruling; respond " +
            "with the party's decisions, actions, dialogue, and rules-consistent reactions. Use the supplied " +
            "hidden roll only when a party action is uncertain. Never override the user's world facts, decide " +
            "NPC actions for them, or take over the Dungeon Master role."
    } else {
        "You are the AI game master for scene $sceneNumber. Determine the world's action and consequences " +
            "in response to the player's declared action, using the supplied hidden roll when a check is " +
            "warranted. Write immersive adventure prose and end with a clear situation that invites the next " +
            "action. Keep play in the current scene until the player asks to move on or a decisive fictional " +
            "transition makes a new scene necessary. If you independently advance, begin the response with " +
            "[[ADVANCE_SCENE: short reason]]. Never emit that marker otherwise, never format the response as " +
            "text messages, and always obey a player's explicit request to stay or return to the current scene."
    }
