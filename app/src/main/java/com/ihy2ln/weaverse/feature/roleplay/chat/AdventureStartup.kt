package com.ihy2ln.weaverse.feature.roleplay.chat

import kotlin.random.Random

enum class AdventureStartupPhase(val storageName: String) {
    None(""),
    Character("character"),
    Choose("choose"),
    Questions("questions"),
    CuratedQuestions("curated_questions"),
    Complete("complete"),
}

enum class AdventureStartupChoice {
    Classic,
    Interview,
    Random,
    Ai,
    CharacterSelector,
    Curated,
}

/** A one-tap opening that uses the campaign's saved setting, mode, and rules. */
data class AdventureStartupPreset(
    val id: String,
    val title: String,
    val description: String,
    val command: String,
)

data class AdventureSetupQuickResponse(
    val id: String,
    val title: String,
    val answer: String,
    val isRandom: Boolean = false,
)

data class AdventurePlanQuestion(
    val id: String,
    val prompt: String,
    val presets: List<String>,
)

private val AdventurePlanQuestions = listOf(
    AdventurePlanQuestion("spotlight", "Which character, bond, or goal should be in the spotlight?", listOf("My character's past", "A companion bond", "The party's main goal")),
    AdventurePlanQuestion("situation", "What is the current situation when the story opens?", listOf("A normal day breaks", "We arrive somewhere new", "We are already in danger")),
    AdventurePlanQuestion("goal", "What should the party hope to accomplish first?", listOf("Find answers", "Protect someone", "Find a way home")),
    AdventurePlanQuestion("tone", "What tone should guide the opening?", listOf("Hopeful adventure", "Tense survival", "Mystery and wonder")),
    AdventurePlanQuestion("complication", "What complication should make the opening memorable?", listOf("A hidden betrayal", "A time limit", "An unexpected ally")),
)

fun adventurePlanQuestions(): List<AdventurePlanQuestion> = AdventurePlanQuestions

private val SetupQuickResponses = listOf(
    AdventureSetupQuickResponse("spotlight-character", "Spotlight my character", "Spotlight: center the opening on my main character's history and immediate personal stake."),
    AdventureSetupQuickResponse("spotlight-bond", "Test a companion bond", "Spotlight: center the opening on a companion bond and a choice that tests our trust."),
    AdventureSetupQuickResponse("pursue-goal", "Pursue the main goal", "Spotlight: center the opening on the party's stated goal and give us a concrete first lead."),
    AdventureSetupQuickResponse("start-mystery", "Start with a mystery", "Spotlight: center the opening on a strange clue that connects to the campaign's main mystery."),
    AdventureSetupQuickResponse("rng-spotlight", "RNG spotlight", "Spotlight: randomize the character, bond, or goal in focus. Tone: randomize. Complication: randomize.", true),
    AdventureSetupQuickResponse("rng-chaos", "RNG chaos start", "Spotlight: randomize. Tone: randomize. Complication: introduce a surprising problem that fits the saved campaign.", true),
)

fun adventureSetupQuickResponses(): List<AdventureSetupQuickResponse> = SetupQuickResponses

private val CuratedStartupPresets = listOf(
    AdventureStartupPreset(
        id = "campaign-hook",
        title = "Open on the campaign hook",
        description = "Start at the first authored problem with the setting's factions and tone in motion.",
        command = "Start with the campaign hook",
    ),
    AdventureStartupPreset(
        id = "character-spotlight",
        title = "Spotlight a character",
        description = "Begin with a personal problem tied to the selected character's background and goals.",
        command = "Start with a character spotlight",
    ),
    AdventureStartupPreset(
        id = "mystery-lead",
        title = "Follow a mystery lead",
        description = "Open on a clue, omen, or strange arrival that invites investigation before combat.",
        command = "Start with a mystery lead",
    ),
    AdventureStartupPreset(
        id = "urgent-crisis",
        title = "Drop us into a crisis",
        description = "Begin in immediate danger with a clear objective and a meaningful first decision.",
        command = "Start with an urgent crisis",
    ),
    AdventureStartupPreset(
        id = "isekai-arrival",
        title = "Isekai arrival",
        description = "Cross into a new world with unfamiliar rules, a first ally, and a problem that only this party can solve.",
        command = "Start with an isekai arrival",
    ),
)

fun adventureStartupPresets(): List<AdventureStartupPreset> = CuratedStartupPresets

fun adventureStartupPreset(input: String): AdventureStartupPreset? {
    val normalized = input.trim().lowercase()
    return CuratedStartupPresets.firstOrNull { preset ->
        normalized == preset.id || normalized == preset.command.lowercase() ||
            normalized.contains(preset.id.replace('-', ' ')) ||
            normalized.contains(preset.title.lowercase())
    }
}

private val StartupMarker = Regex(
    "\\[\\[ADVENTURE_STARTUP:\\s*(character|choose|questions|curated_questions|complete)]]",
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
            appendLine("1 · AI Startup — provide character backstory, current situation, and future goals.")
            appendLine("2 · Character Selector — choose the protagonist, then pick one of three random opening scenes.")
            appendLine("3 · Quick random start — I’ll surprise you with the party’s first problem and objective.")
            append("Reply with 1, 2, or 3, or tap a curated start below. This is campaign setup, so no action roll is needed.")
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
    "curated_questions" -> AdventureStartupPhase.CuratedQuestions
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
        adventureStartupPreset(input) != null -> AdventureStartupChoice.Curated
        normalized == "1" || "ai" in normalized || "backstory" in normalized || "goals" in normalized ->
            AdventureStartupChoice.Ai
        normalized == "2" || "character" in normalized || "selector" in normalized ->
            AdventureStartupChoice.CharacterSelector
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
    AdventureStartupPhase.Choose -> when (adventureStartupChoice(input)) {
        AdventureStartupChoice.Ai -> AdventureStartupPhase.Questions
        AdventureStartupChoice.Curated -> AdventureStartupPhase.CuratedQuestions
        else -> AdventureStartupPhase.Complete
    }
    AdventureStartupPhase.Questions -> AdventureStartupPhase.Complete
    AdventureStartupPhase.CuratedQuestions -> AdventureStartupPhase.Complete
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
        AdventureStartupChoice.Curated -> adventureStartupPreset(input)?.let { preset ->
            curatedStartupQuestions(preset)
        } ?: curatedStartupQuestions(null)
        AdventureStartupChoice.Ai -> adventureAiStartupFieldsPrompt()
        AdventureStartupChoice.CharacterSelector -> characterSelectorStartupPrompt()
        AdventureStartupChoice.Classic ->
            openingDirective("Classic tabletop opening selected: ${ClassicOpenings.random(random)}.")
        AdventureStartupChoice.Random ->
            openingDirective("Random opening selected: ${RandomOpenings.random(random)}.")
        AdventureStartupChoice.Interview -> adventureAiStartupFieldsPrompt()
    }
    AdventureStartupPhase.Questions ->
        openingDirective(
            "Use the player's interview answers as authoritative setup. Fill only harmless missing details yourself.",
        )
    AdventureStartupPhase.CuratedQuestions ->
        openingDirective(
            "Use the selected curated opening and the player's answers as authoritative setup. " +
                "If the player asks for randomness or says surprise me, invent fitting details from the saved " +
                "campaign context; otherwise honor their character anchor, tone, and desired complication.",
        )
    else -> ""
}

private fun curatedStartupQuestions(preset: AdventureStartupPreset?): String {
    val selected = preset?.let { "${it.title} selected. ${it.description}" }
        ?: "A curated campaign opening was selected."
    return selected + " Use the saved campaign setting details, mode, rule system, and house rules as authoritative context. " +
        "Before writing the opening scene, present the five-question Adventure Plan: spotlight character/bond/goal, " +
        "current situation, first party goal, tone, and opening complication. For each question offer concise preset " +
        "answers, accept the player's own wording, and allow Skip. Do not begin the adventure yet, do not roll dice, " +
        "and end by inviting the plan answers."
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
