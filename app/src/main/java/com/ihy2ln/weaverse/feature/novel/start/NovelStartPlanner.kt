package com.ihy2ln.weaverse.feature.novel.start

import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.core.story.companionRule
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgAdventurePlan
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterBeat
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterPlanPayload
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline
import com.ihy2ln.weaverse.feature.roleplay.campaign.RpgSceneDraft
import com.ihy2ln.weaverse.feature.roleplay.campaign.answer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The novel's copy of the RPG four-step start: Create Your Own Story, the Chapter One
 * plan, verification, and Scene One. It reuses the RPG's outline, scene-guideline and
 * draft shapes so both modes parse the same JSON, and drops everything that only means
 * something at a table — play-as role, game mode, rule system, and house rules.
 */
@Serializable
data class NovelSetupSnapshot(
    val title: String = "Untitled Book",
    val genre: String = "",
    val setting: String = "",
    /** The chosen Setting Details preset's guidance text, blank when none was picked. */
    val settingDetails: String = "",
    val characters: String = "",
    val pointOfView: String = "Third-person limited",
    val tense: String = "Past tense",
    val styleGuide: String = "",
    /** Solo / Duo / Party / Team as an enum id, so an older save still loads. */
    val companions: String = StoryCompanionMode.Party.id,
    /** The chosen Setting Template, by id — the same catalogue campaigns pick from. */
    val settingId: String = "high-fantasy",
    /** The chosen Setting Details preset, by id. */
    val settingDetailId: String = "frontier",
    /** The chosen narrative perspective template, by id. */
    val narrativePovId: String = "third-limited",
    /** The Setting Template's own guidance text for the AI. */
    val settingGuidance: String = "",
    /** The perspective template's own directive. */
    val perspectiveGuidance: String = "",
)

/** One question on the Create Your Own Story step. */
data class NovelStartQuestion(
    val id: String,
    val prompt: String,
    val presets: List<String>,
)

/**
 * The same six questions the campaign asks, worded for a book. "Party" becomes "cast",
 * because a novel has one.
 */
fun novelStartQuestions(): List<NovelStartQuestion> = listOf(
    NovelStartQuestion(
        id = "plot",
        prompt = "What plot premise or central conflict should drive the book?",
        presets = listOf(
            "A hidden mystery", "A survival crisis", "A journey with someone to protect",
            "A political struggle", "A search for something lost", "A difficult homecoming",
            "An arrival in an unfamiliar world",
        ),
    ),
    NovelStartQuestion(
        id = "goal",
        prompt = "What should the cast's first goal be?",
        presets = listOf(
            "Follow up a clue", "Protect someone", "Get out of immediate danger",
            "Recover a person or an object", "Hold a fragile peace together",
            "Reach somewhere far away", "Find a way home",
        ),
    ),
    NovelStartQuestion(
        id = "scene",
        prompt = "Where and how should the first scene begin?",
        presets = listOf(
            "A crowded room", "An ambush on the road", "A ruin no one visits",
            "A ship in a storm", "A locked room", "A celebration", "Waking somewhere new",
        ),
    ),
    NovelStartQuestion(
        id = "cast",
        prompt = "Who is in the opening cast?",
        presets = listOf(
            "The protagonist alone", "The protagonist and one other",
            "A small group who already know each other", "Strangers thrown together",
            "A family", "A protagonist and the person they are hiding from",
        ),
    ),
    NovelStartQuestion(
        id = "tone",
        prompt = "What tone should guide the opening?",
        presets = listOf(
            "Hopeful", "Grim and close", "Fast and cinematic", "Mystery and wonder",
            "Character-focused romance", "Light comedy", "Dark and unsettling",
        ),
    ),
    NovelStartQuestion(
        id = "complication",
        prompt = "What opening complication or threat should appear?",
        presets = listOf(
            "A betrayal no one has noticed yet", "A deadline", "Someone missing",
            "Someone following them", "An omen", "An unexpected ally",
            "A misunderstanding that will cost someone",
        ),
    ),
)

private val novelQuestionIds = novelStartQuestions().map { it.id }

private val plannerJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun NovelSetupSnapshot.header(): String = buildString {
    appendLine("BOOK SETUP")
    appendLine("Title: $title")
    if (genre.isNotBlank()) appendLine("Genre: $genre")
    if (setting.isNotBlank()) appendLine("Setting: $setting")
    if (settingGuidance.isNotBlank()) appendLine("Setting guidance: $settingGuidance")
    if (settingDetails.isNotBlank()) appendLine("Setting details: $settingDetails")
    if (characters.isNotBlank()) appendLine("Main characters: $characters")
    appendLine("POV: $pointOfView")
    if (perspectiveGuidance.isNotBlank()) appendLine("Perspective guidance: $perspectiveGuidance")
    appendLine("Tense: $tense")
    if (styleGuide.isNotBlank()) appendLine("Style guide: $styleGuide")
    append("Company: ${StoryCompanionMode.fromId(companions).label}")
}

/** Step one: three tappable ideas per question, tailored to this book. */
fun novelSuggestionPrompt(setup: NovelSetupSnapshot): String = """
${companionRule(StoryCompanionMode.fromId(setup.companions))}

You are a novel planner. Return ONLY one JSON object with exactly these keys:
{"plot":["","",""],"goal":["","",""],"scene":["","",""],"cast":["","",""],"tone":["","",""],"complication":["","",""]}
Give three concise, distinct, tappable answers per question. Tailor every suggestion to
this book rather than repeating generic defaults — the genre, setting and style guide
below should change what is plausible and what the tone sounds like.

${setup.header()}
""".trimIndent()

fun parseNovelSuggestions(text: String): Map<String, List<String>>? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        val objectValue = plannerJson.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        novelQuestionIds.associateWith { id ->
            objectValue[id]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)
        }.takeIf { result -> result.values.all { it.size == 3 } }
    }.getOrNull()
}

/** Used when the model is unreachable, so the step is never a dead end. */
fun fallbackNovelSuggestions(setup: NovelSetupSnapshot): Map<String, List<String>> {
    val setting = setup.setting.ifBlank { "the book's setting" }
    val cast = setup.characters.ifBlank { "the protagonist" }
    val genre = setup.genre.ifBlank { "the story" }
    return mapOf(
        "plot" to listOf(
            "Something buried in $setting surfaces again",
            "A rival interest threatens an arrangement everyone depended on",
            "$cast turn out to be tied to a disaster nobody has explained",
        ),
        "goal" to listOf(
            "Protect someone who is caught up in it",
            "Reach the first reliable lead before anyone else does",
            "Get somewhere safe, carrying proof of what happened",
        ),
        "scene" to listOf(
            "A familiar place in $setting stops being safe",
            "An arrival at the edge of $setting, badly timed",
            "A quiet conversation broken by the first sign of trouble",
        ),
        "cast" to listOf(
            "$cast, already together",
            "$cast and a local who does not trust them",
            "$cast alone, with everyone else at a distance",
        ),
        "tone" to listOf(
            "Character-driven $genre",
            "Tension held at a low burn, with room to breathe",
            "Mystery, discovery, and choices that cost something",
        ),
        "complication" to listOf(
            "The one person helping is holding something back",
            "There are hours, not days",
            "Someone mistakes them for who they are not",
        ),
    )
}

/** Step two: the chapter outline and the opening-scene guideline. */
fun novelChapterPlanPrompt(setup: NovelSetupSnapshot, plan: RpgAdventurePlan): String = """
${companionRule(StoryCompanionMode.fromId(setup.companions))}

You are preparing Chapter One of a novel. Return ONLY one JSON object matching this shape:
{"outline":{"workingTitle":"","premise":"","primaryObjective":"","antagonist":"","importantLocations":"","beats":[{"id":"beat-1","title":"","summary":"","completed":false}],"optionalBeat":"","majorChallenge":"","climax":"","possibleOutcomes":""},"openingScene":{"title":"","locationAndAtmosphere":"","startingCast":"","immediateObjective":"","conflictAndStakes":"","complication":"","firstDecisionHook":"","sceneArtTags":""}}
Create 3 to 5 flexible beats. Do not write the prose yet. This is a book, not a game:
no rules, no dice, no player, no choice lists. Honour the genre and style guide below
in tone, stakes, and what the world allows.

${setup.header()}

CREATE YOUR OWN STORY
Plot: ${plan.answer("plot", "Choose a fitting central conflict")}
First goal: ${plan.answer("goal", "Choose a fitting first objective")}
First scene: ${plan.answer("scene", "Choose a fitting opening location")}
Cast: ${plan.answer("cast", setup.characters.ifBlank { "The protagonist alone" })}
Tone: ${plan.answer("tone", "Character-driven")}
Complication: ${plan.answer("complication", "Choose a fitting complication")}
""".trimIndent()

fun fallbackNovelChapterPlan(setup: NovelSetupSnapshot, plan: RpgAdventurePlan): RpgChapterPlanPayload {
    val plot = plan.answer("plot", "Something disturbs ${setup.setting.ifBlank { "the world of the book" }}")
    val goal = plan.answer("goal", "Follow up the first sign that something is wrong")
    val location = plan.answer("scene", "A crossroads at the edge of town")
    val cast = plan.answer("cast", setup.characters.ifBlank { "The protagonist alone" })
    val complication = plan.answer("complication", "A deadline")
    return RpgChapterPlanPayload(
        outline = RpgChapterOutline(
            workingTitle = "The First Sign",
            premise = plot,
            primaryObjective = goal,
            antagonist = complication,
            importantLocations = location,
            beats = listOf(
                RpgChapterBeat("beat-1", "The Opening", "The reader meets the cast and the problem lands."),
                RpgChapterBeat("beat-2", "The First Lead", "A decision reveals who can be trusted, and what it will cost."),
                RpgChapterBeat("beat-3", "The Turn", "The chapter's central pressure arrives in full."),
            ),
            optionalBeat = "A quieter scene that shows what the protagonist stands to lose.",
            majorChallenge = "The moment the protagonist cannot avoid the problem any longer.",
            climax = "The chapter's pressure comes to a head.",
            possibleOutcomes = "Resolution opens the next chapter; failure changes the cost without ending the book.",
        ),
        openingScene = RpgOpeningSceneGuideline(
            title = "Chapter One",
            locationAndAtmosphere = location,
            startingCast = cast,
            immediateObjective = goal,
            conflictAndStakes = plot,
            complication = complication,
            firstDecisionHook = "The line that makes the reader turn the page.",
            sceneArtTags = listOf(setup.genre, setup.setting, location).filter { it.isNotBlank() }.joinToString(", "),
        ),
    )
}

/** Step four: Chapter One's opening scene as finished prose. */
fun novelOpeningScenePrompt(
    setup: NovelSetupSnapshot,
    plan: RpgAdventurePlan,
    outline: RpgChapterOutline,
    scene: RpgOpeningSceneGuideline,
): String {
    val rule = companionRule(StoryCompanionMode.fromId(setup.companions))
    return """
$rule

Write the opening scene of this novel now, as finished prose, using the verified plan
below as canon. Return ONLY JSON: {"prose":"the full scene","choices":[],"sceneArtTags":"comma separated tags"}.
Write in ${setup.pointOfView.lowercase()} and ${setup.tense.lowercase()}. Establish the place, the people
present, and the first problem, and end on a line that carries the reader into the next
scene. Leave "choices" empty: this is a book, so there are no options to offer and no
player to address. No planning notes, no headings, no rules or dice language.

${setup.header()}

Plot: ${plan.answer("plot", outline.premise)}
Chapter title: ${outline.workingTitle}
Chapter premise: ${outline.premise}
What the chapter drives at: ${outline.primaryObjective}
Opposition: ${outline.antagonist}
Opening title: ${scene.title}
Location: ${scene.locationAndAtmosphere}
Cast present: ${scene.startingCast}
Immediate objective: ${scene.immediateObjective}
Conflict and stakes: ${scene.conflictAndStakes}
Complication: ${scene.complication}
Closing hook: ${scene.firstDecisionHook}
Art tags: ${scene.sceneArtTags}

$rule
""".trimIndent()
}

fun fallbackNovelSceneDraft(plan: RpgAdventurePlan, scene: RpgOpeningSceneGuideline): RpgSceneDraft = RpgSceneDraft(
    prose = buildString {
        appendLine(scene.locationAndAtmosphere.ifBlank { "The scene opens where the story begins." })
        appendLine()
        appendLine(scene.startingCast.ifBlank { plan.answer("cast", "The protagonist") } + " is here, and the day has not gone as planned.")
        appendLine()
        append(scene.complication.ifBlank { "Something is about to go wrong." })
    },
    choices = emptyList(),
    sceneArtTags = scene.sceneArtTags,
)

/**
 * The Setting Template and perspective catalogues are shared with campaigns, so their
 * guidance is written for a table: parties, players, GMs, and play. Carried into a book
 * unchanged it would drag the table back in through the side door, so the table words
 * are rewritten on the way through. The catalogue itself is untouched — campaigns keep
 * reading the original text.
 */
fun novelizeGuidance(text: String): String {
    if (text.isBlank()) return text
    var out = text
    listOf(
        """\bRun a campaign in\b""" to "Set the book in",
        """\bRun (?:a|the) campaign\b""" to "Write the book",
        """\bplayer characters\b""" to "viewpoint characters",
        """\bplayer character\b""" to "viewpoint character",
        """\bplayer agency\b""" to "the reader's investment",
        """\bthe players\b""" to "the protagonists",
        """\bthe player\b""" to "the protagonist",
        """\bthe party\b""" to "the cast",
        """\bDungeon Master\b""" to "the narrator",
        """\bGame Master\b""" to "the narrator",
        """\bthe GM\b""" to "the narrator",
        """\bcampaigns\b""" to "books",
        """\bcampaign\b""" to "book",
        """\bundermine play\b""" to "undermine the story",
        """\bduring play\b""" to "in the story",
        """\bin play\b""" to "in the story",
    ).forEach { (pattern, replacement) ->
        out = Regex(pattern, RegexOption.IGNORE_CASE).replace(out, replacement)
    }
    return out.trim()
}

/**
 * Genre templates for the setup step. The field stays free text — these only fill it,
 * and a typed genre is never overwritten.
 */
fun novelGenreTemplates(): List<String> = listOf(
    "Literary fiction",
    "Epic fantasy",
    "Urban fantasy",
    "Dark fantasy",
    "Science fiction",
    "Space opera",
    "Cyberpunk",
    "Post-apocalyptic",
    "Isekai / portal fantasy",
    "LitRPG / progression",
    "Mystery",
    "Thriller",
    "Horror",
    "Romance",
    "Romantasy",
    "Historical fiction",
    "Coming of age",
    "Slice of life",
    "Adventure",
    "Western",
    "Satire / comedy",
    "Young adult",
)

/** One style-guide template: a short label and the guidance it writes into the field. */
data class NovelStyleGuideTemplate(
    val id: String,
    val label: String,
    val guidance: String,
)

/**
 * Style-guide templates, the style-guide counterpart to the campaign's Setting
 * Details presets. Picking one writes its guidance into the field, where it stays
 * editable — nothing here is locked in.
 */
fun novelStyleGuideTemplates(): List<NovelStyleGuideTemplate> = listOf(
    NovelStyleGuideTemplate(
        "clean-modern",
        "Clean and modern",
        "Plain, current prose. Short to medium sentences, concrete nouns, few adverbs. " +
            "Let dialogue and action carry the scene; describe only what the viewpoint " +
            "character would actually notice.",
    ),
    NovelStyleGuideTemplate(
        "literary",
        "Literary and close",
        "Careful, unhurried prose with room for interiority. Favour precise images over " +
            "explanation, let subtext do the work, and end scenes a beat earlier than " +
            "expected. Avoid summarising a character's feelings outright.",
    ),
    NovelStyleGuideTemplate(
        "cinematic",
        "Cinematic",
        "Write in visible shots: place, movement, gesture, line of dialogue. Keep the " +
            "camera outside the head unless a thought changes what happens next. Short " +
            "paragraphs, hard cuts between beats.",
    ),
    NovelStyleGuideTemplate(
        "pulp",
        "Fast and pulpy",
        "Momentum first. Keep scenes short, start late and leave early, and end most of " +
            "them on a turn. Plain vocabulary, active verbs, minimal scenery. Dialogue " +
            "should be quick and a little sharper than life.",
    ),
    NovelStyleGuideTemplate(
        "lyrical",
        "Lyrical",
        "Let rhythm matter. Vary sentence length deliberately, use imagery drawn from " +
            "the setting rather than stock metaphor, and allow the occasional long " +
            "sentence to carry a whole moment. Never let the sound obscure the sense.",
    ),
    NovelStyleGuideTemplate(
        "dry-wit",
        "Dry and wry",
        "Understatement over jokes. The narration notices the absurd without pointing " +
            "at it. Keep the comedy in the gap between what people say and what they " +
            "mean, and never undercut a genuinely serious moment for a laugh.",
    ),
    NovelStyleGuideTemplate(
        "grim",
        "Grim and grounded",
        "Consequences stick. Injuries cost something, weather and hunger matter, and " +
            "violence is brief and ugly rather than choreographed. Keep the prose sober " +
            "and specific; no grandstanding, no dwelling on cruelty for its own sake.",
    ),
    NovelStyleGuideTemplate(
        "warm",
        "Warm and character-first",
        "People before plot. Give every scene a relationship doing something, keep " +
            "sensory detail domestic and specific, and let quiet moments run their " +
            "length. Conflict should come from what characters want, not from cruelty.",
    ),
    NovelStyleGuideTemplate(
        "ya",
        "Young adult voice",
        "Close, immediate, and unfussy. A strong first-person-feeling voice even in " +
            "third person, contemporary rhythm, and chapters that end on a pull. Take " +
            "the protagonist's feelings seriously rather than narrating them from above.",
    ),
    NovelStyleGuideTemplate(
        "light-novel",
        "Light novel",
        "Brisk scenes, strong character voice, and frequent dialogue. Keep description " +
            "functional and let reactions carry the comedy or tension. Short chapters, " +
            "clear stakes, and a hook at the end of each.",
    ),
)
