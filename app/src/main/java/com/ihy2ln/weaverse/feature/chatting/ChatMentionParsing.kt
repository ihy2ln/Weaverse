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
 * Matches plain-text talk *about* someone — no `@` needed — against [candidates]:
 * a full name anywhere in [text], or a first name of four or more characters as a
 * whole word. The longer floor than [matchMentionedCharacters] keeps short, common
 * first names from turning ordinary sentences into summons.
 */
fun matchNamedCharacters(text: String, candidates: List<RpCharacterEntity>): List<RpCharacterEntity> {
    if (text.isBlank()) return emptyList()
    val matched = linkedMapOf<String, RpCharacterEntity>()
    candidates.sortedByDescending { it.name.length }.forEach { character ->
        if (matched.containsKey(character.id)) return@forEach
        val full = character.name.trim()
        if (full.isBlank()) return@forEach
        if (Regex("\\b${Regex.escape(full)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            matched[character.id] = character
            return@forEach
        }
        val firstName = full.split(Regex("\\s+")).firstOrNull().orEmpty().trim('"', '\'')
        if (firstName.length >= 4 &&
            Regex("\\b${Regex.escape(firstName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
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

/** The `@token` currently being typed at the caret, or null when there isn't one. */
fun activeMentionQuery(text: String, caret: Int = text.length): String? {
    val end = caret.coerceIn(0, text.length)
    val at = text.lastIndexOf('@', (end - 1).coerceAtLeast(0))
    if (at < 0) return null
    // Must start a word: "email@host" is not a mention.
    if (at > 0 && text[at - 1].isLetterOrDigit()) return null
    val token = text.substring(at + 1, end)
    // A mention is one or two words — enough for "@Kaela Storm", not a whole sentence.
    if (token.count { it == ' ' } > 1 || token.contains('\n')) return null
    return token
}

/**
 * Ranks [names] for an `@` [query]: names whose first or any later word starts with it
 * come first, then anything merely containing it. A blank query offers everyone.
 */
fun rankMentionMatches(query: String, names: List<String>): List<String> {
    val needle = query.trim()
    if (needle.isBlank()) return names
    val starts = mutableListOf<String>()
    val wordStarts = mutableListOf<String>()
    val contains = mutableListOf<String>()
    names.forEach { name ->
        val words = name.split(' ', '-', '"').filter { it.isNotBlank() }
        when {
            name.startsWith(needle, ignoreCase = true) -> starts += name
            words.any { it.startsWith(needle, ignoreCase = true) } -> wordStarts += name
            name.contains(needle, ignoreCase = true) -> contains += name
        }
    }
    return starts + wordStarts + contains
}

/** Replaces the `@token` at the caret with [name], leaving a trailing space. */
fun completeMention(text: String, caret: Int, name: String): Pair<String, Int> {
    val end = caret.coerceIn(0, text.length)
    val at = text.lastIndexOf('@', (end - 1).coerceAtLeast(0))
    if (at < 0) return text to end
    val completed = "@" + name + " "
    val next = text.substring(0, at) + completed + text.substring(end)
    return next to (at + completed.length)
}
