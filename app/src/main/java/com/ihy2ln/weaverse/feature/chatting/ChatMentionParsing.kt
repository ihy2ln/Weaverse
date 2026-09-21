package com.ihy2ln.weaverse.feature.chatting

import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity

/** One `Name: text` row parsed out of a (possibly multi-speaker) reply. */
data class ParsedLine(val character: RpCharacterEntity?, val displayName: String, val text: String)

/**
 * Matches `@name` tokens in [text] against [candidates] (longest name first, so "Jane Doe"
 * wins over "Jane"), honouring a full-name match or a first name of 3+ characters.
 */
fun matchMentionedCharacters(text: String, candidates: List<RpCharacterEntity>): List<RpCharacterEntity> {
    if (!text.contains('@')) return emptyList()
    val matched = linkedMapOf<String, RpCharacterEntity>()
    candidates.sortedByDescending { it.name.length }.forEach { character ->
        if (matched.containsKey(character.id)) return@forEach
        val full = Regex("@${Regex.escape(character.name)}", RegexOption.IGNORE_CASE)
        if (full.containsMatchIn(text)) {
            matched[character.id] = character
            return@forEach
        }
        val firstName = character.name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (firstName.length >= 3 &&
            Regex("@${Regex.escape(firstName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            matched[character.id] = character
        }
    }
    return matched.values.toList()
}

/**
 * Splits a raw reply into rows on leading `Name:` lines, matching against [members]
 * (case-insensitive, first-name tolerant). A name that doesn't match any member still
 * starts its own row, carried as [ParsedLine.displayName]. Lines with no leading name
 * merge into the previous row. Falls back to one row carrying the whole reply when
 * nothing matches — today's single-speaker behaviour for plain channel chat.
 */
fun parseSpeakerLines(raw: String, members: List<RpCharacterEntity>): List<ParsedLine> {
    val rows = mutableListOf<ParsedLine>()
    var currentCharacter: RpCharacterEntity? = null
    var currentName = ""
    var currentText: StringBuilder? = null
    var matchedAny = false
    // Tolerates the model wrapping the name in markdown emphasis, e.g. "**Clarity Lockhart:** ...".
    val nameLine = Regex("^\\*{0,2}([A-Z][A-Za-z'.\\- ]{0,39})\\*{0,2}:\\*{0,2}\\s?(.*)$")
    raw.lines().forEach { line ->
        val match = nameLine.matchEntire(line.trim())
        if (match != null) {
            currentText?.let { rows += ParsedLine(currentCharacter, currentName, it.toString().trim()) }
            val name = match.groupValues[1].trim()
            val member = members.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: members.firstOrNull {
                    it.name.trim().split(Regex("\\s+")).firstOrNull()?.equals(name, ignoreCase = true) == true
                }
            currentCharacter = member
            currentName = member?.name ?: name
            currentText = StringBuilder(match.groupValues[2])
            matchedAny = true
        } else if (currentText != null) {
            currentText!!.append('\n').append(line)
        } else {
            currentText = StringBuilder(line)
        }
    }
    currentText?.let { rows += ParsedLine(currentCharacter, currentName, it.toString().trim()) }
    val cleaned = rows.filter { it.text.isNotBlank() }
    return if (matchedAny && cleaned.isNotEmpty()) cleaned else listOf(ParsedLine(null, "", raw.trim()))
}
