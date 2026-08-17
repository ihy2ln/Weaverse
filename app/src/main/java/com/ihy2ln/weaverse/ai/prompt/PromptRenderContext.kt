package com.ihy2ln.weaverse.ai.prompt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything a [PromptTemplateEngine] pass needs to resolve one message's
 * template tokens/functions/conditionals against. Built fresh per generation
 * call from whatever scene/book/series data that call site already has.
 */
data class PromptRenderContext(
    val novelTense: String = "past tense",
    val novelLanguage: String = "General English",
    val novelTitle: String = "",
    val seriesTitle: String = "",
    val seriesDescription: String = "",
    val dateToday: String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)),
    val pov: String = "",
    val povType: String = "",
    val povCharacter: String = "",
    val scenePreviousFullText: String = "",
    val scenePreviousPovCharacter: String = "",
    val sceneFullTextCurrent: String = "",
    val textBefore: String = "",
    val textAfter: String = "",
    val storySoFar: String = "",
    val message: String = "",
    val outputWords: Int = 200,
    /** Pre-resolved `{include("Weaverse/X")}` bodies, keyed by component name (e.g. "Codex", "Chat/DefaultContext"). */
    val componentBlocks: Map<String, String> = emptyMap(),
) {
    val hasTextBefore: Boolean get() = textBefore.isNotBlank()
    val hasTextAfter: Boolean get() = textAfter.isNotBlank()
    val isStartOfText: Boolean get() = textBefore.isBlank()
}
