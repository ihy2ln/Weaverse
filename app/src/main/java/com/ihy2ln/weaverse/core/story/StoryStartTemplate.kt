package com.ihy2ln.weaverse.core.story

import kotlinx.serialization.Serializable

/**
 * The shared "Create Your Own Adventure" start, used by every mode that opens a new
 * story: RPG campaigns today, Novel next. A mode supplies its own vocabulary through
 * [StoryStartVocabulary]; the questions, the companion rule, and the prompt block are
 * the same everywhere so a start behaves the same wherever it is offered.
 */
@Serializable
enum class StoryCompanionMode(
    val id: String,
    val label: String,
    val blurb: String,
) {
    /** Nobody travels with the protagonist. The hard case the AI keeps breaking. */
    Solo(
        id = "solo",
        label = "Solo",
        blurb = "Alone. No companion joins, now or later, unless you ask.",
    ),
    Duo(
        id = "duo",
        label = "Duo",
        blurb = "The protagonist and exactly one companion.",
    ),
    Party(
        id = "party",
        label = "Party",
        blurb = "A small group of three to five who travel together.",
    ),
    Team(
        id = "team",
        label = "Team",
        blurb = "A larger crew or unit, six or more, with named regulars.",
    ),
    ;

    companion object {
        fun fromId(id: String?): StoryCompanionMode =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: Party
    }
}

/**
 * The instruction that keeps the model honest about company. Models treat a lone
 * protagonist as a problem to fix and introduce a rescuer, a guide, or a talking
 * animal within a paragraph, so the rule is stated as a hard constraint, repeated in
 * the negative, and given an explicit allowance for the people who may still appear.
 */
fun companionRule(mode: StoryCompanionMode): String = when (mode) {
    StoryCompanionMode.Solo -> """
        PARTY SIZE: SOLO — this is a hard constraint, not a preference.
        - The protagonist travels alone. Do not introduce a companion, sidekick, guide,
          mentor, rescuer, familiar, talking animal, or AI helper who joins them.
        - Do not have anyone offer to come along, follow at a distance, or "happen to be
          heading the same way". Do not end a scene with someone attaching themselves.
        - Other people still exist: they can be met, talked to, fought, traded with, and
          left behind. What they may not do is join the protagonist.
        - If the plan or a previous scene implies a companion, write the protagonist
          alone anyway and let that person stay where they are.
    """.trimIndent()

    StoryCompanionMode.Duo -> """
        PARTY SIZE: DUO — exactly one companion travels with the protagonist.
        - Do not add a third travelling member. Others may appear, help, and leave, but
          the travelling pair stays two.
    """.trimIndent()

    StoryCompanionMode.Party -> """
        PARTY SIZE: PARTY — three to five travel together.
        - Keep the travelling group inside that range; extra people are guests, not
          members, unless the player says otherwise.
    """.trimIndent()

    StoryCompanionMode.Team -> """
        PARTY SIZE: TEAM — a larger crew of six or more, with named regulars.
        - Keep the regulars consistent between scenes rather than inventing new faces
          each time.
    """.trimIndent()
}

/** One CYOA question: a prompt plus tappable presets that stay editable. */
data class StoryStartQuestion(
    val id: String,
    val prompt: String,
    val presets: List<String>,
)

/**
 * Per-mode wording for the shared questions. RPG talks about parties and campaigns;
 * Novel talks about casts and books. The question ids stay the same so answers,
 * suggestions, and prompts are interchangeable.
 */
data class StoryStartVocabulary(
    val modeId: String,
    /** "campaign" or "book" — what the start is creating. */
    val workNoun: String,
    /** "the party" or "the cast". */
    val castNoun: String,
    /** "adventure" or "story". */
    val storyNoun: String,
) {
    companion object {
        val Rpg = StoryStartVocabulary("rpg", "campaign", "the party", "adventure")
        val Novel = StoryStartVocabulary("novel", "book", "the cast", "story")
    }
}

/** The shared question set, worded for [vocabulary]. */
fun storyStartQuestions(vocabulary: StoryStartVocabulary): List<StoryStartQuestion> = listOf(
    StoryStartQuestion(
        id = "plot",
        prompt = "What plot premise or central conflict should drive the ${vocabulary.workNoun}?",
        presets = listOf(
            "A hidden mystery", "A survival crisis", "An escort journey", "A political struggle",
            "A treasure hunt", "A difficult homecoming", "An isekai arrival",
        ),
    ),
    StoryStartQuestion(
        id = "goal",
        prompt = "What should ${vocabulary.castNoun}'s first goal be?",
        presets = listOf(
            "Investigate a clue", "Protect someone", "Escape immediate danger",
            "Recover a person or artifact", "Negotiate a fragile peace",
            "Reach a distant location", "Find a way home",
        ),
    ),
    StoryStartQuestion(
        id = "scene",
        prompt = "Where and how should the first scene begin?",
        presets = listOf(
            "A crowded tavern", "A roadside ambush", "A ruined shrine", "A ship in a storm",
            "A locked room", "A celebration", "A new-world arrival",
        ),
    ),
    StoryStartQuestion(
        id = "tone",
        prompt = "What tone and presentation should guide the opening?",
        presets = listOf(
            "Hopeful adventure", "Grim survival", "High-action spectacle", "Mystery and wonder",
            "Character-focused romance", "Light comedy", "Dark fantasy",
        ),
    ),
    StoryStartQuestion(
        id = "complication",
        prompt = "What opening complication or threat should appear?",
        presets = listOf(
            "A hidden betrayal", "A strict time limit", "A missing person", "A pursuing faction",
            "A supernatural omen", "An unexpected ally", "A dangerous misunderstanding",
        ),
    ),
)

/**
 * The block every mode pastes into its planning and scene prompts. Companion rule
 * first: it is the constraint models are most likely to ignore, and the earliest and
 * last instructions are the ones they follow.
 */
fun storyStartPromptBlock(
    companions: StoryCompanionMode,
    answers: Map<String, String>,
    vocabulary: StoryStartVocabulary,
): String = buildString {
    appendLine(companionRule(companions))
    appendLine()
    appendLine("CREATE YOUR OWN ${vocabulary.storyNoun.uppercase()}")
    storyStartQuestions(vocabulary).forEach { question ->
        val answer = answers[question.id].orEmpty().trim()
        if (answer.isNotBlank()) appendLine("${question.id.replaceFirstChar(Char::uppercase)}: $answer")
    }
    append("Company: ${companions.label} — ${companions.blurb}")
}
