package com.ihy2ln.weaverse.feature.novel.codex

/** A `!kind …` line typed into any composer: what to write, and what it is. */
data class CodexBangCommand(
    val kind: CodexEntryKind,
    /** The keyword as typed, for status messages ("!location"). */
    val keyword: String,
    /** Everything after the keyword — the brief the AI writes from. May be blank. */
    val brief: String,
)

/** One selectable `!kind …` command: its template kind, description, and aliases. */
data class BangCommandSpec(
    val keyword: String,
    val kind: CodexEntryKind,
    val description: String,
    val aliases: List<String>,
)

/**
 * `!location a drowned port city on the Marrow` — one line that writes the
 * prose and files the entry at the same time. The keyword picks the codex
 * template; the rest is the brief. Defaults can be removed and custom
 * keywords added from Settings → Composer commands.
 */
object CodexBang {
    /** The built-in primary commands, as listed in Settings and the popup. */
    val defaultCommands: List<BangCommandSpec> = listOf(
        BangCommandSpec(
            "character",
            CodexEntryKind.Character,
            "Writes the prose and files a character / NPC entry.",
            listOf("char", "npc", "person", "roster"),
        ),
        BangCommandSpec(
            "location",
            CodexEntryKind.Location,
            "Writes the prose and files a place / setting entry.",
            listOf("place", "loc", "setting"),
        ),
        BangCommandSpec(
            "object",
            CodexEntryKind.Item,
            "Writes the prose and files an item / weapon / thing entry.",
            listOf("item", "thing", "gear", "weapon"),
        ),
        BangCommandSpec(
            "lore",
            CodexEntryKind.Lore,
            "Writes the prose and files a history / myth / event entry.",
            listOf("history", "myth", "event"),
        ),
        BangCommandSpec(
            "other",
            CodexEntryKind.Other,
            "Writes the prose and files a note / faction / misc entry.",
            listOf("note", "misc", "faction"),
        ),
    )

    private val defaultAliases: Map<String, CodexEntryKind> = buildMap {
        defaultCommands.forEach { spec ->
            put(spec.keyword, spec.kind)
            spec.aliases.forEach { put(it, spec.kind) }
        }
    }

    /** The one-liner shown in help and in the composer's hint. */
    const val HELP = "!character · !location · !object · !lore · !other — writes it and files it"

    private fun aliasMap(custom: Map<String, String>, removed: Set<String>): Map<String, CodexEntryKind> {
        if (custom.isEmpty() && removed.isEmpty()) return defaultAliases
        val map = defaultAliases.toMutableMap()
        removed.forEach { key -> map.remove(key.lowercase()) }
        custom.forEach { (keyword, kindName) ->
            val kind = runCatching { CodexEntryKind.valueOf(kindName) }.getOrNull()
            if (kind != null) map[keyword.lowercase()] = kind
        }
        return map
    }

    /**
     * Reads a bang command off the front of composer text. Returns null for
     * ordinary prose, so a line that merely contains "!" is never hijacked.
     */
    fun parse(
        input: String,
        custom: Map<String, String> = emptyMap(),
        removed: Set<String> = emptySet(),
    ): CodexBangCommand? {
        val text = input.trimStart()
        if (!text.startsWith("!") || text.length < 2) return null
        val body = text.drop(1)
        val keyword = body.takeWhile { it.isLetter() }
        if (keyword.isBlank()) return null
        val kind = aliasMap(custom, removed)[keyword.lowercase()] ?: return null
        val brief = body.drop(keyword.length).trimStart(' ', ':', '-', '—', '–', '\t', '\n').trim()
        return CodexBangCommand(kind = kind, keyword = "!${keyword.lowercase()}", brief = brief)
    }

    /** Compatibility helper for callers that only need keyword strings. */
    fun suggestions(input: String): List<String> {
        val prefix = input.trimStart().removePrefix("!").takeWhile { it.isLetter() }.lowercase()
        return defaultCommands
            .flatMap { spec -> listOf(spec.keyword) + spec.aliases }
            .filter { prefix.isBlank() || it.startsWith(prefix) }
            .map { "!$it" }
    }
}

/** One row in the composer's `!` command popup and the Settings command list. */
data class BangCommandInfo(
    /** Canonical keyword, without the "!". */
    val keyword: String,
    val description: String,
    val aliases: List<String>,
) {
    val title: String get() = "!$keyword"
}

/** Effective command rows: defaults minus removed, plus the user's custom keywords. */
fun effectiveBangCommands(
    custom: Map<String, String> = emptyMap(),
    removed: Set<String> = emptySet(),
): List<BangCommandInfo> {
    val removedLower = removed.map { it.lowercase() }.toSet()
    val defaults = CodexBang.defaultCommands
        .filter { it.keyword !in removedLower }
        .map { BangCommandInfo(it.keyword, it.description, it.aliases) }
    val customs = custom.entries.mapNotNull { (keyword, kindName) ->
        val kind = runCatching { CodexEntryKind.valueOf(kindName) }.getOrNull() ?: return@mapNotNull null
        BangCommandInfo(
            keyword.lowercase(),
            "Custom command · writes a ${kind.label.lowercase()} entry",
            emptyList(),
        )
    }.sortedBy { it.keyword }
    return defaults + customs
}

/** Popup rows whose keyword or alias starts with what has been typed after `!`. */
fun matchingBangCommands(typed: String, commands: List<BangCommandInfo>): List<BangCommandInfo> {
    val prefix = typed.trimStart().removePrefix("!").takeWhile { it.isLetter() }.lowercase()
    if (prefix.isBlank()) return commands
    return commands.filter { info ->
        info.keyword.startsWith(prefix) || info.aliases.any { it.startsWith(prefix) }
    }
}
