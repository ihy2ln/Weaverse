package com.ihy2ln.weaverse.feature.roleplay.textgame

/**
 * Parses the small, human-readable contract used by optional AI narration.
 * The parser never invents a gameplay action: an action is only attached when
 * the model explicitly writes an ACTION token, and the reducer validates it.
 */
internal fun parseTextGameStoryProposal(text: String, sourceId: String): TextGameStoryProposal? {
    val marker = text.lineSequence().indexOfFirst { it.trim().equals("STORY_OPTIONS:", ignoreCase = true) }
    if (marker < 0) return null
    val lines = text.lines()
    val prose = lines.take(marker).joinToString("\n").trim()
    if (prose.isBlank()) return null
    val options = lines.drop(marker + 1).mapNotNull { line ->
        val match = Regex("^\\s*(\\d+)[.)]\\s+(.+?)\\s*$").matchEntire(line) ?: return@mapNotNull null
        val rawLabel = match.groupValues[2].trim()
        val action = Regex("\\s*\\[ACTION:\\s*([A-Za-z0-9_.-]+)]\\s*$", RegexOption.IGNORE_CASE)
            .find(rawLabel)
        TextGameStoryOption(
            id = "${sourceId}-${match.groupValues[1]}",
            label = action?.let { rawLabel.removeRange(it.range).trim() } ?: rawLabel,
            validatedChoiceId = action?.groupValues?.get(1),
        )
    }
    if (options.size < 3) return null
    return TextGameStoryProposal(
        id = "ai-$sourceId",
        prose = prose,
        options = options.take(3),
        sourcePrompt = sourceId,
    )
}
