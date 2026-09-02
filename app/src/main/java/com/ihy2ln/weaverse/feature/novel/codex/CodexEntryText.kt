package com.ihy2ln.weaverse.feature.novel.codex

import com.ihy2ln.weaverse.feature.roleplay.characters.RpgCharacterSheet

/**
 * Turns a filled sheet into the prose that goes into the manuscript or the
 * chat. The generated text is *rendered from the entry*, never written
 * separately, so what the reader sees and what the codex holds are the same
 * words — field for field, in the sheet's own order.
 */
object CodexEntryText {
    /** Renders a non-character entry: a location, an object, lore, or anything else. */
    fun render(kind: CodexEntryKind, name: String, sheet: CodexSheetData): String = when (kind) {
        CodexEntryKind.Location -> renderLocation(name, sheet.location)
        CodexEntryKind.Item -> renderItem(name, sheet.item)
        CodexEntryKind.Lore -> renderLore(name, sheet.lore)
        CodexEntryKind.Other -> renderOther(name, sheet.other)
        CodexEntryKind.Character -> name
    }

    private fun renderLocation(name: String, place: LocationSheet): String = compose(
        name = name,
        subtitle = listOf(
            CodexEntryKind.Location.label,
            place.locationType,
            place.scale,
            place.population.takeIf { it > 0 }?.let { "population ${"%,d".format(it)}" }.orEmpty(),
        ),
        sections = listOf(
            "" to place.description,
            "Appearance" to place.appearance,
            "Region" to place.region,
            "Ruler" to place.ruler,
            "Census" to place.census,
            "Notable residents" to place.notableResidents,
            "Factions" to place.factions,
            "History" to place.history,
            "Lore & legends" to place.lore,
            "Points of interest" to place.pointsOfInterest,
            "Services & trade" to place.services,
            "Defenses" to place.defenses,
            "Hooks & secrets" to place.hooks,
            "Notes" to place.notes,
        ),
    )

    private fun renderItem(name: String, item: ItemSheet): String = compose(
        name = name,
        subtitle = listOf(
            CodexEntryKind.Item.label,
            item.itemType,
            item.rarity,
            item.value,
            item.weight,
            if (item.requiresAttunement) "requires attunement" else "",
        ),
        sections = listOf(
            "" to item.description,
            "Appearance" to item.appearance,
            "Stats" to statLine(item),
            "Properties" to item.properties,
            "Effects" to item.effects,
            "Attunement" to item.attunementNotes,
            "Origin" to item.origin,
            "History" to item.history,
            "Lore & legends" to item.lore,
            "Owner" to item.owner,
            "Past owners" to item.pastOwners,
            "Kept at" to item.location,
            "Notes" to item.notes,
        ),
    )

    /** The stat block on one line, so the prose carries the same numbers the sheet does. */
    private fun statLine(item: ItemSheet): String {
        if (!item.statsFilledIn) return ""
        return listOf(
            item.damage.takeIf { it.isNotBlank() }?.let { "Damage $it" }.orEmpty(),
            item.attackBonus.takeIf { it.isNotBlank() }?.let { "Attack $it" }.orEmpty(),
            item.armorClassBonus.takeIf { it.isNotBlank() }?.let { "AC $it" }.orEmpty(),
            item.range.takeIf { it.isNotBlank() }?.let { "Range $it" }.orEmpty(),
            item.charges.takeIf { it.isNotBlank() }?.let { "Charges $it" }.orEmpty(),
            item.saveDc.takeIf { it.isNotBlank() }?.let { "Save DC $it" }.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun renderLore(name: String, lore: LoreSheet): String = compose(
        name = name,
        subtitle = listOf(CodexEntryKind.Lore.label, lore.loreType, lore.era),
        sections = listOf(
            "" to lore.summary,
            "" to lore.explanation,
            "In detail" to lore.details,
            "Origins" to lore.origins,
            "Timeline" to lore.timeline,
            "Why it matters" to lore.significance,
            "Related people" to lore.relatedPeople,
            "Related places" to lore.relatedPlaces,
            "Related objects" to lore.relatedThings,
            "Sources" to lore.sources,
            "Common beliefs" to lore.beliefs,
            "Secrets" to lore.secrets,
            "Notes" to lore.notes,
        ),
    )

    private fun renderOther(name: String, other: OtherSheet): String = compose(
        name = name,
        subtitle = listOf(CodexEntryKind.Other.label, other.subtype),
        sections = listOf(
            "" to other.summary,
            "" to other.description,
            "In detail" to other.details,
            "Background" to other.background,
            "Connections" to other.connections,
        ) + other.customFields.map { it.label.ifBlank { "Field" } to it.value } + listOf(
            "Notes" to other.notes,
        ),
    )

    /** Renders a character from the roster sheet that holds it. */
    fun renderCharacter(
        name: String,
        sheet: RpgCharacterSheet,
        description: String,
        personality: String,
        gear: List<String>,
    ): String = compose(
        name = name,
        subtitle = listOf(
            sheet.characterClass,
            "level ${sheet.level}",
            sheet.species,
            "${sheet.currentHp}/${sheet.maxHp} HP",
            "AC ${sheet.armorClass}",
        ),
        sections = listOf(
            "" to description,
            "Appearance" to sheet.appearance,
            "Personality" to personality,
            "Backstory" to sheet.backstoryAndPersonality,
            "Background" to sheet.background,
            "Alignment" to sheet.alignment,
            "Abilities" to abilityLine(sheet),
            "Skills & proficiencies" to sheet.skillsAndProficiencies,
            "Attacks & actions" to sheet.attacksAndActions,
            "Features & traits" to sheet.featuresAndTraits.ifBlank { sheet.classFeatures },
            "Spells" to sheet.preparedSpells.ifBlank { sheet.spells },
            "Carried" to gear.joinToString(", "),
        ),
    )

    private fun abilityLine(sheet: RpgCharacterSheet): String = listOf(
        "STR ${sheet.strength}",
        "DEX ${sheet.dexterity}",
        "CON ${sheet.constitution}",
        "INT ${sheet.intelligence}",
        "WIS ${sheet.wisdom}",
        "CHA ${sheet.charisma}",
    ).joinToString(" · ")

    /** Title, a small-caps style subtitle, then every filled field in sheet order. */
    private fun compose(
        name: String,
        subtitle: List<String>,
        sections: List<Pair<String, String>>,
    ): String = buildString {
        append(name.trim().ifBlank { "Untitled" })
        val line = subtitle.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ")
        if (line.isNotBlank()) {
            append("\n")
            append(line)
        }
        sections.forEach { (label, value) ->
            val body = value.trim()
            if (body.isBlank()) return@forEach
            append("\n\n")
            if (label.isNotBlank()) {
                append(label)
                append("\n")
            }
            append(body)
        }
    }
}
