package com.ihy2ln.weaverse.feature.novel.start

import com.ihy2ln.weaverse.core.story.StoryCompanionMode
import com.ihy2ln.weaverse.core.story.StoryStartVocabulary
import com.ihy2ln.weaverse.core.story.companionRule
import com.ihy2ln.weaverse.core.story.storyStartQuestions

/**
 * The Novel side of the shared four-step start, worded for a book. It mirrors the RPG
 * planner — suggestions, chapter plan, opening scene — but drops everything that only
 * makes sense at a table: play-as role, game mode, rule system, and house rules.
 */
data class NovelStartSetup(
    val title: String = "",
    val genre: String = "",
    val setting: String = "",
    val pointOfView: String = "Third-person limited",
    val tense: String = "Past tense",
    val characters: String = "",
    val styleGuide: String = "",
    val companions: StoryCompanionMode = StoryCompanionMode.Party,
)

private fun NovelStartSetup.header(): String = buildString {
    appendLine("BOOK SETUP")
    appendLine("Title: ${title.ifBlank { "Untitled" }}")
    if (genre.isNotBlank()) appendLine("Genre: $genre")
    if (setting.isNotBlank()) appendLine("Setting: $setting")
    appendLine("Point of view: $pointOfView")
    appendLine("Tense: $tense")
    if (characters.isNotBlank()) appendLine("Main characters: $characters")
    if (styleGuide.isNotBlank()) appendLine("Style guide: $styleGuide")
    append("Company: ${companions.label} — ${companions.blurb}")
}

/** Step two: per-question suggestions tailored to this book. */
fun novelSuggestionPrompt(setup: NovelStartSetup): String {
    val ids = storyStartQuestions(StoryStartVocabulary.Novel).joinToString(",") { "\"${it.id}\":[\"\",\"\",\"\"]" }
    return """
${companionRule(setup.companions)}

Suggest opening ideas for a novel. Return ONLY one JSON object of this shape:
{$ids}
Give three short, concrete options per key, each fitting this book and different from the others.

${setup.header()}
""".trimIndent()
}

/** Step three: the chapter outline and the guideline for the first scene. */
fun novelOutlinePrompt(setup: NovelStartSetup, answers: Map<String, String>): String = """
${companionRule(setup.companions)}

You are planning Chapter One of a novel. Return ONLY one JSON object matching this shape:
{"outline":{"workingTitle":"","premise":"","primaryObjective":"","antagonist":"","importantLocations":"","beats":[{"id":"beat-1","title":"","summary":"","completed":false}],"optionalBeat":"","majorChallenge":"","climax":"","possibleOutcomes":""},"openingScene":{"title":"","locationAndAtmosphere":"","startingCast":"","immediateObjective":"","conflictAndStakes":"","complication":"","firstDecisionHook":"","sceneArtTags":""}}
Create 3 to 5 flexible beats. Do not write the prose yet.

${setup.header()}

CREATE YOUR OWN STORY
${storyStartQuestions(StoryStartVocabulary.Novel).mapNotNull { question ->
    answers[question.id]?.trim()?.takeIf { it.isNotBlank() }
        ?.let { "${question.id.replaceFirstChar(Char::uppercase)}: $it" }
}.joinToString("\n")}
""".trimIndent()

/** Step four: the opening prose itself, written into the book's first scene. */
fun novelOpeningPrompt(
    setup: NovelStartSetup,
    outline: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgChapterOutline,
    scene: com.ihy2ln.weaverse.feature.roleplay.campaign.RpgOpeningSceneGuideline,
): String {
    val rule = companionRule(setup.companions)
    return """
$rule

Write the opening scene of this novel as finished prose, using the plan below as canon.
Return ONLY JSON: {"prose":"the scene","choices":[],"sceneArtTags":"comma separated tags"}.
Write in ${setup.pointOfView.lowercase()} and ${setup.tense.lowercase()}. Establish place, the people present,
and the first problem, and end on a line that pulls the reader into the next scene. No
planning notes, no headings, no choice lists — this is a novel, not a game.

${setup.header()}

Chapter title: ${outline.workingTitle}
Premise: ${outline.premise}
Objective: ${outline.primaryObjective}
Opposition: ${outline.antagonist}
Opening title: ${scene.title}
Location: ${scene.locationAndAtmosphere}
Cast present: ${scene.startingCast}
Immediate objective: ${scene.immediateObjective}
Conflict and stakes: ${scene.conflictAndStakes}
Complication: ${scene.complication}

$rule
""".trimIndent()
}
