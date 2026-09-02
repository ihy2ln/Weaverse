package com.ihy2ln.weaverse.feature.roleplay.chat

/** One `*keyword` player-turn command typed into the RPG composer. */
data class RpgTurnCommand(
    val keyword: String,
    val description: String,
    /** Whether the DM resolves a check/attack roll before narrating. */
    val requiresRoll: Boolean,
    /** Steering tag prepended to the prompt, e.g. "[Inner thought]". */
    val promptTag: String?,
)

object RpgTurnCommands {
    val all: List<RpgTurnCommand> = listOf(
        RpgTurnCommand("action", "Player action — the DM rolls for it", true, null),
        RpgTurnCommand("thought", "Inner thought — narrated, no roll", false, "[Inner thought]"),
        RpgTurnCommand("speak", "Spoken line to the party", false, "[Spoken aloud]"),
        RpgTurnCommand("check", "Ask for a skill check", true, "[Skill check]"),
        RpgTurnCommand("attack", "Attempt an attack roll", true, "[Attack]"),
        RpgTurnCommand("cast", "Cast a spell — roll for it", true, "[Spellcasting]"),
        RpgTurnCommand("rest", "Take a short rest to recover", false, "[Short rest]"),
    )

    /**
     * Effective command list: built-ins minus removed, plus the user's custom
     * keywords (stored as "keyword|description|requiresRoll" strings).
     */
    fun effectiveCommands(
        custom: Set<String> = emptySet(),
        removed: Set<String> = emptySet(),
    ): List<RpgTurnCommand> {
        val removedLower = removed.map { it.lowercase() }.toSet()
        val defaults = all.filter { it.keyword !in removedLower }
        val customs = custom.mapNotNull { raw ->
            val parts = raw.split('|')
            if (parts.size < 3) return@mapNotNull null
            val keyword = parts[0].trim().lowercase()
            if (!keyword.matches(Regex("[a-z]+"))) return@mapNotNull null
            RpgTurnCommand(
                keyword = keyword,
                description = parts[1].ifBlank { "Custom player turn" },
                requiresRoll = parts[2] == "1",
                promptTag = "[${keyword.replaceFirstChar { it.uppercase() }}]",
            )
        }.sortedBy { it.keyword }
        return defaults + customs
    }

    /** Popup rows whose keyword starts with what has been typed after `*`. */
    fun matches(typed: String, commands: List<RpgTurnCommand> = all): List<RpgTurnCommand> {
        val prefix = typed.trimStart().removePrefix("*").takeWhile { it.isLetter() }.lowercase()
        if (prefix.isBlank()) return commands
        return commands.filter { it.keyword.startsWith(prefix) }
    }

    /** Parses `*keyword rest of the line`; null for ordinary prose. */
    fun parse(input: String, commands: List<RpgTurnCommand> = all): ParsedTurn? {
        val text = input.trimStart()
        if (!text.startsWith("*") || text.length < 2) return null
        val body = text.drop(1)
        val keyword = body.takeWhile { it.isLetter() }.lowercase()
        val command = commands.firstOrNull { it.keyword == keyword } ?: return null
        val rest = body.drop(keyword.length).trimStart(' ', ':', '-', '—').trim()
        return ParsedTurn(command, rest)
    }

    data class ParsedTurn(val command: RpgTurnCommand, val text: String)
}
