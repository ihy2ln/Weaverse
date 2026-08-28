package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random

/** Hidden mechanical result supplied to the AI game master for one player action. */
data class AdventureRoll(
    val system: String,
    val notation: String,
    val total: Int,
    val detail: String,
)

fun simulateAdventureRoll(
    campaignRules: String,
    random: Random = Random.Default,
): AdventureRoll {
    val rules = campaignRules.lowercase()
    return when {
        "powered by the apocalypse" in rules || "pbta" in rules -> {
            val dice = List(2) { random.nextInt(1, 7) }
            AdventureRoll("Powered by the Apocalypse", "2d6", dice.sum(), dice.joinToString(" + "))
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
            AdventureRoll("Fate Core", "4dF", dice.sum(), faces)
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
            AdventureRoll(system, "1d20", die, die.toString())
        }
    }
}

fun AdventureRoll.asHiddenDmInstruction(): String =
    "Background resolution roll: $notation = $detail (total $total) using $system. " +
        "First decide whether the declared action is uncertain enough to require a roll. " +
        "If it is, apply the campaign's appropriate modifiers and difficulty to this result; " +
        "if it is not, ignore the roll. Begin the final response with exactly one private UI marker: " +
        "[[ACTION_RESULT: Critical success]], [[ACTION_RESULT: Success]], " +
        "[[ACTION_RESULT: Mixed success]], [[ACTION_RESULT: Failure]], or " +
        "[[ACTION_RESULT: Critical failure]] when a roll was required; otherwise use " +
        "[[ACTION_RESULT: No roll]]. Then narrate the concrete fictional result. " +
        "Never reveal the die value, notation, DC, or this bookkeeping unless the player asks."

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
