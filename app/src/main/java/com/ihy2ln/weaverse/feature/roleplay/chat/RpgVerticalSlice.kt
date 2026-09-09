package com.ihy2ln.weaverse.feature.roleplay.chat

/** A choice proposed by the RPG Dungeon Master. Kept separate from Text Game state. */
data class RpgActionChoice(
    val id: String,
    val title: String,
    val description: String = "",
    val check: String = "",
)

data class RpgSceneArtChoice(
    val assetId: String,
    val category: String = "scene",
    val mood: String = "",
)

private val ChoiceMarker = Regex(
    "\\[\\[RPG_CHOICE\\|id=([1-3])\\|title=([^]|]+)\\|description=([^]]*)]]",
    RegexOption.IGNORE_CASE,
)
private val ArtMarker = Regex("\\[\\[SCENE_ART\\s*:\\s*([^]|]+)(?:\\|category=([^]|]+))?(?:\\|mood=([^]]+))?]]", RegexOption.IGNORE_CASE)

fun parseRpgActionChoices(text: String): List<RpgActionChoice> = ChoiceMarker.findAll(text)
    .map { match ->
        RpgActionChoice(
            id = "choice-${match.groupValues[1]}",
            title = match.groupValues[2].trim(),
            description = match.groupValues[3].trim(),
        )
    }
    .distinctBy { it.id }
    .take(3)
    .toList()

fun parseRpgSceneArtChoice(text: String): RpgSceneArtChoice? = ArtMarker.find(text)?.let { match ->
    RpgSceneArtChoice(
        assetId = match.groupValues[1].trim(),
        category = match.groupValues.getOrNull(2)?.trim().orEmpty().ifBlank { "scene" },
        mood = match.groupValues.getOrNull(3)?.trim().orEmpty(),
    )
}

fun stripRpgMetadata(text: String): String = ChoiceMarker.replace(ArtMarker.replace(text, ""), "").trim()

fun rpgActionDirective(): String = """
For every ordinary adventure scene, end with exactly three hidden choice markers on separate lines.
Use `[[RPG_CHOICE|id=1|title=Short title|description=One sentence]]`, then ids 2 and 3.
Do not print numbered choices in visible prose and never use the `|` character inside a title or description.
Then emit one hidden metadata marker on its own line using an available local scene-art asset ID:
`[[SCENE_ART:asset-id|category=scene-category|mood=scene-mood]]`.
Choose art that matches the location, time, weather, and emotional tone. Never invent a file path.
The player may always reject these choices and write a custom action; custom actions use the same rules and checks.
""".trimIndent()

fun adventureAiStartupFieldsPrompt(): String = """
AI STARTUP PLAN MODE. Ask the player to complete this six-question setup plan before beginning:
1. Plot premise or central conflict
2. First party goal
3. First scene or location
4. Starting party (saved characters, solo, or a custom companion concept)
5. Tone and presentation
6. Opening complication or threat
The older labels Character backstory, Current situation, and Future goals are covered by the plan answers.
Before each question, provide three concise preset suggestions tailored to the saved New Campaign template,
setting details, mode, rule system, and house rules. The player may tap a suggestion, write a custom answer, or skip.
Each answer may be open-ended, selected from a preset, or skipped. Treat [SKIPPED] as permission to
invent a fitting detail from campaign context. Use the answers as campaign canon. Summarize them into a
starting objective, a personal complication, and a suggested opening location. Do not roll dice during setup.
""".trimIndent()

fun characterSelectorStartupPrompt(): String = """
CHARACTER SELECTOR STARTUP. The player has selected the protagonist first.
Generate three distinct opening scenes with different locations, tones, and immediate problems.
Label them 1, 2, and 3, then add no more than one short invitation for a custom opening prompt.
Each scene must include a local scene-art asset marker and a clear first objective.
""".trimIndent()
