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
        "if it is not, ignore the roll. Do not reveal this bookkeeping unless the player asks."
