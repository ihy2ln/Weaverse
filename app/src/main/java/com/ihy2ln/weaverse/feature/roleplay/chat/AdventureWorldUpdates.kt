package com.ihy2ln.weaverse.feature.roleplay.chat

/** Structured world changes emitted privately by the AI DM and applied by the app. */
data class AdventureCharacterUpdate(
    val name: String,
    val species: String = "",
    val characterClass: String = "Adventurer",
    val background: String = "",
    val level: Int = 1,
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10,
    val role: String = "NPC",
    val description: String = "",
    val portraitBrief: String = "",
)

data class AdventureLoreUpdate(
    val category: String,
    val name: String,
    val summary: String,
)

data class AdventureWorldUpdates(
    val prose: String,
    val characters: List<AdventureCharacterUpdate>,
    val lore: List<AdventureLoreUpdate>,
    val sceneSynopsis: String = "",
)

private val RosterMarker = Regex(
    "\\[\\[ROSTER_CHARACTER\\|(.+?)]]",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val LoreMarker = Regex(
    "\\[\\[LORE_UPDATE\\|(.+?)]]",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val SceneSynopsisMarker = Regex(
    "\\[\\[SCENE_SYNOPSIS:\\s*(.+?)]]",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

fun adventureWorldUpdatesFrom(text: String): AdventureWorldUpdates {
    fun fields(payload: String): Map<String, String> = payload
        .split('|')
        .mapNotNull { part ->
            val split = part.indexOf('=')
            if (split <= 0) null
            else part.substring(0, split).trim().lowercase() to part.substring(split + 1).trim()
        }
        .toMap()

    val characters = RosterMarker.findAll(text).mapNotNull { match ->
        val value = fields(match.groupValues[1])
        val name = value["name"].orEmpty().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        AdventureCharacterUpdate(
            name = name.take(100),
            species = value["species"].orEmpty().take(80),
            characterClass = value["class"].orEmpty().ifBlank { "Adventurer" }.take(80),
            background = value["background"].orEmpty().take(120),
            level = value["level"]?.toIntOrNull()?.coerceIn(1, 20) ?: 1,
            strength = value["strength"]?.toIntOrNull()?.coerceIn(1, 30) ?: 10,
            dexterity = value["dexterity"]?.toIntOrNull()?.coerceIn(1, 30) ?: 10,
            constitution = value["constitution"]?.toIntOrNull()?.coerceIn(1, 30) ?: 10,
            intelligence = value["intelligence"]?.toIntOrNull()?.coerceIn(1, 30) ?: 10,
            wisdom = value["wisdom"]?.toIntOrNull()?.coerceIn(1, 30) ?: 10,
            charisma = value["charisma"]?.toIntOrNull()?.coerceIn(1, 30) ?: 10,
            role = value["role"].orEmpty().ifBlank { "NPC" }.take(40),
            description = value["description"].orEmpty().take(600),
            portraitBrief = value["portrait"].orEmpty().take(600),
        )
    }.toList()
    val lore = LoreMarker.findAll(text).mapNotNull { match ->
        val value = fields(match.groupValues[1])
        val name = value["name"].orEmpty().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        AdventureLoreUpdate(
            category = value["category"].orEmpty().ifBlank { "Lore" }.take(60),
            name = name.take(120),
            summary = value["summary"].orEmpty().take(1_500),
        )
    }.toList()
    return AdventureWorldUpdates(
        prose = SceneSynopsisMarker.replace(LoreMarker.replace(RosterMarker.replace(text, ""), ""), "").trimStart(),
        characters = characters,
        lore = lore,
        sceneSynopsis = SceneSynopsisMarker.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty().take(1_500),
    )
}

fun adventureWorldProseFrom(text: String): String {
    val cleaned = adventureWorldUpdatesFrom(text).prose
    val partialStarts = listOf("[[ROSTER_", "[[LORE_", "[[SCENE_")
        .map { cleaned.indexOf(it, ignoreCase = true) }
        .filter { it >= 0 }
    return if (partialStarts.isEmpty()) cleaned else cleaned.substring(0, partialStarts.min()).trimEnd()
}

fun adventureWorldUpdateDirective(): String =
    "Maintain the adventure's backend records. Begin every resolved adventure response with " +
        "[[SCENE_SYNOPSIS: one concise cumulative synopsis of the current scene so far]]. When a named " +
        "character is first created or first met, " +
        "emit one private marker before the visible prose: [[ROSTER_CHARACTER|name=Name|species=Species|" +
        "class=Class or role|background=Background|level=1|strength=10|dexterity=10|constitution=10|" +
        "intelligence=10|wisdom=10|charisma=10|role=Team, NPC, Enemy, or Other|description=One concise " +
        "sentence|portrait=Concise visual portrait brief]]. Do not use | inside values and do not repeat a " +
        "marker for an unchanged known character. For an important newly established place, faction, item, " +
        "or fact, emit [[LORE_UPDATE|category=Locations, Factions, Items, or Lore|name=Name|summary=Concise " +
        "current fact]]. These markers are hidden from the player and must never replace the visible response."
