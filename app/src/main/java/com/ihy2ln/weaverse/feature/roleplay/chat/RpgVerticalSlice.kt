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

private val ChoiceLine = Regex("(?m)^\\s*(?:CHOICE\\s*)?([1-3])[.):]\\s*(.+?)\\s*$", RegexOption.IGNORE_CASE)
private val ArtMarker = Regex("\\[\\[SCENE_ART\\s*:\\s*([^]|]+)(?:\\|category=([^]|]+))?(?:\\|mood=([^]]+))?]]", RegexOption.IGNORE_CASE)

fun parseRpgActionChoices(text: String): List<RpgActionChoice> = ChoiceLine.findAll(text)
    .map { match ->
        val raw = match.groupValues[2].trim()
        val split = raw.split(" — ", " - ", limit = 2)
        RpgActionChoice(
            id = "choice-${match.groupValues[1]}",
            title = split.first().trim(),
            description = split.getOrNull(1)?.trim().orEmpty(),
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

fun stripRpgMetadata(text: String): String = ArtMarker.replace(text, "").trim()

fun rpgActionDirective(): String = """
For every ordinary adventure scene, end your visible reply with exactly three numbered actionable choices.
Format them as `1. Short title — one sentence`, `2. Short title — one sentence`, and `3. Short title — one sentence`.
Then emit one hidden metadata marker on its own line using an available local scene-art asset ID:
`[[SCENE_ART:asset-id|category=scene-category|mood=scene-mood]]`.
Choose art that matches the location, time, weather, and emotional tone. Never invent a file path.
The player may always reject these choices and write a custom action; custom actions use the same rules and checks.
""".trimIndent()

fun adventureAiStartupFieldsPrompt(): String = """
AI STARTUP. Ask for exactly these three setup fields before beginning:
1. Character backstory
2. Current situation
3. Future goals
Use the answers as campaign canon. Summarize them into a starting objective, a personal complication,
and a suggested opening location. Do not roll dice during setup.
""".trimIndent()

fun characterSelectorStartupPrompt(): String = """
CHARACTER SELECTOR STARTUP. The player has selected the protagonist first.
Generate three distinct opening scenes with different locations, tones, and immediate problems.
Label them 1, 2, and 3, then add no more than one short invitation for a custom opening prompt.
Each scene must include a local scene-art asset marker and a clear first objective.
""".trimIndent()
