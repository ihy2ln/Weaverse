package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random

enum class AdventureStartupPhase(val storageName: String) {
    None(""),
    Character("character"),
    Choose("choose"),
    Questions("questions"),
    Complete("complete"),
}

enum class AdventureStartupChoice {
    Classic,
    Interview,
    Random,
}

private val StartupMarker = Regex(
    "\\[\\[ADVENTURE_STARTUP:\\s*(character|choose|questions|complete)]]",
    RegexOption.IGNORE_CASE,
)

private val ClassicOpenings = listOf(
    "the party meets in a crowded tavern when a desperate stranger arrives with a dangerous offer",
    "the party wakes aboard a ship during a violent storm as something strikes the hull below the waterline",
    "the characters are transported from another world and awaken beside a ruined summoning circle",
    "a guarded caravan reaches a blocked mountain pass just as its guide disappears",
    "the party regains consciousness in a dungeon cell while an alarm echoes beyond the door",
)

private val RandomOpenings = listOf(
    "a royal funeral is interrupted when the supposedly dead ruler sits up and names one party member",
    "the party falls from a clear sky toward a floating city whose defenses mistake them for invaders",
    "everyone in town has forgotten the previous night except the party, and the sun has failed to rise",
    "the characters inherit a locked inn that only appears at crossroads where disasters are about to happen",
    "a wounded dragon crashes into the marketplace carrying a sealed message addressed to the party",
    "the party wakes inside a moving colossal creature with a map tattooed across their shared memories",
)

fun adventureStartupPrompt(userIsDungeonMaster: Boolean, needsCharacter: Boolean = false): String {
    val perspective = if (userIsDungeonMaster) {
        "I’ll help frame the opening before you take over as Dungeon Master."
    } else {
        "I’m your AI Dungeon Master. I’ll frame the first situation before asking what your party does."
    }
    if (needsCharacter) {
        return withAdventureStartupMarker(
            buildString {
                appendLine("Create your first adventurer")
                appendLine("No main character is selected, so I’ll help you make one before the adventure begins.")
                appendLine()
                appendLine("Answer as much or as little as you want:")
                appendLine("1 · Name, pronouns, species/ancestry, class, and background")
                appendLine("2 · Character concept, personality, appearance, and main motivation")
                appendLine("3 · Choose Standard Array (15, 14, 13, 12, 10, 8), roll-style stats, or give your own six scores")
                appendLine("4 · Starting equipment, notable skill, spell, or signature weapon")
                append("You can also say “surprise me.” I’ll build a complete editable roster sheet and visual portrait brief, then we’ll choose the opening.")
            },
            AdventureStartupPhase.Character,
        )
    }
    return withAdventureStartupMarker(
        buildString {
            appendLine("Adventure setup")
            appendLine(perspective)
            appendLine()
            appendLine("Choose how we begin:")
            appendLine("1 · Classic D&D opening — tavern meeting, shipwreck, isekai arrival, caravan trouble, or dungeon escape.")
            appendLine("2 · Build it together — I’ll ask a few short questions about where, when, who, what is happening, and the main goal.")
            appendLine("3 · Random start — I’ll surprise you and immediately frame the party’s first problem and objective.")
            append("Reply with 1, 2, or 3. This is campaign setup, so no action roll is needed.")
        },
        AdventureStartupPhase.Choose,
    )
}

fun adventureStartupPhase(text: String): AdventureStartupPhase = when (
    StartupMarker.find(text)?.groupValues?.getOrNull(1)?.lowercase()
) {
    "character" -> AdventureStartupPhase.Character
    "choose" -> AdventureStartupPhase.Choose
    "questions" -> AdventureStartupPhase.Questions
    "complete" -> AdventureStartupPhase.Complete
    else -> AdventureStartupPhase.None
}

fun adventureStartupProseFrom(text: String): String = StartupMarker.replace(text, "").trimStart()

fun withAdventureStartupMarker(text: String, phase: AdventureStartupPhase): String =
    if (phase == AdventureStartupPhase.None) text.trim()
    else "[[ADVENTURE_STARTUP:${phase.storageName}]]\n${text.trim()}"

fun adventureStartupChoice(input: String): AdventureStartupChoice {
    val normalized = input.trim().lowercase()
    return when {
        normalized == "2" || "question" in normalized || "together" in normalized || "build" in normalized ->
            AdventureStartupChoice.Interview
        normalized == "3" || "random" in normalized || "surprise" in normalized ->
            AdventureStartupChoice.Random
        else -> AdventureStartupChoice.Classic
    }
}

fun nextAdventureStartupPhase(
    current: AdventureStartupPhase,
    input: String,
): AdventureStartupPhase = when (current) {
    AdventureStartupPhase.Character -> AdventureStartupPhase.Choose
    AdventureStartupPhase.Choose -> if (adventureStartupChoice(input) == AdventureStartupChoice.Interview) {
        AdventureStartupPhase.Questions
    } else {
        AdventureStartupPhase.Complete
    }
    AdventureStartupPhase.Questions -> AdventureStartupPhase.Complete
    else -> AdventureStartupPhase.None
}

fun adventureStartupDirective(
    current: AdventureStartupPhase,
    input: String,
    random: Random = Random.Default,
): String = when (current) {
    AdventureStartupPhase.Character ->
        "Create one complete level-1 player character from the player's answers. Fill harmless omissions " +
            "with genre-appropriate defaults and use the selected campaign rules. Before your visible reply, " +
            "emit exactly one machine marker in this format: [[ROSTER_CHARACTER|name=Name|species=Species|" +
            "class=Class|background=Background|level=1|strength=10|dexterity=10|constitution=10|" +
            "intelligence=10|wisdom=10|charisma=10|role=Team|description=One sentence|" +
            "portrait=Concise visual portrait brief]]. Do not use the | character inside a value. Then briefly " +
            "introduce the finished editable character and present the three opening choices: 1 classic D&D, " +
            "2 build it together, or 3 random. Do not begin the adventure and do not roll dice."
    AdventureStartupPhase.Choose -> when (adventureStartupChoice(input)) {
        AdventureStartupChoice.Classic ->
            openingDirective("Classic tabletop opening selected: ${ClassicOpenings.random(random)}.")
        AdventureStartupChoice.Random ->
            openingDirective("Random opening selected: ${RandomOpenings.random(random)}.")
        AdventureStartupChoice.Interview ->
            "Adventure setup interview is selected. Do not begin the adventure yet. Ask one compact, " +
                "numbered set of 3–5 probing questions that collectively establish: (1) where the first " +
                "scene takes place, (2) when it occurs or the era, (3) who the protagonists and important " +
                "people are, (4) what is happening right now, and (5) the party's main goal. Offer a few " +
                "quick suggestions and allow 'surprise me' for any answer. Do not ask the player to narrate " +
                "the first action and do not roll dice."
    }
    AdventureStartupPhase.Questions ->
        openingDirective(
            "Use the player's interview answers as authoritative setup. Fill only harmless missing details yourself.",
        )
    else -> ""
}

private fun openingDirective(seed: String): String =
    "$seed Act as the AI Dungeon Master and write the actual opening scene now. Clearly establish where " +
        "and when it begins, identify who is present, show what is happening immediately, and give the party " +
        "a concrete main goal or urgent lead. Start in motion with sensory detail, NPC/world initiative, and " +
        "a meaningful problem. The AI DM—not the player—must begin the quest chain. End only after the scene " +
        "is fully framed, with a clear invitation for the party's first decision. Do not roll dice for setup."

fun isLegacyPassiveAdventureOpening(text: String): Boolean =
    (text.contains("stand at the threshold of the first scene", ignoreCase = true) &&
        text.contains("Describe what they do in the action box below", ignoreCase = true)) ||
        text.contains("Describe the opening scene, world response, or ruling below", ignoreCase = true)
